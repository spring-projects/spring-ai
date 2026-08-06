/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.bedrock.converse;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.CachePointBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentSource;
import software.amazon.awssdk.services.bedrockruntime.model.ImageBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ImageSource;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.JsonSchemaDefinition;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.OutputConfig;
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormat;
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormatStructure;
import software.amazon.awssdk.services.bedrockruntime.model.S3Location;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.services.bedrockruntime.model.VideoBlock;
import software.amazon.awssdk.services.bedrockruntime.model.VideoFormat;
import software.amazon.awssdk.services.bedrockruntime.model.VideoSource;

import org.springframework.ai.bedrock.converse.api.BedrockCacheOptions;
import org.springframework.ai.bedrock.converse.api.BedrockCacheStrategy;
import org.springframework.ai.bedrock.converse.api.BedrockMediaFormat;
import org.springframework.ai.bedrock.converse.api.ConverseApiUtils;
import org.springframework.ai.bedrock.converse.api.ConverseChatResponseStream;
import org.springframework.ai.bedrock.converse.api.MediaFetcher;
import org.springframework.ai.bedrock.converse.api.URLValidator;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.JsonHelper;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

/**
 * A {@link ChatModel} implementation that uses the Amazon Bedrock Converse API to
 * interact with the <a href=
 * "https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference-supported-models-features.html">Supported
 * models</a>. <br/>
 * <br/>
 * The Converse API doesn't support any embedding models (such as Titan Embeddings G1 -
 * Text) or image generation models (such as Stability AI).
 *
 * <p>
 * https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference.html
 * <p>
 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html
 * <p>
 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ConverseStream.html
 * <p>
 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-ids.html
 * <p>
 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters.html
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @author Alexandros Pappas
 * @author Jihoon Kim
 * @author Soby Chacko
 * @author Sun Yuhan
 * @author Thomas Vitale
 * @author Sebastien Deleuze
 * @author Jewoo Shin
 * @since 1.0.0
 */
public class BedrockProxyChatModel implements ChatModel {

	private static final JsonHelper jsonHelper = new JsonHelper();

	private static final Log logger = LogFactory.getLog(BedrockProxyChatModel.class);

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private final BedrockRuntimeClient bedrockRuntimeClient;

	private final BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient;

	private final BedrockChatOptions options;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	private final ToolCallingManager toolCallingManager;

	/**
	 * Conventions to use for generating observations.
	 */
	private @Nullable ChatModelObservationConvention observationConvention;

	private final MediaFetcher mediaFetcher;

	public BedrockProxyChatModel(BedrockRuntimeClient bedrockRuntimeClient,
			BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient, BedrockChatOptions options,
			ObservationRegistry observationRegistry, ToolCallingManager toolCallingManager) {
		this(bedrockRuntimeClient, bedrockRuntimeAsyncClient, options, observationRegistry, toolCallingManager,
				new MediaFetcher());
	}

	public BedrockProxyChatModel(BedrockRuntimeClient bedrockRuntimeClient,
			BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient, BedrockChatOptions options,
			ObservationRegistry observationRegistry, ToolCallingManager toolCallingManager, MediaFetcher mediaFetcher) {

		Assert.notNull(bedrockRuntimeClient, "bedrockRuntimeClient must not be null");
		Assert.notNull(bedrockRuntimeAsyncClient, "bedrockRuntimeAsyncClient must not be null");
		Assert.notNull(toolCallingManager, "toolCallingManager must not be null");
		Assert.notNull(mediaFetcher, "mediaFetcher must not be null");

		this.bedrockRuntimeClient = bedrockRuntimeClient;
		this.bedrockRuntimeAsyncClient = bedrockRuntimeAsyncClient;
		this.options = options;
		this.observationRegistry = observationRegistry;
		this.toolCallingManager = toolCallingManager;
		this.mediaFetcher = mediaFetcher;
	}

	/**
	 * Invoke the model and return the response.
	 *
	 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters.html
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html
	 * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/bedrockruntime/BedrockRuntimeClient.html#converse
	 * @return The model invocation response.
	 */
	@Override
	public ChatResponse call(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalCall(requestPrompt, null);
	}

