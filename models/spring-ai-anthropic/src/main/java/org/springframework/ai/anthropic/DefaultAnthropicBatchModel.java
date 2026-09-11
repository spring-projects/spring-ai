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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.batches.BatchCreateParams;
import com.anthropic.models.messages.batches.MessageBatchIndividualResponse;
import com.anthropic.models.messages.batches.MessageBatchResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.anthropic.http.okhttp.AnthropicHttpClientBuilderCustomizer;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Default {@link AnthropicBatchModel} implementation, built on the official
 * <a href="https://github.com/anthropics/anthropic-sdk-java">Anthropic Java SDK</a>.
 *
 * <p>
 * <b>Requests are mapped exactly like realtime ones.</b> Each
 * {@link AnthropicBatchRequest} carries a {@link Prompt} that goes through the very same
 * {@code Prompt} to {@code MessageCreateParams} conversion used by
 * {@link AnthropicChatModel#call(Prompt)}, so system messages, conversation history,
 * images and PDF documents, prompt caching, thinking, structured output and tool
 * definitions behave the same on both paths. Likewise, a succeeded result is converted
 * into a {@link ChatResponse} with the same generations, metadata keys and usage as a
 * realtime call.
 *
 * <p>
 * <b>Tool calls are not executed.</b> Tool definitions are sent, but a batch response
 * containing {@code tool_use} blocks is returned as-is: there is no interactive
 * tool-execution loop, because a batch entry cannot be continued mid-flight. Surface the
 * tool calls from {@link org.springframework.ai.chat.model.Generation#getOutput() the
 * assistant message} and submit a follow-up batch if you need a second turn.
 *
 * <p>
 * <b>Results are unordered.</b> Correlate them through
 * {@link AnthropicBatchResult#customId()}, never by position.
 *
 * <p>
 * Typical usage:
 *
 * <pre>{@code
 * AnthropicBatch batch = batchModel.submit(List.of(
 *         AnthropicBatchRequest.of("invoice-1", "Summarize invoice 1"),
 *         AnthropicBatchRequest.of("invoice-2", "Summarize invoice 2")));
 *
 * // later, on the application's own schedule
 * if (batchModel.retrieve(batch.id()).isEnded()) {
 *     batchModel.results(batch.id())
 *         .doOnNext(result -> store(result.customId(), result))
 *         .blockLast();
 * }
 * }</pre>
 *
 * @author Ricken Bazolo
 * @since 2.0.0
 * @see AnthropicBatchRequest
 * @see AnthropicBatchResult
 * @see AnthropicChatModel
 */
public final class DefaultAnthropicBatchModel implements AnthropicBatchModel {

	private static final Log logger = LogFactory.getLog(DefaultAnthropicBatchModel.class);

	private static final AnthropicBatchObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultAnthropicBatchObservationConvention();

	private static final ToolCallingManager DEFAULT_TOOL_CALLING_MANAGER = ToolCallingManager.builder().build();

	private final AnthropicClient anthropicClient;

	private final AnthropicChatOptions options;

	private final ToolCallingManager toolCallingManager;

	private final ObservationRegistry observationRegistry;

	private AnthropicBatchObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	private DefaultAnthropicBatchModel(@Nullable AnthropicClient anthropicClient,
			@Nullable AnthropicChatOptions options, @Nullable ToolCallingManager toolCallingManager,
			@Nullable ObservationRegistry observationRegistry, @Nullable MeterRegistry meterRegistry,
			@Nullable ExecutorService dispatcherExecutor,
			List<AnthropicHttpClientBuilderCustomizer> httpClientCustomizers) {

		this.options = options != null ? options : AnthropicChatOptions.builder().build();
		this.observationRegistry = Objects.requireNonNullElse(observationRegistry, ObservationRegistry.NOOP);
		this.toolCallingManager = Objects.requireNonNullElse(toolCallingManager, DEFAULT_TOOL_CALLING_MANAGER);

		this.anthropicClient = Objects.requireNonNullElseGet(anthropicClient,
				() -> AnthropicSetup.setupSyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getTimeout(), this.options.getMaxRetries(), this.options.getProxy(),
						this.options.getCustomHeaders(), this.observationRegistry, meterRegistry, dispatcherExecutor,
						httpClientCustomizers));
	}

	/**
	 * Creates a new builder for {@link AnthropicBatchModel}.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Gets the default options applied to batch entries whose prompt carries none.
	 * @return the default options
	 */
	public AnthropicChatOptions getOptions() {
		return this.options;
	}

	/**
	 * Returns the underlying synchronous Anthropic SDK client, for accessing SDK features
	 * this model does not expose (for example listing batches).
	 * @return the sync client
	 */
	public AnthropicClient getAnthropicClient() {
		return this.anthropicClient;
	}

	/**
	 * Submits a batch of prompts for asynchronous processing.
	 *
	 * <p>
	 * Returns as soon as Anthropic accepts the batch; no request has been processed yet.
	 * Persist {@link AnthropicBatch#id()} together with the {@code customId} of every
	 * entry so that polling and correlation survive an application restart.
	 * @param requests the batch entries; must be non-empty and carry distinct
	 * {@code customId} values
	 * @return the accepted batch, in state {@link AnthropicBatchStatus#IN_PROGRESS}
	 * @throws IllegalArgumentException if the list is empty or a {@code customId} is
	 * duplicated
	 * @throws com.anthropic.errors.AnthropicServiceException if the API rejects the batch
	 */
	@Override
	public AnthropicBatch submit(List<AnthropicBatchRequest> requests) {
		Assert.notEmpty(requests, "requests must not be empty");

		Set<String> customIds = new LinkedHashSet<>();
		List<BatchCreateParams.Request> sdkRequests = new ArrayList<>(requests.size());
		Map<String, Set<String>> additionalHeaders = new LinkedHashMap<>();
		Set<String> requestModels = new LinkedHashSet<>();

		for (AnthropicBatchRequest request : requests) {
			Assert.isTrue(customIds.add(request.customId()), () -> "Duplicate customId in batch: '" + request.customId()
					+ "'. Results are correlated by customId, so it must be unique within a batch.");

			MessageCreateParams params = AnthropicChatModel.createRequest(buildRequestPrompt(request.prompt()),
					this.toolCallingManager, this.options.getSkillContainer());

			requestModels.add(params.model().asString());
			collectAdditionalHeaders(params._additionalHeaders(), additionalHeaders);

			sdkRequests.add(BatchCreateParams.Request.builder()
				.customId(request.customId())
				.params(toBatchRequestParams(params))
				.build());
		}

		BatchCreateParams.Builder builder = BatchCreateParams.builder().requests(sdkRequests);
		// Per-request headers cannot be expressed per batch entry, so entry-level headers
		// (for example the beta headers implied by a skill container) are merged onto the
		// batch request itself.
		additionalHeaders.forEach(builder::replaceAdditionalHeaders);

		AnthropicBatchObservationContext observationContext = AnthropicBatchObservationContext.builder()
			.operation(AnthropicBatchObservationContext.Operation.CREATE)
			.provider(AiProvider.ANTHROPIC.value())
			.requestModel(requestModels.size() == 1 ? requestModels.iterator().next() : null)
			.requestCount(requests.size())
			.build();

		return observe(observationContext, () -> {
			AnthropicBatch batch = AnthropicBatch
				.from(this.anthropicClient.messages().batches().create(builder.build(), requestOptions()));
			observationContext.setBatch(batch);
			if (logger.isDebugEnabled()) {
				logger.debug("Submitted Anthropic message batch " + batch.id() + " with " + requests.size()
						+ " request(s).");
			}
			return batch;
		});
	}

	/**
	 * Retrieves the current state of a batch.
	 *
	 * <p>
	 * Call this on the application's own schedule; Spring AI performs no polling. Results
	 * become readable once {@link AnthropicBatch#isEnded()} is {@code true}.
	 * @param batchId the batch identifier returned by {@link #submit(List)}
	 * @return the current batch state
	 * @throws com.anthropic.errors.NotFoundException if no such batch exists
	 */
	@Override
	public AnthropicBatch retrieve(String batchId) {
		Assert.hasText(batchId, "batchId must not be empty");

		AnthropicBatchObservationContext observationContext = observationContext(
				AnthropicBatchObservationContext.Operation.RETRIEVE);

		return observe(observationContext, () -> {
			AnthropicBatch batch = AnthropicBatch
				.from(this.anthropicClient.messages().batches().retrieve(batchId, requestOptions()));
			observationContext.setBatch(batch);
			return batch;
		});
	}

	/**
	 * Streams the results of an ended batch.
	 *
	 * <p>
	 * The underlying JSONL stream is consumed lazily and the SDK stream is closed when
	 * the returned {@link Flux} terminates or is cancelled, so a batch with a very large
	 * number of entries never has to be held in memory. Because the SDK exposes a
	 * blocking stream, items are emitted on {@link Schedulers#boundedElastic()}.
	 *
	 * <p>
	 * Results arrive in an unspecified order: key them by
	 * {@link AnthropicBatchResult#customId()}. Individual failures are emitted as
	 * {@link AnthropicBatchResultStatus#ERRORED} items rather than thrown, so one bad
	 * entry never hides the rest.
	 * @param batchId the batch identifier
	 * @return a lazily-populated flux of per-request results
	 */
	@Override
	public Flux<AnthropicBatchResult> results(String batchId) {
		Assert.hasText(batchId, "batchId must not be empty");

		return Flux.defer(() -> {
			AnthropicBatchObservationContext observationContext = observationContext(
					AnthropicBatchObservationContext.Operation.RESULTS);
			Observation observation = AnthropicBatchObservationDocumentation.BATCH_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);
			observation.start();

			ResultCounters counters = new ResultCounters();

			return Flux
				.using(() -> this.anthropicClient.messages().batches().resultsStreaming(batchId, requestOptions()),
						streamResponse -> Flux.fromStream(streamResponse.stream()).map(response -> {
							AnthropicBatchResult result = toBatchResult(response);
							counters.record(result.status());
							return result;
						}), StreamResponse::close)
				.doOnError(observation::error)
				.doFinally(signal -> {
					observationContext.setRequestCounts(counters.snapshot());
					observation.stop();
				})
				.subscribeOn(Schedulers.boundedElastic());
		});
	}

	/**
	 * Requests cancellation of a batch.
	 *
	 * <p>
	 * Cancellation is not immediate: the batch moves to
	 * {@link AnthropicBatchStatus#CANCELING} and requests that already completed keep
	 * their result, while the remaining ones end up as
	 * {@link AnthropicBatchResultStatus#CANCELED}.
	 * @param batchId the batch identifier
	 * @return the batch state after the cancellation request
	 */
	@Override
	public AnthropicBatch cancel(String batchId) {
		Assert.hasText(batchId, "batchId must not be empty");

		AnthropicBatchObservationContext observationContext = observationContext(
				AnthropicBatchObservationContext.Operation.CANCEL);

		return observe(observationContext, () -> {
			AnthropicBatch batch = AnthropicBatch
				.from(this.anthropicClient.messages().batches().cancel(batchId, requestOptions()));
			observationContext.setBatch(batch);
			return batch;
		});
	}

	/**
	 * Deletes a batch. Only batches whose processing has ended can be deleted.
	 * @param batchId the batch identifier
	 */
	@Override
	public void delete(String batchId) {
		Assert.hasText(batchId, "batchId must not be empty");

		AnthropicBatchObservationContext observationContext = observationContext(
				AnthropicBatchObservationContext.Operation.DELETE);

		AnthropicBatchObservationDocumentation.BATCH_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> this.anthropicClient.messages().batches().delete(batchId, requestOptions()));
	}

	/**
	 * Use the provided convention for reporting observation data.
	 * @param observationConvention the provided convention
	 */
	public void setObservationConvention(AnthropicBatchObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	private <T> T observe(AnthropicBatchObservationContext observationContext, java.util.function.Supplier<T> action) {
		T result = AnthropicBatchObservationDocumentation.BATCH_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(action);
		Assert.state(result != null, "Anthropic batch operation returned no result");
		return result;
	}

	private AnthropicBatchObservationContext observationContext(AnthropicBatchObservationContext.Operation operation) {
		return AnthropicBatchObservationContext.builder()
			.operation(operation)
			.provider(AiProvider.ANTHROPIC.value())
			.build();
	}

	/**
	 * Applies this model's default options when the prompt carries none, mirroring
	 * {@link AnthropicChatModel}'s behaviour.
	 */
	private Prompt buildRequestPrompt(Prompt prompt) {
		return prompt.getOptions() == null ? prompt.mutate().chatOptions(this.options).build() : prompt;
	}

	private RequestOptions requestOptions() {
		Duration timeout = this.options.getTimeout();
		return timeout != null ? RequestOptions.builder().timeout(timeout).build() : RequestOptions.none();
	}

	private static void collectAdditionalHeaders(Headers headers, Map<String, Set<String>> target) {
		for (String name : headers.names()) {
			target.computeIfAbsent(name, key -> new LinkedHashSet<>()).addAll(headers.values(name));
		}
	}

	/**
	 * Copies a realtime {@link MessageCreateParams} onto the batch entry parameter shape.
	 * The two SDK types are structurally identical but nominally distinct, so every field
	 * is transferred explicitly; {@code stream} is deliberately never set, as batch
	 * entries cannot stream.
	 */
	private static BatchCreateParams.Request.Params toBatchRequestParams(MessageCreateParams params) {
		BatchCreateParams.Request.Params.Builder builder = BatchCreateParams.Request.Params.builder()
			.maxTokens(params._maxTokens())
			.messages(params._messages())
			.model(params._model());

		params.cacheControl().ifPresent(builder::cacheControl);
		params.container().ifPresent(builder::container);
		params.inferenceGeo().ifPresent(builder::inferenceGeo);
		params.metadata().ifPresent(builder::metadata);
		params.outputConfig().ifPresent(builder::outputConfig);
		params.serviceTier()
			.ifPresent(serviceTier -> builder
				.serviceTier(BatchCreateParams.Request.Params.ServiceTier.of(serviceTier.asString())));
		params.stopSequences().ifPresent(builder::stopSequences);
		params.system().ifPresent(system -> {
			if (system.isString()) {
				builder.system(BatchCreateParams.Request.Params.System.ofString(system.asString()));
			}
			else if (system.isTextBlockParams()) {
				builder.system(BatchCreateParams.Request.Params.System.ofTextBlockParams(system.asTextBlockParams()));
			}
		});
		params.temperature().ifPresent(builder::temperature);
		params.thinking().ifPresent(builder::thinking);
		params.toolChoice().ifPresent(builder::toolChoice);
		params.tools().ifPresent(builder::tools);
		params.topK().ifPresent(builder::topK);
		params.topP().ifPresent(builder::topP);

		// Carries anything set through putAdditionalBodyProperty(), notably the skills
		// container.
		params._additionalBodyProperties().forEach(builder::putAdditionalProperty);

		return builder.build();
	}

	/**
	 * Converts one JSONL entry into a Spring AI result, reusing the realtime response
	 * conversion so that a batched message yields the same {@link ChatResponse} shape as
	 * {@link AnthropicChatModel#call(Prompt)}.
	 */
	private static AnthropicBatchResult toBatchResult(MessageBatchIndividualResponse response) {
		String customId = response.customId();
		MessageBatchResult result = response.result();

		if (result.isSucceeded()) {
			Message message = result.asSucceeded().message();
			List<Citation> citations = new ArrayList<>();
			List<AnthropicWebSearchResult> webSearchResults = new ArrayList<>();
			List<Generation> generations = AnthropicChatModel.buildGenerations(message, citations, webSearchResults);
			Usage usage = AnthropicChatModel.getDefaultUsage(message.usage());
			// Batch results carry no per-request rate-limit headers.
			ChatResponse chatResponse = new ChatResponse(generations,
					AnthropicChatModel.from(message, usage, citations, webSearchResults, new EmptyRateLimit()));
			return AnthropicBatchResult.succeeded(customId, chatResponse);
		}
		if (result.isErrored()) {
			return AnthropicBatchResult.errored(customId, AnthropicBatchError.from(result.asErrored().error()));
		}
		if (result.isCanceled()) {
			return AnthropicBatchResult.of(customId, AnthropicBatchResultStatus.CANCELED);
		}
		if (result.isExpired()) {
			return AnthropicBatchResult.of(customId, AnthropicBatchResultStatus.EXPIRED);
		}
		if (logger.isWarnEnabled()) {
			logger.warn("Unrecognised batch result type for customId '" + customId + "': " + result);
		}
		return AnthropicBatchResult.of(customId, AnthropicBatchResultStatus.UNKNOWN);
	}

	/**
	 * Accumulates per-outcome counters while the result stream is consumed, so the
	 * {@link AnthropicBatchObservationContext.Operation#RESULTS} observation can report
	 * them without buffering the results themselves.
	 */
	private static final class ResultCounters {

		private final AtomicLong succeeded = new AtomicLong();

		private final AtomicLong errored = new AtomicLong();

		private final AtomicLong canceled = new AtomicLong();

		private final AtomicLong expired = new AtomicLong();

		void record(AnthropicBatchResultStatus status) {
			switch (status) {
				case SUCCEEDED -> this.succeeded.incrementAndGet();
				case ERRORED -> this.errored.incrementAndGet();
				case CANCELED -> this.canceled.incrementAndGet();
				case EXPIRED -> this.expired.incrementAndGet();
				case UNKNOWN -> {
				}
			}
		}

		AnthropicBatchRequestCounts snapshot() {
			return new AnthropicBatchRequestCounts(0, this.succeeded.get(), this.errored.get(), this.canceled.get(),
					this.expired.get());
		}

	}

	/**
	 * Builder for {@link AnthropicBatchModel}. Accepts the same connection and
	 * observability configuration as {@link AnthropicChatModel.Builder}, so a batch model
	 * reuses the application's Anthropic credentials, base URL, timeout, retries, proxy,
	 * custom headers and HTTP client customizers.
	 */
	public static final class Builder {

		private @Nullable AnthropicClient anthropicClient;

		private @Nullable AnthropicChatOptions options;

		private @Nullable ToolCallingManager toolCallingManager;

		private @Nullable ObservationRegistry observationRegistry;

		private @Nullable MeterRegistry meterRegistry;

		private @Nullable ExecutorService dispatcherExecutor;

		private List<AnthropicHttpClientBuilderCustomizer> httpClientCustomizers = new ArrayList<>();

		private Builder() {
		}

		/**
		 * Sets a pre-configured Anthropic SDK client. When supplied, the
		 * connection-related options are ignored.
		 * @param anthropicClient the client
		 * @return this builder
		 */
		public Builder anthropicClient(AnthropicClient anthropicClient) {
			this.anthropicClient = anthropicClient;
			return this;
		}

		/**
		 * Sets the default options applied to batch entries whose prompt carries none,
		 * and the connection settings used when no client is supplied.
		 * @param options the options
		 * @return this builder
		 */
		public Builder options(AnthropicChatOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * Sets the tool calling manager used to resolve the tool definitions advertised
		 * to the model. Tool calls returned by a batch are never executed; see the
		 * class-level documentation.
		 * @param toolCallingManager the tool calling manager
		 * @return this builder
		 */
		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
			return this;
		}

		/**
		 * Sets the observation registry batch operations report to.
		 * @param observationRegistry the observation registry
		 * @return this builder
		 */
		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		/**
		 * Sets the meter registry OkHttp connection-pool gauges are bound to.
		 * @param meterRegistry the meter registry, or {@code null} to disable the gauges
		 * @return this builder
		 */
		public Builder meterRegistry(@Nullable MeterRegistry meterRegistry) {
			this.meterRegistry = meterRegistry;
			return this;
		}

		/**
		 * Sets the OkHttp dispatcher executor. The caller owns its lifecycle.
		 * @param dispatcherExecutor the executor, or {@code null} for the library default
		 * @return this builder
		 */
		public Builder dispatcherExecutor(@Nullable ExecutorService dispatcherExecutor) {
			this.dispatcherExecutor = dispatcherExecutor;
			return this;
		}

		/**
		 * Adds a customizer applied to the underlying OkHttp client builder.
		 * @param customizer the customizer
		 * @return this builder
		 */
		public Builder httpClientBuilderCustomizer(AnthropicHttpClientBuilderCustomizer customizer) {
			Assert.notNull(customizer, "customizer cannot be null");
			this.httpClientCustomizers.add(customizer);
			return this;
		}

		/**
		 * Replaces the customizers applied to the underlying OkHttp client builder.
		 * @param customizers the customizers
		 * @return this builder
		 */
		public Builder httpClientBuilderCustomizers(List<AnthropicHttpClientBuilderCustomizer> customizers) {
			Assert.notNull(customizers, "customizers cannot be null");
			this.httpClientCustomizers = CollectionUtils.isEmpty(customizers) ? new ArrayList<>()
					: new ArrayList<>(customizers);
			return this;
		}

		/**
		 * Builds the batch model.
		 * @return a new {@link DefaultAnthropicBatchModel}
		 */
		public DefaultAnthropicBatchModel build() {
			return new DefaultAnthropicBatchModel(this.anthropicClient, this.options, this.toolCallingManager,
					this.observationRegistry, this.meterRegistry, this.dispatcherExecutor, this.httpClientCustomizers);
		}

	}

}
