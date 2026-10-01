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

package org.springframework.ai.anthropic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.HttpResponseFor;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.CitationCharLocation;
import com.anthropic.models.messages.CitationContentBlockLocation;
import com.anthropic.models.messages.CitationPageLocation;
import com.anthropic.models.messages.CitationsDelta;
import com.anthropic.models.messages.CitationsWebSearchResultLocation;
import com.anthropic.models.messages.CodeExecutionTool20260120;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.RedactedThinkingBlock;
import com.anthropic.models.messages.RedactedThinkingBlockParam;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.TextCitation;
import com.anthropic.models.messages.ThinkingBlock;
import com.anthropic.models.messages.ThinkingBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolChoiceAuto;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.anthropic.models.messages.UrlImageSource;
import com.anthropic.models.messages.UrlPdfSource;
import com.anthropic.models.messages.UserLocation;
import com.anthropic.models.messages.WebSearchResultBlock;
import com.anthropic.models.messages.WebSearchTool20260209;
import com.anthropic.models.messages.WebSearchToolResultBlock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.anthropic.http.okhttp.AnthropicHttpClientBuilderCustomizer;
import org.springframework.ai.anthropic.metadata.AnthropicRateLimit;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.StreamingParts;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
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
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;

/**
 * {@link ChatModel} and {@link StreamingChatModel} implementation using the official
 * <a href="https://github.com/anthropics/anthropic-sdk-java">Anthropic Java SDK</a>.
 *
 * <p>
 * Supports synchronous and streaming completions, tool calling, and Micrometer-based
 * observability. API credentials are auto-detected from {@code ANTHROPIC_API_KEY} if not
 * configured.
 *
 * <p>
 * <b>Observability.</b> Two layers of Micrometer observations are emitted: a
 * {@code gen_ai.client.operation} span per chat-model call (with token usage, model
 * metadata, and request parameters), and an {@code okhttp.requests} span per outbound
 * HTTP attempt (with HTTP method, URI, status code, and {@code traceparent} propagation).
 * Optional OkHttp connection-pool gauges are bound to the
 * {@link io.micrometer.core.instrument.MeterRegistry} when supplied. For synchronous
 * calls the HTTP span nests under the chat-model span; for streaming calls the HTTP span
 * fires but is not parented under the chat-model span due to an SDK-internal thread
 * boundary — see {@link #stream(org.springframework.ai.chat.prompt.Prompt)}.
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
 * @author Sebastien Deleuze
 * @author Ilayaperumal Gopinathan
 * @author Jewoo Shin
 * @author Seeun Kim
 * @author guan xu
 * @since 1.0.0
 * @see AnthropicChatOptions
 * @see <a href="https://docs.anthropic.com/en/api/messages">Anthropic Messages API</a>
 */
public final class AnthropicChatModel implements ChatModel, StreamingChatModel {

	private static final Log logger = LogFactory.getLog(AnthropicChatModel.class);

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	private static final String BETA_SKILLS = "skills-2025-10-02";

	private static final String BETA_CODE_EXECUTION = "code-execution-2025-08-25";

	private static final String BETA_FILES_API = "files-api-2025-04-14";

	/**
	 * The {@link OpaquePayload#provider()} of parts produced by this model.
	 * @since 2.1.0
	 */
	public static final String ANTHROPIC_PROVIDER = "anthropic";

	/**
	 * The {@link OpaquePayload#kind()} of a thinking block signature; the payload data is
	 * the signature, replayed unmodified.
	 * @since 2.1.0
	 */
	public static final String PAYLOAD_SIGNATURE = "signature";

	/**
	 * The {@link OpaquePayload#kind()} of a redacted thinking block, whose reasoning
	 * Anthropic withheld: the {@link ReasoningPart} has no text and the payload data is
	 * the opaque block data, replayed unmodified.
	 * @since 2.1.0
	 */
	public static final String PAYLOAD_REDACTED_THINKING = "redacted_thinking";

	/**
	 * Metadata key set to {@code true} on streaming {@link AssistantMessage} chunks that
	 * carry incremental thinking (reasoning) text from a thinking-enabled model.
	 * @since 2.0.2
	 * @see #THINKING_TEXT_METADATA_KEY
	 * @deprecated since 2.1.0; a thinking chunk holds a {@link ReasoningPart}, see
	 * {@link AssistantMessage#getReasoning()}
	 */
	@Deprecated(since = "2.1.0", forRemoval = true)
	public static final String THINKING_METADATA_KEY = "thinking";

	/**
	 * Metadata key holding the incremental thinking text on streaming
	 * {@link AssistantMessage} chunks where {@link #THINKING_METADATA_KEY} is
	 * {@code true}.
	 * @since 2.0.2
	 * @see #THINKING_METADATA_KEY
	 * @deprecated since 2.1.0; read the text of the chunk's {@link ReasoningPart}, see
	 * {@link AssistantMessage#getReasoning()}
	 */
	@Deprecated(since = "2.1.0", forRemoval = true)
	public static final String THINKING_TEXT_METADATA_KEY = "thinkingText";

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	private final AnthropicClient anthropicClient;

	private final AnthropicClientAsync anthropicClientAsync;

	private final AnthropicChatOptions options;

	private final ObservationRegistry observationRegistry;

	private final @Nullable MeterRegistry meterRegistry;

	private final @Nullable ExecutorService dispatcherExecutor;

	private final ToolCallingManager toolCallingManager;

	private ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Creates a new builder for {@link AnthropicChatModel}.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Private constructor - use {@link #builder()} to create instances.
	 */
	private AnthropicChatModel(@Nullable AnthropicClient anthropicClient,
			@Nullable AnthropicClientAsync anthropicClientAsync, @Nullable AnthropicChatOptions options,
			@Nullable ToolCallingManager toolCallingManager, @Nullable ObservationRegistry observationRegistry,
			@Nullable MeterRegistry meterRegistry, @Nullable ExecutorService dispatcherExecutor,
			List<AnthropicHttpClientBuilderCustomizer> httpClientCustomizers) {

		if (options == null) {
			this.options = AnthropicChatOptions.builder().build();
		}
		else {
			this.options = options;
		}

		// Must precede the AnthropicSetup calls below so the HTTP client gets the user's
		// registries and dispatcher.
		this.observationRegistry = Objects.requireNonNullElse(observationRegistry, ObservationRegistry.NOOP);
		this.meterRegistry = meterRegistry;
		this.dispatcherExecutor = dispatcherExecutor;

		this.anthropicClient = Objects.requireNonNullElseGet(anthropicClient,
				() -> AnthropicSetup.setupSyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getTimeout(), this.options.getMaxRetries(), this.options.getProxy(),
						this.options.getCustomHeaders(), this.observationRegistry, this.meterRegistry,
						this.dispatcherExecutor, httpClientCustomizers));

		this.anthropicClientAsync = Objects.requireNonNullElseGet(anthropicClientAsync,
				() -> AnthropicSetup.setupAsyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getTimeout(), this.options.getMaxRetries(), this.options.getProxy(),
						this.options.getCustomHeaders(), this.observationRegistry, this.meterRegistry,
						this.dispatcherExecutor, httpClientCustomizers));

