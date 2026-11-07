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

package org.springframework.ai.elevenlabs;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.audio.tts.observation.DefaultTextToSpeechModelObservationConvention;
import org.springframework.ai.audio.tts.observation.TextToSpeechModelObservationContext;
import org.springframework.ai.audio.tts.observation.TextToSpeechModelObservationConvention;
import org.springframework.ai.audio.tts.observation.TextToSpeechModelObservationDocumentation;
import org.springframework.ai.elevenlabs.api.ElevenLabsApi;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Implementation of the {@link TextToSpeechModel} interface for ElevenLabs TTS API.
 *
 * @author Alexandros Pappas
 * @author Sebastien Deleuze
 * @author Minju Kim
 */
public class ElevenLabsTextToSpeechModel implements TextToSpeechModel {

	private final Log logger = LogFactory.getLog(getClass());

	private final ElevenLabsApi elevenLabsApi;

	private final RetryTemplate retryTemplate;

	private final ElevenLabsTextToSpeechOptions options;

	private final ObservationRegistry observationRegistry;

	private TextToSpeechModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	private static final TextToSpeechModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultTextToSpeechModelObservationConvention();

	public ElevenLabsTextToSpeechModel(ElevenLabsApi elevenLabsApi, ElevenLabsTextToSpeechOptions options) {
		this(elevenLabsApi, options, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public ElevenLabsTextToSpeechModel(ElevenLabsApi elevenLabsApi, ElevenLabsTextToSpeechOptions options,
			RetryTemplate retryTemplate) {
		this(elevenLabsApi, options, retryTemplate, ObservationRegistry.NOOP);
	}

	/**
	 * Creates a model that reports its calls to the given observation registry.
	 * @param elevenLabsApi the ElevenLabs API client
	 * @param options the default options
	 * @param retryTemplate the retry template
	 * @param observationRegistry the observation registry,
	 * {@link ObservationRegistry#NOOP} for none
	 * @since 2.0.2
	 */
	public ElevenLabsTextToSpeechModel(ElevenLabsApi elevenLabsApi, ElevenLabsTextToSpeechOptions options,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		Assert.notNull(elevenLabsApi, "ElevenLabsApi must not be null");
		Assert.notNull(options, "ElevenLabsSpeechOptions must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		Assert.notNull(observationRegistry, "ObservationRegistry must not be null");

		this.elevenLabsApi = elevenLabsApi;
		this.options = options;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
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

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
		RequestContext requestContext = prepareRequest(prompt);

		var observationContext = TextToSpeechModelObservationContext.builder()
			.prompt(new TextToSpeechPrompt(prompt.getInstructions(), requestContext.options))
			.provider(AiProvider.ELEVENLABS.value())
			.build();

		return Objects.requireNonNull(
				TextToSpeechModelObservationDocumentation.TEXT_TO_SPEECH_MODEL_OPERATION
					.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
							this.observationRegistry)
					.observe(() -> {
						byte[] audioData = RetryUtils.execute(this.retryTemplate, () -> {
							var response = this.elevenLabsApi.textToSpeech(requestContext.request,
									requestContext.voiceId, requestContext.queryParameters);
							if (response.getBody() == null) {
								if (logger.isWarnEnabled()) {
									logger.warn("No speech response returned for request: " + requestContext.request);
								}
								return new byte[0];
							}
							return response.getBody();
						});
						TextToSpeechResponse response = new TextToSpeechResponse(List.of(new Speech(audioData)));
						observationContext.setResponse(response);
						return response;
					}));
	}

	@Override
	public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
		RequestContext requestContext = prepareRequest(prompt);

		return Flux.deferContextual(contextView -> {
			var observationContext = TextToSpeechModelObservationContext.builder()
				.prompt(new TextToSpeechPrompt(prompt.getInstructions(), requestContext.options))
				.provider(AiProvider.ELEVENLABS.value())
				.streaming(true)
				.build();
			Observation observation = TextToSpeechModelObservationDocumentation.TEXT_TO_SPEECH_MODEL_OPERATION
				.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
						this.observationRegistry);
			Observation parentObservation = contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
			observation.parentObservation(parentObservation);
			try (Observation.Scope ignored = parentObservation != null ? parentObservation.openScope()
					: Observation.Scope.NOOP) {
				observation.start();
			}

			Flux<TextToSpeechResponse> responses;
			try {
				// The stream is built here, before any subscription, so a failure at
				// this point ends the observation: the Flux's callbacks never run.
				responses = RetryUtils.execute(this.retryTemplate,
						() -> this.elevenLabsApi
							.textToSpeechStream(requestContext.request, requestContext.voiceId,
									requestContext.queryParameters)
							.map(entity -> new TextToSpeechResponse(
									List.of(new Speech(Objects.requireNonNull(entity.getBody()))))));
			}
			catch (RuntimeException ex) {
				observation.error(ex);
				observation.stop();
				throw ex;
			}
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

	private RequestContext prepareRequest(TextToSpeechPrompt prompt) {
		ElevenLabsApi.SpeechRequest request = createRequest(prompt);
		ElevenLabsTextToSpeechOptions options = getOptions(prompt);
		String voiceId = options.getVoice();
		Assert.state(voiceId != null, "voiceId must not be null");
		MultiValueMap<String, String> queryParameters = buildQueryParameters(options);

		return new RequestContext(request, voiceId, queryParameters, options);
	}

	private MultiValueMap<String, String> buildQueryParameters(ElevenLabsTextToSpeechOptions options) {
		MultiValueMap<String, String> queryParameters = new LinkedMultiValueMap<>();
		if (options.getEnableLogging() != null) {
			queryParameters.add("enable_logging", options.getEnableLogging().toString());
		}
		if (options.getFormat() != null) {
			queryParameters.add("output_format", options.getFormat());
		}
		return queryParameters;
	}

	private ElevenLabsApi.SpeechRequest createRequest(TextToSpeechPrompt prompt) {
		ElevenLabsTextToSpeechOptions options = getOptions(prompt);

		String voiceId = options.getVoice();
		Assert.notNull(voiceId, "A voiceId must be specified in the ElevenLabsSpeechOptions.");

		String text = prompt.getInstructions().getText();
		Assert.hasText(text, "Prompt must contain text to convert to speech.");

		return ElevenLabsApi.SpeechRequest.builder()
			.text(text)
			.modelId(options.getModelId())
			.voiceSettings(options.getVoiceSettings())
			.languageCode(options.getLanguageCode())
			.pronunciationDictionaryLocators(options.getPronunciationDictionaryLocators())
			.seed(options.getSeed())
			.previousText(options.getPreviousText())
			.nextText(options.getNextText())
			.previousRequestIds(options.getPreviousRequestIds())
			.nextRequestIds(options.getNextRequestIds())
			.applyTextNormalization(options.getApplyTextNormalization())
			.applyLanguageTextNormalization(options.getApplyLanguageTextNormalization())
			.build();
	}

	private ElevenLabsTextToSpeechOptions getOptions(TextToSpeechPrompt prompt) {
		TextToSpeechOptions runtimeOptions = prompt.getOptions();
		if (runtimeOptions == null) {
			return this.options;
		}
		if (runtimeOptions instanceof ElevenLabsTextToSpeechOptions elevenLabsSpeechOptions) {
			return merge(elevenLabsSpeechOptions, this.options);
		}
		// Portable options only carry the model, voice, format and speed. The speed is
		// applied on top of the default voice settings so that their other values are
		// kept.
		ElevenLabsTextToSpeechOptions.Builder portableOptions = ElevenLabsTextToSpeechOptions.builder()
			.model(runtimeOptions.getModel())
			.voice(runtimeOptions.getVoice())
			.format(runtimeOptions.getFormat());
		if (runtimeOptions.getSpeed() != null) {
			portableOptions.voiceSettings(this.options.getVoiceSettings()).speed(runtimeOptions.getSpeed());
		}
		return merge(portableOptions.build(), this.options);
	}

	private ElevenLabsTextToSpeechOptions merge(ElevenLabsTextToSpeechOptions runtimeOptions,
			ElevenLabsTextToSpeechOptions options) {
		return ElevenLabsTextToSpeechOptions.builder()
			.modelId(getOrDefault(runtimeOptions.getModelId(), options.getModelId()))
			.voice(getOrDefault(runtimeOptions.getVoice(), options.getVoice()))
			.voiceId(getOrDefault(runtimeOptions.getVoiceId(), options.getVoiceId()))
			.enableLogging(getOrDefault(runtimeOptions.getEnableLogging(), options.getEnableLogging()))
			.format(getOrDefault(runtimeOptions.getFormat(), options.getFormat()))
			.outputFormat(getOrDefault(runtimeOptions.getOutputFormat(), options.getOutputFormat()))
			.voiceSettings(getOrDefault(runtimeOptions.getVoiceSettings(), options.getVoiceSettings()))
			.languageCode(getOrDefault(runtimeOptions.getLanguageCode(), options.getLanguageCode()))
			.pronunciationDictionaryLocators(getOrDefault(runtimeOptions.getPronunciationDictionaryLocators(),
					options.getPronunciationDictionaryLocators()))
			.seed(getOrDefault(runtimeOptions.getSeed(), options.getSeed()))
			.previousText(getOrDefault(runtimeOptions.getPreviousText(), options.getPreviousText()))
			.nextText(getOrDefault(runtimeOptions.getNextText(), options.getNextText()))
			.previousRequestIds(getOrDefault(runtimeOptions.getPreviousRequestIds(), options.getPreviousRequestIds()))
			.nextRequestIds(getOrDefault(runtimeOptions.getNextRequestIds(), options.getNextRequestIds()))
			.applyTextNormalization(
					getOrDefault(runtimeOptions.getApplyTextNormalization(), options.getApplyTextNormalization()))
			.applyLanguageTextNormalization(getOrDefault(runtimeOptions.getApplyLanguageTextNormalization(),
					options.getApplyLanguageTextNormalization()))
			.build();
	}

	private <T> @Nullable T getOrDefault(@Nullable T runtimeValue, @Nullable T defaultValue) {
		return runtimeValue != null ? runtimeValue : defaultValue;
	}

	/**
	 * @since 2.0.0
	 */
	@Override
	public ElevenLabsTextToSpeechOptions getOptions() {
		return this.options;
	}

	/**
	 * @deprecated use {@link #getOptions()} instead.
	 */
	@Deprecated(forRemoval = true)
	@Override
	@SuppressWarnings("removal")
	public ElevenLabsTextToSpeechOptions getDefaultOptions() {
		return this.options;
	}

	public static final class Builder {

		private @Nullable ElevenLabsApi elevenLabsApi;

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ElevenLabsTextToSpeechOptions options = ElevenLabsTextToSpeechOptions.builder().build();

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		public Builder elevenLabsApi(ElevenLabsApi elevenLabsApi) {
			this.elevenLabsApi = elevenLabsApi;
			return this;
		}

		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		public Builder options(ElevenLabsTextToSpeechOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * Sets the observation registry the model reports its calls to. Defaults to
		 * {@link ObservationRegistry#NOOP}: no observations.
		 * @param observationRegistry the observation registry
		 * @return this builder
		 * @since 2.0.2
		 */
		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public ElevenLabsTextToSpeechModel build() {
			Assert.notNull(this.elevenLabsApi, "ElevenLabsApi must not be null");
			Assert.notNull(this.options, "ElevenLabsSpeechOptions must not be null");
			return new ElevenLabsTextToSpeechModel(this.elevenLabsApi, this.options, this.retryTemplate,
					this.observationRegistry);
		}

	}

	private record RequestContext(ElevenLabsApi.SpeechRequest request, String voiceId,
			MultiValueMap<String, String> queryParameters, ElevenLabsTextToSpeechOptions options) {
	}

}
