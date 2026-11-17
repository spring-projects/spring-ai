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

package org.springframework.ai.azure.speech.transcription;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

/**
 * Options for Azure AI Speech transcription.
 * <p>
 * Every field may be configured as a model default and overridden per request. An unset
 * request field inherits the default; see
 * {@link Builder#merge(AudioTranscriptionOptions)}.
 * <p>
 * The enumerations below are Spring AI's own rather than the Azure Speech SDK types, so
 * that property binding and options serialization never touch natively backed SDK
 * classes. They are translated inside the SDK adapter.
 *
 * @author Spring AI Contributors
 * @since 2.1.0
 */
public class AzureSpeechAudioTranscriptionOptions implements AudioTranscriptionOptions {

	/**
	 * Reported by {@link #getModel()} when neither a recognition model nor a Custom
	 * Speech endpoint ID is configured. Chosen so that observations carry a stable,
	 * non-secret value instead of the {@code none} placeholder.
	 */
	public static final String DEFAULT_MODEL = "azure-speech-default";

	/**
	 * Default overall deadline for a transcription operation.
	 */
	public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

	/**
	 * Default guard on the PCM payload served to the SDK, about 52 minutes of 16 kHz mono
	 * 16-bit audio.
	 */
	public static final DataSize DEFAULT_MAX_AUDIO_SIZE = DataSize.ofMegabytes(100);

	private @Nullable String model;

	private @Nullable String endpointId;

	private @Nullable String language;

	private @Nullable List<String> candidateLanguages;

	private @Nullable LanguageIdMode languageIdMode;

	private @Nullable Profanity profanity;

	private @Nullable OutputFormat outputFormat;

	private @Nullable Boolean wordLevelTimestamps;

	private @Nullable List<String> phrases;

	private @Nullable Duration timeout;

