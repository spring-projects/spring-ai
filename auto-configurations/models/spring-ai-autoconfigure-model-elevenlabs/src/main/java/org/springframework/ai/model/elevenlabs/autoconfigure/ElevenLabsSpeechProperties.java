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

package org.springframework.ai.model.elevenlabs.autoconfigure;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.elevenlabs.ElevenLabsTextToSpeechOptions;
import org.springframework.ai.elevenlabs.api.ElevenLabsApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Configuration properties for the ElevenLabs Text-to-Speech API.
 *
 * @author Alexandros Pappas
 * @author Sebastien Deleuze
 */
@ConfigurationProperties(ElevenLabsSpeechProperties.CONFIG_PREFIX)
public class ElevenLabsSpeechProperties {

	public static final String CONFIG_PREFIX = "spring.ai.elevenlabs.tts";

	public static final String DEFAULT_MODEL_ID = "eleven_turbo_v2_5";

	private static final String DEFAULT_VOICE_ID = "9BWtsMINqrJLrRacOk9x";

	private static final ElevenLabsApi.OutputFormat DEFAULT_OUTPUT_FORMAT = ElevenLabsApi.OutputFormat.MP3_22050_32;

	private @Nullable String modelId = DEFAULT_MODEL_ID;

	private @Nullable String voiceId = DEFAULT_VOICE_ID;

	private @Nullable Boolean enableLogging;

	private @Nullable String outputFormat = DEFAULT_OUTPUT_FORMAT.getValue();

	private ElevenLabsApi.SpeechRequest.@Nullable VoiceSettings voiceSettings;

	private @Nullable String languageCode;

	private @Nullable List<ElevenLabsApi.SpeechRequest.PronunciationDictionaryLocator> pronunciationDictionaryLocators;

	private @Nullable Integer seed;

	private @Nullable String previousText;

	private @Nullable String nextText;

	private @Nullable List<String> previousRequestIds;

	private @Nullable List<String> nextRequestIds;

	private ElevenLabsApi.SpeechRequest.@Nullable TextNormalizationMode applyTextNormalization;

	private @Nullable Boolean applyLanguageTextNormalization;

	public @Nullable String getModel() {
		return getModelId();
	}

	public void setModel(@Nullable String model) {
		setModelId(model);
	}

	public @Nullable String getModelId() {
		return this.modelId;
	}

	public void setModelId(@Nullable String modelId) {
		this.modelId = modelId;
	}

	public @Nullable String getVoice() {
		return getVoiceId();
	}

	public void setVoice(@Nullable String voice) {
		setVoiceId(voice);
	}

	public @Nullable String getVoiceId() {
		return this.voiceId;
	}

	public void setVoiceId(@Nullable String voiceId) {
		this.voiceId = voiceId;
	}

	public @Nullable Boolean getEnableLogging() {
		return this.enableLogging;
	}

	public void setEnableLogging(@Nullable Boolean enableLogging) {
		this.enableLogging = enableLogging;
	}

	public @Nullable String getFormat() {
		return getOutputFormat();
	}

	public void setFormat(@Nullable String format) {
		setOutputFormat(format);
	}

	public @Nullable String getOutputFormat() {
		return this.outputFormat;
	}

	public void setOutputFormat(@Nullable String outputFormat) {
		this.outputFormat = outputFormat;
	}

	public ElevenLabsApi.SpeechRequest.@Nullable VoiceSettings getVoiceSettings() {
		return this.voiceSettings;
	}

	public void setVoiceSettings(ElevenLabsApi.SpeechRequest.@Nullable VoiceSettings voiceSettings) {
		this.voiceSettings = voiceSettings;
	}

	public @Nullable String getLanguageCode() {
		return this.languageCode;
	}

	public void setLanguageCode(@Nullable String languageCode) {
		this.languageCode = languageCode;
	}

	public @Nullable List<ElevenLabsApi.SpeechRequest.PronunciationDictionaryLocator> getPronunciationDictionaryLocators() {
		return this.pronunciationDictionaryLocators;
	}

