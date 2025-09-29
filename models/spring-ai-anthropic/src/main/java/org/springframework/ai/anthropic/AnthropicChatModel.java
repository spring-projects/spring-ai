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

package org.springframework.ai.anthropic;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicApi.AnthropicMessage;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionResponse;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock.Source;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock.Type;
import org.springframework.ai.anthropic.api.AnthropicApi.Role;
import org.springframework.ai.anthropic.api.AnthropicCacheOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheTtl;
import org.springframework.ai.anthropic.api.utils.CacheEligibilityResolver;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
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
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * The {@link ChatModel} implementation for the Anthropic service.
 *
 * @author Christian Tzolov
 * @author luocongqiu
 * @author Mariusz Bernacki
 * @author Thomas Vitale
 * @author Claudio Silva Junior
 * @author Alexandros Pappas
 * @author Jonghoon Park
 * @author Soby Chacko
 * @author Austin Dase
 * @since 1.0.0
 */
public class AnthropicChatModel implements ChatModel {

	public static final String DEFAULT_MODEL_NAME = AnthropicApi.ChatModel.CLAUDE_3_7_SONNET.getValue();

	public static final Integer DEFAULT_MAX_TOKENS = 500;

	public static final Double DEFAULT_TEMPERATURE = 0.8;

	private static final Logger logger = LoggerFactory.getLogger(AnthropicChatModel.class);

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	/**
	 * The retry template used to retry the OpenAI API calls.
	 */
	public final RetryTemplate retryTemplate;

	/**
	 * The lower-level API for the Anthropic service.
	 */
	private final AnthropicApi anthropicApi;

	/**
	 * The default options used for the chat completion requests.
	 */
	private final AnthropicChatOptions defaultOptions;

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
	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public AnthropicChatModel(AnthropicApi anthropicApi, AnthropicChatOptions defaultOptions,
			ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		this(anthropicApi, defaultOptions, toolCallingManager, retryTemplate, observationRegistry,
				new DefaultToolExecutionEligibilityPredicate());
	}