	private @Nullable DataSize maxAudioSize;

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Returns the stable model label for observations: the recognition model if set,
	 * otherwise the Custom Speech endpoint ID, otherwise {@link #DEFAULT_MODEL}.
	 * <p>
	 * This value never carries the region, the endpoint URL, or any credential.
	 */
	@Override
	public String getModel() {
		if (StringUtils.hasText(this.model)) {
			return this.model;
		}
		if (StringUtils.hasText(this.endpointId)) {
			return this.endpointId;
		}
		return DEFAULT_MODEL;
	}

	public @Nullable String getRecognitionModel() {
		return this.model;
	}

	public void setRecognitionModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable String getEndpointId() {
		return this.endpointId;
	}

	public void setEndpointId(@Nullable String endpointId) {
		this.endpointId = endpointId;
	}

	public @Nullable String getLanguage() {
		return this.language;
	}

	public void setLanguage(@Nullable String language) {
		this.language = language;
	}

	public @Nullable List<String> getCandidateLanguages() {
		return this.candidateLanguages;
	}

	public void setCandidateLanguages(@Nullable List<String> candidateLanguages) {
		this.candidateLanguages = (candidateLanguages != null) ? List.copyOf(candidateLanguages) : null;
	}

	public @Nullable LanguageIdMode getLanguageIdMode() {
		return this.languageIdMode;
	}

	public void setLanguageIdMode(@Nullable LanguageIdMode languageIdMode) {
		this.languageIdMode = languageIdMode;
	}

	public @Nullable Profanity getProfanity() {
		return this.profanity;
	}

	public void setProfanity(@Nullable Profanity profanity) {
		this.profanity = profanity;
	}

	public @Nullable OutputFormat getOutputFormat() {
		return this.outputFormat;
	}

	public void setOutputFormat(@Nullable OutputFormat outputFormat) {
		this.outputFormat = outputFormat;
	}

	public @Nullable Boolean getWordLevelTimestamps() {
		return this.wordLevelTimestamps;
	}

	public void setWordLevelTimestamps(@Nullable Boolean wordLevelTimestamps) {
		this.wordLevelTimestamps = wordLevelTimestamps;
	}

	public @Nullable List<String> getPhrases() {
		return this.phrases;
	}

	public void setPhrases(@Nullable List<String> phrases) {
		this.phrases = (phrases != null) ? List.copyOf(phrases) : null;
	}

	public @Nullable Duration getTimeout() {
		return this.timeout;
	}

	public void setTimeout(@Nullable Duration timeout) {
		this.timeout = timeout;
	}

	public @Nullable DataSize getMaxAudioSize() {
		return this.maxAudioSize;
	}

	public void setMaxAudioSize(@Nullable DataSize maxAudioSize) {
		this.maxAudioSize = maxAudioSize;
	}

	/**
	 * Returns the configured overall deadline, or {@link #DEFAULT_TIMEOUT} when unset.
	 */
	public Duration resolvedTimeout() {
		return (this.timeout != null) ? this.timeout : DEFAULT_TIMEOUT;
	}

	/**
	 * Returns the configured PCM payload guard, or {@link #DEFAULT_MAX_AUDIO_SIZE} when
	 * unset.
	 */
	public DataSize resolvedMaxAudioSize() {
		return (this.maxAudioSize != null) ? this.maxAudioSize : DEFAULT_MAX_AUDIO_SIZE;
	}

	/**
	 * Returns {@code true} when word-level timestamps were explicitly requested.
	 */
	public boolean isWordLevelTimestampsEnabled() {
		return Boolean.TRUE.equals(this.wordLevelTimestamps);
	}

	/**
	 * Returns {@code true} when detailed output was explicitly requested.
	 */
	public boolean isDetailedOutput() {
		return this.outputFormat == OutputFormat.DETAILED;
	}

	/**
	 * Returns {@code true} when automatic language identification is configured.
	 */
	public boolean isLanguageIdentificationEnabled() {
		return this.candidateLanguages != null && !this.candidateLanguages.isEmpty();
	}

	public AzureSpeechAudioTranscriptionOptions copy() {
		return builder().from(this).build();
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AzureSpeechAudioTranscriptionOptions that)) {
			return false;
		}
		return Objects.equals(this.model, that.model) && Objects.equals(this.endpointId, that.endpointId)
				&& Objects.equals(this.language, that.language)
				&& Objects.equals(this.candidateLanguages, that.candidateLanguages)
				&& this.languageIdMode == that.languageIdMode && this.profanity == that.profanity
				&& this.outputFormat == that.outputFormat
				&& Objects.equals(this.wordLevelTimestamps, that.wordLevelTimestamps)
				&& Objects.equals(this.phrases, that.phrases) && Objects.equals(this.timeout, that.timeout)
				&& Objects.equals(this.maxAudioSize, that.maxAudioSize);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.endpointId, this.language, this.candidateLanguages, this.languageIdMode,
				this.profanity, this.outputFormat, this.wordLevelTimestamps, this.phrases, this.timeout,
				this.maxAudioSize);
	}

	/**
	 * Never includes a credential: none of the fields above can hold one.
	 */
	@Override
	public String toString() {
		return "AzureSpeechAudioTranscriptionOptions{model=" + this.model + ", endpointId=" + this.endpointId
				+ ", language=" + this.language + ", candidateLanguages=" + this.candidateLanguages
				+ ", languageIdMode=" + this.languageIdMode + ", profanity=" + this.profanity + ", outputFormat="
				+ this.outputFormat + ", wordLevelTimestamps=" + this.wordLevelTimestamps + ", phrases=" + this.phrases
				+ ", timeout=" + this.timeout + ", maxAudioSize=" + this.maxAudioSize + '}';
	}

	private AzureSpeechAudioTranscriptionOptions shallowCopy() {
		AzureSpeechAudioTranscriptionOptions copy = new AzureSpeechAudioTranscriptionOptions();
		copy.model = this.model;
		copy.endpointId = this.endpointId;
		copy.language = this.language;
		copy.candidateLanguages = this.candidateLanguages;
		copy.languageIdMode = this.languageIdMode;
		copy.profanity = this.profanity;
		copy.outputFormat = this.outputFormat;
		copy.wordLevelTimestamps = this.wordLevelTimestamps;
		copy.phrases = this.phrases;
		copy.timeout = this.timeout;
		copy.maxAudioSize = this.maxAudioSize;
		return copy;
	}

	/**
	 * How automatic language identification runs over the audio.
	 */
	public enum LanguageIdMode {

		/**
		 * Identify the language once, from the opening audio. The SDK default.
		 */
		AT_START,

		/**
		 * Re-identify the language throughout the session. Supported only over a
		 * {@code SpeechConfig} created from an endpoint.
		 */
		CONTINUOUS

	}

	/**
	 * How profanity is handled in recognized text.
	 */
	public enum Profanity {

		/**
		 * Replace profane words with asterisks.
		 */
		MASKED,

		/**
		 * Remove profane words.
		 */
		REMOVED,

		/**
		 * Leave recognized text untouched.
		 */
		RAW

	}

	/**
	 * The shape of the recognition result requested from the service.
	 */
	public enum OutputFormat {

		/**
		 * Display text only. The SDK default.
		 */
		SIMPLE,

		/**
		 * Display text plus the detailed JSON result.
		 */
		DETAILED

	}

	public static final class Builder {

		private final AzureSpeechAudioTranscriptionOptions options = new AzureSpeechAudioTranscriptionOptions();

		private Builder() {
		}

		/**
		 * Copies every field of the given options into this builder.
		 */
		public Builder from(AzureSpeechAudioTranscriptionOptions other) {
			this.options.model = other.model;
			this.options.endpointId = other.endpointId;
			this.options.language = other.language;
			this.options.candidateLanguages = other.candidateLanguages;
			this.options.languageIdMode = other.languageIdMode;
			this.options.profanity = other.profanity;
			this.options.outputFormat = other.outputFormat;
			this.options.wordLevelTimestamps = other.wordLevelTimestamps;
			this.options.phrases = other.phrases;
			this.options.timeout = other.timeout;
			this.options.maxAudioSize = other.maxAudioSize;
			return this;
		}

		/**
		 * Merges runtime options over the values already in this builder. A non-null
		 * runtime value overrides; an unset runtime field inherits.
		 * <p>
		 * For a foreign {@link AudioTranscriptionOptions} implementation only the
		 * portable {@link AudioTranscriptionOptions#getModel()} value is honored, and it
		 * is copied into the recognition model field. A model name the service does not
		 * recognize therefore surfaces as a service cancellation rather than a silent
		 * fallback.
		 * @param runtimeOptions the per-request options, possibly {@code null}
		 * @return this builder
		 */
		public Builder merge(@Nullable AudioTranscriptionOptions runtimeOptions) {
			if (runtimeOptions == null) {
				return this;
			}
			if (!(runtimeOptions instanceof AzureSpeechAudioTranscriptionOptions azureOptions)) {
				String foreignModel = runtimeOptions.getModel();
				if (StringUtils.hasText(foreignModel)) {
					this.options.model = foreignModel;
				}
				return this;
			}
			if (azureOptions.model != null) {
				this.options.model = azureOptions.model;
			}
			if (azureOptions.endpointId != null) {
				this.options.endpointId = azureOptions.endpointId;
			}
			if (azureOptions.language != null) {
				this.options.language = azureOptions.language;
			}
			if (azureOptions.candidateLanguages != null) {
				this.options.candidateLanguages = azureOptions.candidateLanguages;
			}
			if (azureOptions.languageIdMode != null) {
				this.options.languageIdMode = azureOptions.languageIdMode;
			}
			if (azureOptions.profanity != null) {
				this.options.profanity = azureOptions.profanity;
			}
			if (azureOptions.outputFormat != null) {
				this.options.outputFormat = azureOptions.outputFormat;
			}
			if (azureOptions.wordLevelTimestamps != null) {
				this.options.wordLevelTimestamps = azureOptions.wordLevelTimestamps;
			}
			if (azureOptions.phrases != null) {
				this.options.phrases = azureOptions.phrases;
			}
			if (azureOptions.timeout != null) {
				this.options.timeout = azureOptions.timeout;
			}
			if (azureOptions.maxAudioSize != null) {
				this.options.maxAudioSize = azureOptions.maxAudioSize;
			}
			return this;
		}

		public Builder model(@Nullable String model) {
			this.options.model = model;
			return this;
		}

		public Builder endpointId(@Nullable String endpointId) {
			this.options.endpointId = endpointId;
			return this;
		}

		public Builder language(@Nullable String language) {
			this.options.language = language;
			return this;
		}

		public Builder candidateLanguages(@Nullable List<String> candidateLanguages) {
			this.options.setCandidateLanguages(candidateLanguages);
			return this;
		}

		public Builder languageIdMode(@Nullable LanguageIdMode languageIdMode) {
			this.options.languageIdMode = languageIdMode;
			return this;
		}

		public Builder profanity(@Nullable Profanity profanity) {
			this.options.profanity = profanity;
			return this;
		}

		public Builder outputFormat(@Nullable OutputFormat outputFormat) {
			this.options.outputFormat = outputFormat;
			return this;
		}

		public Builder wordLevelTimestamps(@Nullable Boolean wordLevelTimestamps) {
			this.options.wordLevelTimestamps = wordLevelTimestamps;
			return this;
		}

		public Builder phrases(@Nullable List<String> phrases) {
			this.options.setPhrases(phrases);
			return this;
		}

		public Builder timeout(@Nullable Duration timeout) {
			this.options.timeout = timeout;
			return this;
		}

		public Builder maxAudioSize(@Nullable DataSize maxAudioSize) {
			this.options.maxAudioSize = maxAudioSize;
			return this;
		}

		public AzureSpeechAudioTranscriptionOptions build() {
			return this.options.shallowCopy();
		}

	}

}
