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

package org.springframework.ai.deepseek;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.StreamingPartIndexer;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletion;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletion.Choice;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.ai.deepseek.api.common.DeepSeekConstants;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * {@link ChatModel} and {@link StreamingChatModel} implementation for {@literal DeepSeek}
 * backed by {@link DeepSeekApi}.
 * <p>
 * The assistant message of each choice is built from ordered {@link MessagePart}s: the
 * {@code reasoning_content} becomes a {@link ReasoningPart} without a payload, the
 * {@code content} a {@link TextPart}, and each tool call a {@link ToolCallPart}. That is
 * also the only order DeepSeek produces them in: an assistant message has one field per
 * kind of content and no list of content blocks, and reasoning that is interleaved with
 * tool calls spans several assistant messages, one per tool call round.
 * <p>
 * An assistant message is replayed as one message: the text parts are joined into the
 * {@code content}, the reasoning parts into {@code reasoning_content}, unless another
 * provider signed some of them, and the tool call parts become the {@code tool_calls}.
 * DeepSeek documents that in thinking mode every assistant message of a request with
 * tools must carry its {@code reasoning_content}, so the reasoning survives between the
 * rounds. The last message of a prompt is sent as a chat prefix completion when it
 * carries {@code true} under {@link #PREFIX_METADATA_KEY}.
 *
 * @author Geng Rong
 * @author Thomas Vitale
 * @author Sebastien Deleuze
 * @author guan xu
 * @author Dimitar Proynov
 */
public class DeepSeekChatModel implements ChatModel {

	/**
	 * Message metadata key that marks an assistant message as the prefix the model
	 * completes, see the DeepSeek chat prefix completion feature. An
	 * {@link AssistantMessage} whose metadata holds {@code true} under this key is sent
	 * with {@code prefix: true} when it is the last message of the prompt, and as a plain
	 * turn otherwise.
	 * @since 2.1.0
	 */
	public static final String PREFIX_METADATA_KEY = "deepseek.prefix";

	private static final Log logger = LogFactory.getLog(DeepSeekChatModel.class);

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	/**
	 * The default options used for the chat completion requests.
	 */
	private final DeepSeekChatOptions options;

	/**
	 * The retry template used to retry the DeepSeek API calls.
	 */
	public final RetryTemplate retryTemplate;

	/**
	 * Low-level access to the DeepSeek API.
	 */
	private final DeepSeekApi deepSeekApi;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * The tool calling manager used to resolve the tool definitions sent to the model.
	 */
	private final ToolCallingManager toolCallingManager;

	/**
	 * Conventions to use for generating observations.
	 */
	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public DeepSeekChatModel(DeepSeekApi deepSeekApi, DeepSeekChatOptions options,
			ToolCallingManager toolCallingManager, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		Assert.notNull(deepSeekApi, "deepSeekApi cannot be null");
		Assert.notNull(options, "options cannot be null");
		Assert.notNull(toolCallingManager, "toolCallingManager cannot be null");
		Assert.notNull(retryTemplate, "retryTemplate cannot be null");
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");
		this.deepSeekApi = deepSeekApi;
		this.options = options;
		this.toolCallingManager = toolCallingManager;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalCall(requestPrompt, null);
	}

	private ChatResponse internalCall(Prompt prompt, @Nullable ChatResponse previousChatResponse) {

		ChatCompletionRequest request = createRequest(prompt, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(DeepSeekConstants.PROVIDER_NAME)
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				ResponseEntity<ChatCompletion> completionEntity = RetryUtils.execute(this.retryTemplate,
						() -> this.deepSeekApi.chatCompletionEntity(request));

				var chatCompletion = completionEntity.getBody();

				if (chatCompletion == null) {
					if (logger.isWarnEnabled()) {
						logger.warn("No chat completion returned for prompt: " + prompt);
					}
					return new ChatResponse(List.of());
				}

				List<Choice> choices = chatCompletion.choices();
				if (choices == null) {
					if (logger.isWarnEnabled()) {
						logger.warn("No choices returned for prompt: " + prompt);
					}
					return new ChatResponse(List.of());
				}

				List<Generation> generations = choices.stream().map(choice -> {
			// @formatter:off
					Map<String, Object> metadata = Map.of(
							"id", chatCompletion.id() != null ? chatCompletion.id() : "",
							"role", choice.message().role() != null ? choice.message().role().name() : "",
							"index", choice.index(),
							"finishReason", choice.finishReason() != null ? choice.finishReason().name() : "");
					// @formatter:on
					return buildGeneration(choice, metadata, null);
				}).toList();

				// Current usage
				ChatCompletion body = completionEntity.getBody();
				Assert.state(body != null, "Body must not be null");
				DeepSeekApi.Usage usage = body.usage();
				Usage currentChatResponseUsage = usage != null ? getDefaultUsage(usage) : new EmptyUsage();
				Usage accumulatedUsage = UsageCalculator.getCumulativeUsage(currentChatResponseUsage,
						previousChatResponse);
				ChatResponse chatResponse = new ChatResponse(generations, from(body, accumulatedUsage));

				observationContext.setResponse(chatResponse);

				return chatResponse;

			});

		return response;
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return internalStream(requestPrompt, null);
	}

	private Flux<ChatResponse> internalStream(Prompt prompt, @Nullable ChatResponse previousChatResponse) {
		return Flux.deferContextual(contextView -> {
			ChatCompletionRequest request = createRequest(prompt, true);

			Flux<DeepSeekApi.ChatCompletionChunk> completionChunks = this.deepSeekApi.chatCompletionStream(request);

			StreamAccumulator streamAccumulator = new StreamAccumulator();

			final ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(DeepSeekConstants.PROVIDER_NAME)
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

			Flux<ChatResponse> chatResponse = completionChunks.map(this::chunkToChatCompletion).map(chatCompletion -> {
				try {
					String id = chatCompletion.id();

					List<Generation> generations = chatCompletion.choices().stream().map(choice -> {
						// @formatter:off
						Map<String, Object> metadata = Map.of(
								"id", chatCompletion.id(),
								"role", streamAccumulator.role(id, choice.message()),
								"finishReason", choice.finishReason() != null ? choice.finishReason().name() : ""
						);
						// @formatter:on
						return buildGeneration(choice, metadata, streamAccumulator.partIndexer(id));
					}).toList();
					DeepSeekApi.Usage usage = chatCompletion.usage();
					Usage currentUsage = (usage != null) ? getDefaultUsage(usage) : new EmptyUsage();
					Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(currentUsage, previousChatResponse);

					return new ChatResponse(generations, from(chatCompletion, cumulativeUsage));
				}
				catch (Exception e) {
					logger.error("Error processing chat completion", e);
					return new ChatResponse(List.of());
				}

			});

			// @formatter:off
			Flux<ChatResponse> flux = chatResponse
				.doOnError(observation::error)
				.doFinally(s -> observation.stop())
				.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on

			return new MessageAggregator().aggregate(flux, observationContext::setResponse);

		});
	}

	/**
	 * Builds the generation of one choice. When {@code partIndexer} is not {@code null}
	 * the choice is a streamed chunk, and its parts are stamped with their stream index,
	 * see {@link StreamingPartIndexer}.
	 */
	@SuppressWarnings("removal")
	private Generation buildGeneration(Choice choice, Map<String, Object> metadata,
			@Nullable StreamingPartIndexer partIndexer) {
		String finishReason = (choice.finishReason() != null ? choice.finishReason().name() : "");
		var generationMetadataBuilder = ChatGenerationMetadata.builder().finishReason(finishReason);

		// Still a DeepSeekAssistantMessage while that type is deprecated, so that
		// existing casts keep working
		AssistantMessage assistantMessage = DeepSeekAssistantMessage.builder()
			.parts(assistantParts(choice.message(), partIndexer))
			.properties(metadata)
			.build();

		return new Generation(assistantMessage, generationMetadataBuilder.build());
	}

	/**
	 * The parts of the assistant message of one choice, in the order DeepSeek produces
	 * them: reasoning, text, then tool calls.
	 */
	private static List<MessagePart> assistantParts(ChatCompletionMessage message,
			@Nullable StreamingPartIndexer partIndexer) {
		List<MessagePart> parts = new ArrayList<>();
		// hasLength, not hasText: a streamed reasoning delta is often a single
		// whitespace or newline token that must be kept.
		String reasoning = message.reasoningContent();
		if (StringUtils.hasLength(reasoning)) {
			parts.add(ReasoningPart.of(reasoning));
		}
		// An empty content has no text part: a tool-call answer or a reasoning-only
		// chunk carries none, and an empty text part between two reasoning deltas would
		// split the reasoning in two.
		String text = message.content();
		if (StringUtils.hasLength(text)) {
			parts.add(TextPart.of(text));
		}
		if (message.toolCalls() != null) {
			for (ToolCall toolCall : message.toolCalls()) {
				parts.add(ToolCallPart.of(new AssistantMessage.ToolCall(toolCall.id(), "function",
						toolCall.function().name(), toolCall.function().arguments())));
			}
		}
		if (partIndexer != null) {
			parts.replaceAll(partIndexer::stamp);
		}
		if (parts.isEmpty()) {
			// A message without any part, such as the role-only first chunk of a stream,
			// keeps an empty text, so that getText() is not null and streamed chunk texts
			// can be joined without a null check.
			parts.add(TextPart.of(""));
		}
		return parts;
	}

	/**
	 * The reasoning of an assistant message to send back as {@code reasoning_content},
	 * joined from its reasoning parts, unless it was overridden through the deprecated
	 * {@link DeepSeekAssistantMessage#setReasoningContent(String)}. DeepSeek reasoning
	 * carries no payload, so a payload means another provider signed the reasoning: it is
	 * only valid with that payload, which this API has no field for, so none of the
	 * reasoning of such a message is replayed.
	 */
	@SuppressWarnings("removal")
	private static @Nullable String reasoningContent(AssistantMessage assistantMessage) {
		List<ReasoningPart> reasoningParts = assistantMessage.getReasoning();
		String reasoning;
		if (assistantMessage instanceof DeepSeekAssistantMessage deepSeekAssistantMessage
				&& deepSeekAssistantMessage.isReasoningContentOverridden()) {
			reasoning = deepSeekAssistantMessage.getReasoningContent();
		}
		else if (reasoningParts.stream().anyMatch(part -> part.payload() != null)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Not replaying reasoning signed by another provider");
			}
			return null;
		}
		else {
			reasoning = reasoningParts.stream()
				.map(ReasoningPart::text)
				.filter(Objects::nonNull)
				.collect(Collectors.joining());
		}
		return StringUtils.hasLength(reasoning) ? reasoning : null;
	}

	private static boolean isPrefix(AssistantMessage assistantMessage) {
		return Boolean.TRUE.equals(assistantMessage.getMetadata().get(PREFIX_METADATA_KEY));
	}

	private ChatResponseMetadata from(DeepSeekApi.ChatCompletion result, Usage usage) {
		Assert.notNull(result, "DeepSeek ChatCompletionResult must not be null");
		var builder = ChatResponseMetadata.builder()
			.id(result.id() != null ? result.id() : "")
			.usage(usage)
			.model(result.model() != null ? result.model() : "")
			.keyValue("created", result.created() != null ? result.created() : 0L)
			.keyValue("system-fingerprint", result.systemFingerprint() != null ? result.systemFingerprint() : "");
		return builder.build();
	}

	private ChatResponseMetadata from(ChatResponseMetadata chatResponseMetadata, Usage usage) {
		Assert.notNull(chatResponseMetadata, "DeepSeek ChatResponseMetadata must not be null");
		var builder = ChatResponseMetadata.builder()
			.id(chatResponseMetadata.getId() != null ? chatResponseMetadata.getId() : "")
			.usage(usage)
			.model(chatResponseMetadata.getModel() != null ? chatResponseMetadata.getModel() : "");
		return builder.build();
	}

	/**
	 * Convert the ChatCompletionChunk into a ChatCompletion. The Usage is set to null.
	 * @param chunk the ChatCompletionChunk to convert
	 * @return the ChatCompletion
	 */
	private DeepSeekApi.ChatCompletion chunkToChatCompletion(DeepSeekApi.ChatCompletionChunk chunk) {
		List<Choice> choices = chunk.choices()
			.stream()
			.map(chunkChoice -> new Choice(chunkChoice.finishReason(), chunkChoice.index(), chunkChoice.delta(),
					chunkChoice.logprobs()))
			.toList();

		return new DeepSeekApi.ChatCompletion(chunk.id(), choices, chunk.created(), chunk.model(), chunk.serviceTier(),
				chunk.systemFingerprint(), chunk.usage());
	}

	private DefaultUsage getDefaultUsage(DeepSeekApi.Usage usage) {
		return new DefaultUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), usage);
	}

	/**
	 * Accessible for testing.
	 */
	ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
		List<Message> instructions = prompt.getInstructions();
		// Only the last message of a prompt can be the prefix of a completion: a flagged
		// message restored from chat memory earlier in the conversation is a plain turn.
		Message lastMessage = instructions.isEmpty() ? null : instructions.get(instructions.size() - 1);
		List<ChatCompletionMessage> chatCompletionMessages = instructions.stream().map(message -> {
			if (message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.SYSTEM) {
				String text = message.getText();
				Assert.state(text != null, "text must not be null");
				return List.of(new ChatCompletionMessage(text,
						ChatCompletionMessage.Role.valueOf(message.getMessageType().name())));
			}
			else if (message.getMessageType() == MessageType.ASSISTANT) {
				var assistantMessage = (AssistantMessage) message;
				List<ToolCall> toolCalls = null;
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					toolCalls = assistantMessage.getToolCalls().stream().map(toolCall -> {
						var function = new ChatCompletionFunction(toolCall.name(), toolCall.arguments());
						return new ToolCall(toolCall.id(), toolCall.type(), function);
					}).toList();
				}
				Boolean isPrefixAssistantMessage = (message == lastMessage && isPrefix(assistantMessage)) ? Boolean.TRUE
						: null;
				String text = assistantMessage.getText();
				Assert.state(text != null, "text must not be null");
				return List.of(new ChatCompletionMessage(text, ChatCompletionMessage.Role.ASSISTANT, null, null,
						toolCalls, isPrefixAssistantMessage, reasoningContent(assistantMessage)));
			}
			else if (message.getMessageType() == MessageType.TOOL) {
				ToolResponseMessage toolMessage = (ToolResponseMessage) message;

				toolMessage.getResponses()
					.forEach(response -> Assert.isTrue(response.id() != null, "ToolResponseMessage must have an id"));
				return toolMessage.getResponses()
					.stream()
					.map(tr -> new ChatCompletionMessage(tr.responseData(), ChatCompletionMessage.Role.TOOL, tr.name(),
							tr.id(), null))
					.toList();
			}
			else {
				throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
			}
		}).flatMap(List::stream).toList();

		ChatCompletionRequest.Builder requestBuilder = ChatCompletionRequest.builder()
			.messages(chatCompletionMessages)
			.stream(stream);

		DeepSeekChatOptions options = (DeepSeekChatOptions) prompt.getOptions();
		Assert.state(options != null, "requestOptions must not be null");

		validateThinkingParameters(options);

		if (options.getModel() != null) {
			requestBuilder.model(options.getModel());
		}
		if (options.getFrequencyPenalty() != null) {
			requestBuilder.frequencyPenalty(options.getFrequencyPenalty());
		}
		if (options.getMaxTokens() != null) {
			requestBuilder.maxTokens(options.getMaxTokens());
		}
		if (options.getPresencePenalty() != null) {
			requestBuilder.presencePenalty(options.getPresencePenalty());
		}
		if (options.getResponseFormat() != null) {
			requestBuilder.responseFormat(options.getResponseFormat());
		}
		if (options.getStop() != null) {
			requestBuilder.stop(options.getStop());
		}
		if (options.getTemperature() != null) {
			requestBuilder.temperature(options.getTemperature());
		}
		if (options.getTopP() != null) {
			requestBuilder.topP(options.getTopP());
		}
		if (options.getLogprobs() != null) {
			requestBuilder.logprobs(options.getLogprobs());
		}
		if (options.getTopLogprobs() != null) {
			requestBuilder.topLogprobs(options.getTopLogprobs());
		}
		if (options.getTools() != null) {
			requestBuilder.tools(options.getTools());
		}
		if (options.getToolChoice() != null) {
			requestBuilder.toolChoice(options.getToolChoice());
		}
		if (options.getThinking() != null) {
			requestBuilder.thinking(options.getThinking());
		}
		if (options.getReasoningEffort() != null) {
			requestBuilder.reasoningEffort(options.getReasoningEffort());
		}

		// Add the tool definitions to the request's tools parameter.
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(options);
		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			requestBuilder.tools(this.getFunctionTools(toolDefinitions));
		}

		return requestBuilder.build();
	}

	/**
	 * Thinking mode does not support the {@code temperature}, {@code top_p},
	 * {@code presence_penalty}, or {@code frequency_penalty} parameters. For
	 * compatibility with existing software, setting these parameters does not trigger an
	 * error but also has no effect, so a warning is logged to alert callers that the
	 * values will be silently ignored by the DeepSeek API.
	 * @param options the chat options to validate
	 */
	private void validateThinkingParameters(DeepSeekChatOptions options) {
		if (logger.isWarnEnabled()) {
			ChatCompletionRequest.Thinking thinking = options.getThinking();
			if (thinking == null || ChatCompletionRequest.Thinking.Type.ENABLED == thinking.type()) {
				List<String> ignoredParameters = new ArrayList<>();
				if (options.getTemperature() != null) {
					ignoredParameters.add("temperature");
				}
				if (options.getTopP() != null) {
					ignoredParameters.add("top_p");
				}
				if (options.getPresencePenalty() != null) {
					ignoredParameters.add("presence_penalty");
				}
				if (options.getFrequencyPenalty() != null) {
					ignoredParameters.add("frequency_penalty");
				}
				if (!ignoredParameters.isEmpty()) {
					logger.warn("Thinking mode does not support the " + String.join(", ", ignoredParameters)
							+ " parameter(s). Please note that, for compatibility with existing software, setting these parameters will not trigger an error but will also have no effect.");
				}
			}
		}
	}

	private List<DeepSeekApi.FunctionTool> getFunctionTools(List<ToolDefinition> toolDefinitions) {
		return toolDefinitions.stream().map(toolDefinition -> {
			var function = new DeepSeekApi.FunctionTool.Function(toolDefinition.description(), toolDefinition.name(),
					toolDefinition.inputSchema());
			return new DeepSeekApi.FunctionTool(function);
		}).toList();
	}

	/**
	 * @since 2.0.0
	 */
	@Override
	public DeepSeekChatOptions getOptions() {
		return this.options;
	}

	@Override
	public String toString() {
		return "DeepSeekChatModel [options=" + this.options + "]";
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

	/**
	 * The state of one streamed response that spans its chunks: the role, which only the
	 * first chunk of a completion carries, and the {@link StreamingPartIndexer} of each
	 * completion.
	 */
	private static final class StreamAccumulator {

		private final Map<String, String> roleById = new ConcurrentHashMap<>();

		private final Map<String, StreamingPartIndexer> partIndexerById = new ConcurrentHashMap<>();

		String role(String id, ChatCompletionMessage message) {
			if (message.role() != null) {
				this.roleById.putIfAbsent(id, message.role().name());
			}
			return this.roleById.getOrDefault(id, "");
		}

		StreamingPartIndexer partIndexer(String id) {
			return this.partIndexerById.computeIfAbsent(id, key -> new StreamingPartIndexer());
		}

	}

	public static final class Builder {

		private @Nullable DeepSeekApi deepSeekApi;

		private DeepSeekChatOptions options = DeepSeekChatOptions.builder().build();

		private @Nullable ToolCallingManager toolCallingManager;

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private Builder() {
		}

		public Builder deepSeekApi(DeepSeekApi deepSeekApi) {
			this.deepSeekApi = deepSeekApi;
			return this;
		}

		public Builder options(DeepSeekChatOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * Sets the tool calling manager used to resolve the tool definitions sent to the
		 * model.
		 * @param toolCallingManager the tool calling manager
		 * @return this builder
		 */
		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
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

		public DeepSeekChatModel build() {
			Assert.state(this.deepSeekApi != null, "DeepSeekApi must not be null");
			return new DeepSeekChatModel(this.deepSeekApi, this.options,
					Objects.requireNonNullElse(this.toolCallingManager, DEFAULT_TOOL_CALLING_MANAGER),
					this.retryTemplate, this.observationRegistry);
		}

	}

}