	public void setPronunciationDictionaryLocators(
			@Nullable List<ElevenLabsApi.SpeechRequest.PronunciationDictionaryLocator> pronunciationDictionaryLocators) {
		this.pronunciationDictionaryLocators = pronunciationDictionaryLocators;
	}

	public @Nullable Integer getSeed() {
		return this.seed;
	}

	public void setSeed(@Nullable Integer seed) {
		this.seed = seed;
	}

	public @Nullable String getPreviousText() {
		return this.previousText;
	}

	public void setPreviousText(@Nullable String previousText) {
		this.previousText = previousText;
	}

	public @Nullable String getNextText() {
		return this.nextText;
	}

	public void setNextText(@Nullable String nextText) {
		this.nextText = nextText;
	}

	public @Nullable List<String> getPreviousRequestIds() {
		return this.previousRequestIds;
	}

	public void setPreviousRequestIds(@Nullable List<String> previousRequestIds) {
		this.previousRequestIds = previousRequestIds;
	}

	public @Nullable List<String> getNextRequestIds() {
		return this.nextRequestIds;
	}

	public void setNextRequestIds(@Nullable List<String> nextRequestIds) {
		this.nextRequestIds = nextRequestIds;
	}

	public ElevenLabsApi.SpeechRequest.@Nullable TextNormalizationMode getApplyTextNormalization() {
		return this.applyTextNormalization;
	}

	public void setApplyTextNormalization(
			ElevenLabsApi.SpeechRequest.@Nullable TextNormalizationMode applyTextNormalization) {
		this.applyTextNormalization = applyTextNormalization;
	}

	public @Nullable Boolean getApplyLanguageTextNormalization() {
		return this.applyLanguageTextNormalization;
	}

	public void setApplyLanguageTextNormalization(@Nullable Boolean applyLanguageTextNormalization) {
		this.applyLanguageTextNormalization = applyLanguageTextNormalization;
	}

	public ElevenLabsTextToSpeechOptions toOptions() {
		ElevenLabsTextToSpeechOptions optionsObject = ElevenLabsTextToSpeechOptions.builder()
			.modelId(this.modelId)
			.voiceId(this.voiceId)
			.outputFormat(this.outputFormat)
			.voiceSettings(this.voiceSettings)
			.languageCode(this.languageCode)
			.pronunciationDictionaryLocators(this.pronunciationDictionaryLocators)
			.seed(this.seed)
			.previousText(this.previousText)
			.nextText(this.nextText)
			.previousRequestIds(this.previousRequestIds)
			.nextRequestIds(this.nextRequestIds)
			.applyTextNormalization(this.applyTextNormalization)
			.applyLanguageTextNormalization(this.applyLanguageTextNormalization)
			.build();
		optionsObject.setEnableLogging(this.enableLogging);
		return optionsObject;
	}

	private Options options = new Options();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getModel() {
			return ElevenLabsSpeechProperties.this.getModel();
		}

		public void setModel(@Nullable String model) {
			ElevenLabsSpeechProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts.model-id")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getModelId() {
			return ElevenLabsSpeechProperties.this.getModelId();
		}

		public void setModelId(@Nullable String modelId) {
			ElevenLabsSpeechProperties.this.setModelId(modelId);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts.voice")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getVoice() {
			return ElevenLabsSpeechProperties.this.getVoice();
		}

		public void setVoice(@Nullable String voice) {
			ElevenLabsSpeechProperties.this.setVoice(voice);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts.voice-id")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getVoiceId() {
			return ElevenLabsSpeechProperties.this.getVoiceId();
		}

		public void setVoiceId(@Nullable String voiceId) {
			ElevenLabsSpeechProperties.this.setVoiceId(voiceId);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts.enable-logging")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getEnableLogging() {
			return ElevenLabsSpeechProperties.this.getEnableLogging();
		}

		public void setEnableLogging(@Nullable Boolean enableLogging) {
			ElevenLabsSpeechProperties.this.setEnableLogging(enableLogging);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts.format")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getFormat() {
			return ElevenLabsSpeechProperties.this.getFormat();
		}

