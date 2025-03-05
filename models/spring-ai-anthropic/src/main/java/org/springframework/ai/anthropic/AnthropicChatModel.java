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
import org.springframework.ai.model.tool.LegacyToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicApi.AnthropicMessage;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionResponse;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock.Source;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock.Type;
import org.springframework.ai.anthropic.api.AnthropicApi.Role;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.metadata.UsageUtils;
import org.springframework.ai.chat.model.AbstractToolCallSupport;
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
import org.springframework.ai.model.Media;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackResolver;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.retry.RetryUtils;
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
 * @since 1.0.0
 */
public class AnthropicChatModel extends AbstractToolCallSupport implements ChatModel {

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
	 * Conventions to use for generating observations.
	 */
	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Construct a new {@link AnthropicChatModel} instance.
	 * @param anthropicApi the lower-level API for the Anthropic service.
	 * @deprecated Use {@link AnthropicChatModel.Builder}.
	 */
	@Deprecated
	public AnthropicChatModel(AnthropicApi anthropicApi) {
		this(anthropicApi,
				AnthropicChatOptions.builder()
					.model(DEFAULT_MODEL_NAME)
					.maxTokens(DEFAULT_MAX_TOKENS)
					.temperature(DEFAULT_TEMPERATURE)
					.build());
	}

