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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.MultipartField;
import com.openai.core.RequestOptions;
import com.openai.models.audio.AudioResponseFormat;
import com.openai.models.audio.transcriptions.Transcription;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import com.openai.models.audio.transcriptions.TranscriptionDiarized;
import com.openai.models.audio.transcriptions.TranscriptionDiarizedSegment;
import com.openai.models.audio.transcriptions.TranscriptionStreamEvent;
import com.openai.models.audio.transcriptions.TranscriptionVerbose;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.transcription.observation.DefaultTranscriptionModelObservationConvention;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationContext;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationConvention;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationDocumentation;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.metadata.OpenAiAudioTranscriptionResponseMetadata;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.ai.openai.setup.OpenAiStreams;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * OpenAI audio transcription model implementation using the OpenAI Java SDK. You provide
 * as input the audio file you want to transcribe and the desired output file format of
 * the transcription of the audio.
 *
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author Sebastien Deleuze
 * @author guan xu
 */
public final class OpenAiAudioTranscriptionModel implements TranscriptionModel {

	private static final Log logger = LogFactory.getLog(OpenAiAudioTranscriptionModel.class);

	private static final TranscriptionModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultTranscriptionModelObservationConvention();

	private final OpenAIClient openAiClient;

	/**
	 * The async client set on the builder, used for streaming in place of the sync
	 * client. None is created by default.
	 */
	private final @Nullable OpenAIClientAsync openAiClientAsync;

	private final Scheduler streamScheduler;

	private final OpenAiAudioTranscriptionOptions options;

	private final ObservationRegistry observationRegistry;

	private TranscriptionModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Creates a new builder for {@link OpenAiAudioTranscriptionModel}.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Creates a builder initialized with this model's configuration.
	 * @return a builder for creating a modified copy
	 */
	public Builder mutate() {
		return new Builder(this);
	}

	private OpenAiAudioTranscriptionModel(OpenAIClient openAiClient, @Nullable OpenAIClientAsync openAiClientAsync,
			Scheduler streamScheduler, OpenAiAudioTranscriptionOptions options,
			ObservationRegistry observationRegistry) {
		this.openAiClient = openAiClient;
		this.openAiClientAsync = openAiClientAsync;
		this.streamScheduler = streamScheduler;
		this.options = options;
		this.observationRegistry = observationRegistry;
	}

	/**
	 * Use the provided convention for reporting observation data.
	 * @param observationConvention the provided convention
	 * @since 2.0.2
	 */
	public void setObservationConvention(TranscriptionModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	/**
	 * Gets the transcription options for this model.
	 * @return the transcription options
	 */
	public OpenAiAudioTranscriptionOptions getOptions() {
		return this.options;
	}

	@Override
	public AudioTranscriptionResponse call(AudioTranscriptionPrompt transcriptionPrompt) {
		// Merge request options with default options
		OpenAiAudioTranscriptionOptions mergedOptions = OpenAiAudioTranscriptionOptions.builder()
			.from(this.options)
			.merge(transcriptionPrompt.getOptions())
			.build();

		Resource audioResource = transcriptionPrompt.getInstructions();
		TranscriptionCreateParams params = buildParams(mergedOptions, audioResource);
		if (logger.isTraceEnabled()) {
			logger.trace("OpenAiAudioTranscriptionModel call with model: " + mergedOptions.getModel());
		}

		RequestOptions requestOptions = this.buildRequestOptions(mergedOptions);

		var observationContext = TranscriptionModelObservationContext.builder()
			.prompt(new AudioTranscriptionPrompt(audioResource, mergedOptions))
			.provider(AiProvider.OPENAI.value())
			.build();

		return Objects.requireNonNull(
				TranscriptionModelObservationDocumentation.TRANSCRIPTION_MODEL_OPERATION
					.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
							this.observationRegistry)
					.observe(() -> {
						TranscriptionCreateResponse response = this.openAiClient.audio()
							.transcriptions()
							.create(params, requestOptions);
						AudioTranscriptionResponse transcriptionResponse = toTranscriptionResponse(mergedOptions,
								response);
						observationContext.setResponse(transcriptionResponse);
						return transcriptionResponse;
					}));
	}

	private AudioTranscriptionResponse toTranscriptionResponse(OpenAiAudioTranscriptionOptions mergedOptions,
			TranscriptionCreateResponse response) {
		DiarizedJsonMisclassificationRecovery.Recovered recovered = toRecoverDiarizedJson(mergedOptions, response);
		if (recovered != null) {
			AudioTranscription transcript = new AudioTranscription(recovered.text());
			AudioTranscriptionResponseMetadata metadata = new OpenAiAudioTranscriptionResponseMetadata(null, null,
					recovered.usage(), recovered.segments(), null);
			return new AudioTranscriptionResponse(transcript, metadata);
		}

		String text = extractText(response);
		AudioTranscription transcript = new AudioTranscription(text);
		return new AudioTranscriptionResponse(transcript, buildMetadata(response));
	}

