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

package org.springframework.ai.openai;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.JsonValue;
import com.openai.core.RequestOptions;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.core.http.HttpResponseFor;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.ReasoningEffort;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.ResponseFormatText;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionAudio;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta.ToolCall;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta.ToolCall.Function;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.FinishReason;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartInputAudio;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionNamedToolChoice;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.chat.completions.ChatCompletionToolChoiceOption;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.completions.CompletionUsage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.RateLimit;
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
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.metadata.OpenAiRateLimit;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

/**
 * Chat Model implementation using the OpenAI Java SDK.
 *
 * @author Julien Dubois
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Ilayaperumal Gopinathan
 * @author Thomas Vitale
 * @author Eric Bottard
 * @author Taewoong Kim
 * @author Jewoo Shin
 * @author guan xu
 * @author Raphael Vullriede
 */
public final class OpenAiChatModel implements ChatModel {

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final String REASONING_CONTENT = "reasoningContent";

	static final String TOOL_CALL_ADDITIONAL_PROPERTIES_METADATA_KEY = "openai.tool_calls.additional_properties";

	private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
	};

	// Jackson 2 required due to OpenAI deserializers
	private static final ObjectMapper objectMapper = new ObjectMapper();

	private final Log logger = LogFactory.getLog(OpenAiChatModel.class);

	private final OpenAIClient openAiClient;

	private final OpenAIClientAsync openAiClientAsync;

	private final OpenAiChatOptions options;

	private final ObservationRegistry observationRegistry;

	private final ToolCallingManager toolCallingManager;

	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Creates a new builder for {@link OpenAiChatModel}.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	private OpenAiChatModel(OpenAIClient openAiClient, OpenAIClientAsync openAiClientAsync, OpenAiChatOptions options,
			ObservationRegistry observationRegistry, ToolCallingManager toolCallingManager) {
		this.openAiClient = openAiClient;
		this.openAiClientAsync = openAiClientAsync;
		this.options = options;
		this.observationRegistry = observationRegistry;
		this.toolCallingManager = toolCallingManager;
	}

	/**
	 * Gets the chat options for this model.
	 * @return the chat options
	 * @since 2.0.0
	 */
	@Override
	public OpenAiChatOptions getOptions() {
		return this.options;
	}

	/**
	 * @deprecated use {@link #getOptions()} instead.
	 */
	@Override
	@Deprecated(forRemoval = true)
	@SuppressWarnings("removal")
	public ChatOptions getDefaultOptions() {
		return this.options;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		verifyPromptChatOptions(requestPrompt);
		return this.internalCall(requestPrompt);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		verifyPromptChatOptions(requestPrompt);
		return internalStream(requestPrompt);
	}

	/**
	 * Internal method to handle chat completion calls with tool execution support.
	 * @param prompt the prompt for the chat completion
	 * @return the chat response
	 */
	private ChatResponse internalCall(Prompt prompt) {
		ChatCompletionCreateParams request = this.createRequest(prompt, false);
		RequestOptions requestOptions = this.buildRequestOptions(prompt);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(AiProvider.OPENAI.value())
			.build();

		return ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				// The raw response carries the x-ratelimit-* headers alongside the parsed
				// body; the plain create(...) call discards them.
				HttpResponseFor<ChatCompletion> rawResponse = this.openAiClient.chat()
					.completions()
					.withRawResponse()
					.create(request, requestOptions);
				ChatCompletion chatCompletion = rawResponse.parse();
				RateLimit rateLimit = OpenAiRateLimit.from(rawResponse.headers());

				if (chatCompletion.choices().isEmpty()) {
					if (logger.isWarnEnabled()) {
						logger.warn("No choices returned for prompt: " + prompt);
					}
					return new ChatResponse(List.of());
				}

				ChatResponse chatResponse = toChatResponse(chatCompletion, request, rateLimit);
				observationContext.setResponse(chatResponse);
				return chatResponse;
			});
	}

	private ChatResponse toChatResponse(ChatCompletion chatCompletion, ChatCompletionCreateParams request,
			RateLimit rateLimit) {
		List<Generation> generations = chatCompletion.choices()
			.stream()
			.map(choice -> buildGeneration(choice, choiceMetadata(chatCompletion.id(), choice), request))
			.toList();
		return new ChatResponse(generations,
				chatResponseMetadataFrom(chatCompletion, currentUsage(chatCompletion), rateLimit));
	}

	private static Map<String, Object> choiceMetadata(String id, ChatCompletion.Choice choice) {
		ChatCompletionMessage message = choice.message();
		return Map.of("id", id, "role", roleOf(message), "index", choice.index(), "finishReason",
				choice.finishReason().value().toString(), "refusal", message.refusal().orElse(""), "annotations",
				message.annotations().orElse((List) List.of(Map.of())), REASONING_CONTENT, getReasoningContent(choice));
	}

	private static String roleOf(ChatCompletionMessage message) {
		return (String) message._role().asString().orElse("");
	}

	/**
	 * Internal method to handle streaming chat completion calls with tool execution
	 * support.
	 * @param prompt the prompt for the chat completion
	 * @return a Flux of chat responses
	 */
	private Flux<ChatResponse> internalStream(Prompt prompt) {
		return Flux.deferContextual(contextView -> {
			ChatCompletionCreateParams request = this.createRequest(prompt, true);
			RequestOptions requestOptions = this.buildRequestOptions(prompt);
			StreamAccumulator streamAccumulator = new StreamAccumulator();
			final ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(AiProvider.OPENAI.value())
				.streaming(true)
				.build();
			Observation parentObservation = contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
			Observation observation = startStreamObservation(observationContext, parentObservation);

			Flux<ChatResponse> chatResponses = mergeToolCallChunks(streamChunks(request, requestOptions))
				.map(chatCompletion -> toStreamedChatResponse(chatCompletion, request, streamAccumulator));

			Flux<ChatResponse> observedResponses = chatResponses.doOnError(observation::error)
				.doFinally(s -> observation.stop())
				.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));

			return new MessageAggregator().aggregate(observedResponses, observationContext::setResponse);
		});
	}

	/**
	 * Creates the observation of a streaming call and starts it as a child of the given
	 * parent observation, if any.
	 */
	private Observation startStreamObservation(ChatModelObservationContext observationContext,
			@Nullable Observation parentObservation) {
		Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
				this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
				this.observationRegistry);
		observation.parentObservation(parentObservation);
		// Briefly make the parent observation current while starting this one, so
		// Micrometer tracing derives the span's parent from the parent observation rather
		// than from whatever scope happens to be open on the current thread (e.g. the
		// servlet HTTP span). This keeps span parenting correct without relying on
		// automatic context propagation.
		try (Observation.Scope ignored = parentObservation != null ? parentObservation.openScope()
				: Observation.Scope.NOOP) {
			observation.start();
		}
		return observation;
	}

	/**
	 * Converts the SDK's {@link AsyncStreamResponse} of chunks to a {@link Flux}.
	 */
	private Flux<ChatCompletionChunk> streamChunks(ChatCompletionCreateParams request, RequestOptions requestOptions) {
		return Flux.<ChatCompletionChunk>create(sink -> {
			AsyncStreamResponse<ChatCompletionChunk> response = this.openAiClientAsync.chat()
				.completions()
				.createStreaming(request, requestOptions);
			sink.onDispose(response::close);
			response.subscribe(sink::next).onCompleteFuture().whenComplete((unused, throwable) -> {
				if (throwable != null) {
					sink.error(throwable);
				}
				else {
					sink.complete();
				}
			});
		});
	}

	/**
	 * Buffers the chunks that stream a tool call until the tool calls are done, so that
	 * each tool call reaches the caller merged into a single chat completion. Any other
	 * chunk is converted on its own.
	 */
	private static Flux<ChatCompletion> mergeToolCallChunks(Flux<ChatCompletionChunk> chunks) {
		AtomicBoolean isInsideTool = new AtomicBoolean(false);
		return chunks.doOnNext(chunk -> {
			if (ChunkMerger.hasToolCall(chunk)) {
				isInsideTool.set(true);
			}
		}).bufferUntil(chunk -> {
			if (isInsideTool.get() && ChunkMerger.toolCallsDone(chunk)) {
				isInsideTool.set(false);
				return true;
			}
			return !isInsideTool.get();
		}).map(ChunkMerger::mergeChunks).map(ChunkMerger::chunkToChatCompletion);
	}

	private ChatResponse toStreamedChatResponse(ChatCompletion chatCompletion, ChatCompletionCreateParams request,
			StreamAccumulator streamAccumulator) {
		String id = chatCompletion.id();
		List<Generation> generations = chatCompletion.choices()
			.stream()
			.map(choice -> buildGeneration(choice, streamedChoiceMetadata(id, choice, streamAccumulator), request))
			.toList();
		// Reading the rate limit here would mean the raw streaming call, whose
		// blocking StreamResponse would hold a worker for the whole stream.
		return new ChatResponse(generations,
				chatResponseMetadataFrom(chatCompletion, currentUsage(chatCompletion), new EmptyRateLimit()));
	}

	private static Map<String, Object> streamedChoiceMetadata(String id, ChatCompletion.Choice choice,
			StreamAccumulator streamAccumulator) {
		ChatCompletionMessage message = choice.message();
		String role = streamAccumulator.role(id, message);
		String accumulatedReasoning = streamAccumulator.accumulateReasoning(id, choice);
		return Map.of("id", id, //
				"role", role, //
				"index", choice.index(), //
				"finishReason", choice.finishReason().value(), //
				"refusal", message.refusal().orElse(""), //
				"annotations", message.annotations().orElseGet(List::of), //
				REASONING_CONTENT, accumulatedReasoning //
		);
	}

	private Generation buildGeneration(ChatCompletion.Choice choice, Map<String, Object> metadata,
			ChatCompletionCreateParams request) {
		ChatCompletionMessage message = choice.message();
		Map<String, String> toolCallAdditionalProperties = extractToolCallAdditionalProperties(message);
		ChatCompletionAudio audioOutput = audioOutput(message, request);

		String textContent = message.content().orElse("");
		List<Media> media = new ArrayList<>();
		if (audioOutput != null) {
			media.add(audioMedia(audioOutput, request));
			if (!StringUtils.hasText(textContent)) {
				textContent = audioOutput.transcript();
			}
		}

		var assistantMessage = AssistantMessage.builder()
			.content(textContent)
			.properties(assistantMessageMetadata(metadata, toolCallAdditionalProperties))
			.toolCalls(assistantToolCalls(message))
			.media(media)
			.build();
		return new Generation(assistantMessage, generationMetadata(choice, audioOutput));
	}

	/**
	 * Builds the metadata of the assistant message: the choice metadata, plus the extra
	 * fields of the tool calls, if any. Optional values are unwrapped so downstream
	 * repositories (Neo4j, MongoDB, JDBC, etc.) can serialize the metadata map without
	 * failing on java.util.Optional.
	 */
	private static Map<String, Object> assistantMessageMetadata(Map<String, Object> choiceMetadata,
			Map<String, String> toolCallAdditionalProperties) {
		Map<String, Object> metadata = new LinkedHashMap<>(choiceMetadata);
		if (!toolCallAdditionalProperties.isEmpty()) {
			metadata.put(TOOL_CALL_ADDITIONAL_PROPERTIES_METADATA_KEY, toolCallAdditionalProperties);
		}
		metadata.replaceAll((key, value) -> value instanceof Optional<?> optional ? optional.orElse(null) : value);
		return metadata;
	}

	private static ChatGenerationMetadata generationMetadata(ChatCompletion.Choice choice,
			@Nullable ChatCompletionAudio audioOutput) {
		ChatCompletion.Choice.FinishReason.Value finishReason = choice.finishReason().value();
		ChatGenerationMetadata.Builder builder = ChatGenerationMetadata.builder()
			.finishReason(
					finishReason != ChatCompletion.Choice.FinishReason.Value._UNKNOWN ? finishReason.name() : null);
		if (audioOutput != null) {
			builder.metadata("audioId", audioOutput.id());
			builder.metadata("audioExpiresAt", audioOutput.expiresAt());
		}
		return builder.build();
	}

	/**
	 * Returns the audio output of the message, or {@code null} when no audio output was
	 * requested or the message carries no audio data.
	 */
	private static @Nullable ChatCompletionAudio audioOutput(ChatCompletionMessage message,
			ChatCompletionCreateParams request) {
		if (request.audio().isEmpty()) {
			return null;
		}
		return message.audio().filter(audio -> StringUtils.hasText(audio.data())).orElse(null);
	}

	private static Media audioMedia(ChatCompletionAudio audioOutput, ChatCompletionCreateParams request) {
		String mimeType = "audio/" + request.audio().orElseThrow().format().value().name().toLowerCase(Locale.ROOT);
		byte[] audioData = Base64.getDecoder().decode(audioOutput.data());
		return Media.builder()
			.mimeType(MimeTypeUtils.parseMimeType(mimeType))
			.data(new ByteArrayResource(audioData))
			.id(audioOutput.id())
			.build();
	}

	/**
	 * Converts the {@code function} tool calls of the message. Tool calls of any other
	 * type, such as {@code custom} tool calls, cannot be executed by a tool callback and
	 * are dropped.
	 */
	private static List<AssistantMessage.ToolCall> assistantToolCalls(ChatCompletionMessage message) {
		return message.toolCalls()
			.orElse(List.of())
			.stream()
			.flatMap(toolCall -> toolCall.function().stream())
			.map(functionToolCall -> new AssistantMessage.ToolCall(functionToolCall.id(), "function",
					functionToolCall.function().name(), functionToolCall.function().arguments()))
			.toList();
	}

	private Map<String, String> extractToolCallAdditionalProperties(ChatCompletionMessage message) {
		Map<String, String> result = new LinkedHashMap<>();
		message.toolCalls()
			.ifPresent(toolCalls -> toolCalls.forEach(toolCall -> toolCall.function().ifPresent(functionToolCall -> {
				Map<String, JsonValue> props = functionToolCall._additionalProperties();
				if (!CollectionUtils.isEmpty(props)) {
					try {
						result.put(functionToolCall.id(), objectMapper.writeValueAsString(props));
					}
					catch (JsonProcessingException ex) {
						throw new RuntimeException(ex);
					}
				}
			})));
		return result;
	}

	private ChatResponseMetadata chatResponseMetadataFrom(ChatCompletion result, Usage usage, RateLimit rateLimit) {
		Assert.notNull(result, "OpenAI ChatCompletion must not be null");
		ChatResponseMetadata.Builder metadataBuilder = ChatResponseMetadata.builder()
			.id(result.id())
			.usage(usage)
			.model(result.model())
			.rateLimit(rateLimit)
			.keyValue("created", getCreated(result));

		result._additionalProperties()
			.forEach((key, jsonValue) -> metadataBuilder.keyValue(key, toMetadataValue(key, jsonValue)));

		return metadataBuilder.build();
	}

	/**
	 * Converts an extra field of the response to a plain Java value, or keeps the JSON
	 * value when it cannot be converted.
	 */
	private @Nullable Object toMetadataValue(String key, JsonValue jsonValue) {
		try {
			return JacksonUtils.getDefaultJsonMapper().convertValue(jsonValue, Object.class);
		}
		catch (Exception e) {
			if (logger.isErrorEnabled()) {
				logger.error("Error parsing JSON value for key '" + key + "': " + jsonValue, e);
			}
			return jsonValue;
		}
	}

	private Usage currentUsage(ChatCompletion chatCompletion) {
		CompletionUsage usage = chatCompletion.usage().orElse(null);
		return usage != null ? getDefaultUsage(usage) : new EmptyUsage();
	}

	/**
	 * Extract the created timestamp from a ChatCompletion result, returning 0 if the
	 * field is absent. Some OpenAI-compatible providers (e.g. GitHub Copilot) do not
	 * include the created field in their response.
	 */
	private long getCreated(ChatCompletion result) {
		try {
			return result.created();
		}
		catch (OpenAIInvalidDataException ex) {
			return 0L;
		}
	}

	private DefaultUsage getDefaultUsage(CompletionUsage usage) {
		Long cacheRead = usage.promptTokensDetails().flatMap(details -> details.cachedTokens()).orElse(null);
		return new DefaultUsage(toIntTokenCount("promptTokens", usage.promptTokens()),
				toIntTokenCount("completionTokens", usage.completionTokens()),
				toIntTokenCount("totalTokens", usage.totalTokens()), usage, cacheRead, null);
	}

	/**
	 * Narrows a server-supplied token count to an {@code int}. Usage counts come from the
	 * deserialised upstream response, so an out-of-range value means the response is not
	 * trustworthy; fail clearly with the offending field and value rather than either
	 * silently truncating the count (which would corrupt downstream cost/usage tracking
	 * with a plausible-looking but wrong number) or letting a bare
	 * {@link ArithmeticException} propagate.
	 * @param fieldName the name of the usage field being converted, for diagnostics
	 * @param value the upstream token count
	 * @return the value narrowed to an {@code int}
	 * @throws IllegalStateException if {@code value} is outside the {@code int} range
	 */
	private static int toIntTokenCount(String fieldName, long value) {
		try {
			return Math.toIntExact(value);
		}
		catch (ArithmeticException ex) {
			throw new IllegalStateException(
					"OpenAI-compatible provider returned an out-of-range " + fieldName + " value: " + value, ex);
		}
	}

	private void verifyPromptChatOptions(Prompt prompt) {
		var chatOptions = prompt.getOptions();

		if (chatOptions != null && chatOptions.getTopK() != null) {
			logger.warn("The topK option is not supported by OpenAI chat models. Ignoring.");
		}
	}

	/**
	 * Creates a chat completion request from the given prompt.
	 * @param prompt the prompt containing messages and options
	 * @param stream whether this is a streaming request
	 * @return the chat completion create parameters
	 */
	ChatCompletionCreateParams createRequest(Prompt prompt, boolean stream) {

		OpenAiChatOptions requestOptions = (OpenAiChatOptions) prompt.getOptions();
		Assert.state(requestOptions != null, "ChatOptions must not be null");

		ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder();

		toMessageParams(prompt.getInstructions(), shouldReplayReasoningContent(requestOptions))
			.forEach(builder::addMessage);

		applyModel(builder, requestOptions);
		applySamplingOptions(builder, requestOptions);
		applyLogprobsOptions(builder, requestOptions);
		applyOutputOptions(builder, requestOptions);
		applyResponseFormat(builder, requestOptions.getResponseFormat());
		applyServiceOptions(builder, requestOptions);
		if (stream) {
			builder.streamOptions(streamOptions(requestOptions.getStreamOptions()));
		}
		applyTools(builder, requestOptions);
		applyExtraBody(builder, requestOptions.getExtraBody());

		return builder.build();
	}

	/**
	 * Replaying reasoning content is what OpenAI-compatible reasoning endpoints such as
	 * DeepSeek's thinking mode require, and what Groq rejects outright, so it can be
	 * turned off per request. Absent an explicit opt-out the content is replayed whenever
	 * present, which is the behavior plain OpenAI is unaffected by.
	 */
	private static boolean shouldReplayReasoningContent(OpenAiChatOptions requestOptions) {
		return !Boolean.FALSE.equals(requestOptions.getReplayReasoningContent());
	}

	private List<ChatCompletionMessageParam> toMessageParams(List<Message> messages, boolean replayReasoningContent) {
		return messages.stream().flatMap(message -> toMessageParams(message, replayReasoningContent).stream()).toList();
	}

	private List<ChatCompletionMessageParam> toMessageParams(Message message, boolean replayReasoningContent) {
		return switch (message.getMessageType()) {
			case USER, SYSTEM -> List.of(ChatCompletionMessageParam.ofUser(userMessageParam(message)));
			case ASSISTANT -> List.of(ChatCompletionMessageParam
				.ofAssistant(assistantMessageParam((AssistantMessage) message, replayReasoningContent)));
			case TOOL -> toolMessageParams((ToolResponseMessage) message);
		};
	}

	/**
	 * Converts a user or a system message. A user message with media is sent as content
	 * parts: its text, then its media (images, audio, files). Any other message is sent
	 * as simple text content.
	 */
	private ChatCompletionUserMessageParam userMessageParam(Message message) {
		ChatCompletionUserMessageParam.Builder builder = ChatCompletionUserMessageParam.builder()
			.role(JsonValue.from(message.getMessageType().getValue()));

		String messageText = message.getText();
		if (message instanceof UserMessage userMessage && !CollectionUtils.isEmpty(userMessage.getMedia())) {
			builder.contentOfArrayOfContentParts(userContentParts(userMessage));
		}
		else if (messageText != null) {
			builder.content(messageText);
		}
		return builder.build();
	}

	private List<ChatCompletionContentPart> userContentParts(UserMessage userMessage) {
		List<ChatCompletionContentPart> parts = new ArrayList<>();
		String messageText = userMessage.getText();
		if (messageText != null && !messageText.isEmpty()) {
			parts.add(textContentPart(messageText));
		}
		for (Media media : userMessage.getMedia()) {
			ChatCompletionContentPart contentPart = toContentPart(media);
			if (contentPart != null) {
				parts.add(contentPart);
			}
		}
		return parts;
	}

	/**
	 * Converts one media to a content part: an image to an image URL, audio to input
	 * audio, a PDF to a file, and anything else to a text part holding the data URL.
	 * Returns {@code null} for an image whose data type is not supported.
	 */
	private @Nullable ChatCompletionContentPart toContentPart(Media media) {
		String mimeType = media.getMimeType().toString();
		if (mimeType.startsWith("image/")) {
			return imageContentPart(media);
		}
		if (mimeType.startsWith("audio/")) {
			return inputAudioContentPart(media, mimeType);
		}
		if ("application/pdf".equals(mimeType)) {
			return fileContentPart(media);
		}
		// Assume it's a file or other media type represented as a data URL
		return textContentPart(fromMediaData(media.getMimeType(), media.getData()));
	}

	private @Nullable ChatCompletionContentPart imageContentPart(Media media) {
		String url = imageUrl(media);
		if (url == null) {
			return null;
		}
		return ChatCompletionContentPart.ofImageUrl(ChatCompletionContentPartImage.builder()
			.imageUrl(ChatCompletionContentPartImage.ImageUrl.builder().url(url).build())
			.build());
	}

	/**
	 * Returns the URL of an image: the URL it holds, or a base64 encoded data URL for its
	 * bytes. Returns {@code null} when the data type is not supported.
	 */
	private @Nullable String imageUrl(Media media) {
		Object data = media.getData();
		if (data instanceof java.net.URI uri) {
			return uri.toString();
		}
		if (data instanceof String text) {
			// The org.springframework.ai.content.Media object should store the URL as a
			// java.net.URI, but it transforms it to String somewhere along the way, for
			// example in its Builder class. So, we accept String as well here for image
			// URLs.
			return text;
		}
		if (data instanceof byte[] bytes) {
			// Assume the bytes are an image. So, convert the bytes to a base64 encoded
			// data URL.
			return "data:" + media.getMimeType() + ";base64," + Base64.getEncoder().encodeToString(bytes);
		}
		if (logger.isInfoEnabled()) {
			logger.info("Could not process image media with data of type: " + data.getClass().getSimpleName()
					+ ". Only java.net.URI is supported for image URLs.");
		}
		return null;
	}

	private ChatCompletionContentPart inputAudioContentPart(Media media, String mimeType) {
		ChatCompletionContentPartInputAudio.InputAudio.Format format = mimeType.contains("mp3")
				? ChatCompletionContentPartInputAudio.InputAudio.Format.MP3
				: ChatCompletionContentPartInputAudio.InputAudio.Format.WAV;
		return ChatCompletionContentPart.ofInputAudio(ChatCompletionContentPartInputAudio.builder()
			.inputAudio(ChatCompletionContentPartInputAudio.InputAudio.builder()
				.data(fromAudioData(media.getData()))
				.format(format)
				.build())
			.build());
	}

	private ChatCompletionContentPart fileContentPart(Media media) {
		return ChatCompletionContentPart.ofFile(ChatCompletionContentPart.File.builder()
			.file(ChatCompletionContentPart.File.FileObject.builder()
				.filename(media.getName())
				.fileData(fromMediaData(media.getMimeType(), media.getData()))
				.build())
			.build());
	}

	private static ChatCompletionContentPart textContentPart(String text) {
		return ChatCompletionContentPart.ofText(ChatCompletionContentPartText.builder().text(text).build());
	}

	/**
	 * Converts an assistant message with its tool calls and, unless
	 * {@code replayReasoningContent} is {@code false}, its reasoning content, see
	 * {@link OpenAiChatOptions#getReplayReasoningContent()}.
	 */
	private ChatCompletionAssistantMessageParam assistantMessageParam(AssistantMessage assistantMessage,
			boolean replayReasoningContent) {
		ChatCompletionAssistantMessageParam.Builder builder = ChatCompletionAssistantMessageParam.builder()
			.role(JsonValue.from(MessageType.ASSISTANT.getValue()));

		String messageText = assistantMessage.getText();
		if (messageText != null) {
			builder.content(messageText);
		}

		List<ChatCompletionMessageToolCall> toolCalls = messageToolCalls(assistantMessage);
		if (!toolCalls.isEmpty()) {
			builder.toolCalls(toolCalls);
		}

		// Replay reasoning content only when present and not opted out - plain OpenAI is
		// unaffected
		String reasoningContent = reasoningContent(assistantMessage);
		if (replayReasoningContent && StringUtils.hasText(reasoningContent)) {
			// "reasoning_content" is the wire field; REASONING_CONTENT is the metadata
			// key
			builder.putAdditionalProperty("reasoning_content", JsonValue.from(reasoningContent));
		}

		return builder.build();
	}

	private List<ChatCompletionMessageToolCall> messageToolCalls(AssistantMessage assistantMessage) {
		Map<String, String> toolCallAdditionalProperties = toolCallAdditionalPropertiesFromMetadata(assistantMessage);
		return assistantMessage.getToolCalls()
			.stream()
			.map(toolCall -> functionToolCall(toolCall, toolCallAdditionalProperties.get(toolCall.id())))
			.toList();
	}

	/**
	 * Rebuilds a {@code function} tool call, with the extra fields an OpenAI-compatible
	 * server returned on it, if any.
	 * @param toolCall the tool call to rebuild
	 * @param additionalPropertiesJson the extra fields of the tool call, as a JSON object
	 * string, or {@code null} if it has none
	 */
	private static ChatCompletionMessageToolCall functionToolCall(AssistantMessage.ToolCall toolCall,
			@Nullable String additionalPropertiesJson) {
		ChatCompletionMessageFunctionToolCall.Builder toolCallBuilder = ChatCompletionMessageFunctionToolCall.builder()
			.id(toolCall.id())
			.function(ChatCompletionMessageFunctionToolCall.Function.builder()
				.name(toolCall.name())
				.arguments(toolCall.arguments())
				.build());
		if (StringUtils.hasText(additionalPropertiesJson)) {
			toolCallBuilder.putAllAdditionalProperties(parseAdditionalProperties(additionalPropertiesJson));
		}
		return ChatCompletionMessageToolCall.ofFunction(toolCallBuilder.build());
	}

	private static Map<String, JsonValue> parseAdditionalProperties(String json) {
		Map<String, JsonValue> additionalProperties = new LinkedHashMap<>();
		try {
			objectMapper.readValue(json, MAP_TYPE_REF)
				.forEach((k, v) -> additionalProperties.put(k, JsonValue.from(v)));
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException(
					"Conversion from JSON to %s failed".formatted(MAP_TYPE_REF.getType().getTypeName()), ex);
		}
		return additionalProperties;
	}

	private static @Nullable String reasoningContent(AssistantMessage assistantMessage) {
		return assistantMessage.getMetadata().get(REASONING_CONTENT) instanceof String reasoning ? reasoning : null;
	}

	/**
	 * Converts a tool response message to one tool message per tool response, or to a
	 * single tool message without a tool call id when it holds no responses.
	 */
	private static List<ChatCompletionMessageParam> toolMessageParams(ToolResponseMessage toolMessage) {
		ChatCompletionToolMessageParam.Builder builder = ChatCompletionToolMessageParam.builder();
		builder.content(toolMessage.getText());
		builder.role(JsonValue.from(MessageType.TOOL.getValue()));

		if (toolMessage.getResponses().isEmpty()) {
			return List.of(ChatCompletionMessageParam.ofTool(builder.build()));
		}
		return toolMessage.getResponses()
			.stream()
			.map(response -> ChatCompletionMessageParam
				.ofTool(builder.toolCallId(response.id()).content(response.responseData()).build()))
			.toList();
	}

	/**
	 * Uses the deployment name if available (for Microsoft Foundry), otherwise the model
	 * name.
	 */
	private static void applyModel(ChatCompletionCreateParams.Builder builder, OpenAiChatOptions requestOptions) {
		if (requestOptions.getDeploymentName() != null) {
			builder.model(requestOptions.getDeploymentName());
		}
		else if (requestOptions.getModel() != null) {
			builder.model(requestOptions.getModel());
		}
	}

	/**
	 * Applies the options that steer token sampling: the frequency and presence
	 * penalties, the seed, the stop sequences, the temperature and top-p.
	 */
	private static void applySamplingOptions(ChatCompletionCreateParams.Builder builder,
			OpenAiChatOptions requestOptions) {
		if (requestOptions.getFrequencyPenalty() != null) {
			builder.frequencyPenalty(requestOptions.getFrequencyPenalty());
		}
		if (requestOptions.getPresencePenalty() != null) {
			builder.presencePenalty(requestOptions.getPresencePenalty());
		}
		if (requestOptions.getSeed() != null) {
			builder.seed(requestOptions.getSeed());
		}
		List<String> stop = requestOptions.getStop();
		if (!CollectionUtils.isEmpty(stop)) {
			builder.stop(stop.size() == 1 ? ChatCompletionCreateParams.Stop.ofString(stop.get(0))
					: ChatCompletionCreateParams.Stop.ofStrings(stop));
		}
		if (requestOptions.getTemperature() != null) {
			builder.temperature(requestOptions.getTemperature());
		}
		if (requestOptions.getTopP() != null) {
			builder.topP(requestOptions.getTopP());
		}
	}

	/**
	 * Applies the options on token probabilities: the logit bias, and whether and how
	 * many log probabilities to return.
	 */
	private static void applyLogprobsOptions(ChatCompletionCreateParams.Builder builder,
			OpenAiChatOptions requestOptions) {
		if (requestOptions.getLogitBias() != null) {
			builder.logitBias(ChatCompletionCreateParams.LogitBias.builder()
				.putAllAdditionalProperties(toJsonValues(requestOptions.getLogitBias()))
				.build());
		}
		if (requestOptions.getLogprobs() != null) {
			builder.logprobs(requestOptions.getLogprobs());
		}
		if (requestOptions.getTopLogprobs() != null) {
			builder.topLogprobs(requestOptions.getTopLogprobs());
		}
	}

	/**
	 * Applies the options that shape the generated output: the token limits, the number
	 * of choices, the output modalities and audio, the reasoning effort and verbosity.
	 */
	private static void applyOutputOptions(ChatCompletionCreateParams.Builder builder,
			OpenAiChatOptions requestOptions) {
		if (requestOptions.getMaxTokens() != null) {
			builder.maxTokens(requestOptions.getMaxTokens());
		}
		if (requestOptions.getMaxCompletionTokens() != null) {
			builder.maxCompletionTokens(requestOptions.getMaxCompletionTokens());
		}
		if (requestOptions.getN() != null) {
			builder.n(requestOptions.getN());
		}
		if (requestOptions.getOutputModalities() != null) {
			builder.modalities(requestOptions.getOutputModalities()
				.stream()
				.map(modality -> ChatCompletionCreateParams.Modality.of(modality.toLowerCase(Locale.ROOT)))
				.toList());
		}
		if (requestOptions.getOutputAudio() != null) {
			builder.audio(requestOptions.getOutputAudio().toChatCompletionAudioParam());
		}
		if (requestOptions.getReasoningEffort() != null) {
			builder.reasoningEffort(ReasoningEffort.of(requestOptions.getReasoningEffort().toLowerCase(Locale.ROOT)));
		}
		if (requestOptions.getVerbosity() != null) {
			builder.verbosity(ChatCompletionCreateParams.Verbosity.of(requestOptions.getVerbosity()));
		}
	}

	private static void applyResponseFormat(ChatCompletionCreateParams.Builder builder,
			@Nullable ResponseFormat responseFormat) {
		if (responseFormat == null) {
			return;
		}
		switch (responseFormat.getType()) {
			case TEXT -> builder.responseFormat(ResponseFormatText.builder().build());
			case JSON_OBJECT -> builder.responseFormat(ResponseFormatJsonObject.builder().build());
			case JSON_SCHEMA -> builder.responseFormat(jsonSchemaResponseFormat(responseFormat));
			default ->
				throw new IllegalArgumentException("Unsupported response format type: " + responseFormat.getType());
		}
	}

	private static ResponseFormatJsonSchema jsonSchemaResponseFormat(ResponseFormat responseFormat) {
		String jsonSchemaString = responseFormat.getJsonSchema() != null ? responseFormat.getJsonSchema() : "";
		try {
			Boolean strict = responseFormat.getStrict();
			ResponseFormatJsonSchema.JsonSchema jsonSchema = ResponseFormatJsonSchema.JsonSchema.builder()
				.name("json_schema")
				.strict(strict != null ? strict : true)
				.schema(objectMapper.readValue(jsonSchemaString, ResponseFormatJsonSchema.JsonSchema.Schema.class))
				.build();
			return ResponseFormatJsonSchema.builder().jsonSchema(jsonSchema).build();
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to parse JSON schema: " + jsonSchemaString, e);
		}
	}

	/**
	 * Applies the options on how the API handles the request: the end-user id, storage
	 * and metadata, the service tier, the prompt cache key and the custom headers.
	 */
	private static void applyServiceOptions(ChatCompletionCreateParams.Builder builder,
			OpenAiChatOptions requestOptions) {
		if (requestOptions.getUser() != null) {
			builder.user(requestOptions.getUser());
		}
		if (requestOptions.getStore() != null) {
			builder.store(requestOptions.getStore());
		}
		Map<String, String> metadata = requestOptions.getMetadata();
		if (!CollectionUtils.isEmpty(metadata)) {
			builder.metadata(ChatCompletionCreateParams.Metadata.builder()
				.putAllAdditionalProperties(toJsonValues(metadata))
				.build());
		}
		if (requestOptions.getServiceTier() != null) {
			builder.serviceTier(ChatCompletionCreateParams.ServiceTier.of(requestOptions.getServiceTier()));
		}
		if (requestOptions.getPromptCacheKey() != null) {
			builder.promptCacheKey(requestOptions.getPromptCacheKey());
		}
		Map<String, String> customHeaders = requestOptions.getCustomHeaders();
		if (!CollectionUtils.isEmpty(customHeaders)) {
			customHeaders.forEach(builder::putAdditionalHeader);
		}
	}

	/**
	 * Converts the stream options, which include usage by default for streaming.
	 */
	private static ChatCompletionStreamOptions streamOptions(OpenAiChatOptions.@Nullable StreamOptions options) {
		if (options == null) {
			return ChatCompletionStreamOptions.builder().includeUsage(true).build();
		}
		ChatCompletionStreamOptions.Builder streamOptionsBuilder = ChatCompletionStreamOptions.builder()
			.includeObfuscation(Boolean.TRUE.equals(options.includeObfuscation()))
			.includeUsage(Boolean.TRUE.equals(options.includeUsage()));
		Map<String, Object> additionalProperties = options.additionalProperties();
		if (!CollectionUtils.isEmpty(additionalProperties)) {
			streamOptionsBuilder.putAllAdditionalProperties(toJsonValues(additionalProperties));
		}
		return streamOptionsBuilder.build();
	}

	/**
	 * Adds the tool definitions to the request's tools parameter, with the tool choice
	 * and whether tools may be called in parallel.
	 */
	private void applyTools(ChatCompletionCreateParams.Builder builder, OpenAiChatOptions requestOptions) {
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			builder.tools(getChatCompletionTools(toolDefinitions, requestOptions));
		}
		ChatCompletionToolChoiceOption toolChoice = toolChoice(requestOptions.getToolChoice());
		if (toolChoice != null) {
			builder.toolChoice(toolChoice);
		}
		if (requestOptions.getParallelToolCalls() != null) {
			builder.parallelToolCalls(requestOptions.getParallelToolCalls());
		}
	}

	/**
	 * Converts the tool choice option, which is either an SDK tool choice, or a string
	 * holding {@code auto}, {@code none}, {@code required} or a tool choice JSON object.
	 * Returns {@code null} for any other value.
	 */
	private static @Nullable ChatCompletionToolChoiceOption toolChoice(@Nullable Object toolChoice) {
		if (toolChoice instanceof ChatCompletionToolChoiceOption toolChoiceOption) {
			return toolChoiceOption;
		}
		if (toolChoice instanceof String toolChoiceString) {
			return parseToolChoiceString(toolChoiceString);
		}
		return null;
	}

	private static ChatCompletionToolChoiceOption parseToolChoiceString(String toolChoice) {
		return switch (toolChoice) {
			case "auto" -> ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.AUTO);
			case "none" -> ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.NONE);
			case "required" -> ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.REQUIRED);
			default -> parseToolChoiceJson(toolChoice);
		};
	}

	private static ChatCompletionToolChoiceOption parseToolChoiceJson(String json) {
		try {
			// Jackson 3 rather than the Jackson 2 objectMapper field: the public
			// parseToolChoice(JsonNode) takes a tools.jackson tree.
			return parseToolChoice(JacksonUtils.getDefaultJsonMapper().readTree(json));
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Failed to parse toolChoice JSON: " + json, e);
		}
	}

	/**
	 * Adds the extraBody parameters as additional body properties for OpenAI-compatible
	 * providers.
	 */
	private static void applyExtraBody(ChatCompletionCreateParams.Builder builder,
			@Nullable Map<String, Object> extraBody) {
		if (!CollectionUtils.isEmpty(extraBody)) {
			builder.additionalBodyProperties(toJsonValues(extraBody));
		}
	}

	private static Map<String, JsonValue> toJsonValues(Map<String, ?> values) {
		return values.entrySet()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, entry -> JsonValue.from(entry.getValue())));
	}

	/**
	 * Creates a RequestOptions instance from the given prompt.
	 * @param prompt the prompt containing messages and options
	 * @return a RequestOptions instance
	 */
	private RequestOptions buildRequestOptions(Prompt prompt) {
		Assert.notNull(prompt, "Prompt cannot be null");
		Assert.isInstanceOf(OpenAiChatOptions.class, prompt.getOptions(),
				"Prompt options must be OpenAiChatOptions type");
		OpenAiChatOptions chatOptions = (OpenAiChatOptions) prompt.getOptions();
		RequestOptions.Builder requestOptionsBuilder = RequestOptions.builder();
		if (chatOptions.getTimeout() != null) {
			requestOptionsBuilder.timeout(chatOptions.getTimeout());
		}
		return requestOptionsBuilder.build();
	}

	private Map<String, String> toolCallAdditionalPropertiesFromMetadata(AssistantMessage assistantMessage) {
		Object value = assistantMessage.getMetadata().get(TOOL_CALL_ADDITIONAL_PROPERTIES_METADATA_KEY);
		if (!(value instanceof Map<?, ?> rawMap)) {
			return Map.of();
		}
		Map<String, String> result = new LinkedHashMap<>();
		rawMap.forEach((k, v) -> {
			if (k instanceof String id && v instanceof String json) {
				result.put(id, json);
			}
		});
		return result;
	}

	public static ChatCompletionToolChoiceOption parseToolChoice(JsonNode node) {
		String type = node.get("type").asString();
		switch (type) {
			case "function":
				String functionName = node.get("function").get("name").asString();
				ChatCompletionNamedToolChoice.Function func = ChatCompletionNamedToolChoice.Function.builder()
					.name(functionName)
					.build();
				ChatCompletionNamedToolChoice named = ChatCompletionNamedToolChoice.builder().function(func).build();
				return ChatCompletionToolChoiceOption.ofNamedToolChoice(named);
			case "auto":
				// There is a built-in “auto” option — but how to get it depends on SDK
				// version
				return ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.AUTO);
			case "required":
				return ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.REQUIRED);
			case "none":
				return ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.NONE);
			default:
				throw new IllegalArgumentException("Unknown tool_choice type: " + type);
		}
	}

	private String fromAudioData(Object audioData) {
		if (audioData instanceof byte[] bytes) {
			return Base64.getEncoder().encodeToString(bytes);
		}
		throw new IllegalArgumentException("Unsupported audio data type: " + audioData.getClass().getSimpleName());
	}

	private String fromMediaData(org.springframework.util.MimeType mimeType, Object mediaContentData) {
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

	private List<ChatCompletionTool> getChatCompletionTools(List<ToolDefinition> toolDefinitions,
			@Nullable OpenAiChatOptions requestOptions) {
		boolean strictMode = isStrictMode(requestOptions);
		return toolDefinitions.stream()
			.map(toolDefinition -> toChatCompletionTool(toolDefinition, strictMode))
			.toList();
	}

	/**
	 * Defaults to false: OpenAI's strict mode requires every schema property to appear in
	 * "required" (optionality is expressed via nullable types, not omission), which
	 * JsonSchemaGenerator does not produce by default. When a caller opts in via
	 * OpenAiChatOptions#strict, applyStrictModeRequirements rewrites the schema to
	 * satisfy that contract.
	 */
	private boolean isStrictMode(@Nullable OpenAiChatOptions requestOptions) {
		if (requestOptions != null && requestOptions.getStrict() != null) {
			return Boolean.TRUE.equals(requestOptions.getStrict());
		}
		return Boolean.TRUE.equals(this.options.getStrict());
	}

	private ChatCompletionTool toChatCompletionTool(ToolDefinition toolDefinition, boolean strictMode) {
		FunctionDefinition functionDefinition = FunctionDefinition.builder()
			.name(toolDefinition.name())
			.description(toolDefinition.description())
			.parameters(functionParameters(toolDefinition.inputSchema(), strictMode))
			.strict(strictMode)
			.build();

		return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder().function(functionDefinition).build());
	}

	/**
	 * Converts the input schema of a tool to function parameters, rewritten to satisfy
	 * strict mode when enabled. A schema that cannot be parsed is logged and skipped.
	 */
	private FunctionParameters functionParameters(String inputSchema, boolean strictMode) {
		FunctionParameters.Builder parametersBuilder = FunctionParameters.builder();
		if (inputSchema.isEmpty()) {
			return parametersBuilder.build();
		}
		// Parse the schema and add its properties directly
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> schemaMap = objectMapper.readValue(inputSchema, Map.class);

			if (strictMode) {
				applyStrictModeRequirements(schemaMap);
			}

			// Add each property from the schema to the parameters
			schemaMap.forEach((key, value) -> parametersBuilder.putAdditionalProperty(key, JsonValue.from(value)));
		}
		catch (Exception e) {
			logger.error("Failed to parse tool schema", e);
		}
		return parametersBuilder.build();
	}

	/**
	 * A JSON Schema fragment representing exactly the {@code null} type, used as the
	 * extra {@code anyOf} branch when widening a {@code $ref}/{@code anyOf}-based
	 * property to accept {@code null}.
	 */
	private static final Map<String, Object> NULL_TYPE_SCHEMA = Map.of("type", "null");

	/**
	 * Rewrites an object schema in place to satisfy OpenAI's strict function-calling
	 * mode: every property must be listed in "required", with optionality expressed via a
	 * nullable type rather than omission from "required", and every object level must pin
	 * {@code "additionalProperties"} to {@code false}. {@link JsonSchemaGenerator}
	 * produces conventional JSON Schema instead - it omits
	 * {@code @ToolParam(required = false)} properties from "required" - so this widens
	 * the type of each such property to also accept {@code null} and backfills "required"
	 * with every property key. Schemas from other sources (MCP servers, hand-written
	 * inputSchema JSON) may also lack "additionalProperties", so it is backfilled with
	 * {@code false} wherever absent; an explicit value is preserved. Recurses into nested
	 * object/array-item schemas and {@code $defs} definitions so their own optional
	 * properties are fixed up too.
	 */
	@SuppressWarnings("unchecked")
	private static void applyStrictModeRequirements(Map<String, Object> schema) {
		if (schema.get("$defs") instanceof Map<?, ?> defs) {
			for (Object definition : defs.values()) {
				if (definition instanceof Map<?, ?> definitionSchema) {
					applyStrictModeRequirements((Map<String, Object>) definitionSchema);
				}
			}
		}

		if (!(schema.get("properties") instanceof Map<?, ?> properties)) {
			return;
		}

		schema.putIfAbsent("additionalProperties", false);

		if (properties.isEmpty()) {
			return;
		}

		List<?> alreadyRequired = schema.get("required") instanceof List<?> required ? required : List.of();

		for (Object propertySchemaValue : properties.values()) {
			if (!(propertySchemaValue instanceof Map<?, ?> propertySchema)) {
				continue;
			}
			applyStrictModeRequirements((Map<String, Object>) propertySchema);
			if (propertySchema.get("items") instanceof Map<?, ?> itemsSchema) {
				applyStrictModeRequirements((Map<String, Object>) itemsSchema);
			}
		}

		for (Map.Entry<?, ?> property : properties.entrySet()) {
			if (alreadyRequired.contains(property.getKey())
					|| !(property.getValue() instanceof Map<?, ?> propertySchema)) {
				continue;
			}
			widenToNullable((Map<String, Object>) propertySchema);
		}

		schema.put("required", new ArrayList<>(properties.keySet()));
	}

	/**
	 * Widens a property schema's "type" to also accept {@code null}. Properties defined
	 * purely via {@code $ref} or {@code anyOf} (no "type" key of their own - typically a
	 * complex object parameter) are instead wrapped in an {@code anyOf} alongside a null
	 * branch, since there is no "type" value to widen directly.
	 */
	private static void widenToNullable(Map<String, Object> propertySchema) {
		Object type = propertySchema.get("type");
		if (type instanceof String typeName) {
			if (!"null".equals(typeName)) {
				propertySchema.put("type", new ArrayList<>(List.of(typeName, "null")));
			}
			return;
		}
		if (type instanceof List<?> typeList) {
			if (!typeList.contains("null")) {
				List<Object> widened = new ArrayList<>(typeList);
				widened.add("null");
				propertySchema.put("type", widened);
			}
			return;
		}
		if (propertySchema.get("anyOf") instanceof List<?> anyOf) {
			if (!anyOf.contains(NULL_TYPE_SCHEMA)) {
				List<Object> widened = new ArrayList<>(anyOf);
				widened.add(NULL_TYPE_SCHEMA);
				propertySchema.put("anyOf", widened);
			}
			return;
		}
		if (propertySchema.containsKey("$ref")) {
			Map<String, Object> refBranch = new LinkedHashMap<>(propertySchema);
			Object description = refBranch.remove("description");
			propertySchema.clear();
			if (description != null) {
				propertySchema.put("description", description);
			}
			propertySchema.put("anyOf", new ArrayList<>(List.of(refBranch, NULL_TYPE_SCHEMA)));
		}
	}

	/**
	 * Returns the {@code reasoning_content}, or else the {@code reasoning}, field that
	 * some OpenAI-compatible servers return on the message, or an empty string if it has
	 * neither.
	 */
	private static String getReasoningContent(ChatCompletion.Choice choice) {
		Map<String, JsonValue> additionalProperties = choice.message()._additionalProperties();
		JsonValue reasoningContent = additionalProperties.get("reasoning_content");
		if (reasoningContent == null) {
			reasoningContent = additionalProperties.get("reasoning");
		}
		return reasoningContent != null ? (String) reasoningContent.asString().orElse("") : "";
	}

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
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
	 * The state of a streaming call that outlives a single chunk: the role, which only
	 * the first chunk of a response carries, and the reasoning fragments of each choice.
	 * The fragments are accumulated so that the final response of the stream carries the
	 * full reasoning content, surviving last-wins metadata aggregation (e.g.
	 * {@link MessageAggregator}).
	 */
	private static final class StreamAccumulator {

		private final Map<String, String> roleByResponseId = new ConcurrentHashMap<>();

		private final Map<String, String> reasoningByChoice = new ConcurrentHashMap<>();

		String role(String responseId, ChatCompletionMessage message) {
			this.roleByResponseId.putIfAbsent(responseId, roleOf(message));
			return this.roleByResponseId.getOrDefault(responseId, "");
		}

		String accumulateReasoning(String responseId, ChatCompletion.Choice choice) {
			return this.reasoningByChoice.merge(responseId + ":" + choice.index(), getReasoningContent(choice),
					String::concat);
		}

	}

	static final class ChunkMerger {

		static boolean hasToolCall(ChatCompletionChunk chunk) {
			return !chunk.choices().isEmpty()
					&& chunk.choices().get(0).delta().toolCalls().filter(toolCalls -> !toolCalls.isEmpty()).isPresent();
		}

		static boolean toolCallsDone(ChatCompletionChunk chunk) {
			return !chunk.choices().isEmpty()
					&& FinishReason.TOOL_CALLS == chunk.choices().get(0).finishReason().orElse(null);
		}

		static ChatCompletionChunk mergeChunks(List<ChatCompletionChunk> chunks) {
			ChatCompletionChunk.Builder builder = chunks.get(0).toBuilder();
			Map<Long, Choice> choices = new LinkedHashMap<>();
			chunks.get(0).choices().forEach(choice -> choices.put(choice.index(), choice));

			for (int i = 1; i < chunks.size(); i++) {
				ChatCompletionChunk chunk = chunks.get(i);
				chunk.usage().ifPresent(builder::usage);
				chunk.serviceTier().ifPresent(builder::serviceTier);
				chunk.choices()
					.forEach(choice -> choices.compute(choice.index(),
							(ix, c) -> c == null ? choice : mergeChoices(c, choice)));
			}
			return builder.choices(new ArrayList<>(choices.values())).build();
		}

		private static Choice mergeChoices(Choice c1, Choice c2) {
			return Choice.builder()
				.index(c1.index())
				.finishReason(c1.finishReason().or(c2::finishReason))
				.logprobs(c1.logprobs().or(c2::logprobs))
				.delta(mergeDeltas(c1.delta(), c2.delta()))
				.build();
		}

		private static Delta mergeDeltas(Delta left, Delta right) {
			// Deltas of the same logical tool call share the required 'index' field.
			// Some OpenAI-compatible providers (e.g. DeepSeek) send an empty-string id
			// on continuation deltas instead of omitting it, so id presence cannot be
			// used to detect the start of a new tool call.
			var tcs = Stream.of(left.toolCalls(), right.toolCalls()).flatMap(Optional::stream).reduce((tcs1, tcs2) -> {
				if (tcs2.isEmpty()) {
					return tcs1;
				}
				Map<Long, ToolCall> mergedByIndex = new LinkedHashMap<>();
				tcs1.forEach(tc -> mergedByIndex.merge(tc.index(), tc, ChunkMerger::mergeToolCalls));
				tcs2.forEach(tc -> mergedByIndex.merge(tc.index(), tc, ChunkMerger::mergeToolCalls));
				return List.copyOf(mergedByIndex.values());
			}).orElse(List.of());

			Delta.Builder deltaBuilder = left.toBuilder().toolCalls(tcs);
			// Concatenate reasoning fragments (e.g. DeepSeek "reasoning_content") so
			// they survive the tool-call chunk merge instead of keeping only the first
			// chunk's value.
			for (String reasoningKey : List.of("reasoning_content", "reasoning")) {
				stringProperty(right, reasoningKey).filter(StringUtils::hasLength)
					.ifPresent(rightFragment -> deltaBuilder.putAdditionalProperty(reasoningKey,
							JsonValue.from(stringProperty(left, reasoningKey).orElse("") + rightFragment)));
			}
			return deltaBuilder.build();
		}

		private static Optional<String> stringProperty(Delta delta, String key) {
			return Optional.ofNullable(delta._additionalProperties().get(key)).flatMap(JsonValue::asString);
		}

		private static ToolCall mergeToolCalls(ToolCall previous, ToolCall current) {
			String arguments = Stream
				.of(previous.function().flatMap(Function::arguments), current.function().flatMap(Function::arguments))
				.flatMap(Optional::stream)
				.collect(Collectors.joining());
			return previous.toBuilder()
				.id(firstWithText(previous.id(), current.id()))
				.putAllAdditionalProperties(current._additionalProperties())
				.function(previous.function()
					.map(Function::toBuilder)
					.orElseGet(Function::builder)
					.name(firstWithText(previous.function().flatMap(Function::name),
							current.function().flatMap(Function::name)))
					.arguments(arguments)
					.build())
				.build();
		}

		private static String firstWithText(Optional<String> first, Optional<String> second) {
			return first.filter(StringUtils::hasText).or(() -> second.filter(StringUtils::hasText)).orElse("");
		}

		/**
		 * Convert a ChatCompletionChunk into a ChatCompletion.
		 */
		static ChatCompletion chunkToChatCompletion(ChatCompletionChunk chunk) {
			List<ChatCompletion.Choice> choices = chunk.choices().stream().map(ChunkMerger::toChoice).toList();

			return ChatCompletion.builder()
				.id(chunk.id())
				.choices(choices)
				.created(getCreated(chunk))
				.model(chunk.model())
				.usage(chunk.usage()
					.orElse(CompletionUsage.builder().promptTokens(0).completionTokens(0).totalTokens(0).build()))
				.putAllAdditionalProperties(chunk._additionalProperties())
				.build();
		}

		private static ChatCompletion.Choice toChoice(Choice choice) {
			return ChatCompletion.Choice.builder()
				.index(choice.index())
				.finishReason(toFinishReason(choice))
				.logprobs(toLogprobs(choice))
				.message(toMessage(choice.delta()))
				.build();
		}

		private static ChatCompletion.Choice.FinishReason toFinishReason(Choice choice) {
			return ChatCompletion.Choice.FinishReason.of(choice.finishReason()
				.map(finishReason -> finishReason.value().name().toLowerCase(Locale.ROOT))
				.orElse(""));
		}

		private static ChatCompletion.Choice.Logprobs toLogprobs(Choice choice) {
			if (choice.logprobs().isEmpty()) {
				return ChatCompletion.Choice.Logprobs.builder().content(List.of()).refusal(List.of()).build();
			}
			var logprobs = choice.logprobs().get();
			return ChatCompletion.Choice.Logprobs.builder()
				.content(logprobs.content())
				.refusal(logprobs.refusal())
				.build();
		}

		private static ChatCompletionMessage toMessage(Delta delta) {
			ChatCompletionMessage.Builder messageBuilder = ChatCompletionMessage.builder()
				.content(delta.content())
				.refusal(delta.refusal())
				// Carry over provider-specific delta fields (e.g. reasoning_content)
				// so they are readable on the message, as in the non-streaming path.
				.putAllAdditionalProperties(delta._additionalProperties());
			delta.toolCalls()
				.ifPresent(toolCalls -> messageBuilder
					.toolCalls(toolCalls.stream().map(ChunkMerger::toFunctionToolCall).toList()));
			return messageBuilder.build();
		}

		private static ChatCompletionMessageToolCall toFunctionToolCall(ToolCall toolCall) {
			Function function = toolCall.function()
				.orElseThrow(() -> new IllegalStateException("Tool call function is missing"));
			String id = toolCall.id()
				.filter(StringUtils::hasText)
				.orElseThrow(() -> new IllegalStateException("Tool call id is missing"));
			String name = function.name()
				.filter(StringUtils::hasText)
				.orElseThrow(() -> new IllegalStateException("Tool call function name is missing"));
			return ChatCompletionMessageToolCall.ofFunction(ChatCompletionMessageFunctionToolCall.builder()
				.putAllAdditionalProperties(toolCall._additionalProperties())
				.id(id)
				.function(ChatCompletionMessageFunctionToolCall.Function.builder()
					.name(name)
					.arguments(function.arguments().orElse(""))
					.build())
				.build());
		}

		/**
		 * Extract the created timestamp from a ChatCompletionChunk, returning 0 if
		 * absent.
		 */
		private static long getCreated(ChatCompletionChunk chunk) {
			try {
				return chunk.created();
			}
			catch (OpenAIInvalidDataException ex) {
				return 0L;
			}
		}

	}

	/**
	 * Response format (text, json_object, json_schema) for OpenAiChatModel responses.
	 *
	 * @author Julien Dubois
	 * @author Mariusz Bernacki
	 * @author Grogdunn
	 * @author Thomas Vitale
	 * @author John Blum
	 * @author Mark Pollack
	 * @author Josh Long
	 * @author Jemin Huh
	 * @author Ueibin Kim
	 * @author Alexandros Pappas
	 * @author luocongqiu
	 * @author Hyunjoon Choi
	 * @author Jonghoon Park
	 * @author Sebastien Deleuze
	 * @author Bishen Yu
	 */
	public static class ResponseFormat {

		private Type type = Type.TEXT;

		private @Nullable String jsonSchema;

		private @Nullable Boolean strict;

		public Type getType() {
			return this.type;
		}

		public void setType(Type type) {
			this.type = type;
		}

		public @Nullable String getJsonSchema() {
			return this.jsonSchema;
		}

		public void setJsonSchema(@Nullable String jsonSchema) {
			this.jsonSchema = jsonSchema;
		}

		/**
		 * Whether to enable strict schema adherence for JSON schema response format.
		 * Defaults to {@code true} when unset.
		 * <p>
		 * This applies only to the JSON schema response format and is distinct from
		 * {@link OpenAiChatOptions.Builder#strict(Boolean)}, which controls strict mode
		 * for tool/function calling.
		 * @return the strict flag, or {@code null} if not configured
		 */
		public @Nullable Boolean getStrict() {
			return this.strict;
		}

		public void setStrict(@Nullable Boolean strict) {
			this.strict = strict;
		}

		@Override
		public boolean equals(@Nullable Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			ResponseFormat that = (ResponseFormat) o;
			return this.type == that.type && Objects.equals(this.jsonSchema, that.jsonSchema)
					&& Objects.equals(this.strict, that.strict);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.type, this.jsonSchema, this.strict);
		}

		public static Builder builder() {
			return new Builder();
		}

		public static final class Builder {

			private final ResponseFormat responseFormat = new ResponseFormat();

			private Builder() {
			}

			public Builder type(Type type) {
				this.responseFormat.setType(type);
				return this;
			}

			public Builder jsonSchema(String jsonSchema) {
				this.responseFormat.setType(Type.JSON_SCHEMA);
				this.responseFormat.setJsonSchema(jsonSchema);
				return this;
			}

			/**
			 * Whether to enable strict schema adherence for JSON schema response format.
			 * <p>
			 * Not to be confused with {@link OpenAiChatOptions.Builder#strict(Boolean)},
			 * which applies to tool/function calling rather than response format.
			 * @param strict the strict flag
			 * @return this builder
			 */
			public Builder strict(@Nullable Boolean strict) {
				this.responseFormat.setStrict(strict);
				return this;
			}

			public ResponseFormat build() {
				return this.responseFormat;
			}

		}

		public enum Type {

			/**
			 * Generates a text response. (default)
			 */
			TEXT,

			/**
			 * Enables JSON mode, which guarantees the message the model generates is
			 * valid JSON.
			 */
			JSON_OBJECT,

			/**
			 * Enables Structured Outputs which guarantees the model will match your
			 * supplied JSON schema.
			 */
			JSON_SCHEMA

		}

	}

	/**
	 * Builder for creating {@link OpenAiChatModel} instances.
	 */
	public static final class Builder {

		private @Nullable OpenAIClient openAiClient;

		private @Nullable OpenAIClientAsync openAiClientAsync;

		private @Nullable OpenAiChatOptions options;

		private @Nullable ToolCallingManager toolCallingManager;

		private @Nullable ObservationRegistry observationRegistry;

		private @Nullable MeterRegistry meterRegistry;

		private List<OpenAiHttpClientBuilderCustomizer> httpClientCustomizers = new ArrayList<>();

		private Builder() {
		}

		/**
		 * Sets the synchronous OpenAI client.
		 * @param openAiClient the synchronous client
		 * @return this builder
		 */
		public Builder openAiClient(OpenAIClient openAiClient) {
			this.openAiClient = openAiClient;
			return this;
		}

		/**
		 * Sets the asynchronous OpenAI client.
		 * @param openAiClientAsync the asynchronous client
		 * @return this builder
		 */
		public Builder openAiClientAsync(OpenAIClientAsync openAiClientAsync) {
			this.openAiClientAsync = openAiClientAsync;
			return this;
		}

		/**
		 * Sets the chat options.
		 * @param options the chat options
		 * @return this builder
		 */
		public Builder options(OpenAiChatOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * Sets the tool calling manager used for internal tool execution.
		 * @param toolCallingManager the tool calling manager
		 * @return this builder
		 * @deprecated since 2.0.0 for removal in 3.0.0 — internal tool execution in
		 * {@link OpenAiChatModel} is superseded by {@code ToolCallingAdvisor} used via
		 * {@code ChatClient}.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
			return this;
		}

		/**
		 * Sets the observation registry for metrics and tracing.
		 * @param observationRegistry the observation registry
		 * @return this builder
		 */
		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public Builder meterRegistry(@Nullable MeterRegistry meterRegistry) {
			this.meterRegistry = meterRegistry;
			return this;
		}

		/**
		 * Registers an {@link OpenAiHttpClientBuilderCustomizer} that mutates the
		 * underlying OkHttp client builder before the OpenAI clients are constructed. Use
		 * this to attach OkHttp interceptors (e.g. OAuth2 bearer-token injection), swap
		 * the dispatcher executor, or tweak any other OkHttp setting. Customizers are
		 * applied in the order they are registered, after Spring AI's own defaults, so
		 * user code wins.
		 */
		public Builder httpClientBuilderCustomizer(OpenAiHttpClientBuilderCustomizer customizer) {
			Assert.notNull(customizer, "customizer cannot be null");
			this.httpClientCustomizers.add(customizer);
			return this;
		}

		/**
		 * Sets the full list of {@link OpenAiHttpClientBuilderCustomizer customizers} to
		 * apply, replacing any customizers registered earlier on this builder. The order
		 * of the list is preserved when invoking the customizers.
		 */
		public Builder httpClientBuilderCustomizers(List<OpenAiHttpClientBuilderCustomizer> customizers) {
			Assert.notNull(customizers, "customizers cannot be null");
			this.httpClientCustomizers = new ArrayList<>(customizers);
			return this;
		}

		/**
		 * Builds a new {@link OpenAiChatModel} instance.
		 * @return the configured chat model
		 */
		public OpenAiChatModel build() {
			OpenAiChatOptions resolvedOptions = Objects.requireNonNullElseGet(this.options,
					() -> OpenAiChatOptions.builder().build());
			ObservationRegistry resolvedObservationRegistry = Objects.requireNonNullElse(this.observationRegistry,
					ObservationRegistry.NOOP);

			OpenAIClient resolvedClient = Objects.requireNonNullElseGet(this.openAiClient,
					() -> OpenAiSetup.setupSyncClient(resolvedOptions.getBaseUrl(), resolvedOptions.getApiKey(),
							resolvedOptions.getCredential(), resolvedOptions.getMicrosoftDeploymentName(),
							resolvedOptions.getMicrosoftFoundryServiceVersion(), resolvedOptions.getOrganizationId(),
							resolvedOptions.isMicrosoftFoundry(), resolvedOptions.isGitHubModels(),
							resolvedOptions.getModel(),
							Objects.requireNonNullElse(resolvedOptions.getTimeout(),
									AbstractOpenAiOptions.DEFAULT_TIMEOUT),
							resolvedOptions.getMaxRetries(), resolvedOptions.getProxy(),
							resolvedOptions.getCustomHeaders(), resolvedObservationRegistry, this.meterRegistry,
							this.httpClientCustomizers));

			OpenAIClientAsync resolvedClientAsync = Objects.requireNonNullElseGet(this.openAiClientAsync,
					() -> OpenAiSetup.setupAsyncClient(resolvedOptions.getBaseUrl(), resolvedOptions.getApiKey(),
							resolvedOptions.getCredential(), resolvedOptions.getMicrosoftDeploymentName(),
							resolvedOptions.getMicrosoftFoundryServiceVersion(), resolvedOptions.getOrganizationId(),
							resolvedOptions.isMicrosoftFoundry(), resolvedOptions.isGitHubModels(),
							resolvedOptions.getModel(),
							Objects.requireNonNullElse(resolvedOptions.getTimeout(),
									AbstractOpenAiOptions.DEFAULT_TIMEOUT),
							resolvedOptions.getMaxRetries(), resolvedOptions.getProxy(),
							resolvedOptions.getCustomHeaders(), resolvedObservationRegistry, this.meterRegistry,
							this.httpClientCustomizers));

			ToolCallingManager resolvedToolCallingManager = Objects.requireNonNullElse(this.toolCallingManager,
					ToolCallingManager.builder().observationRegistry(resolvedObservationRegistry).build());

			return new OpenAiChatModel(resolvedClient, resolvedClientAsync, resolvedOptions,
					resolvedObservationRegistry, resolvedToolCallingManager);
		}

	}

}