	private ChatResponse internalCall(Prompt prompt, @Nullable ChatResponse perviousChatResponse) {

		ConverseRequest converseRequest = this.createRequest(prompt);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(AiProvider.BEDROCK_CONVERSE.value())
			.build();

		ChatResponse chatResponse = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				ConverseResponse converseResponse = this.bedrockRuntimeClient.converse(converseRequest);

				if (logger.isDebugEnabled()) {
					logger.debug("ConverseResponse: " + converseResponse);
				}

				var response = this.toChatResponse(converseResponse, perviousChatResponse);

				observationContext.setResponse(response);

				return response;
			});

		return chatResponse;
	}

	/**
	 * @since 2.0.0
	 */
	@Override
	public BedrockChatOptions getOptions() {
		return this.options;
	}

	ConverseRequest createRequest(Prompt prompt) {

		BedrockChatOptions options = (BedrockChatOptions) prompt.getOptions();
		Assert.state(options != null, "Prompt options must not be null");

		// Get cache options to determine strategy
		BedrockCacheOptions cacheOptions = options.getCacheOptions();
		boolean shouldCacheConversationHistory = cacheOptions != null
				&& cacheOptions.getStrategy() == BedrockCacheStrategy.CONVERSATION_HISTORY;

		// Get all non-system messages
		List<org.springframework.ai.chat.messages.Message> allNonSystemMessages = prompt.getInstructions()
			.stream()
			.filter(message -> message.getMessageType() != MessageType.SYSTEM)
			.toList();

		// Find the last user message index for CONVERSATION_HISTORY caching
		int lastUserMessageIndex = -1;
		if (shouldCacheConversationHistory) {
			for (int i = allNonSystemMessages.size() - 1; i >= 0; i--) {
				if (allNonSystemMessages.get(i).getMessageType() == MessageType.USER) {
					lastUserMessageIndex = i;
					break;
				}
			}
			if (logger.isDebugEnabled()) {
				logger.debug("CONVERSATION_HISTORY caching: lastUserMessageIndex=" + lastUserMessageIndex
						+ ", totalMessages=" + allNonSystemMessages.size());
			}
		}

		// Build instruction messages with potential caching
		List<Message> instructionMessages = new ArrayList<>();
		for (int i = 0; i < allNonSystemMessages.size(); i++) {
			org.springframework.ai.chat.messages.Message message = allNonSystemMessages.get(i);

			// Determine if this message should have a cache point
			// For CONVERSATION_HISTORY: cache point goes on the last user message
			boolean shouldApplyCachePoint = shouldCacheConversationHistory && i == lastUserMessageIndex;

			if (message.getMessageType() == MessageType.USER) {
				List<ContentBlock> contents = new ArrayList<>();
				if (message instanceof UserMessage) {
					var userMessage = (UserMessage) message;
					contents.add(ContentBlock.fromText(userMessage.getText()));

					if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
						List<ContentBlock> mediaContent = userMessage.getMedia()
							.stream()
							.map(this::mapMediaToContentBlock)
							.toList();
						contents.addAll(mediaContent);
					}
				}

				// Apply cache point if this is the last user message
				if (shouldApplyCachePoint) {
					contents.add(ContentBlock.fromCachePoint(buildCachePoint(cacheOptions)));
					logger.debug("Applied cache point on last user message (conversation history caching)");
				}

				instructionMessages.add(Message.builder().content(contents).role(ConversationRole.USER).build());
			}
			else if (message.getMessageType() == MessageType.ASSISTANT) {
				AssistantMessage assistantMessage = (AssistantMessage) message;
				List<ContentBlock> contentBlocks = new ArrayList<>();
				if (StringUtils.hasText(message.getText())) {
					contentBlocks.add(ContentBlock.fromText(message.getText()));
				}
				// Replay the signed Bedrock reasoning blocks, unmodified, before the
				// tool-use blocks. Bedrock validates that reasoning comes before its
				// tool use when the matching tool result is sent back (gh-6413).
				if (assistantMessage instanceof BedrockAssistantMessage bedrockAssistantMessage
						&& bedrockAssistantMessage.hasReasoningContents()) {
					for (BedrockReasoningContent reasoningContent : bedrockAssistantMessage.getReasoningContents()) {
						contentBlocks.add(reasoningContent.toContentBlock());
					}
				}
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {

						var argumentsDocument = ConverseApiUtils
							.convertObjectToDocument(jsonHelper.fromJsonToMap(toolCall.arguments()));

						contentBlocks.add(ContentBlock.fromToolUse(ToolUseBlock.builder()
							.toolUseId(toolCall.id())
							.name(toolCall.name())
							.input(argumentsDocument)
							.build()));

					}
				}

				instructionMessages
					.add(Message.builder().content(contentBlocks).role(ConversationRole.ASSISTANT).build());
			}
			else if (message.getMessageType() == MessageType.TOOL) {
				List<ContentBlock> contentBlocks = new ArrayList<>(
						((ToolResponseMessage) message).getResponses().stream().map(toolResponse -> {
							ToolResultBlock toolResultBlock = ToolResultBlock.builder()
								.toolUseId(toolResponse.id())
								.content(ToolResultContentBlock.builder().text(toolResponse.responseData()).build())
								.build();
							return ContentBlock.fromToolResult(toolResultBlock);
						}).toList());

				instructionMessages.add(Message.builder().content(contentBlocks).role(ConversationRole.USER).build());
			}
			else {
				throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
			}
		}

		// Determine if system message caching should be applied
		boolean shouldCacheSystem = cacheOptions != null
				&& (cacheOptions.getStrategy() == BedrockCacheStrategy.SYSTEM_ONLY
						|| cacheOptions.getStrategy() == BedrockCacheStrategy.SYSTEM_AND_TOOLS);

		if (logger.isDebugEnabled() && cacheOptions != null) {
			logger.debug("Cache strategy: " + cacheOptions.getStrategy() + ", shouldCacheSystem: " + shouldCacheSystem);
		}

		List<org.springframework.ai.chat.messages.Message> systemMessageList = prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.SYSTEM)
			.toList();

		// With multi-block system caching, place the cache point after the
		// second-to-last block so a trailing dynamic block can vary without
		// invalidating the cached prefix.
		boolean multiBlockSystemCaching = cacheOptions != null && cacheOptions.isMultiBlockSystemCaching()
				&& systemMessageList.size() > 1;
		int cacheBoundaryIndex = multiBlockSystemCaching ? systemMessageList.size() - 2 : systemMessageList.size() - 1;

		List<SystemContentBlock> systemMessages = new ArrayList<>();
		for (int i = 0; i < systemMessageList.size(); i++) {
			org.springframework.ai.chat.messages.Message sysMessage = systemMessageList.get(i);

			SystemContentBlock textBlock = SystemContentBlock.builder().text(sysMessage.getText()).build();
			systemMessages.add(textBlock);

			// SystemContentBlock is a union: text and cachePoint must be separate blocks.
			if (i == cacheBoundaryIndex && shouldCacheSystem) {
				SystemContentBlock cachePointBlock = SystemContentBlock.builder()
					.cachePoint(buildCachePoint(cacheOptions))
					.build();
				systemMessages.add(cachePointBlock);
			}
		}

		ToolConfiguration toolConfiguration = null;

		// Add the tool definitions to the request's tools parameter.
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(options);

		// Determine if tool caching should be applied
		boolean shouldCacheTools = cacheOptions != null
				&& (cacheOptions.getStrategy() == BedrockCacheStrategy.TOOLS_ONLY
						|| cacheOptions.getStrategy() == BedrockCacheStrategy.SYSTEM_AND_TOOLS);

		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			List<Tool> bedrockTools = new ArrayList<>();

			for (int i = 0; i < toolDefinitions.size(); i++) {
				ToolDefinition toolDefinition = toolDefinitions.get(i);
				var description = toolDefinition.description();
				var name = toolDefinition.name();
				String inputSchema = toolDefinition.inputSchema();

				// Create tool specification
				Tool tool = Tool.builder()
					.toolSpec(ToolSpecification.builder()
						.name(name)
						.description(description)
						.inputSchema(ToolInputSchema
							.fromJson(ConverseApiUtils.convertObjectToDocument(jsonHelper.fromJsonToMap(inputSchema))))
						.build())
					.build();
				bedrockTools.add(tool);

				// Apply cache point marker after last tool if caching is enabled
				// Tool is a UNION type - toolSpec and cachePoint must be separate objects
				boolean isLastTool = (i == toolDefinitions.size() - 1);
				if (isLastTool && shouldCacheTools) {
					Tool cachePointTool = Tool.builder().cachePoint(buildCachePoint(cacheOptions)).build();
					bedrockTools.add(cachePointTool);
					logger.debug("Applied cache point after tool definitions");
				}
			}

			toolConfiguration = ToolConfiguration.builder().tools(bedrockTools).build();
		}

		InferenceConfiguration inferenceConfiguration = InferenceConfiguration.builder()
			.maxTokens(options.getMaxTokens())
			.stopSequences(options.getStopSequences())
			.temperature(options.getTemperature() != null ? options.getTemperature().floatValue() : null)
			.topP(options.getTopP() != null ? options.getTopP().floatValue() : null)
			.build();

		BedrockChatOptions bedrockOptions = (BedrockChatOptions) prompt.getOptions();
		Assert.notNull(bedrockOptions, "options can't be null here");
		Document additionalModelRequestFields = null;
		if (!CollectionUtils.isEmpty(bedrockOptions.getRequestParameters())) {
			additionalModelRequestFields = ConverseApiUtils
				.convertObjectToDocument(bedrockOptions.getRequestParameters());
		}

		Map<String, String> requestMetadata = ConverseApiUtils
			.getRequestMetadata(prompt.getUserMessage().getMetadata());

		return ConverseRequest.builder()
			.modelId(options.getModel())
			.inferenceConfig(inferenceConfiguration)
			.messages(instructionMessages)
			.system(systemMessages)
			.additionalModelRequestFields(additionalModelRequestFields)
			.toolConfig(toolConfiguration)
			.requestMetadata(requestMetadata)
			.outputConfig(buildOutputConfig(options))
			.build();
	}

	private static CachePointBlock buildCachePoint(@Nullable BedrockCacheOptions cacheOptions) {
		CachePointBlock.Builder builder = CachePointBlock.builder().type("default");
		if (cacheOptions != null && cacheOptions.getTtl() != null) {
			builder.ttl(cacheOptions.getTtl().getValue());
		}
		return builder.build();
	}

	private @Nullable OutputConfig buildOutputConfig(BedrockChatOptions options) {
		String schema = options.getOutputSchema();
		if (schema == null) {
			return null;
		}

		return OutputConfig.builder()
			.textFormat(OutputFormat.builder()
				.type("json_schema")
				.structure(OutputFormatStructure.builder()
					.jsonSchema(JsonSchemaDefinition.builder().schema(schema).name("response_schema").build())
					.build())
				.build())
			.build();
	}

	ContentBlock mapMediaToContentBlock(Media media) {

		var mimeType = media.getMimeType();

		if (BedrockMediaFormat.isSupportedVideoFormat(mimeType)) { // Video
			VideoFormat videoFormat = BedrockMediaFormat.getVideoFormat(mimeType);
			VideoSource videoSource = null;
			if (media.getData() instanceof byte[] bytes) {
				videoSource = VideoSource.builder().bytes(SdkBytes.fromByteArrayUnsafe(bytes)).build();
			}
			else if (media.getData() instanceof String uriText) {
				videoSource = VideoSource.builder().s3Location(S3Location.builder().uri(uriText).build()).build();
			}
			else if (media.getData() instanceof URL url) {
				try {
					videoSource = VideoSource.builder()
						.s3Location(S3Location.builder().uri(url.toURI().toString()).build())
						.build();
				}
				catch (URISyntaxException e) {
					throw new IllegalArgumentException(e);
				}
			}
			else {
				throw new IllegalArgumentException("Invalid video content type: " + media.getData().getClass());
			}

			return ContentBlock.fromVideo(VideoBlock.builder().source(videoSource).format(videoFormat).build());
		}
		else if (BedrockMediaFormat.isSupportedImageFormat(mimeType)) { // Image
			ImageSource.Builder sourceBuilder = ImageSource.builder();
			if (media.getData() instanceof byte[] bytes) {
				sourceBuilder.bytes(SdkBytes.fromByteArrayUnsafe(bytes)).build();
			}
			else if (media.getData() instanceof String text) {

				if (text.startsWith("s3://")) {
					sourceBuilder.s3Location(S3Location.builder().uri(text).build()).build();
				}
				else if (text.startsWith("http://") || text.startsWith("https://")) {
					// Not base64
					if (URLValidator.isValidURLStrict(text)) {
						try {
							byte[] bytes = this.mediaFetcher.fetch(URI.create(text));
							sourceBuilder.bytes(SdkBytes.fromByteArrayUnsafe(bytes)).build();
						}
						catch (SecurityException | RestClientException e) {
							throw new RuntimeException("Failed to read media data from URL: " + text, e);
						}
					}
					else {
						throw new SecurityException("URL is not valid under strict validation rules: " + text);
					}
				}
				else {
					// Assume it's base64-encoded image data
					sourceBuilder.bytes(SdkBytes.fromByteArray(Base64.getDecoder().decode(text)));
				}
			}
			else if (media.getData() instanceof URL url) {

				try {
					String protocol = url.getProtocol();
					if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
						throw new SecurityException("Unsupported URL protocol: " + protocol);
					}
					byte[] bytes = this.mediaFetcher.fetch(url.toURI());
					sourceBuilder.bytes(SdkBytes.fromByteArrayUnsafe(bytes)).build();
				}
				catch (SecurityException | RestClientException | URISyntaxException e) {
					throw new IllegalArgumentException("Failed to read media data from URL: " + url, e);
				}
			}
			else {
				throw new IllegalArgumentException("Invalid Image content type: " + media.getData().getClass());
			}

			return ContentBlock.fromImage(ImageBlock.builder()
				.source(sourceBuilder.build())
				.format(BedrockMediaFormat.getImageFormat(mimeType))
				.build());
		}
		else if (BedrockMediaFormat.isSupportedDocumentFormat(mimeType)) { // Document

			return ContentBlock.fromDocument(DocumentBlock.builder()
				.name(sanitizeDocumentName(media.getName()))
				.format(BedrockMediaFormat.getDocumentFormat(mimeType))
				.source(DocumentSource.builder().bytes(SdkBytes.fromByteArray(media.getDataAsByteArray())).build())
				.build());
		}

		throw new IllegalArgumentException("Unsupported media format: " + mimeType);
	}

	/**
	 * Sanitizes a document name to conform to Amazon Bedrock's naming restrictions. The
	 * name can only contain alphanumeric characters, whitespace characters (no more than
	 * one in a row), hyphens, parentheses, and square brackets.
	 * @param name the document name to sanitize
	 * @return the sanitized document name
	 * @see <a href=
	 * "https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_DocumentBlock.html">DocumentBlock
	 * API Reference</a>
	 */
	static String sanitizeDocumentName(String name) {
		return name.replaceAll("[^a-zA-Z0-9\\s\\-()\\[\\]]", "-");
	}

	/**
	 * Convert {@link ConverseResponse} to {@link ChatResponse} includes model output,
	 * stopReason, usage, metrics etc.
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html#API_runtime_Converse_ResponseSyntax
	 * @param response The Bedrock Converse response.
	 * @return The ChatResponse entity.
	 */
	private ChatResponse toChatResponse(ConverseResponse response, @Nullable ChatResponse perviousChatResponse) {

		Assert.notNull(response, "'response' must not be null.");

		Message message = response.output().message();

		// Preserve Bedrock reasoning blocks (signed reasoning text or redacted content)
		// so the tool-calling loop can replay them, unmodified, on the next request. See
		// gh-6413.
		List<BedrockReasoningContent> reasoningContents = message.content()
			.stream()
			.filter(content -> content.type() == ContentBlock.Type.REASONING_CONTENT)
			.map(content -> BedrockReasoningContent.from(content.reasoningContent()))
			.toList();

		List<ContentBlock> toolUseContentBlocks = message.content()
			.stream()
			.filter(c -> c.type() == ContentBlock.Type.TOOL_USE)
			.toList();
		boolean hasToolUse = !CollectionUtils.isEmpty(toolUseContentBlocks);

		// When the response carries reasoning but no tool use, surface the reasoning on
		// the final-text assistant message without displacing the text returned by
		// ChatResponse.getResult().
		boolean attachReasoningToText = !hasToolUse && !CollectionUtils.isEmpty(reasoningContents);

		List<Generation> generations = new ArrayList<>();
		for (ContentBlock content : message.content()) {
			if (content.type() == ContentBlock.Type.TOOL_USE || content.text() == null) {
				continue;
			}
			AssistantMessage assistantMessage = (attachReasoningToText && generations.isEmpty())
					? BedrockAssistantMessage.builder()
						.content(content.text())
						.properties(Map.of())
						.reasoningContents(reasoningContents)
						.build()
					: AssistantMessage.builder().content(content.text()).properties(Map.of()).build();
			generations.add(new Generation(assistantMessage,
					ChatGenerationMetadata.builder().finishReason(response.stopReasonAsString()).build()));
		}

		List<Generation> allGenerations = new ArrayList<>(generations);

		if (response.stopReasonAsString() != null && generations.isEmpty()) {
			AssistantMessage assistantMessage = attachReasoningToText ? BedrockAssistantMessage.builder()
				.properties(Map.of())
				.reasoningContents(reasoningContents)
				.build() : AssistantMessage.builder().properties(Map.of()).build();
			Generation generation = new Generation(assistantMessage,
					ChatGenerationMetadata.builder().finishReason(response.stopReasonAsString()).build());
			allGenerations.add(generation);
		}

		if (hasToolUse) {

			List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

			for (ContentBlock toolUseContentBlock : toolUseContentBlocks) {

				var functionCallId = toolUseContentBlock.toolUse().toolUseId();
				var functionName = toolUseContentBlock.toolUse().name();
				var functionArguments = toolUseContentBlock.toolUse().input().toString();

				toolCalls
					.add(new AssistantMessage.ToolCall(functionCallId, "function", functionName, functionArguments));
			}

			// Attach the signed reasoning to the same assistant turn as the tool calls so
			// DefaultToolCallingManager carries it into the next request history
			// (gh-6413).
			AssistantMessage assistantMessage = CollectionUtils.isEmpty(reasoningContents)
					? AssistantMessage.builder().content("").properties(Map.of()).toolCalls(toolCalls).build()
					: BedrockAssistantMessage.builder()
						.content("")
						.properties(Map.of())
						.toolCalls(toolCalls)
						.reasoningContents(reasoningContents)
						.build();
			Generation toolCallGeneration = new Generation(assistantMessage,
					ChatGenerationMetadata.builder().finishReason(response.stopReasonAsString()).build());
			allGenerations.add(toolCallGeneration);
		}

		Integer promptTokens = response.usage().inputTokens();
		Integer generationTokens = response.usage().outputTokens();
		int totalTokens = response.usage().totalTokens();
		Integer cacheReadInputTokens = response.usage().cacheReadInputTokens();
		Integer cacheWriteInputTokens = response.usage().cacheWriteInputTokens();

		if (perviousChatResponse != null && perviousChatResponse.getMetadata() != null
				&& perviousChatResponse.getMetadata().getUsage() != null) {

			promptTokens += perviousChatResponse.getMetadata().getUsage().getPromptTokens();
			generationTokens += perviousChatResponse.getMetadata().getUsage().getCompletionTokens();
			totalTokens += perviousChatResponse.getMetadata().getUsage().getTotalTokens();

			// Merge cache metrics from previous response if available
			if (perviousChatResponse.getMetadata().getUsage().getNativeUsage() instanceof TokenUsage) {
				TokenUsage previousTokenUsage = (TokenUsage) perviousChatResponse.getMetadata()
					.getUsage()
					.getNativeUsage();
				if (cacheReadInputTokens == null) {
					cacheReadInputTokens = previousTokenUsage.cacheReadInputTokens();
				}
				else if (previousTokenUsage.cacheReadInputTokens() != null) {
					cacheReadInputTokens += previousTokenUsage.cacheReadInputTokens();
				}
				if (cacheWriteInputTokens == null) {
					cacheWriteInputTokens = previousTokenUsage.cacheWriteInputTokens();
				}
				else if (previousTokenUsage.cacheWriteInputTokens() != null) {
					cacheWriteInputTokens += previousTokenUsage.cacheWriteInputTokens();
				}
			}
		}

		// Create native TokenUsage with cache metrics
		TokenUsage nativeTokenUsage = TokenUsage.builder()
			.inputTokens(promptTokens)
			.outputTokens(generationTokens)
			.totalTokens(totalTokens)
			.cacheReadInputTokens(cacheReadInputTokens)
			.cacheWriteInputTokens(cacheWriteInputTokens)
			.build();

		DefaultUsage usage = new DefaultUsage(promptTokens, generationTokens, totalTokens, nativeTokenUsage,
				cacheReadInputTokens != null ? cacheReadInputTokens.longValue() : null,
				cacheWriteInputTokens != null ? cacheWriteInputTokens.longValue() : null);

		Document modelResponseFields = response.additionalModelResponseFields();

		ConverseMetrics metrics = response.metrics();

		var metadataBuilder = ChatResponseMetadata.builder()
			.id(response.responseMetadata() != null ? response.responseMetadata().requestId() : "Unknown")
			.usage(usage);

		// Add cache metrics to metadata if available (for backward compatibility)
		Map<String, Object> additionalMetadata = new HashMap<>();
		if (response.usage().cacheReadInputTokens() != null) {
			additionalMetadata.put("cacheReadInputTokens", response.usage().cacheReadInputTokens());
		}
		if (response.usage().cacheWriteInputTokens() != null) {
			additionalMetadata.put("cacheWriteInputTokens", response.usage().cacheWriteInputTokens());
		}
		if (!additionalMetadata.isEmpty()) {
			metadataBuilder.metadata(additionalMetadata);
		}

		return new ChatResponse(allGenerations, metadataBuilder.build());
	}

	/**
	 * Invoke the model and return the response stream.
	 *
	 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters.html
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html
	 * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/bedrockruntime/BedrockRuntimeAsyncClient.html#converseStream
	 * @return The model invocation response stream.
	 */
	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalStream(requestPrompt, null);
	}

	private Flux<ChatResponse> internalStream(Prompt prompt, @Nullable ChatResponse perviousChatResponse) {
		Assert.notNull(prompt, "'prompt' must not be null");

		return Flux.deferContextual(contextView -> {

			ConverseRequest converseRequest = this.createRequest(prompt);

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(AiProvider.BEDROCK_CONVERSE.value())
				.streaming(true)
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			Observation parentObservation = contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
			observation.parentObservation(parentObservation);
			try (Observation.Scope ignored = parentObservation != null ? parentObservation.openScope()
					: Observation.Scope.NOOP) {
				observation.start();
			}

			ConverseStreamRequest converseStreamRequest = ConverseStreamRequest.builder()
				.modelId(converseRequest.modelId())
				.inferenceConfig(converseRequest.inferenceConfig())
				.messages(converseRequest.messages())
				.system(converseRequest.system())
				.additionalModelRequestFields(converseRequest.additionalModelRequestFields())
				.toolConfig(converseRequest.toolConfig())
				.requestMetadata(converseRequest.requestMetadata())
				.outputConfig(converseRequest.outputConfig())
				.build();

			Usage accumulatedUsage = null;
			if (perviousChatResponse != null && perviousChatResponse.getMetadata() != null) {
				accumulatedUsage = perviousChatResponse.getMetadata().getUsage();
			}

			Flux<ChatResponse> chatResponses = new ConverseChatResponseStream(this.bedrockRuntimeAsyncClient,
					converseStreamRequest, accumulatedUsage)
				.stream();

			ChatOptions options = prompt.getOptions();
			Assert.state(options != null, "Prompt options must not be null");

			Flux<ChatResponse> chatResponseFlux = chatResponses.concatMap(chatResponse -> Flux.just(chatResponse))
				.doOnNext(ignored -> observationContext.recordTimeToFirstChunk())
				.doOnError(observation::error)
				.doFinally(s -> observation.stop())
				.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));

			return new MessageAggregator().aggregate(chatResponseFlux, observationContext::setResponse);
		});
	}

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Look at the options of the provided prompt. If none are provided, return a new
	 * prompt using this model {@link ChatModel#getOptions() options}. Otherwise, use the
	 * prompt as is.
	 */
	private Prompt buildRequestPrompt(Prompt prompt) {
		if (prompt.getOptions() == null) {
			return prompt.mutate().chatOptions(this.getOptions()).build();
		}
		else {
			return prompt;
		}
	}

	public static final class Builder {

		private @Nullable AwsCredentialsProvider credentialsProvider;

		private @Nullable Region region;

		private Duration timeout = Duration.ofMinutes(5L);

		private Duration connectionTimeout = Duration.ofSeconds(5L);

		private Duration asyncReadTimeout = Duration.ofSeconds(30L);

		private Duration connectionAcquisitionTimeout = Duration.ofSeconds(30L);

		private Duration socketTimeout = Duration.ofSeconds(30L);

		private @Nullable ToolCallingManager toolCallingManager;

		private BedrockChatOptions options = BedrockChatOptions.builder().build();

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private @Nullable ChatModelObservationConvention customObservationConvention;

		private @Nullable BedrockRuntimeClient bedrockRuntimeClient;

		private @Nullable BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient;

		private Builder() {
		}

		/**
		 * Sets the tool calling manager used for internal tool execution.
		 * @param toolCallingManager the tool calling manager
		 * @return this builder
		 * @deprecated since 2.0.0 for removal in 3.0.0 — internal tool execution in
		 * {@link BedrockProxyChatModel} is superseded by {@code ToolCallingAdvisor} used
		 * via {@code ChatClient}.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
			return this;
		}

		public Builder credentialsProvider(AwsCredentialsProvider credentialsProvider) {
			Assert.notNull(credentialsProvider, "'credentialsProvider' must not be null.");
			this.credentialsProvider = credentialsProvider;
			return this;
		}

		public Builder region(@Nullable Region region) {
			this.region = region;
			return this;
		}

		public Builder timeout(Duration timeout) {
			Assert.notNull(timeout, "'timeout' must not be null.");
			this.timeout = timeout;
			return this;
		}

		public Builder connectionTimeout(Duration connectionTimeout) {
			Assert.notNull(connectionTimeout, "'connectionTimeout' must not be null.");
			this.connectionTimeout = connectionTimeout;
			return this;
		}

		public Builder asyncReadTimeout(Duration asyncReadTimeout) {
			Assert.notNull(asyncReadTimeout, "'asyncReadTimeout' must not be null.");
			this.asyncReadTimeout = asyncReadTimeout;
			return this;
		}

		public Builder connectionAcquisitionTimeout(Duration connectionAcquisitionTimeout) {
			Assert.notNull(connectionAcquisitionTimeout, "'connectionAcquisitionTimeout' must not be null.");
			this.connectionAcquisitionTimeout = connectionAcquisitionTimeout;
			return this;
		}

		public Builder socketTimeout(Duration socketTimeout) {
			Assert.notNull(socketTimeout, "'socketTimeout' must not be null.");
			this.socketTimeout = socketTimeout;
			return this;
		}

		public Builder options(BedrockChatOptions options) {
			Assert.notNull(options, "'options' must not be null.");
			this.options = options;
			return this;
		}

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			Assert.notNull(observationRegistry, "'observationRegistry' must not be null.");
			this.observationRegistry = observationRegistry;
			return this;
		}

		public Builder customObservationConvention(ChatModelObservationConvention observationConvention) {
			Assert.notNull(observationConvention, "'observationConvention' must not be null.");
			this.customObservationConvention = observationConvention;
			return this;
		}

		public Builder bedrockRuntimeClient(@Nullable BedrockRuntimeClient bedrockRuntimeClient) {
			this.bedrockRuntimeClient = bedrockRuntimeClient;
			return this;
		}

		public Builder bedrockRuntimeAsyncClient(@Nullable BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient) {
			this.bedrockRuntimeAsyncClient = bedrockRuntimeAsyncClient;
			return this;
		}

		public BedrockProxyChatModel build() {

			if (this.bedrockRuntimeClient == null) {

				var httpClientBuilder = ApacheHttpClient.builder()
					.connectionAcquisitionTimeout(this.connectionAcquisitionTimeout)
					.connectionTimeout(this.connectionTimeout)
					.socketTimeout(this.socketTimeout);

				this.bedrockRuntimeClient = BedrockRuntimeClient.builder()
					.region(getRegion())
					.httpClientBuilder(httpClientBuilder)
					.credentialsProvider(this.credentialsProvider)
					.overrideConfiguration(c -> c.apiCallTimeout(this.timeout))
					.build();
			}

			if (this.bedrockRuntimeAsyncClient == null) {

				var httpClientBuilder = NettyNioAsyncHttpClient.builder()
					.tcpKeepAlive(true)
					.readTimeout(this.asyncReadTimeout)
					.connectionTimeout(this.connectionTimeout)
					.connectionAcquisitionTimeout(this.connectionAcquisitionTimeout)
					.maxConcurrency(200);

				this.bedrockRuntimeAsyncClient = BedrockRuntimeAsyncClient.builder()
					.region(getRegion())
					.httpClientBuilder(httpClientBuilder)
					.credentialsProvider(this.credentialsProvider)
					.overrideConfiguration(c -> c.apiCallTimeout(this.timeout))
					.build();
			}

			this.toolCallingManager = this.toolCallingManager != null ? this.toolCallingManager
					: ToolCallingManager.builder().observationRegistry(this.observationRegistry).build();

			BedrockProxyChatModel bedrockProxyChatModel = new BedrockProxyChatModel(this.bedrockRuntimeClient,
					this.bedrockRuntimeAsyncClient, this.options, this.observationRegistry, this.toolCallingManager);

			if (this.customObservationConvention != null) {
				bedrockProxyChatModel.setObservationConvention(this.customObservationConvention);
			}

			return bedrockProxyChatModel;
		}

		private Region getRegion() {
			if (this.region != null) {
				return this.region;
			}
			try {
				return DefaultAwsRegionProviderChain.builder().build().getRegion();
			}
			catch (SdkClientException e) {
				logger.debug("Failed to load region from DefaultAwsRegionProviderChain, using US_EAST_1");
				return Region.US_EAST_1;
			}
		}

	}

}