	/**
	 * Construct a new {@link AnthropicChatModel} instance.
	 * @param anthropicApi the lower-level API for the Anthropic service.
	 * @param defaultOptions the default options used for the chat completion requests.
	 * @deprecated Use {@link AnthropicChatModel.Builder}.
	 */
	@Deprecated
	public AnthropicChatModel(AnthropicApi anthropicApi, AnthropicChatOptions defaultOptions) {
		this(anthropicApi, defaultOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Construct a new {@link AnthropicChatModel} instance.
	 * @param anthropicApi the lower-level API for the Anthropic service.
	 * @param defaultOptions the default options used for the chat completion requests.
	 * @param retryTemplate the retry template used to retry the Anthropic API calls.
	 * @deprecated Use {@link AnthropicChatModel.Builder}.
	 */
	@Deprecated
	public AnthropicChatModel(AnthropicApi anthropicApi, AnthropicChatOptions defaultOptions,
			RetryTemplate retryTemplate) {
		this(anthropicApi, defaultOptions, retryTemplate, null);
	}

	/**
	 * Construct a new {@link AnthropicChatModel} instance.
	 * @param anthropicApi the lower-level API for the Anthropic service.
	 * @param defaultOptions the default options used for the chat completion requests.
	 * @param retryTemplate the retry template used to retry the Anthropic API calls.
	 * @param functionCallbackResolver the function callback resolver used to resolve the
	 * function by its name.
	 * @deprecated Use {@link AnthropicChatModel.Builder}.
	 */
	@Deprecated
	public AnthropicChatModel(AnthropicApi anthropicApi, AnthropicChatOptions defaultOptions,
			RetryTemplate retryTemplate, FunctionCallbackResolver functionCallbackResolver) {
		this(anthropicApi, defaultOptions, retryTemplate, functionCallbackResolver, List.of());
	}

	/**
	 * Construct a new {@link AnthropicChatModel} instance.
	 * @param anthropicApi the lower-level API for the Anthropic service.
	 * @param defaultOptions the default options used for the chat completion requests.
	 * @param retryTemplate the retry template used to retry the Anthropic API calls.
	 * @param functionCallbackResolver the function callback resolver used to resolve the
	 * function by its name.
	 * @param toolFunctionCallbacks the tool function callbacks used to handle the tool
	 * calls.
	 * @deprecated Use {@link AnthropicChatModel.Builder}.
	 */
	@Deprecated
	public AnthropicChatModel(AnthropicApi anthropicApi, AnthropicChatOptions defaultOptions,
			RetryTemplate retryTemplate, FunctionCallbackResolver functionCallbackResolver,
			List<FunctionCallback> toolFunctionCallbacks) {
		this(anthropicApi, defaultOptions, retryTemplate, functionCallbackResolver, toolFunctionCallbacks,
				ObservationRegistry.NOOP);
	}

	/**
	 * Construct a new {@link AnthropicChatModel} instance.
	 * @param anthropicApi the lower-level API for the Anthropic service.
	 * @param defaultOptions the default options used for the chat completion requests.
	 * @param retryTemplate the retry template used to retry the Anthropic API calls.
	 * @param functionCallbackResolver the function callback resolver used to resolve the
	 * function by its name.
	 * @param toolFunctionCallbacks the tool function callbacks used to handle the tool
	 * calls.
	 * @deprecated Use {@link AnthropicChatModel.Builder}.
	 */
	@Deprecated
	public AnthropicChatModel(AnthropicApi anthropicApi, AnthropicChatOptions defaultOptions,
			RetryTemplate retryTemplate, @Nullable FunctionCallbackResolver functionCallbackResolver,
			@Nullable List<FunctionCallback> toolFunctionCallbacks, ObservationRegistry observationRegistry) {
		this(anthropicApi, defaultOptions,
				LegacyToolCallingManager.builder()
					.functionCallbackResolver(functionCallbackResolver)
					.functionCallbacks(toolFunctionCallbacks)
					.build(),
				retryTemplate, observationRegistry);
		logger.warn("This constructor is deprecated and will be removed in the next milestone. "
				+ "Please use the MistralAiChatModel.Builder or the new constructor accepting ToolCallingManager instead.");
	}

	public AnthropicChatModel(AnthropicApi anthropicApi, AnthropicChatOptions defaultOptions,
			ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		// We do not pass the 'defaultOptions' to the AbstractToolSupport,
		// because it modifies them. We are using ToolCallingManager instead,
		// so we just pass empty options here.
		super(null, AnthropicChatOptions.builder().build(), List.of());

		Assert.notNull(anthropicApi, "anthropicApi cannot be null");
		Assert.notNull(defaultOptions, "defaultOptions cannot be null");
		Assert.notNull(toolCallingManager, "toolCallingManager cannot be null");
		Assert.notNull(retryTemplate, "retryTemplate cannot be null");
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");

		this.anthropicApi = anthropicApi;
		this.defaultOptions = defaultOptions;
		this.toolCallingManager = toolCallingManager;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
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
			.requestOptions(prompt.getOptions())
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
				Usage accumulatedUsage = UsageUtils.getCumulativeUsage(currentChatResponseUsage, previousChatResponse);

				ChatResponse chatResponse = toChatResponse(completionEntity.getBody(), accumulatedUsage);
				observationContext.setResponse(chatResponse);

				return chatResponse;
			});

		if (ToolCallingChatOptions.isInternalToolExecutionEnabled(prompt.getOptions()) && response != null
				&& response.hasToolCalls()) {
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
				.requestOptions(prompt.getOptions())
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			Flux<ChatCompletionResponse> response = this.anthropicApi.chatCompletionStream(request,
					this.getAdditionalHttpHeaders(prompt));

			// @formatter:off
			Flux<ChatResponse> chatResponseFlux = response.switchMap(chatCompletionResponse -> {
				AnthropicApi.Usage usage = chatCompletionResponse.usage();
				Usage currentChatResponseUsage = usage != null ? this.getDefaultUsage(chatCompletionResponse.usage()) : new EmptyUsage();
				Usage accumulatedUsage = UsageUtils.getCumulativeUsage(currentChatResponseUsage, previousChatResponse);
				ChatResponse chatResponse = toChatResponse(chatCompletionResponse, accumulatedUsage);

				if (ToolCallingChatOptions.isInternalToolExecutionEnabled(prompt.getOptions()) && chatResponse.hasToolCalls() && chatResponse.hasFinishReasons(Set.of("tool_use"))) {
					var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, chatResponse);
					if (toolExecutionResult.returnDirect()) {
						// Return tool execution result directly to the client.
						return Flux.just(ChatResponse.builder().from(chatResponse)
							.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
							.build());
					} else {
						// Send the tool execution result back to the model.
						return this.internalStream(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
								chatResponse);
					}
				}

				return Mono.just(chatResponse);
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

		List<Generation> generations = chatCompletion.content()
			.stream()
			.filter(content -> content.type() != ContentBlock.Type.TOOL_USE)
			.map(content -> new Generation(new AssistantMessage(content.text(), Map.of()),
					ChatGenerationMetadata.builder().finishReason(chatCompletion.stopReason()).build()))
			.toList();

		List<Generation> allGenerations = new ArrayList<>(generations);

		if (chatCompletion.stopReason() != null && generations.isEmpty()) {
			Generation generation = new Generation(new AssistantMessage(null, Map.of()),
					ChatGenerationMetadata.builder().finishReason(chatCompletion.stopReason()).build());
			allGenerations.add(generation);
		}

		List<ContentBlock> toolToUseList = chatCompletion.content()
			.stream()
			.filter(c -> c.type() == ContentBlock.Type.TOOL_USE)
			.toList();

		if (!CollectionUtils.isEmpty(toolToUseList)) {
			List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

			for (ContentBlock toolToUse : toolToUseList) {

				var functionCallId = toolToUse.id();
				var functionName = toolToUse.name();
				var functionArguments = JsonParser.toJson(toolToUse.input());

				toolCalls
					.add(new AssistantMessage.ToolCall(functionCallId, "function", functionName, functionArguments));
			}

			AssistantMessage assistantMessage = new AssistantMessage("", Map.of(), toolCalls);
			Generation toolCallGeneration = new Generation(assistantMessage,
					ChatGenerationMetadata.builder().finishReason(chatCompletion.stopReason()).build());
			allGenerations.add(toolCallGeneration);
		}

		return new ChatResponse(allGenerations, this.from(chatCompletion, usage));
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
			else if (prompt.getOptions() instanceof FunctionCallingOptions functionCallingOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(functionCallingOptions, FunctionCallingOptions.class,
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
			requestOptions.setHttpHeaders(
					mergeHttpHeaders(runtimeOptions.getHttpHeaders(), this.defaultOptions.getHttpHeaders()));
			requestOptions.setInternalToolExecutionEnabled(
					ModelOptionsUtils.mergeOption(runtimeOptions.isInternalToolExecutionEnabled(),
							this.defaultOptions.isInternalToolExecutionEnabled()));
			requestOptions.setToolNames(ToolCallingChatOptions.mergeToolNames(runtimeOptions.getToolNames(),
					this.defaultOptions.getToolNames()));
			requestOptions.setToolCallbacks(ToolCallingChatOptions.mergeToolCallbacks(runtimeOptions.getToolCallbacks(),
					this.defaultOptions.getToolCallbacks()));
			requestOptions.setToolContext(ToolCallingChatOptions.mergeToolContext(runtimeOptions.getToolContext(),
					this.defaultOptions.getToolContext()));
		}
		else {
			requestOptions.setHttpHeaders(this.defaultOptions.getHttpHeaders());
			requestOptions.setInternalToolExecutionEnabled(this.defaultOptions.isInternalToolExecutionEnabled());
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

		List<AnthropicMessage> userMessages = prompt.getInstructions()
			.stream()
			.filter(message -> message.getMessageType() != MessageType.SYSTEM)
			.map(message -> {
				if (message.getMessageType() == MessageType.USER) {
					List<ContentBlock> contents = new ArrayList<>(List.of(new ContentBlock(message.getText())));
					if (message instanceof UserMessage userMessage) {
						if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
							List<ContentBlock> mediaContent = userMessage.getMedia().stream().map(media -> {
								Type contentBlockType = getContentBlockTypeByMedia(media);
								var source = new Source(media.getMimeType().toString(),
										this.fromMediaData(media.getData()));
								return new ContentBlock(contentBlockType, source);
							}).toList();
							contents.addAll(mediaContent);
						}
					}
					return new AnthropicMessage(contents, Role.valueOf(message.getMessageType().name()));
				}
				else if (message.getMessageType() == MessageType.ASSISTANT) {
					AssistantMessage assistantMessage = (AssistantMessage) message;
					List<ContentBlock> contentBlocks = new ArrayList<>();
					if (StringUtils.hasText(message.getText())) {
						contentBlocks.add(new ContentBlock(message.getText()));
					}
					if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
						for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
							contentBlocks.add(new ContentBlock(Type.TOOL_USE, toolCall.id(), toolCall.name(),
									ModelOptionsUtils.jsonToMap(toolCall.arguments())));
						}
					}
					return new AnthropicMessage(contentBlocks, Role.ASSISTANT);
				}
				else if (message.getMessageType() == MessageType.TOOL) {
					List<ContentBlock> toolResponses = ((ToolResponseMessage) message).getResponses()
						.stream()
						.map(toolResponse -> new ContentBlock(Type.TOOL_RESULT, toolResponse.id(),
								toolResponse.responseData()))
						.toList();
					return new AnthropicMessage(toolResponses, Role.USER);
				}
				else {
					throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
				}
			})
			.toList();

		String systemPrompt = prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.SYSTEM)
			.map(m -> m.getText())
			.collect(Collectors.joining(System.lineSeparator()));

		ChatCompletionRequest request = new ChatCompletionRequest(this.defaultOptions.getModel(), userMessages,
				systemPrompt, this.defaultOptions.getMaxTokens(), this.defaultOptions.getTemperature(), stream);

		AnthropicChatOptions requestOptions = (AnthropicChatOptions) prompt.getOptions();
		request = ModelOptionsUtils.merge(requestOptions, request, ChatCompletionRequest.class);

		// Add the tool definitions to the request's tools parameter.
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			request = ModelOptionsUtils.merge(request, this.defaultOptions, ChatCompletionRequest.class);
			request = ChatCompletionRequest.from(request).withTools(getFunctionTools(toolDefinitions)).build();
		}