	private static DiarizedJsonMisclassificationRecovery.@Nullable Recovered toRecoverDiarizedJson(
			OpenAiAudioTranscriptionOptions options, TranscriptionCreateResponse response) {
		if (!options.isDiarizedJsonWorkaroundEnabled()
				|| options.getResponseFormat() != AudioResponseFormat.DIARIZED_JSON || !response.isTranscription()) {
			return null;
		}
		return DiarizedJsonMisclassificationRecovery.tryRecover(response.asTranscription());
	}

	@Override
	public Flux<AudioTranscriptionResponse> stream(AudioTranscriptionPrompt transcriptionPrompt) {
		// Merge request options with default options
		OpenAiAudioTranscriptionOptions mergedOptions = OpenAiAudioTranscriptionOptions.builder()
			.from(this.options)
			.merge(transcriptionPrompt.getOptions())
			.build();

		Resource audioResource = transcriptionPrompt.getInstructions();
		TranscriptionCreateParams params = buildParams(mergedOptions, audioResource);
		if (logger.isTraceEnabled()) {
			logger.trace("OpenAiAudioTranscriptionModel stream with model: " + mergedOptions.getModel());
		}

		RequestOptions requestOptions = this.buildRequestOptions(mergedOptions);

		return Flux.deferContextual(contextView -> {
			var observationContext = TranscriptionModelObservationContext.builder()
				.prompt(new AudioTranscriptionPrompt(audioResource, mergedOptions))
				.provider(AiProvider.OPENAI.value())
				.streaming(true)
				.build();
			Observation observation = TranscriptionModelObservationDocumentation.TRANSCRIPTION_MODEL_OPERATION
				.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
						this.observationRegistry);
			Observation parentObservation = contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
			observation.parentObservation(parentObservation);
			// Start under the parent observation's scope, as OpenAiChatModel does, so a
			// tracing handler takes the parent span from it and not from the thread.
			try (Observation.Scope ignored = parentObservation != null ? parentObservation.openScope()
					: Observation.Scope.NOOP) {
				observation.start();
			}

			Flux<TranscriptionStreamEvent> chunks = streamEvents(params, requestOptions, observation);

			// The whole transcript is collected for the handlers: from the deltas, or
			// from the segments when a stream has no deltas. A diarized stream carries
			// both with the same text, so counting both would double it.
			StringBuilder deltas = new StringBuilder();
			StringBuilder segments = new StringBuilder();
			Flux<AudioTranscriptionResponse> responses = chunks.map(event -> {
				String text = extractStreamEventText(event);
				(event.isTranscriptTextDelta() ? deltas : segments).append(text);
				return new AudioTranscriptionResponse(new AudioTranscription(text),
						new AudioTranscriptionResponseMetadata());
			})
				.doOnComplete(() -> observationContext.setResponse(new AudioTranscriptionResponse(
						new AudioTranscription(deltas.isEmpty() ? segments.toString() : deltas.toString()),
						new AudioTranscriptionResponseMetadata())));
			return observed(responses, observation);
		});
	}

	/**
	 * Streams the events of a transcription with the sync client, as
	 * {@code OpenAiChatModel} does, so the HTTP observation and the {@code traceparent}
	 * header it writes become children of the transcription observation. An async client
	 * set on the builder is still used, but then the HTTP observation is not a child of
	 * the transcription observation.
	 */
	private Flux<TranscriptionStreamEvent> streamEvents(TranscriptionCreateParams params, RequestOptions requestOptions,
			Observation observation) {
		OpenAIClientAsync clientAsync = this.openAiClientAsync;
		if (clientAsync != null) {
			return OpenAiStreams
				.streamAsync(() -> clientAsync.audio().transcriptions().createStreaming(params, requestOptions));
		}
		return OpenAiStreams.stream(
				() -> this.openAiClient.audio().transcriptions().createStreaming(params, requestOptions), observation,
				this.streamScheduler);
	}

	/**
	 * Ends the observation with the stream: on completion, error or cancel, before the
	 * terminal signal reaches the subscriber (unlike {@code doFinally}), so a caller that
	 * blocks on the stream finds the observation stopped. Stopped once, whichever comes
	 * first.
	 */
	private static <T> Flux<T> observed(Flux<T> stream, Observation observation) {
		AtomicBoolean stopped = new AtomicBoolean();
		Runnable stop = () -> {
			if (stopped.compareAndSet(false, true)) {
				observation.stop();
			}
		};
		return stream.doOnError(observation::error)
			.doOnError(ex -> stop.run())
			.doOnComplete(stop)
			.doOnCancel(stop)
			.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
	}

	private TranscriptionCreateParams buildParams(OpenAiAudioTranscriptionOptions options, Resource audioResource) {
		MultipartField<InputStream> fileField = MultipartField.<InputStream>builder()
			.value(openStream(audioResource))
			.filename(getFilename(audioResource))
			.build();
		String model;
		if (options.getDeploymentName() != null) {
			model = options.getDeploymentName();
		}
		else {
			model = options.getModel();
		}
		Assert.notNull(model, "Model must not be null");
		TranscriptionCreateParams.Builder builder = TranscriptionCreateParams.builder().file(fileField).model(model);

		if (options.getResponseFormat() != null) {
			builder.responseFormat(options.getResponseFormat());
		}
		if (options.getLanguage() != null) {
			builder.language(options.getLanguage());
		}
		if (options.getPrompt() != null) {
			builder.prompt(options.getPrompt());
		}
		if (options.getTemperature() != null) {
			builder.temperature(options.getTemperature().doubleValue());
		}
		if (options.getTimestampGranularities() != null && !options.getTimestampGranularities().isEmpty()) {
			builder.timestampGranularities(options.getTimestampGranularities());
		}
		if (options.getChunkingStrategy() != null) {
			builder.chunkingStrategy(options.getChunkingStrategy());
		}
		if (!CollectionUtils.isEmpty(options.getKnownSpeakerNames())) {
			builder.knownSpeakerNames(options.getKnownSpeakerNames());
		}
		if (!CollectionUtils.isEmpty(options.getKnownSpeakerReferences())) {
			builder.knownSpeakerReferences(options.getKnownSpeakerReferences());
		}
		if (!CollectionUtils.isEmpty(options.getCustomHeaders())) {
			options.getCustomHeaders().forEach(builder::putAdditionalHeader);
		}
		return builder.build();
	}

	/**
	 * Creates a RequestOptions instance from the given transcription options.
	 * @param options the transcription options
	 * @return a RequestOptions instance
	 */
	private RequestOptions buildRequestOptions(OpenAiAudioTranscriptionOptions options) {
		Assert.notNull(options, "Options cannot be null");
		RequestOptions.Builder requestOptionsBuilder = RequestOptions.builder();
		if (options.getTimeout() != null) {
			requestOptionsBuilder.timeout(options.getTimeout());
		}
		return requestOptionsBuilder.build();
	}

	private static String extractText(TranscriptionCreateResponse response) {
		if (response.isTranscription()) {
			return response.asTranscription().text();
		}
		if (response.isVerbose()) {
			return response.asVerbose().text();
		}
		if (response.isDiarized()) {
			return response.asDiarized().text();
		}
		return "";
	}

	private static AudioTranscriptionResponseMetadata buildMetadata(TranscriptionCreateResponse response) {
		if (response.isVerbose()) {
			TranscriptionVerbose verbose = response.asVerbose();
			return new OpenAiAudioTranscriptionResponseMetadata(verbose.duration(), verbose.language(),
					verbose.usage().orElse(null), verbose.segments().orElse(null), verbose.words().orElse(null));
		}
		if (response.isDiarized()) {
			TranscriptionDiarized diarized = response.asDiarized();
			// The real API does not return duration for diarized_json; use the raw field
			// accessor to avoid the required-field check on the typed duration() method.
			Double duration = diarized._duration().asKnown().orElse(null);
			// Similarly, use the raw accessor for segments to guard against an absent or
			// null field without triggering getRequired()'s exception.
			List<TranscriptionDiarizedSegment> segments = diarized._segments().asKnown().orElse(null);
			return new OpenAiAudioTranscriptionResponseMetadata(duration, null, diarized.usage().orElse(null), segments,
					null);
		}
		if (response.isTranscription()) {
			Transcription transcription = response.asTranscription();
			return new OpenAiAudioTranscriptionResponseMetadata(null, null, transcription.usage().orElse(null), null,
					null);
		}
		return new AudioTranscriptionResponseMetadata();
	}

	private static String extractStreamEventText(TranscriptionStreamEvent event) {
		if (event.isTranscriptTextDelta()) {
			return event.asTranscriptTextDelta().delta();
		}
		if (event.isTranscriptTextSegment()) {
			return event.asTranscriptTextSegment().text();
		}
		return "";
	}

	private static InputStream openStream(Resource resource) {
		Assert.notNull(resource, "Resource must not be null");
		try {
			return resource.getInputStream();
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Failed to read resource: " + resource, e);
		}
	}

	private static String getFilename(Resource audioResource) {
		String filename = audioResource.getFilename();
		if (filename == null) {
			filename = "audio";
		}
		return filename;
	}

	/**
	 * Builder for creating {@link OpenAiAudioTranscriptionModel} instances.
	 */
	public static final class Builder {

		private @Nullable OpenAIClient openAiClient;

		private @Nullable OpenAIClientAsync openAiClientAsync;

		private @Nullable Scheduler streamScheduler;

		private @Nullable OpenAiAudioTranscriptionOptions options;

		private @Nullable ObservationRegistry observationRegistry;

		private List<OpenAiHttpClientBuilderCustomizer> httpClientCustomizers = new ArrayList<>();

		private Builder() {
		}

		private Builder(OpenAiAudioTranscriptionModel model) {
			this.openAiClient = model.openAiClient;
			this.openAiClientAsync = model.openAiClientAsync;
			this.streamScheduler = model.streamScheduler;
			this.options = model.options;
			this.observationRegistry = model.observationRegistry;
		}

		/**
		 * Sets the observation registry the model reports its calls to. Defaults to
		 * {@link ObservationRegistry#NOOP}: no observations.
		 * @param observationRegistry the observation registry
		 * @return this builder
		 * @since 2.0.2
		 */
		public Builder observationRegistry(@Nullable ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		/**
		 * Sets the OpenAI client.
		 * @param openAiClient the OpenAI client
		 * @return this builder
		 */
		public Builder openAiClient(OpenAIClient openAiClient) {
			this.openAiClient = openAiClient;
			return this;
		}

		/**
		 * Sets the OpenAI client async.
		 * <p>
		 * When set, streaming requests are sent with this client, but their HTTP
		 * observations are then not children of the transcription observation, so they
		 * don't join the caller's trace. Configure only
		 * {@link #openAiClient(OpenAIClient)} to keep streaming requests in the caller's
		 * trace.
		 * @param openAiClientAsync the OpenAI client async
		 * @return this builder
		 */
		public Builder openAiClientAsync(OpenAIClientAsync openAiClientAsync) {
			this.openAiClientAsync = openAiClientAsync;
			return this;
		}

		/**
		 * Sets the {@link Scheduler} that
		 * {@link OpenAiAudioTranscriptionModel#stream(AudioTranscriptionPrompt)} sends
		 * its requests and reads their responses on, unless an async client is set. Each
		 * open stream holds one of its threads for the whole stream. Defaults to a
		 * dedicated pool sized like Reactor's shared {@code Schedulers.boundedElastic()};
		 * override to size it for the expected number of concurrent streams, or to share
		 * a scheduler across components.
		 * @param streamScheduler the scheduler to use, or {@code null} to restore the
		 * default
		 * @return this builder
		 * @since 2.1.0
		 */
		public Builder streamScheduler(@Nullable Scheduler streamScheduler) {
			this.streamScheduler = streamScheduler;
			return this;
		}

		/**
		 * Sets the transcription options.
		 * @param options the transcription options
		 * @return this builder
		 */
		public Builder options(OpenAiAudioTranscriptionOptions options) {
			this.options = options;
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
		 * Builds a new {@link OpenAiAudioTranscriptionModel} instance.
		 * @return the configured transcription model
		 */
		public OpenAiAudioTranscriptionModel build() {
			var options = Objects.requireNonNullElseGet(this.options,
					() -> OpenAiAudioTranscriptionOptions.builder().build());
			var observationRegistry = Objects.requireNonNullElse(this.observationRegistry, ObservationRegistry.NOOP);

			var openAiClient = Objects.requireNonNullElseGet(this.openAiClient,
					() -> OpenAiSetup.setupSyncClient(options.getBaseUrl(), options.getApiKey(),
							options.getCredential(), options.getMicrosoftDeploymentName(),
							options.getMicrosoftFoundryServiceVersion(), options.getOrganizationId(),
							options.isMicrosoftFoundry(), options.isGitHubModels(), options.getModel(),
							Objects.requireNonNullElse(options.getTimeout(), AbstractOpenAiOptions.DEFAULT_TIMEOUT),
							options.getMaxRetries(), options.getProxy(), options.getCustomHeaders(),
							observationRegistry, null, this.httpClientCustomizers));

			return new OpenAiAudioTranscriptionModel(openAiClient, this.openAiClientAsync,
					Objects.requireNonNullElseGet(this.streamScheduler, OpenAiStreams::defaultScheduler), options,
					observationRegistry);
		}

	}

}
