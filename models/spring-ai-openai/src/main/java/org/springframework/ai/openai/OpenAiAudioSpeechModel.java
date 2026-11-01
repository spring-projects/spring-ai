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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpResponse;
import com.openai.errors.OpenAIIoException;
import com.openai.models.audio.speech.SpeechCreateParams;
import com.openai.models.audio.speech.SpeechModel;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.audio.tts.observation.DefaultTextToSpeechModelObservationConvention;
import org.springframework.ai.audio.tts.observation.TextToSpeechModelObservationContext;
import org.springframework.ai.audio.tts.observation.TextToSpeechModelObservationConvention;
import org.springframework.ai.audio.tts.observation.TextToSpeechModelObservationDocumentation;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.metadata.OpenAiAudioSpeechResponseMetadata;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * OpenAI audio speech client implementation using the OpenAI Java SDK.
 *
 * @author Ahmed Yousri
 * @author Hyunjoon Choi
 * @author Thomas Vitale
 * @author Jonghoon Park
 * @author Ilayaperumal Gopinathan
 * @author Sebastien Deleuze
 * @author guan xu
 */
public final class OpenAiAudioSpeechModel implements TextToSpeechModel {

	private static final Log logger = LogFactory.getLog(OpenAiAudioSpeechModel.class);

	private static final int STREAM_CHUNK_SIZE = 8192;

	/**
	 * Default {@link #stream(TextToSpeechPrompt)} scheduler, used unless a
	 * {@link Builder#streamScheduler(Scheduler) custom one} is set. A dedicated pool,
	 * sized like Reactor's shared {@link Schedulers#boundedElastic()}, so long-lived
	 * streams don't contend with unrelated blocking work elsewhere in the app.
	 */
	private static final Scheduler DEFAULT_STREAM_SCHEDULER = Schedulers.newBoundedElastic(
			Schedulers.DEFAULT_BOUNDED_ELASTIC_SIZE, Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE,
			"openai-audio-speech");

	private static final TextToSpeechModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultTextToSpeechModelObservationConvention();

	private final OpenAIClient openAiClient;

	private final OpenAiAudioSpeechOptions options;

	private final Scheduler streamScheduler;

	private final ObservationRegistry observationRegistry;

