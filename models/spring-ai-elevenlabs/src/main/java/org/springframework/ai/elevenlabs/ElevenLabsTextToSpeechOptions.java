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

import org.jspecify.annotations.Nullable;

import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.elevenlabs.api.ElevenLabsApi;

/**
 * Options for ElevenLabs text-to-speech.
 *
 * @author Alexandros Pappas
 * @author Sebastien Deleuze
 */
public class ElevenLabsTextToSpeechOptions implements TextToSpeechOptions {

	private final @Nullable String modelId;

	// Path Params
	private final @Nullable String voiceId;

	// End Path Params

	// Query Params
	private final @Nullable Boolean enableLogging;

	private final @Nullable String outputFormat;

	// End Query Params

	private final ElevenLabsApi.SpeechRequest.@Nullable VoiceSettings voiceSettings;

	private final @Nullable String languageCode;

	private final @Nullable List<ElevenLabsApi.SpeechRequest.PronunciationDictionaryLocator> pronunciationDictionaryLocators;

	private final @Nullable Integer seed;

	private final @Nullable String previousText;

	private final @Nullable String nextText;

	private final @Nullable List<String> previousRequestIds;

	private final @Nullable List<String> nextRequestIds;

	private final ElevenLabsApi.SpeechRequest.@Nullable TextNormalizationMode applyTextNormalization;

	private final @Nullable Boolean applyLanguageTextNormalization;

	protected ElevenLabsTextToSpeechOptions(@Nullable String modelId, @Nullable String voiceId,
			@Nullable Boolean enableLogging, @Nullable String outputFormat,
			ElevenLabsApi.SpeechRequest.@Nullable VoiceSettings voiceSettings, @Nullable String languageCode,
			@Nullable List<ElevenLabsApi.SpeechRequest.PronunciationDictionaryLocator> pronunciationDictionaryLocators,
			@Nullable Integer seed, @Nullable String previousText, @Nullable String nextText,
			@Nullable List<String> previousRequestIds, @Nullable List<String> nextRequestIds,
			ElevenLabsApi.SpeechRequest.@Nullable TextNormalizationMode applyTextNormalization,
			@Nullable Boolean applyLanguageTextNormalization) {
		this.modelId = modelId;
		this.voiceId = voiceId;
		this.enableLogging = enableLogging;
		this.outputFormat = outputFormat;
		this.voiceSettings = voiceSettings;
		this.languageCode = languageCode;
		this.pronunciationDictionaryLocators = pronunciationDictionaryLocators != null
				? List.copyOf(pronunciationDictionaryLocators) : null;
		this.seed = seed;
		this.previousText = previousText;
		this.nextText = nextText;
		this.previousRequestIds = previousRequestIds != null ? List.copyOf(previousRequestIds) : null;
		this.nextRequestIds = nextRequestIds != null ? List.copyOf(nextRequestIds) : null;
		this.applyTextNormalization = applyTextNormalization;
		this.applyLanguageTextNormalization = applyLanguageTextNormalization;
	}

	public static ElevenLabsTextToSpeechOptions.Builder builder() {
		return new ElevenLabsTextToSpeechOptions.Builder();
	}

	@Override
	public @Nullable String getModel() {
		return getModelId();
	}

	public @Nullable String getModelId() {
		return this.modelId;
	}

	@Override
	public @Nullable String getVoice() {
		return getVoiceId();
	}

	public @Nullable String getVoiceId() {
		return this.voiceId;
	}

	public @Nullable Boolean getEnableLogging() {
		return this.enableLogging;
	}

	@Override
	public @Nullable String getFormat() {
		return getOutputFormat();
	}

	public @Nullable String getOutputFormat() {
		return this.outputFormat;
	}

	@Override
	public @Nullable Double getSpeed() {
		if (this.getVoiceSettings() != null) {
			return this.getVoiceSettings().speed();
		}
		return null;
	}

	public ElevenLabsApi.SpeechRequest.@Nullable VoiceSettings getVoiceSettings() {
		return this.voiceSettings;
	}

	public @Nullable String getLanguageCode() {
		return this.languageCode;
	}

	public @Nullable List<ElevenLabsApi.SpeechRequest.PronunciationDictionaryLocator> getPronunciationDictionaryLocators() {
		return this.pronunciationDictionaryLocators;
	}

	public @Nullable Integer getSeed() {
		return this.seed;
	}

	public @Nullable String getPreviousText() {
		return this.previousText;
	}

	public @Nullable String getNextText() {
		return this.nextText;
	}

	public @Nullable List<String> getPreviousRequestIds() {
		return this.previousRequestIds;
	}

	public @Nullable List<String> getNextRequestIds() {
		return this.nextRequestIds;
	}

	public ElevenLabsApi.SpeechRequest.@Nullable TextNormalizationMode getApplyTextNormalization() {
		return this.applyTextNormalization;
	}

