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

package org.springframework.ai.cohere.chat;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
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
import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletion;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.Role;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionRequest;
import org.springframework.ai.cohere.api.CohereApi.FunctionTool;
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
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;

/**
 * Represents a Cohere Chat Model.
 *
 * @author Ricken Bazolo
 */
public class CohereChatModel implements ChatModel {

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * The default options used for the chat completion requests.
	 */
	private final CohereChatOptions defaultOptions;

	/**
	 * Low-level access to the Cohere API.
	 */
	private final CohereApi cohereApi;

	private final RetryTemplate retryTemplate;

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

	public CohereChatModel(CohereApi cohereApi, CohereChatOptions defaultOptions, ToolCallingManager toolCallingManager,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		this(cohereApi, defaultOptions, toolCallingManager, retryTemplate, observationRegistry,
				new DefaultToolExecutionEligibilityPredicate());
	}

	public CohereChatModel(CohereApi cohereApi, CohereChatOptions defaultOptions, ToolCallingManager toolCallingManager,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry,
			ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
		Assert.notNull(cohereApi, "cohereApi cannot be null");
		Assert.notNull(defaultOptions, "defaultOptions cannot be null");
		Assert.notNull(toolCallingManager, "toolCallingManager cannot be null");
		Assert.notNull(retryTemplate, "retryTemplate cannot be null");
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");
		Assert.notNull(toolExecutionEligibilityPredicate, "toolExecutionEligibilityPredicate cannot be null");
		this.cohereApi = cohereApi;
		this.defaultOptions = defaultOptions;
		this.toolCallingManager = toolCallingManager;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
		this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
	}

	public static ChatResponseMetadata from(ChatCompletion result) {
		Assert.notNull(result, "Cohere ChatCompletion must not be null");
		DefaultUsage usage = getDefaultUsage(result.usage());
		return ChatResponseMetadata.builder().id(result.id()).usage(usage).build();
	}

	public static ChatResponseMetadata from(ChatCompletion result, Usage usage) {
		Assert.notNull(result, "Cohere ChatCompletion must not be null");
		return ChatResponseMetadata.builder().id(result.id()).usage(usage).build();
	}

	private static DefaultUsage getDefaultUsage(CohereApi.Usage usage) {
		return new DefaultUsage(usage.tokens().inputTokens(), usage.tokens().outputTokens(), null, usage);
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalCall(requestPrompt, null);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalStream(requestPrompt, null);
	}

