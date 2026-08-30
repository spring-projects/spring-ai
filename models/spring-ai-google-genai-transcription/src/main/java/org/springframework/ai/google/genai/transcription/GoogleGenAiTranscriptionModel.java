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

package org.springframework.ai.google.genai.transcription;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.cloud.speech.v2.AutoDetectDecodingConfig;
import com.google.cloud.speech.v2.CustomPromptConfig;
import com.google.cloud.speech.v2.DenoiserConfig;
import com.google.cloud.speech.v2.ExplicitDecodingConfig;
import com.google.cloud.speech.v2.RecognitionConfig;
import com.google.cloud.speech.v2.RecognitionFeatures;
import com.google.cloud.speech.v2.RecognitionResponseMetadata;
import com.google.cloud.speech.v2.RecognizeRequest;
import com.google.cloud.speech.v2.RecognizeResponse;
import com.google.cloud.speech.v2.SpeakerDiarizationConfig;
import com.google.cloud.speech.v2.SpeechRecognitionAlternative;
import com.google.cloud.speech.v2.SpeechRecognitionResult;
import com.google.cloud.speech.v2.TranslationConfig;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.Durations;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.transcription.observation.AudioTranscriptionModelObservationContext;
import org.springframework.ai.audio.transcription.observation.AudioTranscriptionModelObservationConvention;
import org.springframework.ai.audio.transcription.observation.AudioTranscriptionModelObservationDocumentation;
import org.springframework.ai.audio.transcription.observation.DefaultAudioTranscriptionModelObservationConvention;
import org.springframework.ai.google.genai.transcription.metadata.GoogleGenAiAudioTranscriptionResponseMetadata;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * A class representing a Transcription Model using the new Google Gen AI SDK.
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
public class GoogleGenAiTranscriptionModel implements TranscriptionModel {

	private static final AudioTranscriptionModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultAudioTranscriptionModelObservationConvention();

	private static final String AUTO_LANGUAGE = "auto";

	private final GoogleGenAiAudioTranscriptionOptions options;

	private final GoogleGenAiTranscriptionConnectionDetails connectionDetails;

	private final RetryTemplate retryTemplate;

	private final ObservationRegistry observationRegistry;