	public @Nullable Boolean getApplyLanguageTextNormalization() {
		return this.applyLanguageTextNormalization;
	}

	@Override
	public boolean equals(@Nullable Object o) {
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

	public static final class Builder {

		private @Nullable String modelId;

		private @Nullable String voiceId;

		private @Nullable Boolean enableLogging;

		private @Nullable String outputFormat;

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

		/**
		 * Sets the model ID using the generic 'model' property. This is an alias for
		 * {@link #modelId(String)}.
		 * @param model The model ID to use.
		 * @return this builder.
		 */
		public Builder model(@Nullable String model) {
			this.modelId = model;
			return this;
		}

		/**
		 * Sets the model ID using the ElevenLabs specific 'modelId' property. This is an
		 * alias for {@link #model(String)}.
		 * @param modelId The model ID to use.
		 * @return this builder.
		 */
		public Builder modelId(@Nullable String modelId) {
			this.modelId = modelId;
			return this;
		}

		/**
		 * Sets the voice ID using the generic 'voice' property. This is an alias for
		 * {@link #voiceId(String)}.
		 * @param voice The voice ID to use.
		 * @return this builder.
		 */
		public Builder voice(@Nullable String voice) {
			this.voiceId = voice;
			return this;
		}

		/**
		 * Sets the voice ID using the ElevenLabs specific 'voiceId' property. This is an
		 * alias for {@link #voice(String)}.
		 * @param voiceId The voice ID to use.
		 * @return this builder.
		 */
		public Builder voiceId(@Nullable String voiceId) {
			this.voiceId = voiceId;
			return this;
		}

		public Builder enableLogging(@Nullable Boolean enableLogging) {
			this.enableLogging = enableLogging;
			return this;
		}

		public Builder format(@Nullable String format) {
			this.outputFormat = format;
			return this;
		}

		public Builder outputFormat(@Nullable String outputFormat) {
			this.outputFormat = outputFormat;
			return this;
		}

		public Builder speed(@Nullable Double speed) {
			if (speed != null) {
				if (this.voiceSettings == null) {
					this.voiceSettings = new ElevenLabsApi.SpeechRequest.VoiceSettings(null, null, null, null, speed);
				}
				else {
					this.voiceSettings = new ElevenLabsApi.SpeechRequest.VoiceSettings(this.voiceSettings.stability(),
							this.voiceSettings.similarityBoost(), this.voiceSettings.style(),
							this.voiceSettings.useSpeakerBoost(), speed);
				}
			}
			else {
				if (this.voiceSettings != null) {
					this.voiceSettings = new ElevenLabsApi.SpeechRequest.VoiceSettings(this.voiceSettings.stability(),
							this.voiceSettings.similarityBoost(), this.voiceSettings.style(),
							this.voiceSettings.useSpeakerBoost(), null);
				}
			}
			return this;
		}

		public Builder voiceSettings(ElevenLabsApi.SpeechRequest.@Nullable VoiceSettings voiceSettings) {
			this.voiceSettings = voiceSettings;
			return this;
		}

		public Builder languageCode(@Nullable String languageCode) {
			this.languageCode = languageCode;
			return this;
		}

		public Builder pronunciationDictionaryLocators(
				@Nullable List<ElevenLabsApi.SpeechRequest.PronunciationDictionaryLocator> pronunciationDictionaryLocators) {
			this.pronunciationDictionaryLocators = pronunciationDictionaryLocators;
			return this;
		}

		public Builder seed(@Nullable Integer seed) {
			this.seed = seed;
			return this;
		}

		public Builder previousText(@Nullable String previousText) {
			this.previousText = previousText;
			return this;
		}

		public Builder nextText(@Nullable String nextText) {
			this.nextText = nextText;
			return this;
		}

		public Builder previousRequestIds(@Nullable List<String> previousRequestIds) {
			this.previousRequestIds = previousRequestIds;
			return this;
		}

		public Builder nextRequestIds(@Nullable List<String> nextRequestIds) {
			this.nextRequestIds = nextRequestIds;
			return this;
		}

		public Builder applyTextNormalization(
				ElevenLabsApi.SpeechRequest.@Nullable TextNormalizationMode applyTextNormalization) {
			this.applyTextNormalization = applyTextNormalization;
			return this;
		}

		public Builder applyLanguageTextNormalization(@Nullable Boolean applyLanguageTextNormalization) {
			this.applyLanguageTextNormalization = applyLanguageTextNormalization;
			return this;
		}

		public ElevenLabsTextToSpeechOptions build() {
			return new ElevenLabsTextToSpeechOptions(this.modelId, this.voiceId, this.enableLogging, this.outputFormat,
					this.voiceSettings, this.languageCode, this.pronunciationDictionaryLocators, this.seed,
					this.previousText, this.nextText, this.previousRequestIds, this.nextRequestIds,
					this.applyTextNormalization, this.applyLanguageTextNormalization);
		}

	}

}