		this.toolCallingManager = Objects.requireNonNullElse(toolCallingManager, DEFAULT_TOOL_CALLING_MANAGER);
	}

	/**
	 * Gets the chat options for this model.
	 * @return the chat options
	 * @since 2.0.0
	 */
	@Override
	public AnthropicChatOptions getOptions() {
		return this.options;
	}

	/**
	 * Returns the underlying synchronous Anthropic SDK client. Useful for accessing SDK
	 * features directly, such as the Files API ({@code client.beta().files()}).
	 * @return the sync client
	 */
	public AnthropicClient getAnthropicClient() {
		return this.anthropicClient;
	}

	/**
	 * Returns the underlying asynchronous Anthropic SDK client. Useful for non-blocking
	 * access to SDK features directly, such as the Files API.
	 * @return the async client
	 */
	public AnthropicClientAsync getAnthropicClientAsync() {
		return this.anthropicClientAsync;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return this.internalCall(requestPrompt, null);
	}

	/**
	 * Streams the chat completion as a {@link Flux} of {@link ChatResponse} events.
	 *
	 * <p>
	 * <b>Observability note.</b> The outbound HTTP attempt is observed as
	 * {@code okhttp.requests} with timer + {@code traceparent}, but for streaming calls
	 * the HTTP span is not parented under the chat-model's
	 * {@code gen_ai.client.operation} span. The SDK's async path internally schedules the
	 * HTTP call on {@code ForkJoinPool.commonPool()} before Spring AI's HTTP client runs,
	 * which drops the calling thread's observation context. Filter by
	 * {@code okhttp.requests} + host {@code api.anthropic.com} and correlate by trace ID
	 * or timestamp if you need to join the spans in your tracing UI.
	 * @param prompt the prompt
	 * @return a {@link Flux} of streamed {@link ChatResponse} events
	 */
	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return internalStream(requestPrompt, null);
	}

	/**
	 * Internal method to handle streaming chat completion calls with tool execution
	 * support. This method is called recursively to support multi-turn tool calling.
	 *
	 * <p>
	 * Rate-limit headers are read from the streaming response via
	 * {@code withRawResponse().createStreaming(...)} and attached to the aggregated
	 * {@link ChatResponse}. Because that SDK call exposes the stream as a blocking
	 * {@link StreamResponse}, the events are pulled on
	 * {@link Schedulers#boundedElastic()}; a streaming call therefore holds a worker
	 * thread for the duration of the stream.
	 * @param prompt The prompt for the chat completion. In a recursive tool-call
	 * scenario, this prompt will contain the full conversation history including the tool
	 * results.
	 * @param previousChatResponse The chat response from the preceding API call. This is
	 * used to accumulate token usage correctly across multiple API calls in a single user
	 * turn.
	 * @return A {@link Flux} of {@link ChatResponse} events, which can include text
	 * chunks and the final response with tool call information or the model's final
	 * answer.
	 */
	private Flux<ChatResponse> internalStream(Prompt prompt, @Nullable ChatResponse previousChatResponse) {

		return Flux.deferContextual(contextView -> {
			MessageCreateParams request = createRequest(prompt, true);

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(prompt)
				.provider(AiProvider.ANTHROPIC.value())
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

			// Track streaming state for usage accumulation and tool calls
			StreamingState streamingState = new StreamingState();

			// Use the raw streaming response so rate-limit headers (available once at
			// stream start) can be captured. The SDK exposes this as a blocking
			// StreamResponse, so events are pulled on a boundedElastic worker.
			Flux<ChatResponse> chatResponseFlux = Mono
				.fromFuture(() -> this.anthropicClientAsync.messages()
					.withRawResponse()
					.createStreaming(request, requestOptionsFor(prompt)))
				.flatMapMany(rawResponse -> {
					streamingState.setRateLimit(AnthropicRateLimit.from(rawResponse.headers()));
					StreamResponse<RawMessageStreamEvent> streamResponse = rawResponse.parse();
					return Flux.fromStream(streamResponse.stream())
						.doFinally(signal -> streamResponse.close())
						.subscribeOn(Schedulers.boundedElastic());
				})
				.<ChatResponse>handle((event, sink) -> {
					ChatResponse chatResponse = convertStreamEventToChatResponse(event, previousChatResponse,
							streamingState);
					if (chatResponse != null) {
						sink.next(chatResponse);
					}
				})
				.doOnError(e -> logger.error("Error processing streaming response", e));

			// @formatter:off
			Flux<ChatResponse> flux = chatResponseFlux
				.doOnError(observation::error)
				.doFinally(s -> observation.stop())
				.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
			// @formatter:on

			// Aggregate streaming responses and handle tool execution on final response
			return new MessageAggregator().aggregate(flux, observationContext::setResponse);
		});
	}

	/**
	 * Converts a streaming event to a ChatResponse. Handles message_start, content_block
	 * events (text and tool_use), and message_delta for final response with usage.
	 * @param event the raw message stream event
	 * @param previousChatResponse the previous chat response for usage accumulation
	 * @param streamingState the state accumulated during streaming
	 * @return the chat response, or null if the event doesn't produce a response
	 */
	private @Nullable ChatResponse convertStreamEventToChatResponse(RawMessageStreamEvent event,
			@Nullable ChatResponse previousChatResponse, StreamingState streamingState) {

		// -- Event: message_start --
		// Captures message ID, model, and input tokens from the first event.
		if (event.messageStart().isPresent()) {
			var startEvent = event.messageStart().get();
			var message = startEvent.message();
			streamingState.setMessageInfo(message.id(), message.model().asString(), message.usage().inputTokens());
			return null;
		}

		// -- Event: content_block_start --
		// Starts tracking a tool use block, emits redacted thinking as a complete part,
		// starts tracking any block type not modeled as a part, and accumulates web
		// search results for the final response metadata.
		if (event.contentBlockStart().isPresent()) {
			var startEvent = event.contentBlockStart().get();
			var contentBlock = startEvent.contentBlock();
			int index = Math.toIntExact(startEvent.index());
			if (contentBlock.isToolUse()) {
				var toolUseBlock = contentBlock.asToolUse();
				streamingState.startToolUse(index, toolUseBlock.id(), toolUseBlock.name());
			}
			else if (contentBlock.isRedactedThinking()) {
				RedactedThinkingBlock redactedBlock = contentBlock.asRedactedThinking();
				// The "data" metadata key is deprecated; use the ReasoningPart payload.
				Map<String, Object> legacyProperties = new HashMap<>();
				legacyProperties.put("data", redactedBlock.data());
				return partChunk(streamingState,
						StreamingParts.complete(redactedReasoningPart(redactedBlock.data()), index), legacyProperties);
			}
			else if (!contentBlock.isText() && !contentBlock.isThinking()) {
				if (contentBlock.isWebSearchToolResult()) {
					WebSearchToolResultBlock wsBlock = contentBlock.asWebSearchToolResult();
					if (wsBlock.content().isResultBlocks()) {
						for (WebSearchResultBlock r : wsBlock.content().asResultBlocks()) {
							streamingState.addWebSearchResult(
									new AnthropicWebSearchResult(r.title(), r.url(), r.pageAge().orElse(null)));
						}
					}
				}
				// server_tool_use, server tool results, container upload, future types:
				// emitted as an UnknownPart once the block stops, since a server tool
				// use still receives its input as deltas.
				streamingState.startUnknownBlock(index,
						contentBlock._json().orElseGet(() -> JsonValue.from(contentBlock)));
			}
			return null;
		}

		// -- Event: content_block_delta --
		// Text, thinking and signature deltas are emitted as indexed partial parts; tool
		// argument deltas are accumulated for the complete call and citations for the
		// final response metadata.
		if (event.contentBlockDelta().isPresent()) {
			var deltaEvent = event.contentBlockDelta().get();
			var delta = deltaEvent.delta();
			int index = Math.toIntExact(deltaEvent.index());

			if (delta.isText()) {
				return partChunk(streamingState, StreamingParts.partial(TextPart.of(delta.asText().text()), index),
						Map.of());
			}

			if (delta.isInputJson()) {
				streamingState.appendInputJson(index, delta.asInputJson().partialJson());
				return null;
			}

			if (delta.isThinking()) {
				String thinkingText = delta.asThinking().thinking();
				// The thinking metadata keys are deprecated; use the ReasoningPart.
				Map<String, Object> legacyProperties = new HashMap<>();
				legacyProperties.put(THINKING_METADATA_KEY, Boolean.TRUE);
				legacyProperties.put(THINKING_TEXT_METADATA_KEY, thinkingText);
				return partChunk(streamingState, StreamingParts.partial(ReasoningPart.of(thinkingText), index),
						legacyProperties);
			}

			if (delta.isSignature()) {
				String signature = delta.asSignature().signature();
				// The "signature" metadata key is deprecated; use the ReasoningPart
				// payload.
				Map<String, Object> legacyProperties = new HashMap<>();
				legacyProperties.put("signature", signature);
				// An empty text so that a thinking block streamed without any thinking
				// delta (display omitted) still aggregates to a replayable part.
				ReasoningPart signaturePart = new ReasoningPart("", null,
						new OpaquePayload(ANTHROPIC_PROVIDER, PAYLOAD_SIGNATURE, signature), Map.of());
				return partChunk(streamingState, StreamingParts.partial(signaturePart, index), legacyProperties);
			}

			if (delta.isCitations()) {
				CitationsDelta citationsDelta = delta.asCitations();
				Citation citation = convertStreamingCitation(citationsDelta.citation());
				if (citation != null) {
					streamingState.addCitation(citation);
				}
				return null;
			}
		}

		// -- Event: content_block_stop --
		// Finalizes the tool call at this index, or emits the block not modeled as a part
		// as a complete UnknownPart.
		if (event.contentBlockStop().isPresent()) {
			int index = Math.toIntExact(event.contentBlockStop().get().index());
			streamingState.finishToolUse(index);
			UnknownPart unknownPart = streamingState.finishUnknownBlock(index);
			if (unknownPart != null) {
				return partChunk(streamingState, StreamingParts.complete(unknownPart, index), Map.of());
			}
			return null;
		}

		// -- Event: message_delta --
		// Final event with stop_reason and usage. Carries the completed tool calls as
		// indexed complete parts so the aggregator places them in block order.
		Optional<ChatResponse> messageDeltaResponse = event.messageDelta().map(deltaEvent -> {
			String stopReason = deltaEvent.delta().stopReason().map(r -> r.toString()).orElse("");
			ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().finishReason(stopReason).build();

			// The final chunk keeps an empty text, as it always had, so a streamed chunk
			// never reports a null text and chunk texts can be joined as before.
			AssistantMessage.Builder<?> messageBuilder = AssistantMessage.builder().content("");
			streamingState.getCompletedToolCalls()
				.forEach((index, toolCall) -> messageBuilder
					.part(StreamingParts.complete(ToolCallPart.of(toolCall), index)));
			Generation generation = new Generation(messageBuilder.build(), metadata);

			// Combine input tokens from message_start with output tokens from
			// message_delta
			long inputTokens = streamingState.getInputTokens();
			long outputTokens = deltaEvent.usage().outputTokens();
			Long cacheRead = deltaEvent.usage().cacheReadInputTokens().orElse(null);
			Long cacheWrite = deltaEvent.usage().cacheCreationInputTokens().orElse(null);
			Usage usage = new DefaultUsage(Integer.valueOf(Math.toIntExact(inputTokens)),
					Integer.valueOf(Math.toIntExact(outputTokens)),
					Integer.valueOf(Math.toIntExact(inputTokens + outputTokens)), deltaEvent.usage(), cacheRead,
					cacheWrite);

			Usage accumulatedUsage = previousChatResponse != null
					? UsageCalculator.getCumulativeUsage(usage, previousChatResponse) : usage;

			ChatResponseMetadata.Builder metadataBuilder = ChatResponseMetadata.builder()
				.id(streamingState.getMessageId())
				.model(streamingState.getModel())
				.rateLimit(streamingState.getRateLimit())
				.usage(accumulatedUsage);

			List<Citation> citations = streamingState.getCitations();
			if (!citations.isEmpty()) {
				metadataBuilder.keyValue("citations", citations).keyValue("citationCount", citations.size());
			}

			List<AnthropicWebSearchResult> webSearchResults = streamingState.getWebSearchResults();
			if (!webSearchResults.isEmpty()) {
				metadataBuilder.keyValue("web-search-results", webSearchResults);
			}

			return new ChatResponse(List.of(generation), metadataBuilder.build());
		});

		return messageDeltaResponse.orElse(null);
	}

	/**
	 * Builds a streamed chunk holding one indexed part. Every chunk carries the response
	 * id and model so that {@link MessageAggregator} can group the parts of one response.
	 * @param streamingState the streaming state holding the message id and model
	 * @param part the indexed part, see {@link StreamingParts}
	 * @param legacyProperties deprecated metadata kept for one release
	 * @return the chunk
	 */
	private static ChatResponse partChunk(StreamingState streamingState, MessagePart part,
			Map<String, Object> legacyProperties) {
		AssistantMessage message = AssistantMessage.builder().part(part).properties(legacyProperties).build();
		ChatResponseMetadata metadata = ChatResponseMetadata.builder()
			.id(streamingState.getMessageId())
			.model(streamingState.getModel())
			.build();
		return new ChatResponse(List.of(new Generation(message)), metadata);
	}

	/**
	 * Internal method to handle synchronous chat completion calls with tool execution
	 * support. This method is called recursively to support multi-turn tool calling.
	 * @param prompt The prompt for the chat completion. In a recursive tool-call
	 * scenario, this prompt will contain the full conversation history including the tool
	 * results.
	 * @param previousChatResponse The chat response from the preceding API call. This is
	 * used to accumulate token usage correctly across multiple API calls in a single user
	 * turn.
	 * @return The final {@link ChatResponse} after all tool calls (if any) are resolved.
	 */
	private ChatResponse internalCall(Prompt prompt, @Nullable ChatResponse previousChatResponse) {

		MessageCreateParams request = createRequest(prompt, false);

		ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
			.prompt(prompt)
			.provider(AiProvider.ANTHROPIC.value())
			.build();

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {

				HttpResponseFor<Message> rawResponse = this.anthropicClient.messages()
					.withRawResponse()
					.create(request, requestOptionsFor(prompt));
				Message message = rawResponse.parse();
				RateLimit rateLimit = AnthropicRateLimit.from(rawResponse.headers());

				List<ContentBlock> contentBlocks = message.content();
				if (contentBlocks.isEmpty()) {
					if (logger.isWarnEnabled()) {
						logger.warn("No content blocks returned for prompt: " + prompt);
					}
					return new ChatResponse(List.of());
				}

				List<Citation> citations = new ArrayList<>();
				List<AnthropicWebSearchResult> webSearchResults = new ArrayList<>();
				List<Generation> generations = buildGenerations(message, citations, webSearchResults);

				// Current usage
				com.anthropic.models.messages.Usage sdkUsage = message.usage();
				Usage currentChatResponseUsage = getDefaultUsage(sdkUsage);
				Usage accumulatedUsage = previousChatResponse != null
						? UsageCalculator.getCumulativeUsage(currentChatResponseUsage, previousChatResponse)
						: currentChatResponseUsage;

				ChatResponse chatResponse = new ChatResponse(generations,
						from(message, accumulatedUsage, citations, webSearchResults, rateLimit));

				observationContext.setResponse(chatResponse);

				return chatResponse;
			});

		return response;
	}

	private static AnthropicChatOptions resolveAnthropicOptions(Prompt prompt) {
		ChatOptions options = prompt.getOptions();
		return options instanceof AnthropicChatOptions anthropicOptions ? anthropicOptions
				: AnthropicChatOptions.builder().build();
	}

	private static RequestOptions requestOptionsFor(Prompt prompt) {
		// Carry the resolved timeout as per-call RequestOptions; the SDK only honors a
		// timeout supplied here, so otherwise AnthropicChatOptions#getTimeout() is
		// ignored.
		Duration timeout = resolveAnthropicOptions(prompt).getTimeout();
		return timeout != null ? RequestOptions.builder().timeout(timeout).build() : RequestOptions.none();
	}

	/**
	 * Creates a {@link MessageCreateParams} request from a Spring AI {@link Prompt}. Maps
	 * message types to Anthropic format: TOOL messages become user messages with
	 * {@link ToolResultBlockParam}, and ASSISTANT messages with tool calls become
	 * {@link ToolUseBlockParam} blocks.
	 * @param prompt the prompt with message history and options
	 * @param stream not currently used; sync/async determined by client method
	 * @return the constructed request parameters
	 */
	MessageCreateParams createRequest(Prompt prompt, boolean stream) {

		MessageCreateParams.Builder builder = MessageCreateParams.builder();

		AnthropicChatOptions requestOptions = resolveAnthropicOptions(prompt);

		// Set required fields
		builder.model(requestOptions.getModel()).maxTokens(requestOptions.getMaxTokens());

		// Create cache resolver
		CacheEligibilityResolver cacheResolver = CacheEligibilityResolver.from(requestOptions.getCacheOptions());

		// Prepare citation documents for inclusion in the first user message
		List<AnthropicCitationDocument> citationDocuments = requestOptions.getCitationDocuments();

		// Collect system messages and non-system messages separately
		List<String> systemTexts = new ArrayList<>();
		List<org.springframework.ai.chat.messages.Message> nonSystemMessages = new ArrayList<>();
		for (org.springframework.ai.chat.messages.Message message : prompt.getInstructions()) {
			if (message.getMessageType() == MessageType.SYSTEM) {
				String text = message.getText();
				if (text != null) {
					systemTexts.add(text);
				}
			}
			else {
				nonSystemMessages.add(message);
			}
		}

		// Process system messages with cache support
		if (!systemTexts.isEmpty()) {
			if (!cacheResolver.isCachingEnabled()) {
				// No caching: join all system texts and use simple string format
				builder.system(String.join("\n\n", systemTexts));
			}
			else if (requestOptions.getCacheOptions().isMultiBlockSystemCaching() && systemTexts.size() > 1) {
				// Multi-block system caching: each text becomes a separate
				// TextBlockParam.
				// Cache control is applied to the second-to-last block.
				List<TextBlockParam> systemBlocks = new ArrayList<>();
				for (int i = 0; i < systemTexts.size(); i++) {
					TextBlockParam.Builder textBlockBuilder = TextBlockParam.builder().text(systemTexts.get(i));
					if (i == systemTexts.size() - 2) {
						CacheControlEphemeral cacheControl = cacheResolver.resolve(MessageType.SYSTEM,
								String.join("\n\n", systemTexts));
						if (cacheControl != null) {
							textBlockBuilder.cacheControl(cacheControl);
							cacheResolver.useCacheBlock();
						}
					}
					systemBlocks.add(textBlockBuilder.build());
				}
				builder.systemOfTextBlockParams(systemBlocks);
			}
			else {
				// Single-block system caching: join all texts into one TextBlockParam
				String joinedText = String.join("\n\n", systemTexts);
				CacheControlEphemeral cacheControl = cacheResolver.resolve(MessageType.SYSTEM, joinedText);
				if (cacheControl != null) {
					builder.systemOfTextBlockParams(
							List.of(TextBlockParam.builder().text(joinedText).cacheControl(cacheControl).build()));
					cacheResolver.useCacheBlock();
				}
				else {
					builder.system(joinedText);
				}
			}
		}

		boolean thinkingEnabled = requestOptions.getThinking() != null
				&& (requestOptions.getThinking().isEnabled() || requestOptions.getThinking().isAdaptive());

		// Process non-system messages
		for (int i = 0; i < nonSystemMessages.size(); i++) {
			org.springframework.ai.chat.messages.Message message = nonSystemMessages.get(i);

			if (message.getMessageType() == MessageType.USER) {
				UserMessage userMessage = (UserMessage) message;
				boolean hasCitationDocs = !CollectionUtils.isEmpty(citationDocuments);
				boolean hasMedia = userMessage.getParts().stream().anyMatch(MediaPart.class::isInstance);
				// The CONVERSATION_HISTORY strategy caches up to the last user message
				boolean applyCacheToUser = cacheResolver.isCachingEnabled()
						&& i == lastIndexOf(nonSystemMessages, MessageType.USER);

				// Compute cache control for last user message
				CacheControlEphemeral userCacheControl = null;
				if (applyCacheToUser) {
					String combinedText = combineEligibleMessagesText(nonSystemMessages, i);
					userCacheControl = cacheResolver.resolve(MessageType.USER, combinedText);
				}

				if (hasCitationDocs || hasMedia || userCacheControl != null) {
					List<ContentBlockParam> contentBlocks = new ArrayList<>();

					// Prepend citation document blocks to the first user message
					if (hasCitationDocs) {
						for (AnthropicCitationDocument doc : Objects.requireNonNull(citationDocuments)) {
							contentBlocks.add(ContentBlockParam.ofDocument(doc.toDocumentBlockParam()));
						}
					}

					// One block per part, in part order, so text and media interleave as
					// the message was built. Legacy messages have text first, then media.
					// The cache breakpoint goes on the last non-empty text block.
					List<MessagePart> userParts = userMessage.getParts();
					int lastTextIndex = -1;
					for (int p = 0; p < userParts.size(); p++) {
						if (userParts.get(p) instanceof TextPart textPart && !textPart.text().isEmpty()) {
							lastTextIndex = p;
						}
					}
					for (int p = 0; p < userParts.size(); p++) {
						MessagePart part = userParts.get(p);
						if (part instanceof TextPart textPart) {
							if (textPart.text().isEmpty()) {
								continue;
							}
							TextBlockParam.Builder textBlockBuilder = TextBlockParam.builder().text(textPart.text());
							if (userCacheControl != null && p == lastTextIndex) {
								textBlockBuilder.cacheControl(userCacheControl);
								cacheResolver.useCacheBlock();
							}
							contentBlocks.add(ContentBlockParam.ofText(textBlockBuilder.build()));
						}
						else if (part instanceof MediaPart mediaPart) {
							contentBlocks.add(getContentBlockParamByMedia(mediaPart.media()));
						}
						else if (logger.isDebugEnabled()) {
							logger.debug("Skipping user message part " + part.getClass().getSimpleName()
									+ " that Anthropic cannot take");
						}
					}

					builder.addUserMessageOfBlockParams(contentBlocks);
				}
				else {
					String text = message.getText();
					if (text != null) {
						builder.addUserMessage(text);
					}
				}
			}
			else if (message.getMessageType() == MessageType.ASSISTANT) {
				AssistantMessage assistantMessage = (AssistantMessage) message;
				if (hasOnlyTextParts(assistantMessage)) {
					String text = assistantMessage.getText();
					if (text != null) {
						builder.addAssistantMessage(text);
					}
				}
				else {
					List<ContentBlockParam> blocks = toAssistantBlockParams(assistantMessage);
					if (thinkingEnabled && onlyToolResponsesFollow(nonSystemMessages, i)) {
						warnIfThinkingNotReplayed(assistantMessage, blocks);
					}
					if (blocks.isEmpty()) {
						// Nothing Anthropic can take from this turn (for example only
						// reasoning from another provider). The API rejects empty
						// content, so the turn is left out as it was before parts.
						if (logger.isDebugEnabled()) {
							logger.debug("Skipping assistant message with no replayable content: " + assistantMessage);
						}
					}
					else {
						builder.addAssistantMessageOfBlockParams(blocks);
					}
				}
			}
			else if (message.getMessageType() == MessageType.TOOL) {
				ToolResponseMessage toolResponseMessage = (ToolResponseMessage) message;
				List<ToolResponseMessage.ToolResponse> responses = toolResponseMessage.getResponses();

				// Compute cache control for the last tool result message of the request.
				// The breakpoint is placed on its final block, caching everything before
				// it (tools + system + prior messages + earlier tool results), so the
				// prior tool outputs are read from cache on subsequent tool-calling
				// rounds.
				CacheControlEphemeral toolCacheControl = null;
				if (cacheResolver.isCachingEnabled() && requestOptions.getCacheOptions().isCacheToolResults()
						&& i == lastIndexOf(nonSystemMessages, MessageType.TOOL)) {
					String combinedText = combineToolResponsesText(responses);
					toolCacheControl = cacheResolver.resolve(MessageType.TOOL, combinedText);
				}

				List<ContentBlockParam> toolResultBlocks = new ArrayList<>();
				for (int r = 0; r < responses.size(); r++) {
					ToolResponseMessage.ToolResponse response = responses.get(r);
					ToolResultBlockParam.Builder toolResultBuilder = ToolResultBlockParam.builder()
						.toolUseId(response.id())
						.content(response.responseData());
					if (toolCacheControl != null && r == responses.size() - 1) {
						toolResultBuilder.cacheControl(toolCacheControl);
					}
					toolResultBlocks.add(ContentBlockParam.ofToolResult(toolResultBuilder.build()));
				}
				if (toolCacheControl != null) {
					cacheResolver.useCacheBlock();
				}
				builder.addUserMessageOfBlockParams(toolResultBlocks);
			}
		}

		// Set optional parameters
		if (requestOptions.getTemperature() != null) {
			builder.temperature(requestOptions.getTemperature());
		}
		if (requestOptions.getTopP() != null) {
			builder.topP(requestOptions.getTopP());
		}
		if (requestOptions.getTopK() != null) {
			builder.topK(requestOptions.getTopK().longValue());
		}
		if (requestOptions.getStopSequences() != null && !requestOptions.getStopSequences().isEmpty()) {
			builder.stopSequences(requestOptions.getStopSequences());
		}
		if (requestOptions.getMetadata() != null) {
			builder.metadata(requestOptions.getMetadata());
		}
		if (requestOptions.getThinking() != null) {
			builder.thinking(requestOptions.getThinking());
		}
		if (requestOptions.getInferenceGeo() != null) {
			builder.inferenceGeo(requestOptions.getInferenceGeo());
		}
		if (requestOptions.getServiceTier() != null) {
			builder.serviceTier(requestOptions.getServiceTier().toSdkServiceTier());
		}

		// Add output configuration if specified (structured output / effort)
		if (requestOptions.getOutputConfig() != null) {
			builder.outputConfig(requestOptions.getOutputConfig());
		}

		// Build combined tool list (user-defined tools + built-in tools)
		List<ToolUnion> allTools = new ArrayList<>();

		// Add user-defined tool definitions
		List<ToolDefinition> toolDefinitions = this.toolCallingManager.resolveToolDefinitions(requestOptions);
		if (!CollectionUtils.isEmpty(toolDefinitions)) {
			List<Tool> tools = toolDefinitions.stream().map(this::toAnthropicTool).toList();

			// Apply cache control to the last tool if caching strategy includes tools
			CacheControlEphemeral toolCacheControl = cacheResolver.resolveToolCacheControl();
			if (toolCacheControl != null && !tools.isEmpty()) {
				List<Tool> modifiedTools = new ArrayList<>();
				for (int i = 0; i < tools.size(); i++) {
					Tool tool = tools.get(i);
					if (i == tools.size() - 1) {
						tool = tool.toBuilder().cacheControl(toolCacheControl).build();
						cacheResolver.useCacheBlock();
					}
					modifiedTools.add(tool);
				}
				tools = modifiedTools;
			}

			tools.stream().map(ToolUnion::ofTool).forEach(allTools::add);
		}

		// Add built-in web search tool if configured
		if (requestOptions.getWebSearchTool() != null) {
			allTools.add(ToolUnion.ofWebSearchTool20260209(toSdkWebSearchTool(requestOptions.getWebSearchTool())));
		}

		if (!allTools.isEmpty()) {
			builder.tools(allTools);

			// Set tool choice if specified, applying disableParallelToolUse if set
			if (requestOptions.getToolChoice() != null) {
				ToolChoice toolChoice = requestOptions.getToolChoice();
				if (Boolean.TRUE.equals(requestOptions.getDisableParallelToolUse())) {
					toolChoice = applyDisableParallelToolUse(toolChoice);
				}
				builder.toolChoice(toolChoice);
			}
			else if (Boolean.TRUE.equals(requestOptions.getDisableParallelToolUse())) {
				builder.toolChoice(ToolChoice.ofAuto(ToolChoiceAuto.builder().disableParallelToolUse(true).build()));
			}
		}

		// Per-request HTTP headers
		Map<String, String> httpHeaders = requestOptions.getHttpHeaders();
		if (!CollectionUtils.isEmpty(httpHeaders)) {
			httpHeaders.forEach(builder::putAdditionalHeader);
		}

		// Skills support
		AnthropicSkillContainer skillContainer = requestOptions.getSkillContainer();
		if (skillContainer == null && this.options.getSkillContainer() != null) {
			skillContainer = this.options.getSkillContainer();
		}
		if (skillContainer != null) {
			// Add container with skills config
			builder.putAdditionalBodyProperty("container",
					JsonValue.from(Map.of("skills", skillContainer.toSkillsList())));

			// Add code execution tool if not already present in user-defined tools
			boolean hasCodeExecution = !CollectionUtils.isEmpty(toolDefinitions)
					&& toolDefinitions.stream().anyMatch(td -> td.name().contains("code_execution"));
			if (!hasCodeExecution) {
				builder.addTool(CodeExecutionTool20260120.builder().build());
			}

			// Add beta headers, merging with any existing anthropic-beta value
			String existingBeta = httpHeaders != null ? httpHeaders.get("anthropic-beta") : null;
			if (existingBeta != null) {
				StringBuilder merged = new StringBuilder(existingBeta);
				if (!existingBeta.contains(BETA_SKILLS)) {
					merged.append(",").append(BETA_SKILLS);
				}
				if (!existingBeta.contains(BETA_CODE_EXECUTION)) {
					merged.append(",").append(BETA_CODE_EXECUTION);
				}
				if (!existingBeta.contains(BETA_FILES_API)) {
					merged.append(",").append(BETA_FILES_API);
				}
				builder.putAdditionalHeader("anthropic-beta", merged.toString());
			}
			else {
				builder.putAdditionalHeader("anthropic-beta",
						BETA_SKILLS + "," + BETA_CODE_EXECUTION + "," + BETA_FILES_API);
			}
		}

		return builder.build();
	}

	/**
	 * Finds the index of the last message of the given type.
	 * @param messages the list of non-system messages
	 * @param messageType the message type to look for
	 * @return the index of the last message of that type, or {@code -1} if there is none
	 */
	private static int lastIndexOf(List<org.springframework.ai.chat.messages.Message> messages,
			MessageType messageType) {
		for (int i = messages.size() - 1; i >= 0; i--) {
			if (messages.get(i).getMessageType() == messageType) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Tells whether only tool response messages follow the message at the given index,
	 * meaning the tool use loop of that message is still open.
	 * @param messages the list of non-system messages
	 * @param index the index of the message
	 * @return {@code true} if every later message is a tool response, or there is none
	 */
	private static boolean onlyToolResponsesFollow(List<org.springframework.ai.chat.messages.Message> messages,
			int index) {
		for (int i = index + 1; i < messages.size(); i++) {
			if (messages.get(i).getMessageType() != MessageType.TOOL) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Combines text from all messages up to and including the specified index, for use in
	 * cache eligibility length checks during CONVERSATION_HISTORY caching.
	 * @param messages the list of non-system messages
	 * @param lastUserIndex the index of the last user message (inclusive)
	 * @return the combined text of eligible messages
	 */
	private String combineEligibleMessagesText(List<org.springframework.ai.chat.messages.Message> messages,
			int lastUserIndex) {
		StringBuilder combined = new StringBuilder();
		for (int i = 0; i <= lastUserIndex && i < messages.size(); i++) {
			String text = messages.get(i).getText();
			if (text != null) {
				combined.append(text);
			}
		}
		return combined.toString();
	}

	private String combineToolResponsesText(List<ToolResponseMessage.ToolResponse> responses) {
		StringBuilder combined = new StringBuilder();
		for (ToolResponseMessage.ToolResponse response : responses) {
			String data = response.responseData();
			if (data != null) {
				combined.append(data);
			}
		}
		return combined.toString();
	}

	/**
	 * Builds the single generation of an Anthropic message response. Every response
	 * content block becomes one {@link MessagePart} in block order: text, thinking and
	 * redacted thinking (as {@link ReasoningPart} carrying the signature to replay), tool
	 * use, and an {@link UnknownPart} for block types that are not modeled yet. Citations
	 * and web search results are accumulated into the response metadata as before.
	 * @param message the Anthropic message response
	 * @param citationAccumulator collects citations found in text blocks
	 * @param webSearchAccumulator collects web search results found in response
	 * @return a single-element list holding the generation
	 */
	private List<Generation> buildGenerations(Message message, List<Citation> citationAccumulator,
			List<AnthropicWebSearchResult> webSearchAccumulator) {

		String finishReason = message.stopReason().map(r -> r.toString()).orElse("");
		ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();

		List<MessagePart> parts = new ArrayList<>();

		for (ContentBlock block : message.content()) {
			if (block.isText()) {
				TextBlock textBlock = block.asText();
				parts.add(TextPart.of(textBlock.text()));

				// Extract citations from text blocks if present
				textBlock.citations().ifPresent(textCitations -> {
					for (TextCitation tc : textCitations) {
						Citation citation = convertTextCitation(tc);
						if (citation != null) {
							citationAccumulator.add(citation);
						}
					}
				});
			}
			else if (block.isToolUse()) {
				ToolUseBlock toolUseBlock = block.asToolUse();
				// ToolUseBlock._input() returns JsonValue, which needs to be converted
				// to a JSON string via the visitor pattern since JsonValue.toString()
				// produces Java Map format ("{key=value}"), not valid JSON.
				String arguments = convertJsonValueToString(toolUseBlock._input());
				parts.add(ToolCallPart.of(new ToolCall(toolUseBlock.id(), "function", toolUseBlock.name(), arguments)));
			}
			else if (block.isThinking()) {
				ThinkingBlock thinkingBlock = block.asThinking();
				parts.add(thinkingReasoningPart(thinkingBlock.thinking(), thinkingBlock.signature()));
			}
			else if (block.isRedactedThinking()) {
				parts.add(redactedReasoningPart(block.asRedactedThinking().data()));
			}
			else {
				if (block.isWebSearchToolResult()) {
					WebSearchToolResultBlock wsBlock = block.asWebSearchToolResult();
					if (wsBlock.content().isResultBlocks()) {
						for (WebSearchResultBlock r : wsBlock.content().asResultBlocks()) {
							webSearchAccumulator
								.add(new AnthropicWebSearchResult(r.title(), r.url(), r.pageAge().orElse(null)));
						}
					}
				}
				// server_tool_use, server tool results, container upload, future types:
				// keep the raw block so nothing is silently dropped.
				parts.add(unknownPart(block._json().orElseGet(() -> JsonValue.from(block))));
			}
		}

		AssistantMessage assistantMessage = AssistantMessage.builder().parts(parts).build();
		return List.of(new Generation(assistantMessage, generationMetadata));
	}

	private static ReasoningPart thinkingReasoningPart(String thinking, String signature) {
		return new ReasoningPart(thinking, null, new OpaquePayload(ANTHROPIC_PROVIDER, PAYLOAD_SIGNATURE, signature),
				Map.of());
	}

	private static ReasoningPart redactedReasoningPart(String data) {
		return new ReasoningPart(null, null, new OpaquePayload(ANTHROPIC_PROVIDER, PAYLOAD_REDACTED_THINKING, data),
				Map.of());
	}

	/**
	 * Keeps a content block this model does not map to a typed part as an
	 * {@link UnknownPart} holding the block JSON, with the block {@code type} as its
	 * kind.
	 * @param json the content block JSON
	 * @return the part
	 */
	private static UnknownPart unknownPart(JsonValue json) {
		Object nativeJson = convertJsonValueToNative(json);
		String kind = (nativeJson instanceof Map<?, ?> map && map.get("type") instanceof String type && !type.isEmpty())
				? type : "unknown";
		return new UnknownPart(ANTHROPIC_PROVIDER, kind, toJsonString(nativeJson), null, Map.of());
	}

	private static boolean hasOnlyTextParts(AssistantMessage assistantMessage) {
		for (MessagePart part : assistantMessage.getParts()) {
			if (!(part instanceof TextPart)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Converts an assistant message to Anthropic content blocks in part order. Reasoning
	 * and unknown parts are replayed only when they were produced by Anthropic; those
	 * from another provider and media are skipped.
	 * @param assistantMessage the message to convert
	 * @return the content blocks
	 */
	private List<ContentBlockParam> toAssistantBlockParams(AssistantMessage assistantMessage) {
		List<ContentBlockParam> blocks = new ArrayList<>();
		for (MessagePart part : assistantMessage.getParts()) {
			if (part instanceof TextPart textPart) {
				if (!textPart.text().isEmpty()) {
					blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(textPart.text()).build()));
				}
			}
			else if (part instanceof ReasoningPart reasoningPart) {
				OpaquePayload payload = reasoningPart.payload();
				if (payload != null && reasoningPart.replayableTo(ANTHROPIC_PROVIDER)) {
					blocks.add(toThinkingBlockParam(reasoningPart, payload));
				}
				else if (logger.isDebugEnabled()) {
					logger.debug("Skipping reasoning part from provider "
							+ (payload != null ? payload.provider() : "none") + " on replay to Anthropic");
				}
			}
			else if (part instanceof ToolCallPart toolCallPart) {
				ToolCall toolCall = toolCallPart.toolCall();
				blocks.add(ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
					.id(toolCall.id())
					.name(toolCall.name())
					.input(buildToolInput(toolCall.arguments()))
					.build()));
			}
			else if (part instanceof MediaPart mediaPart) {
				// Anthropic assistant turns take text, thinking and tool_use blocks only;
				// media produced by another provider is not replayed, as before parts.
				if (logger.isDebugEnabled()) {
					logger.debug("Skipping assistant media " + mediaPart.media().getName() + " on replay to Anthropic");
				}
			}
			else if (part instanceof ToolResultPart) {
				throw new IllegalArgumentException(
						"Tool results belong in a tool response message, not in an assistant message");
			}
			else if (part instanceof UnknownPart unknownPart) {
				ContentBlockParam block = toUnknownBlockParam(unknownPart);
				if (block != null) {
					blocks.add(block);
				}
			}
			else {
				throw new IllegalStateException("Unhandled message part type " + part.getClass().getName());
			}
		}
		return blocks;
	}

	/**
	 * Rebuilds a content block this model keeps as an {@link UnknownPart}, such as a
	 * server tool use or its result, from the JSON it arrived as. Anthropic needs these
	 * blocks back, for example to continue a turn paused with {@code pause_turn}, or to
	 * cite web search results through their encrypted content. The conversion is schema
	 * agnostic, so a block type this SDK version does not know still round-trips.
	 * @param unknownPart the part to convert
	 * @return the content block, or {@code null} if the part was produced by another
	 * provider or its JSON cannot be converted
	 */
	private static @Nullable ContentBlockParam toUnknownBlockParam(UnknownPart unknownPart) {
		if (!ANTHROPIC_PROVIDER.equals(unknownPart.provider())) {
			if (logger.isDebugEnabled()) {
				logger.debug("Skipping unknown part of kind " + unknownPart.kind() + " from provider "
						+ unknownPart.provider() + " on replay to Anthropic");
			}
			return null;
		}
		try {
			JsonValue json = ObjectMappers.jsonMapper().readValue(unknownPart.rawJson(), JsonValue.class);
			return json.convert(ContentBlockParam.class);
		}
		catch (Exception ex) {
			// Better a turn missing one block than a failed turn.
			if (logger.isWarnEnabled()) {
				logger.warn("Could not replay the Anthropic content block of kind " + unknownPart.kind()
						+ "; dropping it: " + ex.getMessage());
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Failure converting the Anthropic content block of kind " + unknownPart.kind(), ex);
			}
			return null;
		}
	}

	/**
	 * Warns when an assistant turn whose tool use loop is still open, with extended
	 * thinking enabled, has tool calls but no thinking block to replay. Anthropic expects
	 * that turn to start with the thinking block it returned; without it, the API does
	 * not fail but silently answers the request without extended thinking, and this
	 * warning says why.
	 * @param assistantMessage the assistant message followed only by tool responses
	 * @param blocks the content blocks the message was converted to
	 */
	private static void warnIfThinkingNotReplayed(AssistantMessage assistantMessage, List<ContentBlockParam> blocks) {
		if (!assistantMessage.hasToolCalls() || !logger.isWarnEnabled()) {
			return;
		}
		boolean replayedThinking = blocks.stream().anyMatch(block -> block.isThinking() || block.isRedactedThinking());
		if (!replayedThinking) {
			logger.warn("Extended thinking is enabled but the assistant turn awaiting tool results has no Anthropic "
					+ "thinking block to replay, so Anthropic is expected to answer without extended thinking. The "
					+ "conversation history was likely stored or rebuilt without its message parts, or produced by "
					+ "another provider or with thinking disabled.");
		}
	}

	/**
	 * Rebuilds the thinking block an Anthropic reasoning part was mapped from. The
	 * payload kind decides the block type: it says whether the payload data is a
	 * signature or redacted block data.
	 */
	private static ContentBlockParam toThinkingBlockParam(ReasoningPart reasoningPart, OpaquePayload payload) {
		if (PAYLOAD_REDACTED_THINKING.equals(payload.kind())) {
			return ContentBlockParam
				.ofRedactedThinking(RedactedThinkingBlockParam.builder().data(payload.data()).build());
		}
		// With the thinking display omitted, Anthropic returns an empty thinking text
		// that must be replayed as is.
		String thinking = Objects.requireNonNullElse(reasoningPart.text(), "");
		return ContentBlockParam
			.ofThinking(ThinkingBlockParam.builder().thinking(thinking).signature(payload.data()).build());
	}

	/**
	 * Creates chat response metadata from the Anthropic message.
	 * @param message the Anthropic message
	 * @param usage the usage information
	 * @return the chat response metadata
	 */
	private ChatResponseMetadata from(Message message, Usage usage, List<Citation> citations,
			List<AnthropicWebSearchResult> webSearchResults, RateLimit rateLimit) {
		Assert.notNull(message, "Anthropic Message must not be null");
		ChatResponseMetadata.Builder metadataBuilder = ChatResponseMetadata.builder()
			.id(message.id())
			.usage(usage)
			.model(message.model().asString())
			.rateLimit(rateLimit)
			.keyValue("anthropic-response", message);
		if (!citations.isEmpty()) {
			metadataBuilder.keyValue("citations", citations).keyValue("citationCount", citations.size());
		}
		if (!webSearchResults.isEmpty()) {
			metadataBuilder.keyValue("web-search-results", webSearchResults);
		}
		return metadataBuilder.build();
	}

	/**
	 * Converts Anthropic SDK usage to Spring AI usage.
	 * @param usage the Anthropic SDK usage
	 * @return the Spring AI usage
	 */
	private Usage getDefaultUsage(com.anthropic.models.messages.Usage usage) {
		if (usage == null) {
			return new EmptyUsage();
		}
		long inputTokens = usage.inputTokens();
		long outputTokens = usage.outputTokens();
		Long cacheRead = usage.cacheReadInputTokens().orElse(null);
		Long cacheWrite = usage.cacheCreationInputTokens().orElse(null);
		return new DefaultUsage(Integer.valueOf(Math.toIntExact(inputTokens)),
				Integer.valueOf(Math.toIntExact(outputTokens)),
				Integer.valueOf(Math.toIntExact(inputTokens + outputTokens)), usage, cacheRead, cacheWrite);
	}

	private @Nullable Citation convertTextCitation(TextCitation textCitation) {
		if (textCitation.isCharLocation()) {
			return fromCharLocation(textCitation.asCharLocation());
		}
		else if (textCitation.isPageLocation()) {
			return fromPageLocation(textCitation.asPageLocation());
		}
		else if (textCitation.isContentBlockLocation()) {
			return fromContentBlockLocation(textCitation.asContentBlockLocation());
		}
		else if (textCitation.isWebSearchResultLocation()) {
			return fromWebSearchResultLocation(textCitation.asWebSearchResultLocation());
		}
		return null;
	}

	private @Nullable Citation convertStreamingCitation(CitationsDelta.Citation citation) {
		if (citation.isCharLocation()) {
			return fromCharLocation(citation.asCharLocation());
		}
		else if (citation.isPageLocation()) {
			return fromPageLocation(citation.asPageLocation());
		}
		else if (citation.isContentBlockLocation()) {
			return fromContentBlockLocation(citation.asContentBlockLocation());
		}
		else if (citation.isWebSearchResultLocation()) {
			return fromWebSearchResultLocation(citation.asWebSearchResultLocation());
		}
		return null;
	}

	private Citation fromCharLocation(CitationCharLocation loc) {
		return Citation.ofCharLocation(loc.citedText(), (int) loc.documentIndex(), loc.documentTitle().orElse(null),
				(int) loc.startCharIndex(), (int) loc.endCharIndex());
	}

	private Citation fromPageLocation(CitationPageLocation loc) {
		return Citation.ofPageLocation(loc.citedText(), (int) loc.documentIndex(), loc.documentTitle().orElse(null),
				(int) loc.startPageNumber(), (int) loc.endPageNumber());
	}

	private Citation fromContentBlockLocation(CitationContentBlockLocation loc) {
		return Citation.ofContentBlockLocation(loc.citedText(), (int) loc.documentIndex(),
				loc.documentTitle().orElse(null), (int) loc.startBlockIndex(), (int) loc.endBlockIndex());
	}

	private Citation fromWebSearchResultLocation(CitationsWebSearchResultLocation loc) {
		return Citation.ofWebSearchResultLocation(loc.citedText(), loc.url(), loc.title().orElse(null));
	}

	/**
	 * Converts a {@link JsonValue} to a valid JSON string. Required because
	 * {@code JsonValue.toString()} produces Java Map format ({@code {key=value}}), not
	 * valid JSON. Converts to native Java objects first, then serializes with Jackson.
	 * @param jsonValue the SDK's JsonValue to convert
	 * @return a valid JSON string
	 * @throws RuntimeException if serialization fails
	 */
	private static String convertJsonValueToString(JsonValue jsonValue) {
		// Convert to native Java objects first, then serialize with Jackson
		return toJsonString(convertJsonValueToNative(jsonValue));
	}

	/**
	 * Serializes a native Java object (see {@link #convertJsonValueToNative(JsonValue)})
	 * to a JSON string.
	 * @param nativeValue the value to serialize
	 * @return a valid JSON string
	 * @throws RuntimeException if serialization fails
	 */
	private static String toJsonString(@Nullable Object nativeValue) {
		try {
			var jsonMapper = tools.jackson.databind.json.JsonMapper.builder().build();
			return jsonMapper.writeValueAsString(nativeValue);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to convert JsonValue to string", e);
		}
	}

	/**
	 * Parses a JSON string to a native Java object, keeping the string itself when it is
	 * not valid JSON so that nothing is lost.
	 * @param json the JSON string
	 * @return the parsed value, or the string when it cannot be parsed
	 */
	private static @Nullable Object parseJson(String json) {
		try {
			var jsonMapper = tools.jackson.databind.json.JsonMapper.builder().build();
			return jsonMapper.readValue(json, Object.class);
		}
		catch (Exception e) {
			if (logger.isWarnEnabled()) {
				logger.warn("Failed to parse streamed block input JSON: " + json, e);
			}
			return json;
		}
	}

	/**
	 * Converts a {@link JsonValue} to a native Java object (null, Boolean, Number,
	 * String, List, or Map) using the SDK's visitor interface.
	 * @param jsonValue the SDK's JsonValue to convert
	 * @return the equivalent native Java object, or null for JSON null
	 */
	private static @Nullable Object convertJsonValueToNative(JsonValue jsonValue) {
		return jsonValue.accept(new JsonValue.Visitor<@Nullable Object>() {
			@Override
			public @Nullable Object visitNull() {
				return null;
			}

			@Override
			public @Nullable Object visitMissing() {
				return null;
			}

			@Override
			public Object visitBoolean(boolean value) {
				return value;
			}

			@Override
			public Object visitNumber(Number value) {
				return value;
			}

			@Override
			public Object visitString(String value) {
				return value;
			}

			@Override
			public Object visitArray(List<? extends JsonValue> values) {
				return values.stream().map(v -> convertJsonValueToNative(v)).toList();
			}

			@Override
			public Object visitObject(java.util.Map<String, ? extends JsonValue> values) {
				java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
				for (java.util.Map.Entry<String, ? extends JsonValue> entry : values.entrySet()) {
					result.put(entry.getKey(), convertJsonValueToNative(entry.getValue()));
				}
				return result;
			}
		});
	}

	/**
	 * Builds a {@link ToolUseBlockParam.Input} from a JSON arguments string.
	 * <p>
	 * When rebuilding conversation history, we need to include the tool call arguments
	 * that were originally sent by the model. This method parses the JSON arguments
	 * string and creates the proper SDK input format.
	 * @param argumentsJson the JSON string containing tool call arguments
	 * @return a ToolUseBlockParam.Input with the parsed arguments
	 */
	private ToolUseBlockParam.Input buildToolInput(String argumentsJson) {
		ToolUseBlockParam.Input.Builder inputBuilder = ToolUseBlockParam.Input.builder();
		if (argumentsJson != null && !argumentsJson.isEmpty()) {
			try {
				var jsonMapper = tools.jackson.databind.json.JsonMapper.builder().build();
				java.util.Map<String, Object> arguments = jsonMapper.readValue(argumentsJson,
						new tools.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
						});
				for (java.util.Map.Entry<String, Object> entry : arguments.entrySet()) {
					inputBuilder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
				}
			}
			catch (Exception e) {
				if (logger.isWarnEnabled()) {
					logger.warn("Failed to parse tool arguments JSON: " + argumentsJson, e);
				}
			}
		}
		return inputBuilder.build();
	}

	/**
	 * Converts a Spring AI {@link ToolDefinition} to an Anthropic SDK {@link Tool}.
	 * <p>
	 * Spring AI provides the input schema as a JSON string, but the SDK expects a
	 * structured {@code Tool.InputSchema} built via the builder pattern.
	 * <p>
	 * Conversion: parses the JSON schema to a Map, extracts "properties" (added via
	 * {@code putAdditionalProperty()}), extracts "required" fields (added via
	 * {@code addRequired()}), then builds the Tool with name, description, and schema.
	 * @param toolDefinition the tool definition with name, description, and JSON schema
	 * @return the Anthropic SDK Tool
	 * @throws RuntimeException if the JSON schema cannot be parsed
	 */
	@SuppressWarnings("unchecked")
	private Tool toAnthropicTool(ToolDefinition toolDefinition) {
		try {
			// Parse the JSON schema string into a Map
			var jsonMapper = tools.jackson.databind.json.JsonMapper.builder().build();
			java.util.Map<String, Object> schemaMap = jsonMapper.readValue(toolDefinition.inputSchema(),
					new tools.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
					});

			// Build properties via putAdditionalProperty (SDK requires structured input)
			Tool.InputSchema.Properties.Builder propertiesBuilder = Tool.InputSchema.Properties.builder();
			Object propertiesObj = schemaMap.get("properties");
			if (propertiesObj instanceof java.util.Map) {
				java.util.Map<String, Object> properties = (java.util.Map<String, Object>) propertiesObj;
				for (java.util.Map.Entry<String, Object> entry : properties.entrySet()) {
					propertiesBuilder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
				}
			}

			Tool.InputSchema.Builder inputSchemaBuilder = Tool.InputSchema.builder()
				.properties(propertiesBuilder.build());

			// Add required fields if present
			Object requiredObj = schemaMap.get("required");
			if (requiredObj instanceof java.util.List) {
				java.util.List<String> required = (java.util.List<String>) requiredObj;
				for (String req : required) {
					inputSchemaBuilder.addRequired(req);
				}
			}

			return Tool.builder()
				.name(toolDefinition.name())
				.description(toolDefinition.description())
				.inputSchema(inputSchemaBuilder.build())
				.build();
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to parse tool input schema: " + toolDefinition.inputSchema(), e);
		}
	}

	/**
	 * Converts a Spring AI {@link AnthropicWebSearchTool} to the Anthropic SDK's
	 * {@link WebSearchTool20260209}.
	 * @param webSearchTool the web search configuration
	 * @return the SDK web search tool
	 */
	private WebSearchTool20260209 toSdkWebSearchTool(AnthropicWebSearchTool webSearchTool) {
		WebSearchTool20260209.Builder sdkBuilder = WebSearchTool20260209.builder();

		if (webSearchTool.getAllowedDomains() != null) {
			sdkBuilder.allowedDomains(webSearchTool.getAllowedDomains());
		}
		if (webSearchTool.getBlockedDomains() != null) {
			sdkBuilder.blockedDomains(webSearchTool.getBlockedDomains());
		}
		if (webSearchTool.getMaxUses() != null) {
			sdkBuilder.maxUses(webSearchTool.getMaxUses());
		}
		if (webSearchTool.getUserLocation() != null) {
			AnthropicWebSearchTool.UserLocation loc = webSearchTool.getUserLocation();
			UserLocation.Builder locBuilder = UserLocation.builder();
			if (loc.city() != null) {
				locBuilder.city(loc.city());
			}
			if (loc.country() != null) {
				locBuilder.country(loc.country());
			}
			if (loc.region() != null) {
				locBuilder.region(loc.region());
			}
			if (loc.timezone() != null) {
				locBuilder.timezone(loc.timezone());
			}
			sdkBuilder.userLocation(locBuilder.build());
		}

		return sdkBuilder.build();
	}

	/**
	 * Converts a Spring AI {@link Media} object to an Anthropic SDK
	 * {@link ContentBlockParam}. Supports images (PNG, JPEG, GIF, WebP) and PDF
	 * documents. Data can be provided as byte[] (base64 encoded) or HTTPS URL string.
	 * @param media the media object containing MIME type and data
	 * @return the appropriate ContentBlockParam (ImageBlockParam or DocumentBlockParam)
	 * @throws IllegalArgumentException if the media type is unsupported
	 */
	private ContentBlockParam getContentBlockParamByMedia(Media media) {
		MimeType mimeType = media.getMimeType();
		String data = fromMediaData(media.getData());

		if (isImageMedia(mimeType)) {
			return createImageBlockParam(mimeType, data);
		}
		else if (isPdfMedia(mimeType)) {
			return createDocumentBlockParam(data);
		}
		throw new IllegalArgumentException("Unsupported media type: " + mimeType
				+ ". Supported types are: images (image/*) and PDF documents (application/pdf)");
	}

	/**
	 * Checks if the given MIME type represents an image.
	 * @param mimeType the MIME type to check
	 * @return true if the type is image/*
	 */
	private boolean isImageMedia(MimeType mimeType) {
		return "image".equals(mimeType.getType());
	}

	/**
	 * Checks if the given MIME type represents a PDF document.
	 * @param mimeType the MIME type to check
	 * @return true if the type is application/pdf
	 */
	private boolean isPdfMedia(MimeType mimeType) {
		return "application".equals(mimeType.getType()) && "pdf".equals(mimeType.getSubtype());
	}

	/**
	 * Extracts media data as a string. Converts byte[] to base64, passes through URL
	 * strings.
	 * @param mediaData the media data (byte[] or String)
	 * @return base64-encoded string or URL string
	 * @throws IllegalArgumentException if data type is unsupported
	 */
	private String fromMediaData(Object mediaData) {
		if (mediaData instanceof byte[] bytes) {
			return Base64.getEncoder().encodeToString(bytes);
		}
		else if (mediaData instanceof String text) {
			return text;
		}
		throw new IllegalArgumentException("Unsupported media data type: " + mediaData.getClass().getSimpleName()
				+ ". Expected byte[] or String.");
	}

	/**
	 * Creates an {@link ImageBlockParam} from the given MIME type and data.
	 * @param mimeType the image MIME type (image/png, image/jpeg, etc.)
	 * @param data base64-encoded image data or HTTPS URL
	 * @return the ImageBlockParam wrapped in ContentBlockParam
	 */
	private ContentBlockParam createImageBlockParam(MimeType mimeType, String data) {
		ImageBlockParam.Source source;
		if (data.startsWith("https://")) {
			source = ImageBlockParam.Source.ofUrl(UrlImageSource.builder().url(data).build());
		}
		else {
			source = ImageBlockParam.Source
				.ofBase64(Base64ImageSource.builder().data(data).mediaType(toSdkImageMediaType(mimeType)).build());
		}
		return ContentBlockParam.ofImage(ImageBlockParam.builder().source(source).build());
	}

	/**
	 * Creates a {@link DocumentBlockParam} for PDF documents.
	 * @param data base64-encoded PDF data or HTTPS URL
	 * @return the DocumentBlockParam wrapped in ContentBlockParam
	 */
	private ContentBlockParam createDocumentBlockParam(String data) {
		DocumentBlockParam.Source source;
		if (data.startsWith("https://")) {
			source = DocumentBlockParam.Source.ofUrl(UrlPdfSource.builder().url(data).build());
		}
		else {
			source = DocumentBlockParam.Source.ofBase64(Base64PdfSource.builder().data(data).build());
		}
		return ContentBlockParam.ofDocument(DocumentBlockParam.builder().source(source).build());
	}

	/**
	 * Converts a Spring MIME type to the SDK's {@link Base64ImageSource.MediaType}.
	 * @param mimeType the Spring MIME type
	 * @return the SDK media type enum value
	 * @throws IllegalArgumentException if the image type is unsupported
	 */
	private Base64ImageSource.MediaType toSdkImageMediaType(MimeType mimeType) {
		String subtype = mimeType.getSubtype();
		return switch (subtype) {
			case "png" -> Base64ImageSource.MediaType.IMAGE_PNG;
			case "jpeg", "jpg" -> Base64ImageSource.MediaType.IMAGE_JPEG;
			case "gif" -> Base64ImageSource.MediaType.IMAGE_GIF;
			case "webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
			default -> throw new IllegalArgumentException("Unsupported image type: " + mimeType
					+ ". Supported types: image/png, image/jpeg, image/gif, image/webp");
		};
	}

	/**
	 * Applies {@code disableParallelToolUse} to an existing {@link ToolChoice} by
	 * rebuilding the appropriate subtype with the flag set to {@code true}.
	 */
	private ToolChoice applyDisableParallelToolUse(ToolChoice toolChoice) {
		if (toolChoice.isAuto()) {
			return ToolChoice.ofAuto(toolChoice.asAuto().toBuilder().disableParallelToolUse(true).build());
		}
		else if (toolChoice.isAny()) {
			return ToolChoice.ofAny(toolChoice.asAny().toBuilder().disableParallelToolUse(true).build());
		}
		else if (toolChoice.isTool()) {
			return ToolChoice.ofTool(toolChoice.asTool().toBuilder().disableParallelToolUse(true).build());
		}
		return toolChoice;
	}

	/**
	 * Use the provided convention for reporting observation data.
	 * @param observationConvention the provided convention
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
	 * Holds state accumulated during streaming: message metadata (ID, model, input
	 * tokens), in-flight tool use blocks and blocks not modeled as a part keyed by
	 * content block index, and metadata accumulated for the final response. Text and
	 * reasoning are not accumulated here; they are streamed as indexed partial parts and
	 * rebuilt by {@link MessageAggregator}.
	 */
	private static class StreamingState {

		private final AtomicReference<String> messageId = new AtomicReference<>();

		private final AtomicReference<String> model = new AtomicReference<>();

		private final AtomicReference<Long> inputTokens = new AtomicReference<>(0L);

		// Tool use blocks being streamed, keyed by content block index
		private final Map<Integer, InFlightToolUse> inFlightToolUses = new LinkedHashMap<>();

		// Completed tool calls keyed by content block index
		private final Map<Integer, ToolCall> completedToolCalls = new TreeMap<>();

		// Blocks not modeled as a part (server tool use and results, container upload),
		// keyed by content block index
		private final Map<Integer, InFlightUnknownBlock> inFlightUnknownBlocks = new HashMap<>();

		private final List<Citation> accumulatedCitations = new ArrayList<>();

		private final List<AnthropicWebSearchResult> accumulatedWebSearchResults = new ArrayList<>();

		private final AtomicReference<RateLimit> rateLimit = new AtomicReference<>(new EmptyRateLimit());

		void setMessageInfo(String id, String modelName, long tokens) {
			this.messageId.set(id);
			this.model.set(modelName);
			this.inputTokens.set(tokens);
		}

		String getMessageId() {
			return Objects.requireNonNullElse(this.messageId.get(), "");
		}

		String getModel() {
			return Objects.requireNonNullElse(this.model.get(), "");
		}

		long getInputTokens() {
			return this.inputTokens.get();
		}

		void setRateLimit(RateLimit rateLimit) {
			this.rateLimit.set(rateLimit);
		}

		RateLimit getRateLimit() {
			return this.rateLimit.get();
		}

		/**
		 * Starts tracking the tool use block at the given content block index.
		 * @param index the content block index
		 * @param toolId the tool call ID
		 * @param toolName the tool name
		 */
		void startToolUse(int index, String toolId, String toolName) {
			this.inFlightToolUses.put(index, new InFlightToolUse(toolId, toolName));
		}

		/**
		 * Starts tracking a content block not modeled as a part at the given index.
		 * @param index the content block index
		 * @param json the block JSON from the start event
		 */
		void startUnknownBlock(int index, JsonValue json) {
			this.inFlightUnknownBlocks.put(index, new InFlightUnknownBlock(json));
		}

		/**
		 * Appends partial JSON to the input of the tool use or server tool use block at
		 * the given index.
		 * @param index the content block index
		 * @param partialJson the partial JSON string
		 */
		void appendInputJson(int index, String partialJson) {
			InFlightToolUse toolUse = this.inFlightToolUses.get(index);
			if (toolUse != null) {
				toolUse.arguments.append(partialJson);
				return;
			}
			InFlightUnknownBlock unknownBlock = this.inFlightUnknownBlocks.get(index);
			if (unknownBlock != null) {
				unknownBlock.input.append(partialJson);
			}
		}

		/**
		 * Finalizes the block not modeled as a part at the given index, if one is being
		 * tracked, with the input streamed as deltas in place of the empty input of the
		 * start event.
		 * @param index the content block index
		 * @return the part holding the block, or {@code null} if none is tracked there
		 */
		@Nullable UnknownPart finishUnknownBlock(int index) {
			InFlightUnknownBlock unknownBlock = this.inFlightUnknownBlocks.remove(index);
			if (unknownBlock == null) {
				return null;
			}
			JsonValue json = unknownBlock.json;
			if (!unknownBlock.input.isEmpty() && convertJsonValueToNative(json) instanceof Map<?, ?> fields) {
				Map<Object, @Nullable Object> completed = new LinkedHashMap<>(fields);
				completed.put("input", parseJson(unknownBlock.input.toString()));
				json = JsonValue.from(completed);
			}
			return unknownPart(json);
		}

		/**
		 * Finalizes the tool use block at the given index, if one is being tracked, and
		 * records it as a completed tool call.
		 * @param index the content block index
		 */
		void finishToolUse(int index) {
			InFlightToolUse toolUse = this.inFlightToolUses.remove(index);
			if (toolUse != null && !toolUse.id.isEmpty() && !toolUse.name.isEmpty()) {
				this.completedToolCalls.put(index,
						new ToolCall(toolUse.id, "function", toolUse.name, toolUse.arguments.toString()));
			}
		}

		/**
		 * Returns the completed tool calls keyed by content block index, in index order.
		 */
		Map<Integer, ToolCall> getCompletedToolCalls() {
			return new TreeMap<>(this.completedToolCalls);
		}

		void addCitation(Citation citation) {
			this.accumulatedCitations.add(citation);
		}

		List<Citation> getCitations() {
			return new ArrayList<>(this.accumulatedCitations);
		}

		void addWebSearchResult(AnthropicWebSearchResult result) {
			this.accumulatedWebSearchResults.add(result);
		}

		List<AnthropicWebSearchResult> getWebSearchResults() {
			return new ArrayList<>(this.accumulatedWebSearchResults);
		}

		private static final class InFlightToolUse {

			private final String id;

			private final String name;

			private final StringBuilder arguments = new StringBuilder();

			InFlightToolUse(String id, String name) {
				this.id = id;
				this.name = name;
			}

		}

		private static final class InFlightUnknownBlock {

			private final JsonValue json;

			private final StringBuilder input = new StringBuilder();

			InFlightUnknownBlock(JsonValue json) {
				this.json = json;
			}

		}

	}

	/**
	 * Builder for creating {@link AnthropicChatModel} instances.
	 */
	public static final class Builder {

		private @Nullable AnthropicClient anthropicClient;

		private @Nullable AnthropicClientAsync anthropicClientAsync;

		private @Nullable AnthropicChatOptions options;

		private @Nullable ToolCallingManager toolCallingManager;

		private @Nullable ObservationRegistry observationRegistry;

		private @Nullable MeterRegistry meterRegistry;

		private @Nullable ExecutorService dispatcherExecutor;

		private List<AnthropicHttpClientBuilderCustomizer> httpClientCustomizers = new ArrayList<>();

		private Builder() {
		}

		/**
		 * Sets the synchronous Anthropic client.
		 * @param anthropicClient the synchronous client
		 * @return this builder
		 */
		public Builder anthropicClient(AnthropicClient anthropicClient) {
			this.anthropicClient = anthropicClient;
			return this;
		}

		/**
		 * Sets the asynchronous Anthropic client.
		 * @param anthropicClientAsync the asynchronous client
		 * @return this builder
		 */
		public Builder anthropicClientAsync(AnthropicClientAsync anthropicClientAsync) {
			this.anthropicClientAsync = anthropicClientAsync;
			return this;
		}

		/**
		 * Sets the chat options.
		 * @param options the chat options
		 * @return this builder
		 */
		public Builder options(AnthropicChatOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * Sets the tool calling manager used for internal tool execution.
		 * @param toolCallingManager the tool calling manager
		 * @return this builder
		 * @deprecated since 2.0.0 for removal in 3.0.0 — internal tool execution in
		 * {@link AnthropicChatModel} is superseded by {@code ToolCallingAdvisor} used via
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

		/**
		 * Sets the meter registry used to bind OkHttp connection-pool gauges (active/idle
		 * connections). Optional; when omitted, no pool gauges are registered.
		 * Auto-configuration wires the application's {@link MeterRegistry} bean here
		 * automatically.
		 * @param meterRegistry the meter registry
		 * @return this builder
		 * @since 2.0.0
		 */
		public Builder meterRegistry(@Nullable MeterRegistry meterRegistry) {
			this.meterRegistry = meterRegistry;
			return this;
		}

		/**
		 * Sets the executor used by the underlying OkHttp dispatcher for both the sync
		 * and async clients. The caller owns the executor's lifecycle — Spring AI will
		 * not shut it down. Typical use: pass
		 * {@code Executors.newVirtualThreadPerTaskExecutor()} on Java 21+ to back HTTP
		 * dispatch with virtual threads. When omitted, an internal platform-thread
		 * executor is created and managed by the HTTP client.
		 * @param dispatcherExecutor the dispatcher executor; null restores the default
		 * @return this builder
		 * @since 2.0.0
		 */
		public Builder dispatcherExecutor(@Nullable ExecutorService dispatcherExecutor) {
			this.dispatcherExecutor = dispatcherExecutor;
			return this;
		}

		/**
		 * Registers an {@link AnthropicHttpClientBuilderCustomizer} that mutates the
		 * underlying OkHttp client builder before the Anthropic clients are constructed.
		 * Use this to attach OkHttp interceptors (e.g. OAuth2 bearer-token injection),
		 * swap the dispatcher executor, or tweak any other OkHttp setting. Customizers
		 * are applied in the order they are registered, after Spring AI's own defaults,
		 * so user code wins.
		 * @param customizer the customizer to add
		 * @return this builder
		 * @since 2.0.0
		 */
		public Builder httpClientBuilderCustomizer(AnthropicHttpClientBuilderCustomizer customizer) {
			Assert.notNull(customizer, "customizer cannot be null");
			this.httpClientCustomizers.add(customizer);
			return this;
		}

		/**
		 * Sets the full list of {@link AnthropicHttpClientBuilderCustomizer customizers}
		 * to apply, replacing any customizers registered earlier on this builder. The
		 * order of the list is preserved when invoking the customizers.
		 * @param customizers the list of customizers
		 * @return this builder
		 * @since 2.0.0
		 */
		public Builder httpClientBuilderCustomizers(List<AnthropicHttpClientBuilderCustomizer> customizers) {
			Assert.notNull(customizers, "customizers cannot be null");
			this.httpClientCustomizers = new ArrayList<>(customizers);
			return this;
		}

		/**
		 * Builds a new {@link AnthropicChatModel} instance.
		 * @return the configured chat model
		 */
		public AnthropicChatModel build() {
			if (!this.httpClientCustomizers.isEmpty() && this.anthropicClient != null) {
				throw new IllegalArgumentException(
						"httpClientBuilderCustomizers cannot be combined with a pre-built anthropicClient "
								+ "because the HTTP layer is already constructed");
			}
			if (!this.httpClientCustomizers.isEmpty() && this.anthropicClientAsync != null) {
				throw new IllegalArgumentException(
						"httpClientBuilderCustomizers cannot be combined with a pre-built anthropicClientAsync "
								+ "because the HTTP layer is already constructed");
			}
			return new AnthropicChatModel(this.anthropicClient, this.anthropicClientAsync, this.options,
					this.toolCallingManager, this.observationRegistry, this.meterRegistry, this.dispatcherExecutor,
					this.httpClientCustomizers);
		}

	}

}