	public Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse previousChatResponse) {
		return Flux.deferContextual(contextView -> {
			var request = createRequest(prompt, true);

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(CohereApi.PROVIDER_NAME)
				.build();

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			Flux<CohereApi.ChatCompletionChunk> completionChunks = RetryUtils.execute(this.retryTemplate,
					() -> this.cohereApi.chatCompletionStream(request));

			// For chunked responses, only the first chunk contains the role.
			// The rest of the chunks with same ID share the same role.
			ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

			// Convert the ChatCompletionChunk into a ChatCompletion to be able to reuse
			// the function call handling logic.
			Flux<ChatResponse> chatResponse = completionChunks.map(this::toChatCompletion)
				.filter(chatCompletion -> chatCompletion != null && chatCompletion.message() != null)
				.switchMap(chatCompletion -> Mono.just(chatCompletion).map(completion -> {
					try {
						@SuppressWarnings("null")
						String id = completion.id();
						ChatCompletionMessage.Provider message = completion.message();

						// Store the role for this completion ID
						if (message.role() != null && id != null) {
							roleMap.putIfAbsent(id, message.role().name());
						}

						List<Generation> generations = message.content().stream().map(content -> {
							Map<String, Object> metadata = Map.of("id", completion.id() != null ? completion.id() : "",
									"role", completion.id() != null ? roleMap.getOrDefault(id, "") : "", "finishReason",
									completion.finishReason() != null ? completion.finishReason().name() : "");
							return buildGeneration(content, completion, metadata);
						}).toList();

						if (completion.usage() != null) {
							DefaultUsage usage = getDefaultUsage(completion.usage());
							Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(usage, previousChatResponse);
							return new ChatResponse(generations, from(completion, cumulativeUsage));
						}
						else {
							return new ChatResponse(generations);
						}
					}
					catch (Exception e) {
						logger.error("Error processing chat completion", e);
						return new ChatResponse(List.of());
					}
				}));

			// @formatter:off
			Flux<ChatResponse> chatResponseFlux = chatResponse.flatMap(response -> {
						if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), response)) {
							return Flux.deferContextual(ctx -> {
								ToolExecutionResult toolExecutionResult;
								try {
									ToolCallReactiveContextHolder.setContext(ctx);
									toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
								}
								finally {
									ToolCallReactiveContextHolder.clearContext();
								}
								if (toolExecutionResult.returnDirect()) {
									// Return tool execution result directly to the client.
									return Flux.just(ChatResponse.builder().from(response)
											.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
											.build());
								}
								else {
									// Send the tool execution result back to the model.
									var chatOptions = CohereChatOptions.fromOptions(prompt.getOptions().copy());
									return this.internalStream(new Prompt(toolExecutionResult.conversationHistory(), chatOptions),
											response);
								}
							}).subscribeOn(Schedulers.boundedElastic());
						}
						else {
							return Flux.just(response);
						}
					})
					.doOnError(observation::error)
					.doFinally(s -> observation.stop())
					.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on;

			return new MessageAggregator().aggregate(chatResponseFlux, observationContext::setResponse);
		});

	}

	private ChatCompletion toChatCompletion(CohereApi.ChatCompletionChunk chunk) {
		if (chunk == null || chunk.delta() == null) {
			return null;
		}

		CohereApi.ChatCompletionChunk.ChunkDelta delta = chunk.delta();
		ChatCompletionMessage message = delta.message();

		ChatCompletionMessage.Provider provider = null;
		if (message != null) {

			List<ChatCompletionMessage.MessageContent> content = extractMessageContent(message.rawContent());

			provider = new ChatCompletionMessage.Provider(content, message.role(), message.toolPlan(),
					message.toolCalls(), message.citations());
		}

		return new ChatCompletion(chunk.id(), delta.finishReason(), provider, null, delta.usage());
	}

	private List<ChatCompletionMessage.MessageContent> extractMessageContent(Object rawContent) {
		if (rawContent == null) {
			return List.of();
		}

		if (rawContent instanceof String text) {
			return List.of(new ChatCompletionMessage.MessageContent("text", text, null));
		}

		if (rawContent instanceof List<?> list) {
			List<ChatCompletionMessage.MessageContent> messageContents = new ArrayList<>();
			for (Object item : list) {
				if (item instanceof ChatCompletionMessage.MessageContent mc) {
					messageContents.add(mc);
				}
				else if (item instanceof Map<?, ?> map) {
					String type = (String) map.get("type");
					String text = (String) map.get("text");
					Object value = map.get("value");
					messageContents.add(new ChatCompletionMessage.MessageContent(type, text, value));
				}
			}
			return messageContents;
		}

		if (rawContent instanceof Map<?, ?> map) {
			String type = (String) map.get("type");
			String text = (String) map.get("text");
			Object value = map.get("value");
			return List.of(new ChatCompletionMessage.MessageContent(type != null ? type : "text", text, value));
		}

		return List.of();
	}

	Prompt buildRequestPrompt(Prompt prompt) {
		// Process runtime options
		CohereChatOptions runtimeOptions = null;
		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
				runtimeOptions = ModelOptionsUtils.copyToTarget(toolCallingChatOptions, ToolCallingChatOptions.class,
						CohereChatOptions.class);
			}
			else {
				runtimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						CohereChatOptions.class);
			}
		}

		// Define request options by merging runtime options and default options
		CohereChatOptions requestOptions = ModelOptionsUtils.merge(runtimeOptions, this.defaultOptions,
				CohereChatOptions.class);

		// Merge @JsonIgnore-annotated options explicitly since they are ignored by
		// Jackson, used by ModelOptionsUtils.
		if (runtimeOptions != null) {
			requestOptions.setInternalToolExecutionEnabled(
					ModelOptionsUtils.mergeOption(runtimeOptions.getInternalToolExecutionEnabled(),
							this.defaultOptions.getInternalToolExecutionEnabled()));
			requestOptions.setToolNames(ToolCallingChatOptions.mergeToolNames(runtimeOptions.getToolNames(),
					this.defaultOptions.getToolNames()));
			requestOptions.setToolCallbacks(ToolCallingChatOptions.mergeToolCallbacks(runtimeOptions.getToolCallbacks(),
					this.defaultOptions.getToolCallbacks()));
			requestOptions.setToolContext(ToolCallingChatOptions.mergeToolContext(runtimeOptions.getToolContext(),
					this.defaultOptions.getToolContext()));
		}
		else {
			requestOptions.setInternalToolExecutionEnabled(this.defaultOptions.getInternalToolExecutionEnabled());
			requestOptions.setToolNames(this.defaultOptions.getToolNames());
			requestOptions.setToolCallbacks(this.defaultOptions.getToolCallbacks());
			requestOptions.setToolContext(this.defaultOptions.getToolContext());
		}

		ToolCallingChatOptions.validateToolCallbacks(requestOptions.getToolCallbacks());

		return new Prompt(prompt.getInstructions(), requestOptions);
	}

	private ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {
		ChatCompletionRequest request = createRequest(prompt, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(CohereApi.PROVIDER_NAME)
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				ResponseEntity<ChatCompletion> completionEntity = RetryUtils.execute(this.retryTemplate,
						() -> this.cohereApi.chatCompletionEntity(request));

				ChatCompletion chatCompletion = completionEntity.getBody();

				if (chatCompletion == null) {
					logger.warn("No chat completion returned for prompt: {}", prompt);
					return new ChatResponse(List.of());
				}

				final Map<String, Object> metadata = Map.of("id",
						chatCompletion.id() != null ? chatCompletion.id() : "", "role",
						chatCompletion.message().role() != null ? chatCompletion.message().role().name() : "",
						"finishReason",
						chatCompletion.finishReason() != null ? chatCompletion.finishReason().name() : "");

				List<Generation> generations = new ArrayList<>();

				if (chatCompletion.finishReason() == null) { // Just for secure
					logger.warn("No chat completion finishReason returned for prompt: {}", prompt);
					return new ChatResponse(List.of());
				}

				if (chatCompletion.finishReason().equals(CohereApi.ChatCompletionFinishReason.TOOL_CALL)) {
					var generation = buildGeneration(null, chatCompletion, metadata);
					generations.add(generation);
				}
				else {
					generations = chatCompletion.message()
						.content()
						.stream()
						.map(content -> buildGeneration(content, chatCompletion, metadata))
						.toList();
				}

				DefaultUsage usage = getDefaultUsage(completionEntity.getBody().usage());
				Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(usage, previousChatResponse);

				ChatResponse chatResponse = new ChatResponse(generations,
						from(completionEntity.getBody(), cumulativeUsage));

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
				// remove tools actions before
				ChatOptions chatOptions = CohereChatOptions.fromOptions2(prompt.getOptions().copy());
				// Send the tool execution result back to the model.
				return this.internalCall(new Prompt(toolExecutionResult.conversationHistory(), chatOptions), null);
			}
		}

		return response;
	}

	private Generation buildGeneration(ChatCompletionMessage.MessageContent content, ChatCompletion completion,
			Map<String, Object> metadata) {
		List<AssistantMessage.ToolCall> toolCalls = completion.message().toolCalls() == null ? List.of()
				: completion.message()
					.toolCalls()
					.stream()
					.map(toolCall -> new AssistantMessage.ToolCall(toolCall.id(), "function",
							toolCall.function().name(), toolCall.function().arguments()))
					.toList();

		var assistantMessage = AssistantMessage.builder()
			.content(content != null ? content.text() : "")
			.toolCalls(toolCalls)
			.properties(metadata)
			.build();

		String finishReason = (completion.finishReason() != null ? completion.finishReason().name() : "");
		var generationMetadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();
		return new Generation(assistantMessage, generationMetadata);
	}

	/**
	 * Accessible for testing.
	 */
	ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
		List<ChatCompletionMessage> chatCompletionMessages = prompt.getInstructions()
			.stream()
			.map(this::convertToCohereMessage)
			.flatMap(List::stream)
			.toList();

		var request = new ChatCompletionRequest(chatCompletionMessages, stream);

		CohereChatOptions requestOptions = (CohereChatOptions) prompt.getOptions();
		request = ModelOptionsUtils.merge(requestOptions, request, ChatCompletionRequest.class);

		// Add the tool definitions to the request's tools parameter.
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			request = ModelOptionsUtils.merge(
					CohereChatOptions.builder().tools(this.getFunctionTools(toolDefinitions)).build(), request,
					ChatCompletionRequest.class);
		}

		return request;
	}

	/**
	 * Convert a Spring AI message to Cohere API message(s).
	 * @param message the Spring AI message to convert
	 * @return list of Cohere ChatCompletionMessage(s)
	 */
	private List<ChatCompletionMessage> convertToCohereMessage(Message message) {
		return switch (message.getMessageType()) {
			case USER -> convertUserMessage(message);
			case ASSISTANT -> convertAssistantMessage(message);
			case SYSTEM -> convertSystemMessage(message);
			case TOOL -> convertToolMessage(message);
		};
	}

	/**
	 * Convert a USER message.
	 */
	private List<ChatCompletionMessage> convertUserMessage(org.springframework.ai.chat.messages.Message message) {
		Object content = message.getText();

		if (message instanceof UserMessage userMessage && !CollectionUtils.isEmpty(userMessage.getMedia())) {
			// Validate images before processing
			CohereImageValidator.validateImages(userMessage.getMedia());

			List<ChatCompletionMessage.MediaContent> contentList = new ArrayList<>(
					List.of(new ChatCompletionMessage.MediaContent(message.getText())));

			contentList.addAll(userMessage.getMedia().stream().map(this::mapToMediaContent).toList());

			content = contentList;
		}

		return List.of(new ChatCompletionMessage(content, Role.USER));
	}

	/**
	 * Convert an ASSISTANT message.
	 */
	private List<ChatCompletionMessage> convertAssistantMessage(org.springframework.ai.chat.messages.Message message) {
		if (!(message instanceof AssistantMessage assistantMessage)) {
			throw new IllegalArgumentException("Unsupported assistant message class: " + message.getClass().getName());
		}

		List<ToolCall> toolCalls = null;
		if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
			toolCalls = convertToolCalls(assistantMessage.getToolCalls());
		}

		return List.of(new ChatCompletionMessage(assistantMessage.getText(), Role.ASSISTANT, toolCalls));
	}

	/**
	 * Convert tool calls.
	 */
	private List<ToolCall> convertToolCalls(List<AssistantMessage.ToolCall> springToolCalls) {
		return springToolCalls.stream().map(toolCall -> {
			var function = new ChatCompletionMessage.ChatCompletionFunction(toolCall.name(), toolCall.arguments());
			return new ToolCall(toolCall.id(), toolCall.type(), function, null);
		}).toList();
	}

	/**
	 * Convert a SYSTEM message.
	 */
	private List<ChatCompletionMessage> convertSystemMessage(org.springframework.ai.chat.messages.Message message) {
		return List.of(new ChatCompletionMessage(message.getText(), Role.SYSTEM));
	}

	/**
	 * Convert a TOOL response message to Cohere format. Validates that all tool responses
	 * have an ID.
	 */
	private List<ChatCompletionMessage> convertToolMessage(Message message) {

		if (!(message instanceof ToolResponseMessage toolResponseMessage)) {
			throw new IllegalArgumentException("Unsupported tool message class: " + message.getClass().getName());
		}

		return toolResponseMessage.getResponses().stream().map(toolResponse -> {
			Assert.notNull(toolResponse.id(), "ToolResponseMessage.ToolResponse must have an id");
			return new ChatCompletionMessage(toolResponse.responseData(), Role.TOOL, toolResponse.name(), null, null,
					toolResponse.id());
		}).toList();
	}

	private ChatCompletionMessage.MediaContent mapToMediaContent(Media media) {
		CohereApi.ChatCompletionMessage.MediaContent.DetailLevel detail = this.defaultOptions != null
				? this.defaultOptions.getImageDetail() : null;
		return new ChatCompletionMessage.MediaContent(new ChatCompletionMessage.MediaContent.ImageUrl(
				this.fromMediaData(media.getMimeType(), media.getData()), detail));
	}

	private String fromMediaData(MimeType mimeType, Object mediaContentData) {
		if (mediaContentData instanceof byte[] bytes) {
			// Assume the bytes are an image. So, convert the bytes to a base64 encoded
			// following the prefix pattern.
			return String.format("data:%s;base64,%s", mimeType.toString(), Base64.getEncoder().encodeToString(bytes));
		}
		else if (mediaContentData instanceof String text) {
			// Assume the text is a URLs or a base64 encoded image prefixed by the user.
			return text;
		}
		else {
			throw new IllegalArgumentException(
					"Unsupported media data type: " + mediaContentData.getClass().getSimpleName());
		}
	}

	private List<FunctionTool> getFunctionTools(List<ToolDefinition> toolDefinitions) {
		return toolDefinitions.stream().map(toolDefinition -> {
			var function = new FunctionTool.Function(toolDefinition.description(), toolDefinition.name(),
					toolDefinition.inputSchema());
			return new FunctionTool(function);
		}).toList();
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return CohereChatOptions.fromOptions(this.defaultOptions);
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

		private CohereApi cohereApi;

		private CohereChatOptions defaultOptions = CohereChatOptions.builder()
			.temperature(0.3)
			.topP(1.0)
			.model(CohereApi.ChatModel.COMMAND_A_R7B.getValue())
			.build();

		private ToolCallingManager toolCallingManager;

		private ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate = new DefaultToolExecutionEligibilityPredicate();

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private Builder() {
		}

		public Builder cohereApi(CohereApi cohereApi) {
			this.cohereApi = cohereApi;
			return this;
		}

		public Builder defaultOptions(CohereChatOptions defaultOptions) {
			this.defaultOptions = defaultOptions;
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

		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public CohereChatModel build() {
			if (this.toolCallingManager != null) {
				return new CohereChatModel(this.cohereApi, this.defaultOptions, this.toolCallingManager,
						this.retryTemplate, this.observationRegistry, this.toolExecutionEligibilityPredicate);
			}
			return new CohereChatModel(this.cohereApi, this.defaultOptions, DEFAULT_TOOL_CALLING_MANAGER,
					this.retryTemplate, this.observationRegistry, this.toolExecutionEligibilityPredicate);
		}

	}

}
