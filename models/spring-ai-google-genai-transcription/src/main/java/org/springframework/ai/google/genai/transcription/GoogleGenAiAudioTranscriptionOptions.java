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

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;

/**
 * Options for Google GenAI (Gemini) audio transcription. The audio is sent inline to a
 * Gemini transcription model through the {@code com.google.genai} SDK.
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
public class GoogleGenAiAudioTranscriptionOptions implements AudioTranscriptionOptions {

	/**
	 * The default Gemini transcription model name.
	 */
	public static final String DEFAULT_MODEL_NAME = "gemini-3.5-transcribe";

	/**
	 * The Gemini live transcription model name, used with the Gemini Live API for
	 * real-time (streaming) speech-to-text.
	 */
	public static final String LIVE_MODEL_NAME = "gemini-3.5-transcribe-live";

	/**
	 * The default instruction sent alongside the audio.
	 */
	public static final String DEFAULT_TRANSCRIPTION_PROMPT = "Transcribe the audio.";

	/**
	 * The Gemini transcription model to use (e.g. {@code gemini-3.5-transcribe}).
	 */
	private final @Nullable String model;

	/**
	 * Natural-language instruction guiding the transcription (e.g. formatting, vocabulary
	 * hints). Overrides {@link #DEFAULT_TRANSCRIPTION_PROMPT} when set.
	 */
	private final @Nullable String prompt;

	/**
	 * BCP-47 language code of the audio (e.g. {@code en-US}). Used as a hint in the
	 * transcription instruction.
	 */
	private final @Nullable String language;

	/**
	 * Sampling temperature to use for the transcription request.
	 */
	private final @Nullable Float temperature;

	/**
	 * The maximum cumulative probability of tokens to consider when sampling (nucleus
	 * sampling).
	 */
	private final @Nullable Float topP;

	/**
	 * The maximum number of tokens to consider when sampling (top-k sampling).
	 */
	private final @Nullable Float topK;

	/**
	 * Number of transcription candidates to generate.
	 */
	private final @Nullable Integer candidateCount;

	/**
	 * The maximum number of tokens to generate in the transcription.
	 */
	private final @Nullable Integer maxOutputTokens;

	/**
	 * Sequences where the model stops generating the transcription.
	 */
	private final @Nullable List<String> stopSequences;

	/**
	 * The MIME type of the transcription response (e.g. {@code text/plain}).
	 */
	private final @Nullable String responseMimeType;

	/**
	 * Penalty applied to tokens based on their presence in the generated transcription.
	 */
	private final @Nullable Float presencePenalty;

	/**
	 * Penalty applied to tokens based on their frequency in the generated transcription.
	 */
	private final @Nullable Float frequencyPenalty;

	/**
	 * Seed used for deterministic sampling.
	 */
	private final @Nullable Integer seed;

	protected GoogleGenAiAudioTranscriptionOptions(@Nullable String model, @Nullable String prompt,
			@Nullable String language, @Nullable Float temperature, @Nullable Float topP, @Nullable Float topK,
			@Nullable Integer candidateCount, @Nullable Integer maxOutputTokens, @Nullable List<String> stopSequences,
			@Nullable String responseMimeType, @Nullable Float presencePenalty, @Nullable Float frequencyPenalty,
			@Nullable Integer seed) {
		this.model = model;
		this.prompt = prompt;
		this.language = language;
		this.temperature = temperature;
		this.topP = topP;
		this.topK = topK;
		this.candidateCount = candidateCount;
		this.maxOutputTokens = maxOutputTokens;
		this.stopSequences = stopSequences != null ? List.copyOf(stopSequences) : null;
		this.responseMimeType = responseMimeType;
		this.presencePenalty = presencePenalty;
		this.frequencyPenalty = frequencyPenalty;
		this.seed = seed;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getModel() {
		return this.model != null ? this.model : DEFAULT_MODEL_NAME;
	}

	public @Nullable String getPrompt() {
		return this.prompt;
	}

	public @Nullable String getLanguage() {
		return this.language;
	}

	public @Nullable Float getTemperature() {
		return this.temperature;
	}

	public @Nullable Float getTopP() {
		return this.topP;
	}

	public @Nullable Float getTopK() {
		return this.topK;
	}

	public @Nullable Integer getCandidateCount() {
		return this.candidateCount;
	}

	public @Nullable Integer getMaxOutputTokens() {
		return this.maxOutputTokens;
	}

	public @Nullable List<String> getStopSequences() {
		return this.stopSequences;
	}

	public @Nullable String getResponseMimeType() {
		return this.responseMimeType;
	}

	public @Nullable Float getPresencePenalty() {
		return this.presencePenalty;
	}

	public @Nullable Float getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public @Nullable Integer getSeed() {
		return this.seed;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final GoogleGenAiAudioTranscriptionOptions that = (GoogleGenAiAudioTranscriptionOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.prompt, that.prompt)
				&& Objects.equals(this.language, that.language) && Objects.equals(this.temperature, that.temperature)
				&& Objects.equals(this.topP, that.topP) && Objects.equals(this.topK, that.topK)
				&& Objects.equals(this.candidateCount, that.candidateCount)
				&& Objects.equals(this.maxOutputTokens, that.maxOutputTokens)
				&& Objects.equals(this.stopSequences, that.stopSequences)
				&& Objects.equals(this.responseMimeType, that.responseMimeType)
				&& Objects.equals(this.presencePenalty, that.presencePenalty)
				&& Objects.equals(this.frequencyPenalty, that.frequencyPenalty) && Objects.equals(this.seed, that.seed);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.prompt, this.language, this.temperature, this.topP, this.topK,
				this.candidateCount, this.maxOutputTokens, this.stopSequences, this.responseMimeType,
				this.presencePenalty, this.frequencyPenalty, this.seed);
	}

	public static final class Builder {

		private @Nullable String model;

		private @Nullable String prompt;

		private @Nullable String language;

		private @Nullable Float temperature;

		private @Nullable Float topP;

		private @Nullable Float topK;

		private @Nullable Integer candidateCount;

		private @Nullable Integer maxOutputTokens;

		private @Nullable List<String> stopSequences;

		private @Nullable String responseMimeType;

		private @Nullable Float presencePenalty;

		private @Nullable Float frequencyPenalty;

		private @Nullable Integer seed;

		private Builder() {
		}

		/**
		 * Initializes this builder with the values of the given options.
		 * @param options the options to copy from
		 * @return this builder
		 */
		public Builder from(GoogleGenAiAudioTranscriptionOptions options) {
			this.model = options.getModel();
			this.prompt = options.getPrompt();
			this.language = options.getLanguage();
			this.temperature = options.getTemperature();
			this.topP = options.getTopP();
			this.topK = options.getTopK();
			this.candidateCount = options.getCandidateCount();
			this.maxOutputTokens = options.getMaxOutputTokens();
			this.stopSequences = options.getStopSequences();
			this.responseMimeType = options.getResponseMimeType();
			this.presencePenalty = options.getPresencePenalty();
			this.frequencyPenalty = options.getFrequencyPenalty();
			this.seed = options.getSeed();
			return this;
		}

		/**
		 * Merges the given runtime options into this builder. Non-null runtime values
		 * override the current builder values.
		 * @param options the runtime options to merge, may be {@code null} or a different
		 * {@link AudioTranscriptionOptions} implementation (only the model is merged in
		 * that case)
		 * @return this builder
		 */
		public Builder merge(@Nullable AudioTranscriptionOptions options) {
			if (options == null) {
				return this;
			}
			if (options instanceof GoogleGenAiAudioTranscriptionOptions googleOptions) {
				if (googleOptions.model != null) {
					this.model = googleOptions.model;
				}
				if (googleOptions.getPrompt() != null) {
					this.prompt = googleOptions.getPrompt();
				}
				if (googleOptions.getLanguage() != null) {
					this.language = googleOptions.getLanguage();
				}
				if (googleOptions.getTemperature() != null) {
					this.temperature = googleOptions.getTemperature();
				}
				if (googleOptions.getTopP() != null) {
					this.topP = googleOptions.getTopP();
				}
				if (googleOptions.getTopK() != null) {
					this.topK = googleOptions.getTopK();
				}
				if (googleOptions.getCandidateCount() != null) {
					this.candidateCount = googleOptions.getCandidateCount();
				}
				if (googleOptions.getMaxOutputTokens() != null) {
					this.maxOutputTokens = googleOptions.getMaxOutputTokens();
				}
				if (googleOptions.getStopSequences() != null) {
					this.stopSequences = googleOptions.getStopSequences();
				}
				if (googleOptions.getResponseMimeType() != null) {
					this.responseMimeType = googleOptions.getResponseMimeType();
				}
				if (googleOptions.getPresencePenalty() != null) {
					this.presencePenalty = googleOptions.getPresencePenalty();
				}
				if (googleOptions.getFrequencyPenalty() != null) {
					this.frequencyPenalty = googleOptions.getFrequencyPenalty();
				}
				if (googleOptions.getSeed() != null) {
					this.seed = googleOptions.getSeed();
				}
			}
			else if (options.getModel() != null) {
				this.model = options.getModel();
			}
			return this;
		}

		public Builder model(@Nullable String model) {
			this.model = model;
			return this;
		}

		public Builder prompt(@Nullable String prompt) {
			this.prompt = prompt;
			return this;
		}

		public Builder language(@Nullable String language) {
			this.language = language;
			return this;
		}

		public Builder temperature(@Nullable Float temperature) {
			this.temperature = temperature;
			return this;
		}

		public Builder topP(@Nullable Float topP) {
			this.topP = topP;
			return this;
		}

		public Builder topK(@Nullable Float topK) {
			this.topK = topK;
			return this;
		}

		public Builder candidateCount(@Nullable Integer candidateCount) {
			this.candidateCount = candidateCount;
			return this;
		}

		public Builder maxOutputTokens(@Nullable Integer maxOutputTokens) {
			this.maxOutputTokens = maxOutputTokens;
			return this;
		}

		public Builder stopSequences(@Nullable List<String> stopSequences) {
			this.stopSequences = stopSequences;
			return this;
		}

		public Builder responseMimeType(@Nullable String responseMimeType) {
			this.responseMimeType = responseMimeType;
			return this;
		}

		public Builder presencePenalty(@Nullable Float presencePenalty) {
			this.presencePenalty = presencePenalty;
			return this;
		}

		public Builder frequencyPenalty(@Nullable Float frequencyPenalty) {
			this.frequencyPenalty = frequencyPenalty;
			return this;
		}

		public Builder seed(@Nullable Integer seed) {
			this.seed = seed;
			return this;
		}

		public GoogleGenAiAudioTranscriptionOptions build() {
			return new GoogleGenAiAudioTranscriptionOptions(this.model, this.prompt, this.language, this.temperature,
					this.topP, this.topK, this.candidateCount, this.maxOutputTokens, this.stopSequences,
					this.responseMimeType, this.presencePenalty, this.frequencyPenalty, this.seed);
		}

	}

}