	private AudioTranscriptionModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public GoogleGenAiTranscriptionModel(GoogleGenAiTranscriptionConnectionDetails connectionDetails,
			GoogleGenAiAudioTranscriptionOptions defaultOptions) {
		this(connectionDetails, defaultOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public GoogleGenAiTranscriptionModel(GoogleGenAiTranscriptionConnectionDetails connectionDetails,
			GoogleGenAiAudioTranscriptionOptions defaultOptions, RetryTemplate retryTemplate) {
		this(connectionDetails, defaultOptions, retryTemplate, ObservationRegistry.NOOP);
	}

	public GoogleGenAiTranscriptionModel(GoogleGenAiTranscriptionConnectionDetails connectionDetails,
			GoogleGenAiAudioTranscriptionOptions defaultTranscriptionOptions, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		Assert.notNull(connectionDetails, "GoogleGenAiTranscriptionConnectionDetails must not be null");
		Assert.notNull(defaultTranscriptionOptions, "GoogleGenAiAudioTranscriptionOptions must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");

		this.options = defaultTranscriptionOptions;
		this.connectionDetails = connectionDetails;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public AudioTranscriptionResponse call(AudioTranscriptionPrompt prompt) {
		final AudioTranscriptionPrompt transcriptionPrompt = buildTranscriptionPrompt(prompt);

		final AudioTranscriptionModelObservationContext observationContext = AudioTranscriptionModelObservationContext
			.builder()
			.transcriptionPrompt(transcriptionPrompt)
			.provider(AiProvider.GOOGLE_GENAI_AI.value())
			.build();

		return AudioTranscriptionModelObservationDocumentation.AUDIO_TRANSCRIPTION_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				final GoogleGenAiAudioTranscriptionOptions options = (GoogleGenAiAudioTranscriptionOptions) transcriptionPrompt
					.getOptions();
				Assert.notNull(options, "Options must not be null");

				final ByteString content = readAudioContent(transcriptionPrompt);
				final RecognitionConfig config = buildRecognitionConfig(options);

				final RecognizeRequest request = RecognizeRequest.newBuilder()
					.setRecognizer(this.connectionDetails.getRecognizerName())
					.setConfig(config)
					.setContent(content)
					.build();

				final RecognizeResponse recognizeResponse = RetryUtils.execute(this.retryTemplate,
						() -> this.connectionDetails.getSpeechClient().recognize(request));

				final AudioTranscriptionResponse response = toAudioTranscriptionResponse(recognizeResponse, options);

				observationContext.setResponse(response);

				return response;
			});
	}

	public void setObservationConvention(@Nullable AudioTranscriptionModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	@Override
	public Flux<AudioTranscriptionResponse> stream(AudioTranscriptionPrompt prompt) {
		return Flux.just(call(prompt));
	}

	// Private methods

	private static ByteString readAudioContent(AudioTranscriptionPrompt prompt) {
		try {
			return ByteString.copyFrom(prompt.getInstructions().getContentAsByteArray());
		}
		catch (IOException exception) {
			throw new UncheckedIOException("Failed to read audio resource", exception);
		}
	}

	private static RecognitionConfig buildRecognitionConfig(GoogleGenAiAudioTranscriptionOptions options) {
		final RecognitionConfig.Builder configBuilder = RecognitionConfig.newBuilder().setModel(options.getModel());

		configBuilder.addAllLanguageCodes(resolveLanguageCodes(options));

		final RecognitionFeatures features = buildRecognitionFeatures(options);
		if (Objects.nonNull(features)) {
			configBuilder.setFeatures(features);
		}

		if (Objects.nonNull(options.getEncoding())) {
			configBuilder.setExplicitDecodingConfig(buildExplicitDecodingConfig(options));
		}
		else {
			configBuilder.setAutoDecodingConfig(AutoDetectDecodingConfig.getDefaultInstance());
		}

		final DenoiserConfig denoiserConfig = buildDenoiserConfig(options);
		if (Objects.nonNull(denoiserConfig)) {
			configBuilder.setDenoiserConfig(denoiserConfig);
		}

		// Translation is supported by chirp_2 only.
		if (StringUtils.hasText(options.getTranslationTargetLanguage())) {
			configBuilder.setTranslationConfig(
					TranslationConfig.newBuilder().setTargetLanguage(options.getTranslationTargetLanguage()).build());
		}

		return configBuilder.build();
	}

	private @Nullable static DenoiserConfig buildDenoiserConfig(GoogleGenAiAudioTranscriptionOptions options) {
		if (Objects.isNull(options.getDenoiseAudio()) && Objects.isNull(options.getSnrThreshold())) {
			return null;
		}

		final DenoiserConfig.Builder builder = DenoiserConfig.newBuilder();
		if (Objects.nonNull(options.getDenoiseAudio())) {
			builder.setDenoiseAudio(options.getDenoiseAudio());
		}
		if (Objects.nonNull(options.getSnrThreshold())) {
			builder.setSnrThreshold(options.getSnrThreshold());
		}
		return builder.build();
	}

	private static List<String> resolveLanguageCodes(GoogleGenAiAudioTranscriptionOptions options) {
		if (!CollectionUtils.isEmpty(options.getLanguageCodes())) {
			return options.getLanguageCodes();
		}
		if (StringUtils.hasText(options.getLanguage())) {
			return List.of(options.getLanguage());
		}
		return List.of(AUTO_LANGUAGE);
	}

	private @Nullable static RecognitionFeatures buildRecognitionFeatures(
			GoogleGenAiAudioTranscriptionOptions options) {
		final RecognitionFeatures.Builder builder = RecognitionFeatures.newBuilder();
		boolean hasFeature = false;

		if (Objects.nonNull(options.getEnableAutomaticPunctuation())) {
			builder.setEnableAutomaticPunctuation(options.getEnableAutomaticPunctuation());
			hasFeature = true;
		}
		if (Objects.nonNull(options.getEnableSpokenPunctuation())) {
			builder.setEnableSpokenPunctuation(options.getEnableSpokenPunctuation());
			hasFeature = true;
		}
		if (Objects.nonNull(options.getEnableSpokenEmojis())) {
			builder.setEnableSpokenEmojis(options.getEnableSpokenEmojis());
			hasFeature = true;
		}
		if (Objects.nonNull(options.getProfanityFilter())) {
			builder.setProfanityFilter(options.getProfanityFilter());
			hasFeature = true;
		}
		if (Objects.nonNull(options.getEnableWordTimeOffsets())) {
			builder.setEnableWordTimeOffsets(options.getEnableWordTimeOffsets());
			hasFeature = true;
		}
		if (Objects.nonNull(options.getEnableWordConfidence())) {
			builder.setEnableWordConfidence(options.getEnableWordConfidence());
			hasFeature = true;
		}
		if (Objects.nonNull(options.getMaxAlternatives())) {
			builder.setMaxAlternatives(options.getMaxAlternatives());
			hasFeature = true;
		}
		if (Objects.nonNull(options.getMultiChannelMode())) {
			builder.setMultiChannelMode(
					RecognitionFeatures.MultiChannelMode.valueOf(options.getMultiChannelMode().name()));
			hasFeature = true;
		}
		// Speaker diarization is supported by chirp_3 only.
		if (Boolean.TRUE.equals(options.getEnableSpeakerDiarization())) {
			final SpeakerDiarizationConfig.Builder diarizationBuilder = SpeakerDiarizationConfig.newBuilder();
			if (Objects.nonNull(options.getMinSpeakerCount())) {
				diarizationBuilder.setMinSpeakerCount(options.getMinSpeakerCount());
			}
			if (Objects.nonNull(options.getMaxSpeakerCount())) {
				diarizationBuilder.setMaxSpeakerCount(options.getMaxSpeakerCount());
			}
			builder.setDiarizationConfig(diarizationBuilder.build());
			hasFeature = true;
		}
		// Custom prompt is supported by chirp_3 only.
		if (StringUtils.hasText(options.getCustomPrompt())) {
			builder.setCustomPromptConfig(
					CustomPromptConfig.newBuilder().setCustomPrompt(options.getCustomPrompt()).build());
			hasFeature = true;
		}

		return hasFeature ? builder.build() : null;
	}

	private static ExplicitDecodingConfig buildExplicitDecodingConfig(GoogleGenAiAudioTranscriptionOptions options) {
		final ExplicitDecodingConfig.Builder builder = ExplicitDecodingConfig.newBuilder()
			.setEncoding(
					ExplicitDecodingConfig.AudioEncoding.valueOf(Objects.requireNonNull(options.getEncoding()).name()));
		if (Objects.nonNull(options.getSampleRateHertz())) {
			builder.setSampleRateHertz(options.getSampleRateHertz());
		}
		if (Objects.nonNull(options.getAudioChannelCount())) {
			builder.setAudioChannelCount(options.getAudioChannelCount());
		}
		return builder.build();
	}

	private static AudioTranscriptionResponse toAudioTranscriptionResponse(RecognizeResponse recognizeResponse,
			GoogleGenAiAudioTranscriptionOptions options) {
		final String text = recognizeResponse.getResultsList()
			.stream()
			.map(SpeechRecognitionResult::getAlternativesList)
			.filter(alternatives -> !alternatives.isEmpty())
			.map(alternatives -> alternatives.get(0))
			.map(SpeechRecognitionAlternative::getTranscript)
			.collect(Collectors.joining(" "));

		final AudioTranscription transcription = new AudioTranscription(text);

		final String language = recognizeResponse.getResultsList()
			.stream()
			.map(SpeechRecognitionResult::getLanguageCode)
			.filter(StringUtils::hasText)
			.findFirst()
			.orElse(null);

		final RecognitionResponseMetadata metadata = recognizeResponse.getMetadata();

		final AudioTranscriptionResponseMetadata responseMetadata = new GoogleGenAiAudioTranscriptionResponseMetadata(
				Durations.toSecondsAsDouble(metadata.getTotalBilledDuration()), language, options.getModel(),
				metadata.getRequestId());

		return new AudioTranscriptionResponse(transcription, responseMetadata);
	}

	private AudioTranscriptionPrompt buildTranscriptionPrompt(AudioTranscriptionPrompt prompt) {
		GoogleGenAiAudioTranscriptionOptions mergedOptions = this.options;

		final AudioTranscriptionOptions requestOptions = prompt.getOptions();
		if (requestOptions instanceof GoogleGenAiAudioTranscriptionOptions googleOptions) {
			mergedOptions = GoogleGenAiAudioTranscriptionOptions.builder()
				.from(this.options)
				.from(googleOptions)
				.build();
		}

		return new AudioTranscriptionPrompt(prompt.getInstructions(), mergedOptions);
	}

}