		return request;
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

		private FunctionCallbackResolver functionCallbackResolver;

		private List<FunctionCallback> toolCallbacks;

		private ToolCallingManager toolCallingManager;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

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

		@Deprecated
		public Builder functionCallbackResolver(FunctionCallbackResolver functionCallbackResolver) {
			this.functionCallbackResolver = functionCallbackResolver;
			return this;
		}

		@Deprecated
		public Builder toolCallbacks(List<FunctionCallback> toolCallbacks) {
			this.toolCallbacks = toolCallbacks;
			return this;
		}

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public AnthropicChatModel build() {
			if (toolCallingManager != null) {
				Assert.isNull(functionCallbackResolver,
						"functionCallbackResolver cannot be set when toolCallingManager is set");
				Assert.isNull(toolCallbacks, "toolCallbacks cannot be set when toolCallingManager is set");

				return new AnthropicChatModel(anthropicApi, defaultOptions, toolCallingManager, retryTemplate,
						observationRegistry);
			}
			if (functionCallbackResolver != null) {
				Assert.isNull(toolCallingManager,
						"toolCallingManager cannot be set when functionCallbackResolver is set");
				List<FunctionCallback> toolCallbacks = this.toolCallbacks != null ? this.toolCallbacks : List.of();

				return new AnthropicChatModel(anthropicApi, defaultOptions, retryTemplate, functionCallbackResolver,
						toolCallbacks, observationRegistry);
			}

			return new AnthropicChatModel(anthropicApi, defaultOptions, DEFAULT_TOOL_CALLING_MANAGER, retryTemplate,
					observationRegistry);
		}

	}

}