	public AnthropicChatModel(AnthropicApi anthropicApi, AnthropicChatOptions defaultOptions,
			ToolCallingManager toolCallingManager, RetryTemplate retryTemplate, ObservationRegistry observationRegistry,
			ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {

		Assert.notNull(anthropicApi, "anthropicApi cannot be null");
		Assert.notNull(defaultOptions, "defaultOptions cannot be null");
		Assert.notNull(toolCallingManager, "toolCallingManager cannot be null");
		Assert.notNull(retryTemplate, "retryTemplate cannot be null");
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");
		Assert.notNull(toolExecutionEligibilityPredicate, "toolExecutionEligibilityPredicate cannot be null");

		this.anthropicApi = anthropicApi;
		this.defaultOptions = defaultOptions;
		this.toolCallingManager = toolCallingManager;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
		this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		// Before moving any further, build the final request Prompt,
		// merging runtime and default options.
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalCall(requestPrompt, null);
	}

	public ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {
		ChatCompletionRequest request = createRequest(prompt, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(AnthropicApi.PROVIDER_NAME)
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				ResponseEntity<ChatCompletionResponse> completionEntity = this.retryTemplate.execute(
						ctx -> this.anthropicApi.chatCompletionEntity(request, this.getAdditionalHttpHeaders(prompt)));

				AnthropicApi.ChatCompletionResponse completionResponse = completionEntity.getBody();
				AnthropicApi.Usage usage = completionResponse.usage();

				Usage currentChatResponseUsage = usage != null ? this.getDefaultUsage(completionResponse.usage())
						: new EmptyUsage();
				Usage accumulatedUsage = UsageCalculator.getCumulativeUsage(currentChatResponseUsage,
						previousChatResponse);

				ChatResponse chatResponse = toChatResponse(completionEntity.getBody(), accumulatedUsage);
				observationContext.setResponse(chatResponse);

				return chatResponse;
			});

		if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), response)) {
			var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
			if (toolExecutionResult.returnDirect()) {
				// Return tool execution result directly to the client.
				return ChatResponse.builder()
					.from(response)
					.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
					.build();
			}
			else {
				// Send the tool execution result back to the model.
				return this.internalCall(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
						response);
			}
		}

		return response;
	}

	private DefaultUsage getDefaultUsage(AnthropicApi.Usage usage) {
		return new DefaultUsage(usage.inputTokens(), usage.outputTokens(), usage.inputTokens() + usage.outputTokens(),
				usage);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		// Before moving any further, build the final request Prompt,
		// merging runtime and default options.
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalStream(requestPrompt, null);
	}

	public Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse previousChatResponse) {
		return Flux.deferContextual(contextView -> {
			ChatCompletionRequest request = createRequest(prompt, true);

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(AnthropicApi.PROVIDER_NAME)
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			Flux<ChatCompletionResponse> response = this.anthropicApi.chatCompletionStream(request,
					this.getAdditionalHttpHeaders(prompt));

			// @formatter:off
			Flux<ChatResponse> chatResponseFlux = response.flatMap(chatCompletionResponse -> {
				AnthropicApi.Usage usage = chatCompletionResponse.usage();
				Usage currentChatResponseUsage = usage != null ? this.getDefaultUsage(chatCompletionResponse.usage()) : new EmptyUsage();
				Usage accumulatedUsage = UsageCalculator.getCumulativeUsage(currentChatResponseUsage, previousChatResponse);
				ChatResponse chatResponse = toChatResponse(chatCompletionResponse, accumulatedUsage);

				if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), chatResponse)) {

					if (chatResponse.hasFinishReasons(Set.of("tool_use"))) {
						// FIXME: bounded elastic needs to be used since tool calling
						//  is currently only synchronous
						return Flux.deferContextual(ctx -> {
							// TODO: factor out the tool execution logic with setting context into a utility.
							ToolExecutionResult toolExecutionResult;
							try {
								ToolCallReactiveContextHolder.setContext(ctx);
								toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, chatResponse);
							}
							finally {
								ToolCallReactiveContextHolder.clearContext();
							}
							if (toolExecutionResult.returnDirect()) {
								// Return tool execution result directly to the client.
								return Flux.just(ChatResponse.builder().from(chatResponse)
									.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
									.build());
							}
							else {
								// Send the tool execution result back to the model.
								return this.internalStream(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
										chatResponse);
							}
						}).subscribeOn(Schedulers.boundedElastic());
					}
					else {
						return Mono.empty();
					}
				}
				else {
					// If internal tool execution is not required, just return the chat response.
					return Mono.just(chatResponse);
				}
			})
			.doOnError(observation::error)
			.doFinally(s -> observation.stop())
			.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on

			return new MessageAggregator().aggregate(chatResponseFlux, observationContext::setResponse);
		});
	}

	private ChatResponse toChatResponse(ChatCompletionResponse chatCompletion, Usage usage) {

		if (chatCompletion == null) {
			logger.warn("Null chat completion returned");
			return new ChatResponse(List.of());
		}

		List<Generation> generations = new ArrayList<>();
		List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
		for (ContentBlock content : chatCompletion.content()) {
			switch (content.type()) {
				case TEXT, TEXT_DELTA:
					generations.add(new Generation(
							AssistantMessage.builder().content(content.text()).properties(Map.of()).build(),
							ChatGenerationMetadata.builder().finishReason(chatCompletion.stopReason()).build()));
					break;
				case THINKING, THINKING_DELTA:
					Map<String, Object> thinkingProperties = new HashMap<>();
					thinkingProperties.put("signature", content.signature());
					generations.add(new Generation(
							AssistantMessage.builder()
								.content(content.thinking())
								.properties(thinkingProperties)
								.build(),
							ChatGenerationMetadata.builder().finishReason(chatCompletion.stopReason()).build()));
					break;
				case REDACTED_THINKING:
					Map<String, Object> redactedProperties = new HashMap<>();
					redactedProperties.put("data", content.data());
					generations.add(new Generation(AssistantMessage.builder().properties(redactedProperties).build(),
							ChatGenerationMetadata.builder().finishReason(chatCompletion.stopReason()).build()));
					break;
				case TOOL_USE:
					var functionCallId = content.id();
					var functionName = content.name();
					var functionArguments = JsonParser.toJson(content.input());
					toolCalls.add(
							new AssistantMessage.ToolCall(functionCallId, "function", functionName, functionArguments));
					break;
			}
		}

		if (chatCompletion.stopReason() != null && generations.isEmpty()) {
			Generation generation = new Generation(AssistantMessage.builder().content("").properties(Map.of()).build(),
					ChatGenerationMetadata.builder().finishReason(chatCompletion.stopReason()).build());
			generations.add(generation);
		}

		if (!CollectionUtils.isEmpty(toolCalls)) {
			AssistantMessage assistantMessage = AssistantMessage.builder()
				.content("")
				.properties(Map.of())
				.toolCalls(toolCalls)
				.build();
			Generation toolCallGeneration = new Generation(assistantMessage,
					ChatGenerationMetadata.builder().finishReason(chatCompletion.stopReason()).build());
			generations.add(toolCallGeneration);
		}
		return new ChatResponse(generations, this.from(chatCompletion, usage));
	}

	private ChatResponseMetadata from(AnthropicApi.ChatCompletionResponse result) {
		return from(result, this.getDefaultUsage(result.usage()));
	}

	private ChatResponseMetadata from(AnthropicApi.ChatCompletionResponse result, Usage usage) {
		Assert.notNull(result, "Anthropic ChatCompletionResult must not be null");
		return ChatResponseMetadata.builder()
			.id(result.id())
			.model(result.model())
			.usage(usage)
			.keyValue("stop-reason", result.stopReason())
			.keyValue("stop-sequence", result.stopSequence())
			.keyValue("type", result.type())
			.build();
	}

	private Source getSourceByMedia(Media media) {
		String data = this.fromMediaData(media.getData());

		// http is not allowed and redirect not allowed
		if (data.startsWith("https://")) {
			return new Source(data);
		}
		else {
			return new Source(media.getMimeType().toString(), data);
		}
	}

	private String fromMediaData(Object mediaData) {
		if (mediaData instanceof byte[] bytes) {
			return Base64.getEncoder().encodeToString(bytes);
		}
		else if (mediaData instanceof String text) {
			return text;
		}
		else {
			throw new IllegalArgumentException("Unsupported media data type: " + mediaData.getClass().getSimpleName());
		}

	}

	private Type getContentBlockTypeByMedia(Media media) {
		String mimeType = media.getMimeType().toString();
		if (mimeType.startsWith("image")) {
			return Type.IMAGE;
		}
		else if (mimeType.contains("pdf")) {
			return Type.DOCUMENT;
		}
		throw new IllegalArgumentException("Unsupported media type: " + mimeType
				+ ". Supported types are: images (image/*) and PDF documents (application/pdf)");
	}

	private MultiValueMap<String, String> getAdditionalHttpHeaders(Prompt prompt) {

		Map<String, String> headers = new HashMap<>(this.defaultOptions.getHttpHeaders());
		if (prompt.getOptions() != null && prompt.getOptions() instanceof AnthropicChatOptions chatOptions) {
			headers.putAll(chatOptions.getHttpHeaders());
		}
		return CollectionUtils.toMultiValueMap(
				headers.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue()))));
	}

	Prompt buildRequestPrompt(Prompt prompt) {
		// Process runtime options
		AnthropicChatOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
						AnthropicChatOptions.class);
			}
			else {
				runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						AnthropicChatOptions.class);
			}
		}

		// Define request options by merging runtime options and default options
		AnthropicChatOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				AnthropicChatOptions.class);

		// Merge @JsonIgnore-annotated options explicitly since they are ignored by
		// Jackson, used by ModelOptionsUtils.
		if (runtimeOptions != null) {
			if (runtimeOptions.getFrequencyPenalty() != null) {
				logger.warn("The frequencyPenalty option is not supported by Anthropic API. Ignoring.");
			}
			if (runtimeOptions.getPresencePenalty() != null) {
				logger.warn("The presencePenalty option is not supported by Anthropic API. Ignoring.");
			}
			requestOptions.setHttpHeaders(
					mergeHttpHeaders(runtimeOptions.getHttpHeaders(), this.defaultOptions.getHttpHeaders()));
			requestOptions.setInternalToolExecutionEnabled(
					ModelOptionsUtils.mergeOption(runtimeOptions.getInternalToolExecutionEnabled(),
							this.defaultOptions.getInternalToolExecutionEnabled()));
			requestOptions.setToolNames(ToolCallingChatOptions.mergeToolNames(runtimeOptions.getToolNames(),
					this.defaultOptions.getToolNames()));
			requestOptions.setToolCallbacks(ToolCallingChatOptions.mergeToolCallbacks(runtimeOptions.getToolCallbacks(),
					this.defaultOptions.getToolCallbacks()));
			requestOptions.setToolContext(ToolCallingChatOptions.mergeToolContext(runtimeOptions.getToolContext(),
					this.defaultOptions.getToolContext()));

			// Merge cache options that are Json-ignored
			requestOptions.setCacheOptions(runtimeOptions.getCacheOptions() != null ? runtimeOptions.getCacheOptions()
					: this.defaultOptions.getCacheOptions());
		}
		else {
			requestOptions.setHttpHeaders(this.defaultOptions.getHttpHeaders());
			requestOptions.setInternalToolExecutionEnabled(this.defaultOptions.getInternalToolExecutionEnabled());
			requestOptions.setToolNames(this.defaultOptions.getToolNames());
			requestOptions.setToolCallbacks(this.defaultOptions.getToolCallbacks());
			requestOptions.setToolContext(this.defaultOptions.getToolContext());
		}

		ToolCallingChatOptions.validateToolCallbacks(requestOptions.getToolCallbacks());

		return new Prompt(prompt.getInstructions(), requestOptions);
	}

	private Map<String, String> mergeHttpHeaders(Map<String, String> runtimeHttpHeaders,
			Map<String, String> defaultHttpHeaders) {
		var mergedHttpHeaders = new HashMap<>(defaultHttpHeaders);
		mergedHttpHeaders.putAll(runtimeHttpHeaders);
		return mergedHttpHeaders;
	}

	ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {

		// Get caching strategy and options from the request
		logger.debug("DEBUGINFO: prompt.getOptions() type: {}, value: {}",
				prompt.getOptions() != null ? prompt.getOptions().getClass().getName() : "null", prompt.getOptions());

		AnthropicChatOptions requestOptions = null;
		if (prompt.getOptions() instanceof AnthropicChatOptions) {
			requestOptions = (AnthropicChatOptions) prompt.getOptions();
			logger.debug("DEBUGINFO: Found AnthropicChatOptions - cacheOptions {}", requestOptions.getCacheOptions());
		}
		else {
			logger.debug("DEBUGINFO: Options is NOT AnthropicChatOptions, it's: {}",
					prompt.getOptions() != null ? prompt.getOptions().getClass().getName() : "null");
		}

		AnthropicCacheOptions cacheOptions = requestOptions != null ? requestOptions.getCacheOptions()
				: AnthropicCacheOptions.DISABLED;

		CacheEligibilityResolver cacheEligibilityResolver = CacheEligibilityResolver.from(cacheOptions);

		// Process system - as array if caching, string otherwise
		Object systemContent = buildSystemContent(prompt, cacheEligibilityResolver);

		// Build messages WITHOUT blanket cache control - strategic placement only
		List<AnthropicMessage> userMessages = buildMessages(prompt, cacheEligibilityResolver);

		// Build base request
		ChatCompletionRequest request = new ChatCompletionRequest(this.defaultOptions.getModel(), userMessages,
				systemContent, this.defaultOptions.getMaxTokens(), this.defaultOptions.getTemperature(), stream);

		request = ModelOptionsUtils.merge(requestOptions, request, ChatCompletionRequest.class);

		// Add the tool definitions with potential caching
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			request = ModelOptionsUtils.merge(request, this.defaultOptions, ChatCompletionRequest.class);
			List<AnthropicApi.Tool> tools = getFunctionTools(toolDefinitions);

			// Apply caching to tools if strategy includes them
			tools = addCacheToLastTool(tools, cacheEligibilityResolver);

			request = ChatCompletionRequest.from(request).tools(tools).build();
		}

		// Add beta header for 1-hour TTL if needed
		if (cacheOptions.getMessageTypeTtl().containsValue(AnthropicCacheTtl.ONE_HOUR)) {
			Map<String, String> headers = new HashMap<>(requestOptions.getHttpHeaders());
			headers.put("anthropic-beta", AnthropicApi.BETA_EXTENDED_CACHE_TTL);
			requestOptions.setHttpHeaders(headers);
		}

		return request;
	}

	private static ContentBlock cacheAwareContentBlock(ContentBlock contentBlock, MessageType messageType,
			CacheEligibilityResolver cacheEligibilityResolver) {
		String basisForLength = switch (contentBlock.type()) {
			case TEXT, TEXT_DELTA -> contentBlock.text();
			case TOOL_RESULT -> contentBlock.content();
			case TOOL_USE -> JsonParser.toJson(contentBlock.input());
			case THINKING, THINKING_DELTA -> contentBlock.thinking();
			case REDACTED_THINKING -> contentBlock.data();
			default -> null;
		};
		return cacheAwareContentBlock(contentBlock, messageType, cacheEligibilityResolver, basisForLength);
	}

	private static ContentBlock cacheAwareContentBlock(ContentBlock contentBlock, MessageType messageType,
			CacheEligibilityResolver cacheEligibilityResolver, String basisForLength) {
		ChatCompletionRequest.CacheControl cacheControl = cacheEligibilityResolver.resolve(messageType, basisForLength);
		if (cacheControl == null) {
			return contentBlock;
		}
		cacheEligibilityResolver.useCacheBlock();
		return ContentBlock.from(contentBlock).cacheControl(cacheControl).build();
	}

	private List<AnthropicApi.Tool> getFunctionTools(List<ToolDefinition> toolDefinitions) {
		return toolDefinitions.stream().map(toolDefinition -> {
			var name = toolDefinition.name();
			var description = toolDefinition.description();
			String inputSchema = toolDefinition.inputSchema();
			return new AnthropicApi.Tool(name, description, JsonParser.fromJson(inputSchema, new TypeReference<>() {
			}));
		}).toList();
	}

	/**
	 * Build messages strategically, applying cache control only where specified by the
	 * strategy.
	 */
	private List<AnthropicMessage> buildMessages(Prompt prompt, CacheEligibilityResolver cacheEligibilityResolver) {

		List<Message> allMessages = prompt.getInstructions()
			.stream()
			.filter(message -> message.getMessageType() != MessageType.SYSTEM)
			.toList();

		// Find the last user message (current question) for CONVERSATION_HISTORY strategy
		int lastUserIndex = -1;
		if (cacheEligibilityResolver.isCachingEnabled()) {
			for (int i = allMessages.size() - 1; i >= 0; i--) {
				if (allMessages.get(i).getMessageType() == MessageType.USER) {
					lastUserIndex = i;
					break;
				}
			}
		}

		List<AnthropicMessage> result = new ArrayList<>();
		for (int i = 0; i < allMessages.size(); i++) {
			Message message = allMessages.get(i);
			MessageType messageType = message.getMessageType();
			if (messageType == MessageType.USER) {
				List<ContentBlock> contentBlocks = new ArrayList<>();
				String content = message.getText();
				// For conversation history caching, apply cache control to the
				// message immediately before the last user message.
				boolean isPenultimateUserMessage = (lastUserIndex > 0) && (i == lastUserIndex - 1);
				ContentBlock contentBlock = new ContentBlock(content);
				if (isPenultimateUserMessage && cacheEligibilityResolver.isCachingEnabled()) {
					// Combine text from all user messages except the last one (current
					// question)
					// as the basis for cache eligibility checks
					String combinedUserMessagesText = combineEligibleUserMessagesText(allMessages, lastUserIndex);
					contentBlocks.add(cacheAwareContentBlock(contentBlock, messageType, cacheEligibilityResolver,
							combinedUserMessagesText));
				}
				else {
					contentBlocks.add(contentBlock);
				}
				if (message instanceof UserMessage userMessage) {
					if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
						List<ContentBlock> mediaContent = userMessage.getMedia().stream().map(media -> {
							Type contentBlockType = getContentBlockTypeByMedia(media);
							var source = getSourceByMedia(media);
							return new ContentBlock(contentBlockType, source);
						}).toList();
						contentBlocks.addAll(mediaContent);
					}
				}
				result.add(new AnthropicMessage(contentBlocks, Role.valueOf(message.getMessageType().name())));
			}
			else if (messageType == MessageType.ASSISTANT) {
				AssistantMessage assistantMessage = (AssistantMessage) message;
				List<ContentBlock> contentBlocks = new ArrayList<>();
				if (StringUtils.hasText(message.getText())) {
					ContentBlock contentBlock = new ContentBlock(message.getText());
					contentBlocks.add(cacheAwareContentBlock(contentBlock, messageType, cacheEligibilityResolver));
				}
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
						ContentBlock contentBlock = new ContentBlock(Type.TOOL_USE, toolCall.id(), toolCall.name(),
								ModelOptionsUtils.jsonToMap(toolCall.arguments()));
						contentBlocks.add(cacheAwareContentBlock(contentBlock, messageType, cacheEligibilityResolver));
					}
				}
				result.add(new AnthropicMessage(contentBlocks, Role.ASSISTANT));
			}
			else if (messageType == MessageType.TOOL) {
				List<ContentBlock> toolResponses = ((ToolResponseMessage) message).getResponses()
					.stream()
					.map(toolResponse -> new ContentBlock(Type.TOOL_RESULT, toolResponse.id(),
							toolResponse.responseData()))
					.map(contentBlock -> cacheAwareContentBlock(contentBlock, messageType, cacheEligibilityResolver))
					.toList();
				result.add(new AnthropicMessage(toolResponses, Role.USER));
			}
			else {
				throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
			}
		}
		return result;
	}

	private String combineEligibleUserMessagesText(List<Message> userMessages, int lastUserIndex) {
		List<Message> userMessagesForEligibility = new ArrayList<>();
		// Only 20 content blocks are considered by anthropic, so limit the number of
		// message content to consider
		int startIndex = Math.max(0, lastUserIndex - 20);
		for (int i = startIndex; i < lastUserIndex; i++) {
			Message message = userMessages.get(i);
			if (message.getMessageType() == MessageType.USER) {
				userMessagesForEligibility.add(message);
			}
		}
		StringBuilder sb = new StringBuilder();
		userMessagesForEligibility.stream().map(Message::getText).filter(StringUtils::hasText).forEach(sb::append);
		return sb.toString();
	}

	/**
	 * Build system content - as array if caching, string otherwise.
	 */
	private Object buildSystemContent(Prompt prompt, CacheEligibilityResolver cacheEligibilityResolver) {

		String systemText = prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.SYSTEM)
			.map(Message::getText)
			.collect(Collectors.joining(System.lineSeparator()));

		if (!StringUtils.hasText(systemText)) {
			return null;
		}

		// Use array format when caching system
		if (cacheEligibilityResolver.isCachingEnabled()) {
			return List
				.of(cacheAwareContentBlock(new ContentBlock(systemText), MessageType.SYSTEM, cacheEligibilityResolver));
		}

		// Use string format when not caching (backward compatible)
		return systemText;
	}

	/**
	 * Add cache control to the last tool for deterministic caching.
	 */
	private List<AnthropicApi.Tool> addCacheToLastTool(List<AnthropicApi.Tool> tools,
			CacheEligibilityResolver cacheEligibilityResolver) {

		ChatCompletionRequest.CacheControl cacheControl = cacheEligibilityResolver.resolveToolCacheControl();

		if (cacheControl == null || tools == null || tools.isEmpty()) {
			return tools;
		}

		List<AnthropicApi.Tool> modifiedTools = new ArrayList<>();
		for (int i = 0; i < tools.size(); i++) {
			AnthropicApi.Tool tool = tools.get(i);
			if (i == tools.size() - 1) {
				// Add cache control to last tool
				tool = new AnthropicApi.Tool(tool.name(), tool.description(), tool.inputSchema(), cacheControl);
				cacheEligibilityResolver.useCacheBlock();
			}
			modifiedTools.add(tool);
		}
		return modifiedTools;
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return AnthropicChatOptions.fromOptions(this.defaultOptions);
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

		private AnthropicApi anthropicApi;

		private AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
			.model(DEFAULT_MODEL_NAME)
			.maxTokens(DEFAULT_MAX_TOKENS)
			.temperature(DEFAULT_TEMPERATURE)
			.build();

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ToolCallingManager toolCallingManager;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate = new DefaultToolExecutionEligibilityPredicate();

		private Builder() {
		}

		public Builder anthropicApi(AnthropicApi anthropicApi) {
			this.anthropicApi = anthropicApi;
			return this;
		}

		public Builder defaultOptions(AnthropicChatOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
			return this;
		}

		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
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

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public AnthropicChatModel build() {
			if (this.toolCallingManager != null) {
				return new AnthropicChatModel(this.anthropicApi, this.defaultOptions, this.toolCallingManager,
						this.retryTemplate, this.observationRegistry, this.toolExecutionEligibilityPredicate);
			}
			return new AnthropicChatModel(this.anthropicApi, this.defaultOptions, DEFAULT_TOOL_CALLING_MANAGER,
					this.retryTemplate, this.observationRegistry, this.toolExecutionEligibilityPredicate);
		}

	}

}
