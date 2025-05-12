/*
 * Copyright 2023-2025 the original author or authors.
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

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitFailureHandler;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentSource;
import software.amazon.awssdk.services.bedrockruntime.model.ImageBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ImageSource;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.S3Location;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
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

import org.springframework.ai.bedrock.converse.api.BedrockMediaFormat;
import org.springframework.ai.bedrock.converse.api.ConverseApiUtils;
import org.springframework.ai.bedrock.converse.api.URLValidator;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
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
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

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
 * @since 1.0.0
 */
public class BedrockProxyChatModel implements ChatModel {

	private static final Logger logger = LoggerFactory.getLogger(BedrockProxyChatModel.class);

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	private final BedrockRuntimeClient bedrockRuntimeClient;

	private final BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient;

	private ToolCallingChatOptions defaultOptions;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	private final ToolCallingManager toolCallingManager;

	/**
	 * The tool execution eligibility predicate used to determine if a tool can be
	 * executed.
	 */
	private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

	/**
	 * Conventions to use for generating observations.
	 */
	private ChatModelObservationConvention observationConvention;

	public BedrockProxyChatModel(BedrockRuntimeClient bedrockRuntimeClient,
			BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient, ToolCallingChatOptions defaultOptions,
			ObservationRegistry observationRegistry, ToolCallingManager toolCallingManager) {
		this(bedrockRuntimeClient, bedrockRuntimeAsyncClient, defaultOptions, observationRegistry, toolCallingManager,
				new DefaultToolExecutionEligibilityPredicate());
	}

