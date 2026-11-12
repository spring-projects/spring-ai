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

package org.springframework.ai.mistralai.transcription;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.transcription.observation.DefaultTranscriptionModelObservationConvention;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationContext;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationConvention;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationDocumentation;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiAudioApi;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.TranscriptionRequest;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.TranscriptionResponse;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.TranscriptionStreamEvent;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.Usage;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;

/**
 * Mistral AI transcription model, turning audio into text with the Voxtral models.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 * @see <a href= "https://docs.mistral.ai/studio/audio/speech-to-text/overview">Speech to
 * Text</a>
 */
public class MistralAiTranscriptionModel implements TranscriptionModel {

	private static final Log logger = LogFactory.getLog(MistralAiTranscriptionModel.class);

	private static final TranscriptionModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultTranscriptionModelObservationConvention();

	private final MistralAiAudioApi mistralAiAudioApi;

	private final MistralAiTranscriptionOptions options;

	private final RetryTemplate retryTemplate;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private TranscriptionModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public MistralAiTranscriptionModel(MistralAiAudioApi mistralAiAudioApi, MistralAiTranscriptionOptions options,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		Assert.notNull(mistralAiAudioApi, "mistralAiAudioApi must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		this.mistralAiAudioApi = mistralAiAudioApi;
		this.options = options;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public AudioTranscriptionResponse call(AudioTranscriptionPrompt transcriptionPrompt) {
		AudioTranscriptionPrompt requestPrompt = buildRequestPrompt(transcriptionPrompt);
		TranscriptionRequest request = createRequest(requestPrompt, false);

		var observationContext = TranscriptionModelObservationContext.builder()
			.prompt(requestPrompt)
			.provider(MistralAiApi.PROVIDER_NAME)
			.build();

		return Objects.requireNonNull(
				TranscriptionModelObservationDocumentation.TRANSCRIPTION_MODEL_OPERATION
					.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
							this.observationRegistry)
					.observe(() -> {
						var responseEntity = RetryUtils.execute(this.retryTemplate,
								() -> this.mistralAiAudioApi.createTranscription(request));
						TranscriptionResponse apiResponse = responseEntity.getBody();

						if (apiResponse == null) {
							if (logger.isWarnEnabled()) {
								logger.warn("No transcription returned for model: " + request.model());
							}
							return new AudioTranscriptionResponse(new AudioTranscription(""));
						}

						AudioTranscriptionResponse transcriptionResponse = toTranscriptionResponse(apiResponse);
						observationContext.setResponse(transcriptionResponse);
						return transcriptionResponse;
					}));
	}

	/**
	 * Streams the transcription as chunks of text. The audio is uploaded whole before the
	 * transcription starts, so streaming makes the first words arrive sooner but not the
	 * whole transcription.
	 * @param transcriptionPrompt the transcription prompt
	 * @return the stream of transcribed text chunks
	 */
	@Override
	public Flux<AudioTranscriptionResponse> stream(AudioTranscriptionPrompt transcriptionPrompt) {
		AudioTranscriptionPrompt requestPrompt = buildRequestPrompt(transcriptionPrompt);
		TranscriptionRequest request = createRequest(requestPrompt, true);

		return Flux.deferContextual(contextView -> {
			var observationContext = TranscriptionModelObservationContext.builder()
				.prompt(requestPrompt)
				.provider(MistralAiApi.PROVIDER_NAME)
				.streaming(true)
				.build();
			Observation observation = TranscriptionModelObservationDocumentation.TRANSCRIPTION_MODEL_OPERATION
				.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
						this.observationRegistry);
			Observation parentObservation = contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
			observation.parentObservation(parentObservation);
			try (Observation.Scope ignored = parentObservation != null ? parentObservation.openScope()
					: Observation.Scope.NOOP) {
				observation.start();
			}

			// The handlers get the whole transcription from the final event, which also
			// carries the usage, or from the text chunks if the stream ends without it.
			StringBuilder text = new StringBuilder();
			Flux<AudioTranscriptionResponse> responses = this.mistralAiAudioApi.createTranscriptionStream(request)
				.doOnNext(event -> {
					if (TranscriptionStreamEvent.DONE.equals(event.type())) {
						observationContext.setResponse(toTranscriptionResponse(event));
					}
				})
				.filter(event -> TranscriptionStreamEvent.TEXT_DELTA.equals(event.type()) && event.text() != null)
				.map(event -> {
					String chunk = Objects.requireNonNull(event.text());
					text.append(chunk);
					return new AudioTranscriptionResponse(new AudioTranscription(chunk));
				})
				.doOnComplete(() -> {
					if (observationContext.getResponse() == null) {
						observationContext.setResponse(new AudioTranscriptionResponse(
								new AudioTranscription(text.toString()), new AudioTranscriptionResponseMetadata()));
					}
				});
			return observed(responses, observation);
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

	private AudioTranscriptionPrompt buildRequestPrompt(AudioTranscriptionPrompt transcriptionPrompt) {
		MistralAiTranscriptionOptions mergedOptions = MistralAiTranscriptionOptions.builder()
			.from(this.options)
			.merge(transcriptionPrompt.getOptions())
			.build();
		return new AudioTranscriptionPrompt(transcriptionPrompt.getInstructions(), mergedOptions);
	}

	private static TranscriptionRequest createRequest(AudioTranscriptionPrompt requestPrompt, boolean stream) {
		MistralAiTranscriptionOptions options = (MistralAiTranscriptionOptions) Objects
			.requireNonNull(requestPrompt.getOptions());
		// Only a call is retried, so only a call needs audio it can read again.
		Resource audio = (stream ? requestPrompt.getInstructions() : rereadable(requestPrompt.getInstructions()));
		return TranscriptionRequest.builder()
			.model(options.getModel())
			.file(audio)
			.language(options.getLanguage())
			.temperature(options.getTemperature())
			.stream(stream ? true : null)
			.diarize(options.getDiarize())
			.contextBias(options.getContextBias())
			.timestampGranularities(options.getTimestampGranularities())
			.build();
	}

	/**
	 * Buffers audio that can be read only once, such as an {@link InputStreamResource},
	 * so that a retry can send it again. Other audio is left as is, to be streamed from
	 * its source on each attempt.
	 */
	private static Resource rereadable(Resource audio) {
		if (!audio.isOpen()) {
			return audio;
		}
		try {
			String filename = audio.getFilename();
			return new ByteArrayResource(audio.getContentAsByteArray(), audio.getDescription()) {

				@Override
				public @Nullable String getFilename() {
					return filename;
				}

			};
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Failed to read audio resource: " + audio, ex);
		}
	}

	private static AudioTranscriptionResponse toTranscriptionResponse(TranscriptionResponse response) {
		var metadata = new MistralAiTranscriptionResponseMetadata(response.model(), response.language(),
				response.segments());
		if (response.usage() != null) {
			metadata.setUsage(getDefaultUsage(response.usage()));
		}
		return new AudioTranscriptionResponse(new AudioTranscription(response.text()), metadata);
	}

	private static AudioTranscriptionResponse toTranscriptionResponse(TranscriptionStreamEvent doneEvent) {
		return toTranscriptionResponse(new TranscriptionResponse(Objects.requireNonNullElse(doneEvent.model(), ""),
				Objects.requireNonNullElse(doneEvent.text(), ""), doneEvent.language(), doneEvent.segments(),
				doneEvent.usage()));
	}

	private static DefaultUsage getDefaultUsage(Usage usage) {
		return new DefaultUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), usage);
	}

	/**
	 * Returns the default options of this model.
	 * @return the default options
	 */
	public MistralAiTranscriptionOptions getOptions() {
		return this.options;
	}

	/**
	 * Use the provided convention for reporting observation data.
	 * @param observationConvention the provided convention
	 */
	public void setObservationConvention(TranscriptionModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable MistralAiAudioApi mistralAiAudioApi;

		private MistralAiTranscriptionOptions options = MistralAiTranscriptionOptions.builder().build();

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private Builder() {
		}

		public Builder mistralAiAudioApi(MistralAiAudioApi mistralAiAudioApi) {
			this.mistralAiAudioApi = mistralAiAudioApi;
			return this;
		}

		public Builder options(MistralAiTranscriptionOptions options) {
			this.options = options;
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

		public MistralAiTranscriptionModel build() {
			Assert.state(this.mistralAiAudioApi != null, "MistralAiAudioApi must not be null");
			return new MistralAiTranscriptionModel(this.mistralAiAudioApi, this.options, this.retryTemplate,
					this.observationRegistry);
		}

	}

}