	private TextToSpeechModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	private OpenAiAudioSpeechModel(Builder builder) {
		this.options = Objects.requireNonNullElseGet(builder.options, () -> OpenAiAudioSpeechOptions.builder().build());
		this.openAiClient = Objects.requireNonNullElseGet(builder.openAiClient,
				() -> OpenAiSetup.setupSyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getCredential(), this.options.getMicrosoftDeploymentName(),
						this.options.getMicrosoftFoundryServiceVersion(), this.options.getOrganizationId(),
						this.options.isMicrosoftFoundry(), this.options.isGitHubModels(), this.options.getModel(),
						Objects.requireNonNullElse(this.options.getTimeout(), AbstractOpenAiOptions.DEFAULT_TIMEOUT),
						this.options.getMaxRetries(), this.options.getProxy(), this.options.getCustomHeaders(),
						ObservationRegistry.NOOP, null, builder.httpClientCustomizers));
		this.streamScheduler = Objects.requireNonNullElse(builder.streamScheduler, DEFAULT_STREAM_SCHEDULER);
		this.observationRegistry = Objects.requireNonNullElse(builder.observationRegistry, ObservationRegistry.NOOP);
	}

	/**
	 * Use the provided convention for reporting observation data.
	 * @param observationConvention the provided convention
	 * @since 2.0.2
	 */
	public void setObservationConvention(TextToSpeechModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	/**
	 * Creates a new builder instance with default configuration.
	 * @return A new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Creates a builder initialized with this model's configuration.
	 * @return A builder for creating a modified copy
	 */
	public Builder mutate() {
		return new Builder(this);
	}

	@Override
	public byte[] call(String text) {
		Assert.hasText(text, "Text must not be null or empty");
		TextToSpeechPrompt prompt = new TextToSpeechPrompt(text);
		return call(prompt).getResult().getOutput();
	}

	@Override
	public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
		Assert.notNull(prompt, "Prompt must not be null");

		OpenAiAudioSpeechOptions mergedOptions = mergeOptions(prompt);
		String inputText = getInputText(prompt, mergedOptions);
		traceRequest("Calling", mergedOptions);

		SpeechCreateParams params = buildSpeechCreateParams(mergedOptions, inputText, false);

		RequestOptions requestOptions = this.buildRequestOptions(mergedOptions);

		// The text the model gets (the options' input wins over the message), with the
		// options as merged.
		var observationContext = TextToSpeechModelObservationContext.builder()
			.prompt(new TextToSpeechPrompt(inputText, mergedOptions))
			.provider(AiProvider.OPENAI.value())
			.build();

		return Objects.requireNonNull(
				TextToSpeechModelObservationDocumentation.TEXT_TO_SPEECH_MODEL_OPERATION
					.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
							this.observationRegistry)
					.observe(() -> {
						HttpResponse httpResponse = this.openAiClient.audio().speech().create(params, requestOptions);
						Headers headers = httpResponse.headers();

						byte[] audioBytes;
						try (InputStream inputStream = httpResponse.body()) {
							audioBytes = inputStream.readAllBytes();
						}
						catch (IOException e) {
							throw new RuntimeException("Failed to read audio speech response", e);
						}

						TextToSpeechResponse response;
						if (audioBytes.length == 0) {
							if (logger.isWarnEnabled()) {
								logger.warn("No speech response returned for prompt: " + prompt);
							}
							response = new TextToSpeechResponse(List.of(new Speech(new byte[0])));
						}
						else {
							response = new TextToSpeechResponse(List.of(new Speech(audioBytes)),
									OpenAiAudioSpeechResponseMetadata.from(headers));
						}
						observationContext.setResponse(response);
						return response;
					}));
	}

	@Override
	public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
		Assert.notNull(prompt, "Prompt must not be null");

		return Flux.deferContextual(contextView -> {
			// Lets openSpeechStream/emitNextChunk tell a deliberate cancellation
			// apart from a genuine I/O failure.
			AtomicBoolean cancelled = new AtomicBoolean(false);

			OpenAiAudioSpeechOptions mergedOptions = mergeOptions(prompt);
			String inputText = getInputText(prompt, mergedOptions);
			var observationContext = TextToSpeechModelObservationContext.builder()
				.prompt(new TextToSpeechPrompt(inputText, mergedOptions))
				.provider(AiProvider.OPENAI.value())
				.streaming(true)
				.build();
			Observation observation = TextToSpeechModelObservationDocumentation.TEXT_TO_SPEECH_MODEL_OPERATION
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

			Flux<TextToSpeechResponse> chunks = Flux.<TextToSpeechResponse, SpeechStreamState>generate(
					() -> openSpeechStream(mergedOptions, inputText, cancelled),
					(state, sink) -> emitNextChunk(state, sink, cancelled), state -> {
						if (state != null) {
							state.close();
						}
					})
				.subscribeOn(this.streamScheduler)
				// After subscribeOn() so this runs before its worker is disposed -
				// i.e. before the blocking call in progress gets interrupted -
				// guaranteeing `cancelled` is already set when that's observed.
				.doOnCancel(() -> cancelled.set(true));
			return observed(chunks, observation);
		});
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

	private @Nullable SpeechStreamState openSpeechStream(OpenAiAudioSpeechOptions mergedOptions, String inputText,
			AtomicBoolean cancelled) {
		traceRequest("Streaming", mergedOptions);

		SpeechCreateParams params = buildSpeechCreateParams(mergedOptions, inputText, true);

		RequestOptions requestOptions = this.buildRequestOptions(mergedOptions);

		HttpResponse httpResponse;
		try {
			httpResponse = this.openAiClient.audio().speech().create(params, requestOptions);
		}
		catch (OpenAIIoException e) {
			// The SDK wraps IOException - including one from this thread being
			// interrupted on cancellation - into OpenAIIoException; `cancelled`
			// tells that apart from a genuine connection failure.
			if (Thread.interrupted()) {
				Thread.currentThread().interrupt();
			}
			if (cancelled.get()) {
				// No response ever arrived, so there's nothing to stream.
				// Flux.generate supports a null state for this (see
				// emitNextChunk's null check below).
				return null;
			}
			throw e;
		}
		InputStream inputStream = httpResponse.body();
		OpenAiAudioSpeechResponseMetadata metadata = OpenAiAudioSpeechResponseMetadata.from(httpResponse.headers());

		return new SpeechStreamState(httpResponse, inputStream, metadata);
	}

	private @Nullable SpeechStreamState emitNextChunk(@Nullable SpeechStreamState state,
			SynchronousSink<TextToSpeechResponse> sink, AtomicBoolean cancelled) {
		if (state == null) {
			// Cancelled before openSpeechStream obtained a response.
			sink.complete();
			return null;
		}

		byte[] buffer = new byte[STREAM_CHUNK_SIZE];
		int bytesRead;
		try {
			bytesRead = state.inputStream().read(buffer);
		}
		catch (IOException e) {
			// A blocked read can fail from cancellation (this thread gets
			// interrupted) or from a genuine fault like a socket timeout -
			// SocketTimeoutException is itself an InterruptedIOException, so
			// the exception type alone can't tell them apart; `cancelled` can.
			if (Thread.interrupted()) {
				Thread.currentThread().interrupt();
			}
			if (cancelled.get()) {
				sink.complete();
			}
			else {
				sink.error(new RuntimeException("Failed to read audio speech stream", e));
			}
			return state;
		}

		if (bytesRead == -1) {
			sink.complete();
			return state;
		}

		byte[] chunk = (bytesRead == buffer.length) ? buffer : Arrays.copyOf(buffer, bytesRead);
		sink.next(new TextToSpeechResponse(List.of(new Speech(chunk)), state.metadata()));
		return state;
	}

	private OpenAiAudioSpeechOptions mergeOptions(TextToSpeechPrompt prompt) {
		return OpenAiAudioSpeechOptions.builder().from(this.options).merge(prompt.getOptions()).build();
	}

	private void traceRequest(String verb, OpenAiAudioSpeechOptions mergedOptions) {
		if (logger.isTraceEnabled()) {
			logger.trace(verb + " OpenAI SDK audio speech with model: " + mergedOptions.getModel() + ", voice: "
					+ mergedOptions.getVoice() + ", format: " + mergedOptions.getResponseFormat() + ", speed: "
					+ mergedOptions.getSpeed());
		}
	}

	private SpeechCreateParams buildSpeechCreateParams(OpenAiAudioSpeechOptions mergedOptions, String inputText,
			boolean streaming) {
		String model;
		if (mergedOptions.getDeploymentName() != null) {
			model = mergedOptions.getDeploymentName();
		}
		else {
			model = mergedOptions.getModel();
		}

		Assert.notNull(model, "Model must not be null");
		Assert.notNull(mergedOptions.getVoice(), "Voice must not be null");
		SpeechCreateParams.Builder paramsBuilder = SpeechCreateParams.builder()
			.model(SpeechModel.of(model))
			.input(inputText)
			.voice(SpeechCreateParams.Voice.ofString(mergedOptions.getVoice()));

		if (mergedOptions.getResponseFormat() != null) {
			paramsBuilder.responseFormat(SpeechCreateParams.ResponseFormat.of(mergedOptions.getResponseFormat()));
		}

		if (mergedOptions.getSpeed() != null) {
			paramsBuilder.speed(mergedOptions.getSpeed());
		}

		if (StringUtils.hasText(mergedOptions.getInstructions())) {
			paramsBuilder.instructions(mergedOptions.getInstructions());
		}

		if (streaming) {
			paramsBuilder.streamFormat(SpeechCreateParams.StreamFormat.AUDIO);
		}

		if (!CollectionUtils.isEmpty(mergedOptions.getCustomHeaders())) {
			mergedOptions.getCustomHeaders().forEach(paramsBuilder::putAdditionalHeader);
		}

		return paramsBuilder.build();
	}

	/**
	 * Creates a RequestOptions instance from the given audio speech options.
	 * @param options the audio speech options
	 * @return a RequestOptions instance
	 */
	private RequestOptions buildRequestOptions(OpenAiAudioSpeechOptions options) {
		Assert.notNull(options, "Options cannot be null");
		RequestOptions.Builder requestOptionsBuilder = RequestOptions.builder();
		if (options.getTimeout() != null) {
			requestOptionsBuilder.timeout(options.getTimeout());
		}
		return requestOptionsBuilder.build();
	}

	/**
	 * @since 2.0.0
	 */
	@Override
	public OpenAiAudioSpeechOptions getOptions() {
		return this.options;
	}

	/**
	 * @deprecated use {@link #getOptions()} instead.
	 */
	@Deprecated(forRemoval = true)
	@Override
	@SuppressWarnings("removal")
	public TextToSpeechOptions getDefaultOptions() {
		return this.options;
	}

	private String getInputText(TextToSpeechPrompt prompt, OpenAiAudioSpeechOptions options) {
		if (StringUtils.hasText(options.getInput())) {
			return options.getInput();
		}
		return prompt.getInstructions().getText();
	}

	/**
	 * Holds the open HTTP response and its still-open body {@link InputStream} for the
	 * duration of a {@link #stream(TextToSpeechPrompt)} subscription.
	 */
	private record SpeechStreamState(HttpResponse httpResponse, InputStream inputStream,
			OpenAiAudioSpeechResponseMetadata metadata) {

		void close() {
			this.httpResponse.close();
		}

	}

	/**
	 * Builder for creating OpenAiAudioSpeechModel instances.
	 */
	public static final class Builder {

		private @Nullable OpenAIClient openAiClient;

		private @Nullable OpenAiAudioSpeechOptions options;

		private @Nullable Scheduler streamScheduler;

		private @Nullable ObservationRegistry observationRegistry;

		private List<OpenAiHttpClientBuilderCustomizer> httpClientCustomizers = new ArrayList<>();

		/**
		 * Default constructor with default options.
		 */
		private Builder() {
			this.options = OpenAiAudioSpeechOptions.builder().build();
		}

		/**
		 * Copy constructor for creating a builder from an existing model.
		 * @param model The model to copy configuration from
		 */
		private Builder(OpenAiAudioSpeechModel model) {
			this.openAiClient = model.openAiClient;
			this.options = model.options;
			this.streamScheduler = model.streamScheduler;
			this.observationRegistry = model.observationRegistry;
		}

		/**
		 * Sets the observation registry the model reports its calls to. Defaults to
		 * {@link ObservationRegistry#NOOP}: no observations.
		 * @param observationRegistry The observation registry to use
		 * @return This builder
		 * @since 2.0.2
		 */
		public Builder observationRegistry(@Nullable ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		/**
		 * Sets the OpenAIClient.
		 * @param openAiClient The OpenAIClient to use
		 * @return This builder
		 */
		public Builder openAiClient(@Nullable OpenAIClient openAiClient) {
			this.openAiClient = openAiClient;
			return this;
		}

		/**
		 * Sets the default options.
		 * @param options The default options to use
		 * @return This builder
		 */
		public Builder options(@Nullable OpenAiAudioSpeechOptions options) {
			if (options != null) {
				this.options = options;
			}
			return this;
		}

		/**
		 * Sets the {@link Scheduler} for
		 * {@link OpenAiAudioSpeechModel#stream(TextToSpeechPrompt)}. Defaults to a
		 * dedicated pool; override to share a scheduler across components or size it for
		 * expected concurrent-stream load.
		 * @param streamScheduler The scheduler to use, or {@code null} to restore the
		 * default
		 * @return This builder
		 */
		public Builder streamScheduler(@Nullable Scheduler streamScheduler) {
			this.streamScheduler = streamScheduler;
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
		 * Builds the OpenAiAudioSpeechModel instance.
		 * @return A new OpenAiAudioSpeechModel instance
		 */
		public OpenAiAudioSpeechModel build() {
			return new OpenAiAudioSpeechModel(this);
		}

	}

}