	public BedrockProxyChatModel(BedrockRuntimeClient bedrockRuntimeClient,
			BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient, ToolCallingChatOptions defaultOptions,
			ObservationRegistry observationRegistry, ToolCallingManager toolCallingManager,
			ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {

		Assert.notNull(bedrockRuntimeClient, "bedrockRuntimeClient must not be null");
		Assert.notNull(bedrockRuntimeAsyncClient, "bedrockRuntimeAsyncClient must not be null");
		Assert.notNull(toolCallingManager, "toolCallingManager must not be null");
		Assert.notNull(toolExecutionEligibilityPredicate, "toolExecutionEligibilityPredicate must not be null");

		this.bedrockRuntimeClient = bedrockRuntimeClient;
		this.bedrockRuntimeAsyncClient = bedrockRuntimeAsyncClient;
		this.defaultOptions = defaultOptions;
		this.observationRegistry = observationRegistry;
		this.toolCallingManager = toolCallingManager;
		this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
	}

	private static ToolCallingChatOptions from(ChatOptions options) {
		return ToolCallingChatOptions.builder()
			.model(options.getModel())
			.maxTokens(options.getMaxTokens())
			.stopSequences(options.getStopSequences())
			.temperature(options.getTemperature())
			.topP(options.getTopP())
			.build();
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

	private ChatResponse internalCall(Prompt prompt, ChatResponse perviousChatResponse) {

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

				logger.debug("ConverseResponse: {}", converseResponse);

				var response = this.toChatResponse(converseResponse, perviousChatResponse);

				observationContext.setResponse(response);

				return response;
			});

		if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), chatResponse)
				&& chatResponse.hasFinishReasons(Set.of(StopReason.TOOL_USE.toString()))) {
			var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, chatResponse);
			if (toolExecutionResult.returnDirect()) {
				// Return tool execution result directly to the client.
				return ChatResponse.builder()
					.from(chatResponse)
					.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
					.build();
			}
			else {
				// Send the tool execution result back to the model.
				return this.internalCall(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
						chatResponse);
			}
		}
		return chatResponse;
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	Prompt buildRequestPrompt(Prompt prompt) {
		ToolCallingChatOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				runtimeOptions = toolCallingChatOptions.copy();
			}
			else {
				runtimeOptions = from(prompt.getOptions());
			}
		}

		// Merge runtime options with the default options
		ToolCallingChatOptions updatedRuntimeOptions = null;
		if (runtimeOptions == null) {
			updatedRuntimeOptions = this.defaultOptions.copy();
		}
		else {
			if (runtimeOptions.getFrequencyPenalty() != null) {
				logger.warn("The frequencyPenalty option is not supported by BedrockProxyChatModel. Ignoring.");
			}
			if (runtimeOptions.getPresencePenalty() != null) {
				logger.warn("The presencePenalty option is not supported by BedrockProxyChatModel. Ignoring.");
			}
			if (runtimeOptions.getTopK() != null) {
				logger.warn("The topK option is not supported by BedrockProxyChatModel. Ignoring.");
			}
			updatedRuntimeOptions = ToolCallingChatOptions.builder()
				.model(runtimeOptions.getModel() != null ? runtimeOptions.getModel() : this.defaultOptions.getModel())
				.maxTokens(runtimeOptions.getMaxTokens() != null ? runtimeOptions.getMaxTokens()
						: this.defaultOptions.getMaxTokens())
				.stopSequences(runtimeOptions.getStopSequences() != null ? runtimeOptions.getStopSequences()
						: this.defaultOptions.getStopSequences())
				.temperature(runtimeOptions.getTemperature() != null ? runtimeOptions.getTemperature()
						: this.defaultOptions.getTemperature())
				.topP(runtimeOptions.getTopP() != null ? runtimeOptions.getTopP() : this.defaultOptions.getTopP())

				.toolCallbacks(runtimeOptions.getToolCallbacks() != null ? runtimeOptions.getToolCallbacks()
						: this.defaultOptions.getToolCallbacks())
				.toolNames(runtimeOptions.getToolNames() != null ? runtimeOptions.getToolNames()
						: this.defaultOptions.getToolNames())
				.toolContext(runtimeOptions.getToolContext() != null ? runtimeOptions.getToolContext()
						: this.defaultOptions.getToolContext())
				.internalToolExecutionEnabled(runtimeOptions.getInternalToolExecutionEnabled() != null
						? runtimeOptions.getInternalToolExecutionEnabled()
						: this.defaultOptions.getInternalToolExecutionEnabled())
				.build();
		}

		ToolCallingChatOptions.validateToolCallbacks(updatedRuntimeOptions.getToolCallbacks());

		return new Prompt(prompt.getInstructions(), updatedRuntimeOptions);
	}

	ConverseRequest createRequest(Prompt prompt) {

		List<Message> instructionMessages = prompt.getInstructions()
			.stream()
			.filter(message -> message.getMessageType() != MessageType.SYSTEM)
			.map(message -> {
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
					return Message.builder().content(contents).role(ConversationRole.USER).build();
				}
				else if (message.getMessageType() == MessageType.ASSISTANT) {
					AssistantMessage assistantMessage = (AssistantMessage) message;
					List<ContentBlock> contentBlocks = new ArrayList<>();
					if (StringUtils.hasText(message.getText())) {
						contentBlocks.add(ContentBlock.fromText(message.getText()));
					}
					if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
						for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {

							var argumentsDocument = ConverseApiUtils
								.convertObjectToDocument(ModelOptionsUtils.jsonToMap(toolCall.arguments()));

							contentBlocks.add(ContentBlock.fromToolUse(ToolUseBlock.builder()
								.toolUseId(toolCall.id())
								.name(toolCall.name())
								.input(argumentsDocument)
								.build()));

						}
					}
					return Message.builder().content(contentBlocks).role(ConversationRole.ASSISTANT).build();
				}
				else if (message.getMessageType() == MessageType.TOOL) {
					List<ContentBlock> contentBlocks = ((ToolResponseMessage) message).getResponses()
						.stream()
						.map(toolResponse -> {
							ToolResultBlock toolResultBlock = ToolResultBlock.builder()
								.toolUseId(toolResponse.id())
								.content(ToolResultContentBlock.builder().text(toolResponse.responseData()).build())
								.build();
							return ContentBlock.fromToolResult(toolResultBlock);
						})
						.toList();
					return Message.builder().content(contentBlocks).role(ConversationRole.USER).build();
				}
				else {
					throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
				}
			})
			.toList();

		List<SystemContentBlock> systemMessages = prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.SYSTEM)
			.map(sysMessage -> SystemContentBlock.builder().text(sysMessage.getText()).build())
			.toList();

		ToolCallingChatOptions updatedRuntimeOptions = prompt.getOptions().copy();

		ToolConfiguration toolConfiguration = null;

		// Add the tool definitions to the request's tools parameter.
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(updatedRuntimeOptions);

		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			List<Tool> bedrockTools = toolDefinitions.stream().map(toolDefinition -> {
				var description = toolDefinition.description();
				var name = toolDefinition.name();
				String inputSchema = toolDefinition.inputSchema();
				return Tool.builder()
					.toolSpec(ToolSpecification.builder()
						.name(name)
						.description(description)
						.inputSchema(ToolInputSchema.fromJson(
								ConverseApiUtils.convertObjectToDocument(ModelOptionsUtils.jsonToMap(inputSchema))))
						.build())
					.build();
			}).toList();

			toolConfiguration = ToolConfiguration.builder().tools(bedrockTools).build();
		}

		InferenceConfiguration inferenceConfiguration = InferenceConfiguration.builder()
			.maxTokens(updatedRuntimeOptions.getMaxTokens())
			.stopSequences(updatedRuntimeOptions.getStopSequences())
			.temperature(updatedRuntimeOptions.getTemperature() != null
					? updatedRuntimeOptions.getTemperature().floatValue() : null)
			.topP(updatedRuntimeOptions.getTopP() != null ? updatedRuntimeOptions.getTopP().floatValue() : null)
			.build();

		Document additionalModelRequestFields = ConverseApiUtils
			.getChatOptionsAdditionalModelRequestFields(this.defaultOptions, prompt.getOptions());

		return ConverseRequest.builder()
			.modelId(updatedRuntimeOptions.getModel())
			.inferenceConfig(inferenceConfiguration)
			.messages(instructionMessages)
			.system(systemMessages)
			.additionalModelRequestFields(additionalModelRequestFields)
			.toolConfig(toolConfiguration)
			.build();
	}

	private ContentBlock mapMediaToContentBlock(Media media) {

		var mimeType = media.getMimeType();

		if (BedrockMediaFormat.isSupportedVideoFormat(mimeType)) { // Video
			VideoFormat videoFormat = BedrockMediaFormat.getVideoFormat(mimeType);
			VideoSource videoSource = null;
			if (media.getData() instanceof byte[] bytes) {
				videoSource = VideoSource.builder().bytes(SdkBytes.fromByteArrayUnsafe(bytes)).build();
			}
			else if (media.getData() instanceof String uriText) {
				// if (URLValidator.isValidURLBasic(uriText)) {
				videoSource = VideoSource.builder().s3Location(S3Location.builder().uri(uriText).build()).build();
				// }
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

				if (URLValidator.isValidURLBasic(text)) {
					try {
						URL url = new URL(text);
						URLConnection connection = url.openConnection();
						try (InputStream is = connection.getInputStream()) {
							sourceBuilder.bytes(SdkBytes.fromByteArrayUnsafe(StreamUtils.copyToByteArray(is))).build();
						}
					}
					catch (IOException e) {
						throw new RuntimeException("Failed to read media data from URL: " + text, e);
					}
				}
				else {
					sourceBuilder.bytes(SdkBytes.fromByteArray(Base64.getDecoder().decode(text)));
				}
			}
			else if (media.getData() instanceof URL url) {

				try (InputStream is = url.openConnection().getInputStream()) {
					byte[] imageBytes = StreamUtils.copyToByteArray(is);
					sourceBuilder.bytes(SdkBytes.fromByteArrayUnsafe(imageBytes)).build();
				}
				catch (IOException e) {
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
				.name(media.getName())
				.format(BedrockMediaFormat.getDocumentFormat(mimeType))
				.source(DocumentSource.builder().bytes(SdkBytes.fromByteArray(media.getDataAsByteArray())).build())
				.build());
		}

		throw new IllegalArgumentException("Unsupported media format: " + mimeType);
	}

	private static byte[] getContentMediaData(Object mediaData) {
		if (mediaData instanceof byte[] bytes) {
			return bytes;
		}
		else if (mediaData instanceof String text) {
			if (URLValidator.isValidURLBasic(text)) {
				try {
					URL url = new URL(text);
					URLConnection connection = url.openConnection();
					try (InputStream is = connection.getInputStream()) {
						return StreamUtils.copyToByteArray(is);
					}
				}
				catch (IOException e) {
					throw new RuntimeException("Failed to read media data from URL: " + text, e);
				}
			}
			return text.getBytes();
		}
		else if (mediaData instanceof URL url) {
			try (InputStream is = url.openConnection().getInputStream()) {
				return StreamUtils.copyToByteArray(is);
			}
			catch (IOException e) {
				throw new RuntimeException("Failed to read media data from URL: " + url, e);
			}
		}
		else {
			throw new IllegalArgumentException("Unsupported media data type: " + mediaData.getClass().getSimpleName());
		}
	}

	/**
	 * Convert {@link ConverseResponse} to {@link ChatResponse} includes model output,
	 * stopReason, usage, metrics etc.
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html#API_runtime_Converse_ResponseSyntax
	 * @param response The Bedrock Converse response.
	 * @return The ChatResponse entity.
	 */
	private ChatResponse toChatResponse(ConverseResponse response, ChatResponse perviousChatResponse) {

		Assert.notNull(response, "'response' must not be null.");

		Message message = response.output().message();

		List<Generation> generations = message.content()
			.stream()
			.filter(content -> content.type() != ContentBlock.Type.TOOL_USE)
			.map(content -> new Generation(new AssistantMessage(content.text(), Map.of()),
					ChatGenerationMetadata.builder().finishReason(response.stopReasonAsString()).build()))
			.toList();

		List<Generation> allGenerations = new ArrayList<>(generations);

		if (response.stopReasonAsString() != null && generations.isEmpty()) {
			Generation generation = new Generation(new AssistantMessage(null, Map.of()),
					ChatGenerationMetadata.builder().finishReason(response.stopReasonAsString()).build());
			allGenerations.add(generation);
		}

		List<ContentBlock> toolUseContentBlocks = message.content()
			.stream()
			.filter(c -> c.type() == ContentBlock.Type.TOOL_USE)
			.toList();

		if (!CollectionUtils.isEmpty(toolUseContentBlocks)) {

			List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

			for (ContentBlock toolUseContentBlock : toolUseContentBlocks) {

				var functionCallId = toolUseContentBlock.toolUse().toolUseId();
				var functionName = toolUseContentBlock.toolUse().name();
				var functionArguments = toolUseContentBlock.toolUse().input().toString();

				toolCalls
					.add(new AssistantMessage.ToolCall(functionCallId, "function", functionName, functionArguments));
			}

			AssistantMessage assistantMessage = new AssistantMessage("", Map.of(), toolCalls);
			Generation toolCallGeneration = new Generation(assistantMessage,
					ChatGenerationMetadata.builder().finishReason(response.stopReasonAsString()).build());
			allGenerations.add(toolCallGeneration);
		}

		Integer promptTokens = response.usage().inputTokens();
		Integer generationTokens = response.usage().outputTokens();
		int totalTokens = response.usage().totalTokens();

		if (perviousChatResponse != null && perviousChatResponse.getMetadata() != null
				&& perviousChatResponse.getMetadata().getUsage() != null) {

			promptTokens += perviousChatResponse.getMetadata().getUsage().getPromptTokens();
			generationTokens += perviousChatResponse.getMetadata().getUsage().getCompletionTokens();
			totalTokens += perviousChatResponse.getMetadata().getUsage().getTotalTokens();
		}

		DefaultUsage usage = new DefaultUsage(promptTokens, generationTokens, totalTokens);

		Document modelResponseFields = response.additionalModelResponseFields();

		ConverseMetrics metrics = response.metrics();

		var chatResponseMetaData = ChatResponseMetadata.builder()
			.id(response.responseMetadata() != null ? response.responseMetadata().requestId() : "Unknown")
			.usage(usage)
			.build();

		return new ChatResponse(allGenerations, chatResponseMetaData);
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

	private Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse perviousChatResponse) {
		Assert.notNull(prompt, "'prompt' must not be null");

		return Flux.deferContextual(contextView -> {

			ConverseRequest converseRequest = this.createRequest(prompt);

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(AiProvider.BEDROCK_CONVERSE.value())
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			ConverseStreamRequest converseStreamRequest = ConverseStreamRequest.builder()
				.modelId(converseRequest.modelId())
				.inferenceConfig(converseRequest.inferenceConfig())
				.messages(converseRequest.messages())
				.system(converseRequest.system())
				.additionalModelRequestFields(converseRequest.additionalModelRequestFields())
				.toolConfig(converseRequest.toolConfig())
				.build();

			Flux<ConverseStreamOutput> response = converseStream(converseStreamRequest);

			Flux<ChatResponse> chatResponses = ConverseApiUtils.toChatResponse(response, perviousChatResponse);

			Flux<ChatResponse> chatResponseFlux = chatResponses.switchMap(chatResponse -> {

				if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), chatResponse)
						&& chatResponse.hasFinishReasons(Set.of(StopReason.TOOL_USE.toString()))) {

					// FIXME: bounded elastic needs to be used since tool calling
					// is currently only synchronous
					return Flux.defer(() -> {
						var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, chatResponse);

						if (toolExecutionResult.returnDirect()) {
							// Return tool execution result directly to the client.
							return Flux.just(ChatResponse.builder()
								.from(chatResponse)
								.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
								.build());
						}
						else {
							// Send the tool execution result back to the model.
							return this.internalStream(
									new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
									chatResponse);
						}
					}).subscribeOn(Schedulers.boundedElastic());
				}
				else {
					return Flux.just(chatResponse);
				}
			})// @formatter:off
			.doOnError(observation::error)
			.doFinally(s -> observation.stop())
			.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on

			return new MessageAggregator().aggregate(chatResponseFlux, observationContext::setResponse);
		});
	}

	public static final EmitFailureHandler DEFAULT_EMIT_FAILURE_HANDLER = EmitFailureHandler
		.busyLooping(Duration.ofSeconds(10));

	/**
	 * Invoke the model and return the response stream.
	 *
	 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters.html
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html
	 * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/bedrockruntime/BedrockRuntimeAsyncClient.html#converseStream
	 * @param converseStreamRequest Model invocation request.
	 * @return The model invocation response stream.
	 */
	public Flux<ConverseStreamOutput> converseStream(ConverseStreamRequest converseStreamRequest) {
		Assert.notNull(converseStreamRequest, "'converseStreamRequest' must not be null");

		Sinks.Many<ConverseStreamOutput> eventSink = Sinks.many().multicast().onBackpressureBuffer();

		ConverseStreamResponseHandler.Visitor visitor = ConverseStreamResponseHandler.Visitor.builder()
			.onDefault(output -> {
				logger.debug("Received converse stream output:{}", output);
				eventSink.emitNext(output, DEFAULT_EMIT_FAILURE_HANDLER);
			})
			.build();

		ConverseStreamResponseHandler responseHandler = ConverseStreamResponseHandler.builder()
			.onEventStream(stream -> stream.subscribe(e -> e.accept(visitor)))
			.onComplete(() -> {
				eventSink.emitComplete(DEFAULT_EMIT_FAILURE_HANDLER);
				logger.info("Completed streaming response.");
			})
			.onError(error -> {
				logger.error("Error handling Bedrock converse stream response", error);
				eventSink.emitError(error, DEFAULT_EMIT_FAILURE_HANDLER);
			})
			.build();

		this.bedrockRuntimeAsyncClient.converseStream(converseStreamRequest, responseHandler);

		return eventSink.asFlux();

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

	public static final class Builder {

		private AwsCredentialsProvider credentialsProvider;

		private Region region = Region.US_EAST_1;

		private Duration timeout = Duration.ofMinutes(10);

		private ToolCallingManager toolCallingManager;

		private ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate = new DefaultToolExecutionEligibilityPredicate();

		private ToolCallingChatOptions defaultOptions = ToolCallingChatOptions.builder().build();

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private ChatModelObservationConvention customObservationConvention;

		private BedrockRuntimeClient bedrockRuntimeClient;

		private BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient;

		private Builder() {
		}

		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
			return this;
		}

		public Builder toolExecutionEligibilityPredicate(
				ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
			this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
			return this;
		}

		public Builder credentialsProvider(AwsCredentialsProvider credentialsProvider) {
			Assert.notNull(credentialsProvider, "'credentialsProvider' must not be null.");
			this.credentialsProvider = credentialsProvider;
			return this;
		}

		public Builder region(Region region) {
			Assert.notNull(region, "'region' must not be null.");
			this.region = region;
			return this;
		}

		public Builder timeout(Duration timeout) {
			Assert.notNull(timeout, "'timeout' must not be null.");
			this.timeout = timeout;
			return this;
		}

		public Builder defaultOptions(ToolCallingChatOptions defaultOptions) {
			Assert.notNull(defaultOptions, "'defaultOptions' must not be null.");
			this.defaultOptions = defaultOptions;
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

		public Builder bedrockRuntimeClient(BedrockRuntimeClient bedrockRuntimeClient) {
			this.bedrockRuntimeClient = bedrockRuntimeClient;
			return this;
		}

		public Builder bedrockRuntimeAsyncClient(BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient) {
			this.bedrockRuntimeAsyncClient = bedrockRuntimeAsyncClient;
			return this;
		}

		public BedrockProxyChatModel build() {

			if (this.bedrockRuntimeClient == null) {
				this.bedrockRuntimeClient = BedrockRuntimeClient.builder()
					.region(this.region)
					.httpClientBuilder(null)
					.credentialsProvider(this.credentialsProvider)
					.overrideConfiguration(c -> c.apiCallTimeout(this.timeout))
					.build();
			}

			if (this.bedrockRuntimeAsyncClient == null) {

				// TODO: Is it ok to configure the NettyNioAsyncHttpClient explicitly???
				var httpClientBuilder = NettyNioAsyncHttpClient.builder()
					.tcpKeepAlive(true)
					.connectionAcquisitionTimeout(Duration.ofSeconds(30))
					.maxConcurrency(200);

				var builder = BedrockRuntimeAsyncClient.builder()
					.region(this.region)
					.httpClientBuilder(httpClientBuilder)
					.credentialsProvider(this.credentialsProvider)
					.overrideConfiguration(c -> c.apiCallTimeout(this.timeout));
				this.bedrockRuntimeAsyncClient = builder.build();
			}

			BedrockProxyChatModel bedrockProxyChatModel = null;

			if (this.toolCallingManager != null) {
				bedrockProxyChatModel = new BedrockProxyChatModel(this.bedrockRuntimeClient,
						this.bedrockRuntimeAsyncClient, this.defaultOptions, this.observationRegistry,
						this.toolCallingManager, this.toolExecutionEligibilityPredicate);

			}
			else {
				bedrockProxyChatModel = new BedrockProxyChatModel(this.bedrockRuntimeClient,
						this.bedrockRuntimeAsyncClient, this.defaultOptions, this.observationRegistry,
						DEFAULT_TOOL_CALLING_MANAGER, this.toolExecutionEligibilityPredicate);
			}

			if (this.customObservationConvention != null) {
				bedrockProxyChatModel.setObservationConvention(this.customObservationConvention);
			}

			return bedrockProxyChatModel;
		}

	}

}
