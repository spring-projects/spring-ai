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

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.ai.mistralai.api.MistralAiAudioApi;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.TimestampGranularity;

/**
 * Options for the Mistral AI transcription API.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public class MistralAiTranscriptionOptions implements AudioTranscriptionOptions {

	public static final String DEFAULT_MODEL = MistralAiAudioApi.TranscriptionModel.VOXTRAL_MINI_LATEST.getValue();

	/**
	 * ID of the model to use. Unset means {@link #DEFAULT_MODEL}, and lets the model of
	 * other options win on {@link Builder#merge merge}.
	 */
	private final @Nullable String model;

	/**
	 * The ISO 639-1 language of the audio, which can improve accuracy.
	 */
	private final @Nullable String language;

	/**
	 * The sampling temperature.
	 */
	private final @Nullable Double temperature;

	/**
	 * Whether to identify the speaker of each segment, which requires segment timestamps.
	 */
	private final @Nullable Boolean diarize;

	/**
	 * Words or phrases to bias the transcription towards, such as names or domain terms.
	 */
	private final @Nullable List<String> contextBias;

	/**
	 * The granularities of the timestamps to include in the transcription.
	 */
	private final @Nullable List<TimestampGranularity> timestampGranularities;

	protected MistralAiTranscriptionOptions(@Nullable String model, @Nullable String language,
			@Nullable Double temperature, @Nullable Boolean diarize, @Nullable List<String> contextBias,
			@Nullable List<TimestampGranularity> timestampGranularities) {
		this.model = model;
		this.language = language;
		this.temperature = temperature;
		this.diarize = diarize;
		this.contextBias = (contextBias != null ? List.copyOf(contextBias) : null);
		this.timestampGranularities = (timestampGranularities != null ? List.copyOf(timestampGranularities) : null);
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getModel() {
		return (this.model != null ? this.model : DEFAULT_MODEL);
	}

	public @Nullable String getLanguage() {
		return this.language;
	}

	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	public @Nullable Boolean getDiarize() {
		return this.diarize;
	}

	public @Nullable List<String> getContextBias() {
		return this.contextBias;
	}

	public @Nullable List<TimestampGranularity> getTimestampGranularities() {
		return this.timestampGranularities;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof MistralAiTranscriptionOptions that)) {
			return false;
		}
		return Objects.equals(getModel(), that.getModel()) && Objects.equals(this.language, that.language)
				&& Objects.equals(this.temperature, that.temperature) && Objects.equals(this.diarize, that.diarize)
				&& Objects.equals(this.contextBias, that.contextBias)
				&& Objects.equals(this.timestampGranularities, that.timestampGranularities);
	}

	@Override
	public int hashCode() {
		return Objects.hash(getModel(), this.language, this.temperature, this.diarize, this.contextBias,
				this.timestampGranularities);
	}

	@Override
	public String toString() {
		return "MistralAiTranscriptionOptions{model=" + getModel() + ", language=" + this.language + ", temperature="
				+ this.temperature + ", diarize=" + this.diarize + ", contextBias=" + this.contextBias
				+ ", timestampGranularities=" + this.timestampGranularities + '}';
	}

	public static final class Builder {

		private @Nullable String model;

		private @Nullable String language;

		private @Nullable Double temperature;

		private @Nullable Boolean diarize;

		private @Nullable List<String> contextBias;

		private @Nullable List<TimestampGranularity> timestampGranularities;

		private Builder() {
		}

		/**
		 * Copies all the options of the given ones.
		 * @param fromOptions the options to copy
		 * @return this builder
		 */
		public Builder from(MistralAiTranscriptionOptions fromOptions) {
			this.model = fromOptions.model;
			this.language = fromOptions.language;
			this.temperature = fromOptions.temperature;
			this.diarize = fromOptions.diarize;
			this.contextBias = fromOptions.contextBias;
			this.timestampGranularities = fromOptions.timestampGranularities;
			return this;
		}

		/**
		 * Overrides the options of this builder with the ones set in the given options.
		 * Of options that are not {@link MistralAiTranscriptionOptions}, only the model
		 * is taken.
		 * @param fromOptions the options to merge, or {@code null} for none
		 * @return this builder
		 */
		public Builder merge(@Nullable AudioTranscriptionOptions fromOptions) {
			if (fromOptions instanceof MistralAiTranscriptionOptions mistralOptions) {
				if (mistralOptions.model != null) {
					this.model = mistralOptions.model;
				}
				if (mistralOptions.language != null) {
					this.language = mistralOptions.language;
				}
				if (mistralOptions.temperature != null) {
					this.temperature = mistralOptions.temperature;
				}
				if (mistralOptions.diarize != null) {
					this.diarize = mistralOptions.diarize;
				}
				if (mistralOptions.contextBias != null) {
					this.contextBias = mistralOptions.contextBias;
				}
				if (mistralOptions.timestampGranularities != null) {
					this.timestampGranularities = mistralOptions.timestampGranularities;
				}
			}
			else if (fromOptions != null && fromOptions.getModel() != null) {
				this.model = fromOptions.getModel();
			}
			return this;
		}

		public Builder model(@Nullable String model) {
			this.model = model;
			return this;
		}

		public Builder model(MistralAiAudioApi.TranscriptionModel model) {
			this.model = model.getValue();
			return this;
		}

		public Builder language(@Nullable String language) {
			this.language = language;
			return this;
		}

		public Builder temperature(@Nullable Double temperature) {
			this.temperature = temperature;
			return this;
		}

		public Builder diarize(@Nullable Boolean diarize) {
			this.diarize = diarize;
			return this;
		}

		public Builder contextBias(@Nullable List<String> contextBias) {
			this.contextBias = contextBias;
			return this;
		}

		public Builder timestampGranularities(@Nullable List<TimestampGranularity> timestampGranularities) {
			this.timestampGranularities = timestampGranularities;
			return this;
		}

		public MistralAiTranscriptionOptions build() {
			return new MistralAiTranscriptionOptions(this.model, this.language, this.temperature, this.diarize,
					this.contextBias, this.timestampGranularities);
		}

	}

}
