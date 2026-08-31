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

package org.springframework.ai.google.genai.tts;

import java.util.List;
import java.util.Objects;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.CustomPronunciationParams;
import com.google.cloud.texttospeech.v1.CustomPronunciations;
import com.google.cloud.texttospeech.v1.CustomVoiceParams;
import com.google.cloud.texttospeech.v1.MultiSpeakerVoiceConfig;
import com.google.cloud.texttospeech.v1.MultispeakerPrebuiltVoice;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.VoiceCloneParams;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import io.micrometer.observation.ObservationRegistry;
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
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Implementation of the {@link TextToSpeechModel} interface for Google Gemini-TTS, backed
 * by the Cloud Text-to-Speech V2 API.
 *
 * @author Olivier Le Quellec
 * @since 2.0.2
 */
public class GoogleGenAiTextToSpeechModel implements TextToSpeechModel {

	private static final TextToSpeechModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultTextToSpeechModelObservationConvention();

	private final GoogleGenAiTextToSpeechConnectionDetails connectionDetails;

	private final GoogleGenAiAudioSpeechOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	private final ObservationRegistry observationRegistry;

	private TextToSpeechModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public GoogleGenAiTextToSpeechModel(GoogleGenAiTextToSpeechConnectionDetails connectionDetails,
			GoogleGenAiAudioSpeechOptions defaultOptions) {
		this(connectionDetails, defaultOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public GoogleGenAiTextToSpeechModel(GoogleGenAiTextToSpeechConnectionDetails connectionDetails,
			GoogleGenAiAudioSpeechOptions defaultOptions, RetryTemplate retryTemplate) {
		this(connectionDetails, defaultOptions, retryTemplate, ObservationRegistry.NOOP);
	}

	public GoogleGenAiTextToSpeechModel(GoogleGenAiTextToSpeechConnectionDetails connectionDetails,
			GoogleGenAiAudioSpeechOptions defaultOptions, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		Assert.notNull(connectionDetails, "connectionDetails must not be null");
		Assert.notNull(defaultOptions, "defaultOptions must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		this.connectionDetails = connectionDetails;
		this.defaultOptions = defaultOptions;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
		final TextToSpeechPrompt textToSpeechPrompt = buildTextToSpeechPrompt(prompt);

		final TextToSpeechModelObservationContext observationContext = TextToSpeechModelObservationContext.builder()
			.textToSpeechPrompt(textToSpeechPrompt)
			.provider(AiProvider.GOOGLE_GENAI_AI.value())
			.build();

		return TextToSpeechModelObservationDocumentation.TEXT_TO_SPEECH_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				final String text = textToSpeechPrompt.getInstructions().getText();
				Assert.hasText(text, "TextToSpeechPrompt must contain non-empty text");

				final GoogleGenAiAudioSpeechOptions options = (GoogleGenAiAudioSpeechOptions) textToSpeechPrompt
					.getOptions();

				final SynthesisInput input = createSynthesisInput(text, options);
				final VoiceSelectionParams voice = createVoiceSelectionParams(options);
				final AudioConfig audioConfig = createAudioConfig(options);

				final SynthesizeSpeechResponse response = RetryUtils.execute(this.retryTemplate,
						() -> this.connectionDetails.getTextToSpeechClient()
							.synthesizeSpeech(input, voice, audioConfig));

				final TextToSpeechResponse ttsResponse = new TextToSpeechResponse(
						List.of(new Speech(response.getAudioContent().toByteArray())));

				observationContext.setResponse(ttsResponse);

				return ttsResponse;
			});
	}

	public void setObservationConvention(@Nullable TextToSpeechModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	@Override
	public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
		return Flux.just(call(prompt));
	}

	private SynthesisInput createSynthesisInput(String text, GoogleGenAiAudioSpeechOptions options) {
		boolean hasSsml = StringUtils.hasText(options.getSsml());
		boolean hasMarkup = StringUtils.hasText(options.getMarkup());
		Assert.state(!(hasSsml && hasMarkup), "'ssml' and 'markup' are mutually exclusive; set only one of them");

		SynthesisInput.Builder inputBuilder = SynthesisInput.newBuilder();
		if (hasSsml) {
			inputBuilder.setSsml(options.getSsml());
		}
		else if (hasMarkup) {
			inputBuilder.setMarkup(options.getMarkup());
		}
		else {
			inputBuilder.setText(text);
		}
		if (StringUtils.hasText(options.getStylePrompt())) {
			inputBuilder.setPrompt(options.getStylePrompt());
		}
		if (!CollectionUtils.isEmpty(options.getCustomPronunciations())) {
			inputBuilder.setCustomPronunciations(createCustomPronunciations(options.getCustomPronunciations()));
		}
		return inputBuilder.build();
	}

	private CustomPronunciations createCustomPronunciations(
			List<GoogleGenAiAudioSpeechOptions.CustomPronunciation> customPronunciations) {
		CustomPronunciations.Builder builder = CustomPronunciations.newBuilder();
		for (GoogleGenAiAudioSpeechOptions.CustomPronunciation customPronunciation : customPronunciations) {
			CustomPronunciationParams.Builder paramsBuilder = CustomPronunciationParams.newBuilder()
				.setPhrase(customPronunciation.phrase())
				.setPronunciation(customPronunciation.pronunciation());
			if (customPronunciation.phoneticEncoding() != null) {
				paramsBuilder.setPhoneticEncoding(CustomPronunciationParams.PhoneticEncoding
					.valueOf("PHONETIC_ENCODING_" + customPronunciation.phoneticEncoding().name()));
			}
			builder.addPronunciations(paramsBuilder.build());
		}
		return builder.build();
	}

	private VoiceSelectionParams createVoiceSelectionParams(GoogleGenAiAudioSpeechOptions options) {
		boolean hasVoiceName = StringUtils.hasText(options.getVoiceName());
		List<GoogleGenAiAudioSpeechOptions.SpeakerVoiceConfig> speakerVoiceConfigs = options.getSpeakerVoiceConfigs();
		boolean hasSpeakerVoiceConfigs = !CollectionUtils.isEmpty(speakerVoiceConfigs);
		Assert.state(!(hasVoiceName && hasSpeakerVoiceConfigs),
				"'voiceName' and 'speakerVoiceConfigs' are mutually exclusive; set only one of them");

		VoiceSelectionParams.Builder voiceBuilder = VoiceSelectionParams.newBuilder();
		if (StringUtils.hasText(options.getModel())) {
			voiceBuilder.setModelName(options.getModel());
		}
		if (StringUtils.hasText(options.getLanguageCode())) {
			voiceBuilder.setLanguageCode(options.getLanguageCode());
		}
		if (options.getSsmlGender() != null) {
			voiceBuilder.setSsmlGender(
					com.google.cloud.texttospeech.v1.SsmlVoiceGender.valueOf(options.getSsmlGender().name()));
		}
		if (StringUtils.hasText(options.getCustomVoiceModel())) {
			voiceBuilder.setCustomVoice(CustomVoiceParams.newBuilder().setModel(options.getCustomVoiceModel()));
		}
		if (StringUtils.hasText(options.getVoiceCloningKey())) {
			voiceBuilder.setVoiceClone(VoiceCloneParams.newBuilder().setVoiceCloningKey(options.getVoiceCloningKey()));
		}
		if (hasVoiceName) {
			voiceBuilder.setName(options.getVoiceName());
		}
		else if (speakerVoiceConfigs != null && hasSpeakerVoiceConfigs) {
			voiceBuilder.setMultiSpeakerVoiceConfig(createMultiSpeakerVoiceConfig(speakerVoiceConfigs));
		}
		return voiceBuilder.build();
	}

	private MultiSpeakerVoiceConfig createMultiSpeakerVoiceConfig(
			List<GoogleGenAiAudioSpeechOptions.SpeakerVoiceConfig> speakerVoiceConfigs) {
		MultiSpeakerVoiceConfig.Builder builder = MultiSpeakerVoiceConfig.newBuilder();
		for (GoogleGenAiAudioSpeechOptions.SpeakerVoiceConfig config : speakerVoiceConfigs) {
			builder.addSpeakerVoiceConfigs(MultispeakerPrebuiltVoice.newBuilder()
				.setSpeakerAlias(config.speakerAlias())
				.setSpeakerId(config.speakerId())
				.build());
		}
		return builder.build();
	}

	private AudioConfig createAudioConfig(GoogleGenAiAudioSpeechOptions options) {
		GoogleGenAiAudioSpeechOptions.AudioEncoding audioEncoding = options.getAudioEncoding() != null
				? options.getAudioEncoding() : GoogleGenAiAudioSpeechOptions.DEFAULT_AUDIO_ENCODING;
		AudioConfig.Builder audioConfigBuilder = AudioConfig.newBuilder()
			.setAudioEncoding(com.google.cloud.texttospeech.v1.AudioEncoding.valueOf(audioEncoding.name()));
		if (options.getSpeed() != null) {
			audioConfigBuilder.setSpeakingRate(options.getSpeed());
		}
		if (options.getPitch() != null) {
			audioConfigBuilder.setPitch(options.getPitch());
		}
		if (options.getVolumeGainDb() != null) {
			audioConfigBuilder.setVolumeGainDb(options.getVolumeGainDb());
		}
		if (options.getSampleRateHertz() != null) {
			audioConfigBuilder.setSampleRateHertz(options.getSampleRateHertz());
		}
		if (!CollectionUtils.isEmpty(options.getEffectsProfileIds())) {
			audioConfigBuilder.addAllEffectsProfileId(options.getEffectsProfileIds());
		}
		return audioConfigBuilder.build();
	}

	private TextToSpeechPrompt buildTextToSpeechPrompt(TextToSpeechPrompt textToSpeechPrompt) {
		GoogleGenAiAudioSpeechOptions mergedOptions = this.defaultOptions;

		final TextToSpeechOptions requestOptions = textToSpeechPrompt.getOptions();
		if (Objects.nonNull(requestOptions)) {
			final GoogleGenAiAudioSpeechOptions.Builder builder = GoogleGenAiAudioSpeechOptions.builder()
				.model(ModelOptionsUtils.mergeOption(requestOptions.getModel(), this.defaultOptions.getModel()))
				.voiceName(ModelOptionsUtils.mergeOption(requestOptions.getVoice(), this.defaultOptions.getVoiceName()))
				.speed(ModelOptionsUtils.mergeOption(requestOptions.getSpeed(), this.defaultOptions.getSpeed()));

			if (requestOptions instanceof GoogleGenAiAudioSpeechOptions googleOptions) {
				builder
					.languageCode(ModelOptionsUtils.mergeOption(googleOptions.getLanguageCode(),
							this.defaultOptions.getLanguageCode()))
					.stylePrompt(ModelOptionsUtils.mergeOption(googleOptions.getStylePrompt(),
							this.defaultOptions.getStylePrompt()))
					.speakerVoiceConfigs(ModelOptionsUtils.mergeOption(googleOptions.getSpeakerVoiceConfigs(),
							this.defaultOptions.getSpeakerVoiceConfigs()))
					.ssml(ModelOptionsUtils.mergeOption(googleOptions.getSsml(), this.defaultOptions.getSsml()))
					.markup(ModelOptionsUtils.mergeOption(googleOptions.getMarkup(), this.defaultOptions.getMarkup()))
					.customPronunciations(ModelOptionsUtils.mergeOption(googleOptions.getCustomPronunciations(),
							this.defaultOptions.getCustomPronunciations()))
					.ssmlGender(ModelOptionsUtils.mergeOption(googleOptions.getSsmlGender(),
							this.defaultOptions.getSsmlGender()))
					.customVoiceModel(ModelOptionsUtils.mergeOption(googleOptions.getCustomVoiceModel(),
							this.defaultOptions.getCustomVoiceModel()))
					.voiceCloningKey(ModelOptionsUtils.mergeOption(googleOptions.getVoiceCloningKey(),
							this.defaultOptions.getVoiceCloningKey()))
					.audioEncoding(ModelOptionsUtils.mergeOption(googleOptions.getAudioEncoding(),
							this.defaultOptions.getAudioEncoding()))
					.pitch(ModelOptionsUtils.mergeOption(googleOptions.getPitch(), this.defaultOptions.getPitch()))
					.volumeGainDb(ModelOptionsUtils.mergeOption(googleOptions.getVolumeGainDb(),
							this.defaultOptions.getVolumeGainDb()))
					.sampleRateHertz(ModelOptionsUtils.mergeOption(googleOptions.getSampleRateHertz(),
							this.defaultOptions.getSampleRateHertz()))
					.effectsProfileIds(ModelOptionsUtils.mergeOption(googleOptions.getEffectsProfileIds(),
							this.defaultOptions.getEffectsProfileIds()));
			}

			mergedOptions = builder.build();
		}

		return new TextToSpeechPrompt(textToSpeechPrompt.getInstructions(), mergedOptions);
	}

	@Override
	public GoogleGenAiAudioSpeechOptions getOptions() {
		return this.defaultOptions;
	}

	/**
	 * @deprecated use {@link #getOptions()} instead.
	 */
	@Deprecated(forRemoval = true)
	@Override
	@SuppressWarnings("removal")
	public GoogleGenAiAudioSpeechOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	public static final class Builder {

		private @Nullable GoogleGenAiTextToSpeechConnectionDetails connectionDetails;

		private GoogleGenAiAudioSpeechOptions options = GoogleGenAiAudioSpeechOptions.builder()
			.model(GoogleGenAiAudioSpeechOptions.DEFAULT_MODEL)
			.build();

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private Builder() {
		}

		public Builder connectionDetails(GoogleGenAiTextToSpeechConnectionDetails connectionDetails) {
			this.connectionDetails = connectionDetails;
			return this;
		}

		public Builder options(GoogleGenAiAudioSpeechOptions options) {
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

		public GoogleGenAiTextToSpeechModel build() {
			Assert.notNull(this.connectionDetails, "connectionDetails must not be null");
			return new GoogleGenAiTextToSpeechModel(this.connectionDetails, this.options, this.retryTemplate,
					this.observationRegistry);
		}

	}

}
