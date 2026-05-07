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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.elevenlabs.api.ElevenLabsApi;

/**
 * Options for ElevenLabs text-to-speech.
 *
 * @author Alexandros Pappas
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ElevenLabsTextToSpeechOptions implements TextToSpeechOptions {

	@JsonProperty("model_id")
	private @Nullable String modelId;

	// Path Params
	@JsonProperty("voice_id")
	private @Nullable String voiceId;

	// End Path Params

	// Query Params
	@JsonProperty("enable_logging")
	private @Nullable Boolean enableLogging;

	@JsonProperty("output_format")
	private @Nullable String outputFormat;

	// End Query Params

	@JsonProperty("voice_settings")
	private ElevenLabsApi.SpeechRequest.@Nullable VoiceSettings voiceSettings;

	@JsonProperty("language_code")
	private @Nullable String languageCode;

	@JsonProperty("pronunciation_dictionary_locators")
	private @Nullable List<ElevenLabsApi.SpeechRequest.PronunciationDictionaryLocator> pronunciationDictionaryLocators;

	@JsonProperty("seed")
	private @Nullable Integer seed;

	@JsonProperty("previous_text")
	private @Nullable String previousText;

	@JsonProperty("next_text")
	private @Nullable String nextText;

	@JsonProperty("previous_request_ids")
	private @Nullable List<String> previousRequestIds;

	@JsonProperty("next_request_ids")
	private @Nullable List<String> nextRequestIds;

	@JsonProperty("apply_text_normalization")
	private ElevenLabsApi.SpeechRequest.@Nullable TextNormalizationMode applyTextNormalization;

	@JsonProperty("apply_language_text_normalization")
	private @Nullable Boolean applyLanguageTextNormalization;

	public static Builder builder() {
		return new ElevenLabsTextToSpeechOptions.Builder();
	}

	@Override
	@JsonIgnore
	public @Nullable String getModel() {
		return getModelId();
	}

	@JsonIgnore
	public void setModel(@Nullable String model) {
		setModelId(model);
	}

	public @Nullable String getModelId() {
		return this.modelId;
	}

	public void setModelId(@Nullable String modelId) {
		this.modelId = modelId;
	}

	@Override
	@JsonIgnore
	public @Nullable String getVoice() {
		return getVoiceId();
	}

	@JsonIgnore
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

	@Override
	@JsonIgnore
	public @Nullable String getFormat() {
		return getOutputFormat();
	}

	@JsonIgnore
	public void setFormat(@Nullable String format) {
		setOutputFormat(format);
	}

	public @Nullable String getOutputFormat() {
		return this.outputFormat;
	}

	public void setOutputFormat(@Nullable String outputFormat) {
		this.outputFormat = outputFormat;
	}

	@Override
	@JsonIgnore
	public @Nullable Double getSpeed() {
		if (this.getVoiceSettings() != null) {
			return this.getVoiceSettings().speed();
		}
		return null;
	}

	@JsonIgnore
	public void setSpeed(@Nullable Double speed) {
		if (speed != null) {
			if (this.getVoiceSettings() == null) {
				this.setVoiceSettings(new ElevenLabsApi.SpeechRequest.VoiceSettings(null, null, null, null, speed));
			}
			else {
				this.setVoiceSettings(new ElevenLabsApi.SpeechRequest.VoiceSettings(this.getVoiceSettings().stability(),
						this.getVoiceSettings().similarityBoost(), this.getVoiceSettings().style(),
						this.getVoiceSettings().useSpeakerBoost(), speed));
			}
		}
		else {
			if (this.getVoiceSettings() != null) {
				this.setVoiceSettings(new ElevenLabsApi.SpeechRequest.VoiceSettings(this.getVoiceSettings().stability(),
						this.getVoiceSettings().similarityBoost(), this.getVoiceSettings().style(),
						this.getVoiceSettings().useSpeakerBoost(), null));
			}
		}
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

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ElevenLabsTextToSpeechOptions that)) {
			return false;
		}
		return Objects.equals(this.modelId, that.modelId) && Objects.equals(this.voiceId, that.voiceId)
				&& Objects.equals(this.outputFormat, that.outputFormat)
				&& Objects.equals(this.voiceSettings, that.voiceSettings)
				&& Objects.equals(this.languageCode, that.languageCode)
				&& Objects.equals(this.pronunciationDictionaryLocators, that.pronunciationDictionaryLocators)
				&& Objects.equals(this.seed, that.seed) && Objects.equals(this.previousText, that.previousText)
				&& Objects.equals(this.nextText, that.nextText)
				&& Objects.equals(this.previousRequestIds, that.previousRequestIds)
				&& Objects.equals(this.applyTextNormalization, that.applyTextNormalization)
				&& Objects.equals(this.nextRequestIds, that.nextRequestIds)
				&& Objects.equals(this.applyLanguageTextNormalization, that.applyLanguageTextNormalization);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.modelId, this.voiceId, this.outputFormat, this.voiceSettings, this.languageCode,
				this.pronunciationDictionaryLocators, this.seed, this.previousText, this.nextText,
				this.previousRequestIds, this.nextRequestIds, this.applyTextNormalization,
				this.applyLanguageTextNormalization);
	}

	@Override
	public String toString() {
		return "ElevenLabsSpeechOptions{" + "modelId='" + this.modelId + '\'' + ", voiceId='" + this.voiceId + '\''
				+ ", outputFormat='" + this.outputFormat + '\'' + ", voiceSettings=" + this.voiceSettings
				+ ", languageCode='" + this.languageCode + '\'' + ", pronunciationDictionaryLocators="
				+ this.pronunciationDictionaryLocators + ", seed=" + this.seed + ", previousText='" + this.previousText
				+ '\'' + ", nextText='" + this.nextText + '\'' + ", previousRequestIds=" + this.previousRequestIds
				+ ", nextRequestIds=" + this.nextRequestIds + ", applyTextNormalization=" + this.applyTextNormalization
				+ ", applyLanguageTextNormalization=" + this.applyLanguageTextNormalization + '}';
	}

	@Override
	@SuppressWarnings("unchecked")
	public ElevenLabsTextToSpeechOptions copy() {
		return ElevenLabsTextToSpeechOptions.builder()
			.modelId(this.getModelId())
			.voice(this.getVoice())
			.voiceId(this.getVoiceId())
			.format(this.getFormat())
			.outputFormat(this.getOutputFormat())
			.voiceSettings(this.getVoiceSettings())
			.languageCode(this.getLanguageCode())
			.pronunciationDictionaryLocators(this.getPronunciationDictionaryLocators())
			.seed(this.getSeed())
			.previousText(this.getPreviousText())
			.nextText(this.getNextText())
			.previousRequestIds(this.getPreviousRequestIds())
			.nextRequestIds(this.getNextRequestIds())
			.applyTextNormalization(this.getApplyTextNormalization())
			.applyLanguageTextNormalization(this.getApplyLanguageTextNormalization())
			.build();
	}

	public static final class Builder {

		private final ElevenLabsTextToSpeechOptions options = new ElevenLabsTextToSpeechOptions();

		/**
		 * Sets the model ID using the generic 'model' property. This is an alias for
		 * {@link #modelId(String)}.
		 * @param model The model ID to use.
		 * @return this builder.
		 */
		public Builder model(@Nullable String model) {
			this.options.setModel(model);
			return this;
		}

		/**
		 * Sets the model ID using the ElevenLabs specific 'modelId' property. This is an
		 * alias for {@link #model(String)}.
		 * @param modelId The model ID to use.
		 * @return this builder.
		 */
		public Builder modelId(@Nullable String modelId) {
			this.options.setModelId(modelId);
			return this;
		}

		/**
		 * Sets the voice ID using the generic 'voice' property. This is an alias for
		 * {@link #voiceId(String)}.
		 * @param voice The voice ID to use.
		 * @return this builder.
		 */
		public Builder voice(@Nullable String voice) {
			this.options.setVoice(voice);
			return this;
		}

		/**
		 * Sets the voice ID using the ElevenLabs specific 'voiceId' property. This is an
		 * alias for {@link #voice(String)}.
		 * @param voiceId The voice ID to use.
		 * @return this builder.
		 */
		public Builder voiceId(@Nullable String voiceId) {
			this.options.setVoiceId(voiceId);
			return this;
		}

		public Builder format(@Nullable String format) {
			this.options.setFormat(format);
			return this;
		}

		public Builder outputFormat(@Nullable String outputFormat) {
			this.options.setOutputFormat(outputFormat);
			return this;
		}

		public Builder voiceSettings(ElevenLabsApi.SpeechRequest.@Nullable VoiceSettings voiceSettings) {
			this.options.setVoiceSettings(voiceSettings);
			return this;
		}

		public Builder languageCode(@Nullable String languageCode) {
			this.options.setLanguageCode(languageCode);
			return this;
		}

		public Builder pronunciationDictionaryLocators(
				@Nullable List<ElevenLabsApi.SpeechRequest.PronunciationDictionaryLocator> pronunciationDictionaryLocators) {
			this.options.setPronunciationDictionaryLocators(pronunciationDictionaryLocators);
			return this;
		}

		public Builder seed(@Nullable Integer seed) {
			this.options.setSeed(seed);
			return this;
		}

		public Builder previousText(@Nullable String previousText) {
			this.options.setPreviousText(previousText);
			return this;
		}

		public Builder nextText(@Nullable String nextText) {
			this.options.setNextText(nextText);
			return this;
		}

		public Builder previousRequestIds(@Nullable List<String> previousRequestIds) {
			this.options.setPreviousRequestIds(previousRequestIds);
			return this;
		}

		public Builder nextRequestIds(@Nullable List<String> nextRequestIds) {
			this.options.setNextRequestIds(nextRequestIds);
			return this;
		}

		public Builder applyTextNormalization(
				ElevenLabsApi.SpeechRequest.@Nullable TextNormalizationMode applyTextNormalization) {
			this.options.setApplyTextNormalization(applyTextNormalization);
			return this;
		}

		public Builder applyLanguageTextNormalization(@Nullable Boolean applyLanguageTextNormalization) {
			this.options.setApplyLanguageTextNormalization(applyLanguageTextNormalization);
			return this;
		}

		public ElevenLabsTextToSpeechOptions build() {
			return this.options;
		}

	}

}