		public void setFormat(@Nullable String format) {
			ElevenLabsSpeechProperties.this.setFormat(format);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts.output-format")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getOutputFormat() {
			return ElevenLabsSpeechProperties.this.getOutputFormat();
		}

		public void setOutputFormat(@Nullable String outputFormat) {
			ElevenLabsSpeechProperties.this.setOutputFormat(outputFormat);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts.voice-settings")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public ElevenLabsApi.SpeechRequest.@Nullable VoiceSettings getVoiceSettings() {
			return ElevenLabsSpeechProperties.this.getVoiceSettings();
		}

		public void setVoiceSettings(ElevenLabsApi.SpeechRequest.@Nullable VoiceSettings voiceSettings) {
			ElevenLabsSpeechProperties.this.setVoiceSettings(voiceSettings);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts.language-code")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getLanguageCode() {
			return ElevenLabsSpeechProperties.this.getLanguageCode();
		}

		public void setLanguageCode(@Nullable String languageCode) {
			ElevenLabsSpeechProperties.this.setLanguageCode(languageCode);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts.pronunciation-dictionary-locators")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable List<ElevenLabsApi.SpeechRequest.PronunciationDictionaryLocator> getPronunciationDictionaryLocators() {
			return ElevenLabsSpeechProperties.this.getPronunciationDictionaryLocators();
		}

		public void setPronunciationDictionaryLocators(
				@Nullable List<ElevenLabsApi.SpeechRequest.PronunciationDictionaryLocator> pronunciationDictionaryLocators) {
			ElevenLabsSpeechProperties.this.setPronunciationDictionaryLocators(pronunciationDictionaryLocators);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts.seed")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getSeed() {
			return ElevenLabsSpeechProperties.this.getSeed();
		}

		public void setSeed(@Nullable Integer seed) {
			ElevenLabsSpeechProperties.this.setSeed(seed);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts.previous-text")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getPreviousText() {
			return ElevenLabsSpeechProperties.this.getPreviousText();
		}

		public void setPreviousText(@Nullable String previousText) {
			ElevenLabsSpeechProperties.this.setPreviousText(previousText);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts.next-text")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getNextText() {
			return ElevenLabsSpeechProperties.this.getNextText();
		}

		public void setNextText(@Nullable String nextText) {
			ElevenLabsSpeechProperties.this.setNextText(nextText);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts.previous-request-ids")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable List<String> getPreviousRequestIds() {
			return ElevenLabsSpeechProperties.this.getPreviousRequestIds();
		}

		public void setPreviousRequestIds(@Nullable List<String> previousRequestIds) {
			ElevenLabsSpeechProperties.this.setPreviousRequestIds(previousRequestIds);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts.next-request-ids")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable List<String> getNextRequestIds() {
			return ElevenLabsSpeechProperties.this.getNextRequestIds();
		}

		public void setNextRequestIds(@Nullable List<String> nextRequestIds) {
			ElevenLabsSpeechProperties.this.setNextRequestIds(nextRequestIds);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts.apply-text-normalization")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public ElevenLabsApi.SpeechRequest.@Nullable TextNormalizationMode getApplyTextNormalization() {
			return ElevenLabsSpeechProperties.this.getApplyTextNormalization();
		}

		public void setApplyTextNormalization(
				ElevenLabsApi.SpeechRequest.@Nullable TextNormalizationMode applyTextNormalization) {
			ElevenLabsSpeechProperties.this.setApplyTextNormalization(applyTextNormalization);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.elevenlabs.tts.apply-language-text-normalization")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getApplyLanguageTextNormalization() {
			return ElevenLabsSpeechProperties.this.getApplyLanguageTextNormalization();
		}

		public void setApplyLanguageTextNormalization(@Nullable Boolean applyLanguageTextNormalization) {
			ElevenLabsSpeechProperties.this.setApplyLanguageTextNormalization(applyLanguageTextNormalization);
		}

	}

}
