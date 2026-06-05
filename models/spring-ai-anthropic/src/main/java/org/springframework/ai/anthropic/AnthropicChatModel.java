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

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.core.JsonValue;
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
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.TextCitation;
import com.anthropic.models.messages.ThinkingBlock;
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

import org.springframework.ai.anthropic.metadata.AnthropicRateLimit;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
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
			@Nullable MeterRegistry meterRegistry, @Nullable ExecutorService dispatcherExecutor) {

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
						this.dispatcherExecutor));

		this.anthropicClientAsync = Objects.requireNonNullElseGet(anthropicClientAsync,
				() -> AnthropicSetup.setupAsyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getTimeout(), this.options.getMaxRetries(), this.options.getProxy(),
						this.options.getCustomHeaders(), this.observationRegistry, this.meterRegistry,
						this.dispatcherExecutor));

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
	public Flux<ChatResponse> internalStream(Prompt prompt, @Nullable ChatResponse previousChatResponse) {

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
				.fromFuture(() -> this.anthropicClientAsync.messages().withRawResponse().createStreaming(request))
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
		// Initializes tool call tracking or emits redacted thinking blocks.
		if (event.contentBlockStart().isPresent()) {
			var startEvent = event.contentBlockStart().get();
			var contentBlock = startEvent.contentBlock();
			if (contentBlock.toolUse().isPresent()) {
				var toolUseBlock = contentBlock.asToolUse();
				streamingState.startToolUse(toolUseBlock.id(), toolUseBlock.name());
			}
			else if (contentBlock.isRedactedThinking()) {
				// Emit redacted thinking block immediately
				RedactedThinkingBlock redactedBlock = contentBlock.asRedactedThinking();
				Map<String, Object> redactedProperties = new HashMap<>();
				redactedProperties.put("data", redactedBlock.data());
				AssistantMessage assistantMessage = AssistantMessage.builder().properties(redactedProperties).build();
				return new ChatResponse(List.of(new Generation(assistantMessage)));
			}
			else if (contentBlock.isWebSearchToolResult()) {
				// Accumulate web search results for final response metadata
				WebSearchToolResultBlock wsBlock = contentBlock.asWebSearchToolResult();
				if (wsBlock.content().isResultBlocks()) {
					for (WebSearchResultBlock r : wsBlock.content().asResultBlocks()) {
						streamingState.addWebSearchResult(
								new AnthropicWebSearchResult(r.title(), r.url(), r.pageAge().orElse(null)));
					}
				}
			}
			return null;
		}

		// -- Event: content_block_delta --
		// Handles incremental text, tool argument JSON, thinking, and citation deltas.
		if (event.contentBlockDelta().isPresent()) {
			var deltaEvent = event.contentBlockDelta().get();
			var delta = deltaEvent.delta();

			// Text chunk — emit immediately
			if (delta.text().isPresent()) {
				String text = delta.asText().text();
				AssistantMessage assistantMessage = AssistantMessage.builder().content(text).build();
				Generation generation = new Generation(assistantMessage);
				return new ChatResponse(List.of(generation));
			}

			// Tool argument JSON chunk — accumulate for later
			if (delta.inputJson().isPresent()) {
				String partialJson = delta.asInputJson().partialJson();
				streamingState.appendToolJson(partialJson);
				return null;
			}

			// Thinking chunk — emit with thinking metadata
			if (delta.isThinking()) {
				String thinkingText = delta.asThinking().thinking();
				Map<String, Object> thinkingProperties = new HashMap<>();
				thinkingProperties.put("thinking", Boolean.TRUE);
				AssistantMessage assistantMessage = AssistantMessage.builder()
					.content(thinkingText)
					.properties(thinkingProperties)
					.build();
				return new ChatResponse(List.of(new Generation(assistantMessage)));
			}

			// Thinking signature — emit with signature metadata
			if (delta.isSignature()) {
				String signature = delta.asSignature().signature();
				Map<String, Object> signatureProperties = new HashMap<>();
				signatureProperties.put("signature", signature);
				AssistantMessage assistantMessage = AssistantMessage.builder().properties(signatureProperties).build();
				return new ChatResponse(List.of(new Generation(assistantMessage)));
			}

			// Citation — accumulate for final response metadata
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
		// Finalizes the current tool call if one was being tracked.
		if (event.contentBlockStop().isPresent()) {
			if (streamingState.isTrackingToolUse()) {
				streamingState.finishToolUse();
			}
			return null;
		}

		// -- Event: message_delta --
		// Final event with stop_reason and usage. Triggers tool execution if needed.
		Optional<ChatResponse> messageDeltaResponse = event.messageDelta().map(deltaEvent -> {
			String stopReason = deltaEvent.delta().stopReason().map(r -> r.toString()).orElse("");
			ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().finishReason(stopReason).build();

			// Build assistant message with any accumulated tool calls
			AssistantMessage.Builder assistantMessageBuilder = AssistantMessage.builder().content("");
			List<ToolCall> toolCalls = streamingState.getCompletedToolCalls();
			if (!toolCalls.isEmpty()) {
				assistantMessageBuilder.toolCalls(toolCalls);
			}

			Generation generation = new Generation(assistantMessageBuilder.build(), metadata);

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
	public ChatResponse internalCall(Prompt prompt, @Nullable ChatResponse previousChatResponse) {

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
					.create(request);
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

		ChatOptions options = prompt.getOptions();
		AnthropicChatOptions requestOptions = options instanceof AnthropicChatOptions anthropicOptions
				? anthropicOptions : AnthropicChatOptions.builder().build();

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

		// Pre-compute last user message index for CONVERSATION_HISTORY strategy
		int lastUserIndex = -1;
		if (cacheResolver.isCachingEnabled()) {
			for (int i = nonSystemMessages.size() - 1; i >= 0; i--) {
				if (nonSystemMessages.get(i).getMessageType() == MessageType.USER) {
					lastUserIndex = i;
					break;
				}
			}
		}

		// Pre-compute last tool result message index for tool result caching. A
		// breakpoint on the final tool result of the request caches the prior tool
		// outputs so they are read from cache on subsequent tool-calling rounds.
		int lastToolIndex = -1;
		if (cacheResolver.isCachingEnabled() && requestOptions.getCacheOptions().isCacheToolResults()) {
			for (int i = nonSystemMessages.size() - 1; i >= 0; i--) {
				if (nonSystemMessages.get(i).getMessageType() == MessageType.TOOL) {
					lastToolIndex = i;
					break;
				}
			}
		}

		// Process non-system messages
		for (int i = 0; i < nonSystemMessages.size(); i++) {
			org.springframework.ai.chat.messages.Message message = nonSystemMessages.get(i);

			if (message.getMessageType() == MessageType.USER) {
				UserMessage userMessage = (UserMessage) message;
				boolean hasCitationDocs = !CollectionUtils.isEmpty(citationDocuments);
				boolean hasMedia = !CollectionUtils.isEmpty(userMessage.getMedia());
				boolean isLastUserMessage = (i == lastUserIndex);
				boolean applyCacheToUser = isLastUserMessage && cacheResolver.isCachingEnabled();

				// Compute cache control for last user message
				CacheControlEphemeral userCacheControl = null;
				if (applyCacheToUser) {
					String combinedText = combineEligibleMessagesText(nonSystemMessages, lastUserIndex);
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

					String text = userMessage.getText();
					if (text != null && !text.isEmpty()) {
						TextBlockParam.Builder textBlockBuilder = TextBlockParam.builder().text(text);
						if (userCacheControl != null) {
							textBlockBuilder.cacheControl(userCacheControl);
							cacheResolver.useCacheBlock();
						}
						contentBlocks.add(ContentBlockParam.ofText(textBlockBuilder.build()));
					}

					if (hasMedia) {
						for (Media media : userMessage.getMedia()) {
							contentBlocks.add(getContentBlockParamByMedia(media));
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
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					List<ContentBlockParam> toolUseBlocks = assistantMessage.getToolCalls()
						.stream()
						.map(toolCall -> ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
							.id(toolCall.id())
							.name(toolCall.name())
							.input(buildToolInput(toolCall.arguments()))
							.build()))
						.toList();
					builder.addAssistantMessageOfBlockParams(toolUseBlocks);
				}
				else {
					String text = message.getText();
					if (text != null) {
						builder.addAssistantMessage(text);
					}
				}
			}
			else if (message.getMessageType() == MessageType.TOOL) {
				ToolResponseMessage toolResponseMessage = (ToolResponseMessage) message;
				List<ToolResponseMessage.ToolResponse> responses = toolResponseMessage.getResponses();

				// Compute cache control for the last tool result message of the request.
				// The breakpoint is placed on its final block, caching everything before
				// it (tools + system + prior messages + earlier tool results).
				CacheControlEphemeral toolCacheControl = null;
				if (i == lastToolIndex) {
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
	 * Builds generations from the Anthropic message response. Extracts text, tool calls,
	 * thinking content, and citations from the response content blocks.
	 * @param message the Anthropic message response
	 * @param citationAccumulator collects citations found in text blocks
	 * @param webSearchAccumulator collects web search results found in response
	 * @return list of generations with text, tool calls, and/or thinking content
	 */
	private List<Generation> buildGenerations(Message message, List<Citation> citationAccumulator,
			List<AnthropicWebSearchResult> webSearchAccumulator) {
		List<Generation> generations = new ArrayList<>();

		String finishReason = message.stopReason().map(r -> r.toString()).orElse("");
		ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();

		// Collect text and tool calls from content blocks
		StringBuilder textContent = new StringBuilder();
		List<ToolCall> toolCalls = new ArrayList<>();

		for (ContentBlock block : message.content()) {
			if (block.isText()) {
				TextBlock textBlock = block.asText();
				textContent.append(textBlock.text());

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
				toolCalls.add(new ToolCall(toolUseBlock.id(), "function", toolUseBlock.name(), arguments));
			}
			else if (block.isThinking()) {
				// ThinkingBlock: stored as a separate Generation with the thinking
				// text as content and signature in metadata properties.
				ThinkingBlock thinkingBlock = block.asThinking();
				Map<String, Object> thinkingProperties = new HashMap<>();
				thinkingProperties.put("signature", thinkingBlock.signature());
				generations.add(new Generation(AssistantMessage.builder()
					.content(thinkingBlock.thinking())
					.properties(thinkingProperties)
					.build(), generationMetadata));
			}
			else if (block.isRedactedThinking()) {
				// RedactedThinkingBlock: safety-redacted reasoning with a data marker.
				RedactedThinkingBlock redactedBlock = block.asRedactedThinking();
				Map<String, Object> redactedProperties = new HashMap<>();
				redactedProperties.put("data", redactedBlock.data());
				generations.add(new Generation(AssistantMessage.builder().properties(redactedProperties).build(),
						generationMetadata));
			}
			else if (block.isWebSearchToolResult()) {
				WebSearchToolResultBlock wsBlock = block.asWebSearchToolResult();
				if (wsBlock.content().isResultBlocks()) {
					for (WebSearchResultBlock r : wsBlock.content().asResultBlocks()) {
						webSearchAccumulator
							.add(new AnthropicWebSearchResult(r.title(), r.url(), r.pageAge().orElse(null)));
					}
				}
			}
			else if (block.isContainerUpload() || block.isServerToolUse() || block.isBashCodeExecutionToolResult()
					|| block.isTextEditorCodeExecutionToolResult() || block.isCodeExecutionToolResult()) {
				if (logger.isWarnEnabled()) {
					logger.warn("Unsupported content block type: " + block);
				}
			}
		}

		AssistantMessage.Builder assistantMessageBuilder = AssistantMessage.builder().content(textContent.toString());

		if (!toolCalls.isEmpty()) {
			assistantMessageBuilder.toolCalls(toolCalls);
		}

		generations.add(new Generation(assistantMessageBuilder.build(), generationMetadata));

		return generations;
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
	private String convertJsonValueToString(JsonValue jsonValue) {
		try {
			var jsonMapper = tools.jackson.databind.json.JsonMapper.builder().build();
			// Convert to native Java objects first, then serialize with Jackson
			Object nativeValue = convertJsonValueToNative(jsonValue);
			return jsonMapper.writeValueAsString(nativeValue);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to convert JsonValue to string", e);
		}
	}

	/**
	 * Converts a {@link JsonValue} to a native Java object (null, Boolean, Number,
	 * String, List, or Map) using the SDK's visitor interface.
	 * @param jsonValue the SDK's JsonValue to convert
	 * @return the equivalent native Java object, or null for JSON null
	 */
	private @Nullable Object convertJsonValueToNative(JsonValue jsonValue) {
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
	 * Holds state accumulated during streaming for building complete responses. This
	 * includes message metadata (ID, model, input tokens) and tool call accumulation
	 * state for streaming tool calling support.
	 */
	private static class StreamingState {

		private final AtomicReference<String> messageId = new AtomicReference<>();

		private final AtomicReference<String> model = new AtomicReference<>();

		private final AtomicReference<Long> inputTokens = new AtomicReference<>(0L);

		// Tool calling state - tracks the current tool being streamed
		private final AtomicReference<String> currentToolId = new AtomicReference<>("");

		private final AtomicReference<String> currentToolName = new AtomicReference<>("");

		private final StringBuilder currentToolJsonAccumulator = new StringBuilder();

		private final List<ToolCall> completedToolCalls = new ArrayList<>();

		private final List<Citation> accumulatedCitations = new ArrayList<>();

		private final List<AnthropicWebSearchResult> accumulatedWebSearchResults = new ArrayList<>();

		private final AtomicReference<RateLimit> rateLimit = new AtomicReference<>(new EmptyRateLimit());

		void setMessageInfo(String id, String modelName, long tokens) {
			this.messageId.set(id);
			this.model.set(modelName);
			this.inputTokens.set(tokens);
		}

		String getMessageId() {
			return this.messageId.get();
		}

		String getModel() {
			return this.model.get();
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
		 * Starts tracking a new tool use block.
		 * @param toolId the tool call ID
		 * @param toolName the tool name
		 */
		void startToolUse(String toolId, String toolName) {
			this.currentToolId.set(toolId);
			this.currentToolName.set(toolName);
			this.currentToolJsonAccumulator.setLength(0);
		}

		/**
		 * Appends partial JSON to the current tool's input accumulator.
		 * @param partialJson the partial JSON string
		 */
		void appendToolJson(String partialJson) {
			this.currentToolJsonAccumulator.append(partialJson);
		}

		/**
		 * Finalizes the current tool use block and adds it to completed tool calls.
		 */
		void finishToolUse() {
			String id = this.currentToolId.get();
			String name = this.currentToolName.get();
			if (!id.isEmpty() && !name.isEmpty()) {
				String arguments = this.currentToolJsonAccumulator.toString();
				this.completedToolCalls.add(new ToolCall(id, "function", name, arguments));
			}
			// Reset current tool state (use empty string as "not tracking" sentinel)
			this.currentToolId.set("");
			this.currentToolName.set("");
			this.currentToolJsonAccumulator.setLength(0);
		}

		/**
		 * Returns true if currently tracking a tool use block.
		 */
		boolean isTrackingToolUse() {
			return !this.currentToolId.get().isEmpty();
		}

		/**
		 * Returns the list of completed tool calls accumulated during streaming.
		 */
		List<ToolCall> getCompletedToolCalls() {
			return new ArrayList<>(this.completedToolCalls);
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
		 * Builds a new {@link AnthropicChatModel} instance.
		 * @return the configured chat model
		 */
		public AnthropicChatModel build() {
			return new AnthropicChatModel(this.anthropicClient, this.anthropicClientAsync, this.options,
					this.toolCallingManager, this.observationRegistry, this.meterRegistry, this.dispatcherExecutor);
		}

	}

}
