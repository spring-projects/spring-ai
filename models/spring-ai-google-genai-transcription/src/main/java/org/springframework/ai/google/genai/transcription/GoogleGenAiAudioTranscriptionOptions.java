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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.util.StringUtils;

/**
 * Options for Google GenAI Speech-to-Text (Chirp) transcription.
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
public class GoogleGenAiAudioTranscriptionOptions implements AudioTranscriptionOptions {

	/**
	 * The default Chirp model name.
	 */
	public static final String DEFAULT_MODEL_NAME = "chirp_3";

	/**
	 * Audio encoding of the data sent in the audio message. Mirrors
	 * {@code com.google.cloud.speech.v2.ExplicitDecodingConfig.AudioEncoding}. When left
	 * unset, the encoding is auto-detected.
	 */
	public enum AudioEncoding {

		AUDIO_ENCODING_UNSPECIFIED, LINEAR16, MULAW, ALAW, AMR, AMR_WB, FLAC, MP3, OGG_OPUS, WEBM_OPUS, MP4_AAC,
		M4A_AAC, MOV_AAC

	}

	/**
	 * Mode for recognizing multi-channel audio. Mirrors
	 * {@code com.google.cloud.speech.v2.RecognitionFeatures.MultiChannelMode}.
	 */
	public enum MultiChannelMode {

		MULTI_CHANNEL_MODE_UNSPECIFIED, SEPARATE_RECOGNITION_PER_CHANNEL

	}

	/**
	 * The Chirp model to use (e.g. {@code chirp_2}, {@code chirp_3}).
	 */
	private final @Nullable String model;

	/**
	 * A single BCP-47 language code (convenience for a single-entry
	 * {@link #languageCodes}).
	 */
	private final @Nullable String language;

	/**
	 * The BCP-47 language codes to use for recognition.
	 */
	private final @Nullable List<String> languageCodes;

	/**
	 * Whether to add punctuation to recognition result hypotheses.
	 */
	private final @Nullable Boolean enableAutomaticPunctuation;

	/**
	 * Whether to replace spoken punctuation with punctuation marks in the text.
	 */
	private final @Nullable Boolean enableSpokenPunctuation;

	/**
	 * Whether to replace spoken emoji with the corresponding emoji in the text.
	 */
	private final @Nullable Boolean enableSpokenEmojis;

	/**
	 * Whether to filter out profanities from the transcript.
	 */
	private final @Nullable Boolean profanityFilter;

	/**
	 * Whether to include word-level start and end time offsets.
	 */
	private final @Nullable Boolean enableWordTimeOffsets;

	/**
	 * Whether to include word-level confidence scores.
	 */
	private final @Nullable Boolean enableWordConfidence;

	/**
	 * Maximum number of recognition hypotheses to be returned.
	 */
	private final @Nullable Integer maxAlternatives;

	/**
	 * Mode for recognizing multi-channel audio.
	 */
	private final @Nullable MultiChannelMode multiChannelMode;

	/**
	 * Whether to enable speaker diarization (chirp_3 only).
	 */
	private final @Nullable Boolean enableSpeakerDiarization;

	/**
	 * Minimum number of speakers to detect during diarization.
	 */
	private final @Nullable Integer minSpeakerCount;

	/**
	 * Maximum number of speakers to detect during diarization.
	 */
	private final @Nullable Integer maxSpeakerCount;

	/**
	 * Target BCP-47 language code for translation (chirp_2 only).
	 */
	private final @Nullable String translationTargetLanguage;

	/**
	 * Explicit audio encoding. When set, explicit decoding is used instead of
	 * auto-detection.
	 */
	private final @Nullable AudioEncoding encoding;

	/**
	 * Sample rate in hertz of the audio data (used with explicit decoding).
	 */
	private final @Nullable Integer sampleRateHertz;

	/**
	 * Number of channels in the audio data (used with explicit decoding).
	 */
	private final @Nullable Integer audioChannelCount;

	protected GoogleGenAiAudioTranscriptionOptions(@Nullable String model, @Nullable String language,
			@Nullable List<String> languageCodes, @Nullable Boolean enableAutomaticPunctuation,
			@Nullable Boolean enableSpokenPunctuation, @Nullable Boolean enableSpokenEmojis,
			@Nullable Boolean profanityFilter, @Nullable Boolean enableWordTimeOffsets,
			@Nullable Boolean enableWordConfidence, @Nullable Integer maxAlternatives,
			@Nullable MultiChannelMode multiChannelMode, @Nullable Boolean enableSpeakerDiarization,
			@Nullable Integer minSpeakerCount, @Nullable Integer maxSpeakerCount,
			@Nullable String translationTargetLanguage, @Nullable AudioEncoding encoding,
			@Nullable Integer sampleRateHertz, @Nullable Integer audioChannelCount) {
		this.model = model;
		this.language = language;
		this.languageCodes = Optional.ofNullable(languageCodes).<List<String>>map(ArrayList::new).orElse(null);
		this.enableAutomaticPunctuation = enableAutomaticPunctuation;
		this.enableSpokenPunctuation = enableSpokenPunctuation;
		this.enableSpokenEmojis = enableSpokenEmojis;
		this.profanityFilter = profanityFilter;
		this.enableWordTimeOffsets = enableWordTimeOffsets;
		this.enableWordConfidence = enableWordConfidence;
		this.maxAlternatives = maxAlternatives;
		this.multiChannelMode = multiChannelMode;
		this.enableSpeakerDiarization = enableSpeakerDiarization;
		this.minSpeakerCount = minSpeakerCount;
		this.maxSpeakerCount = maxSpeakerCount;
		this.translationTargetLanguage = translationTargetLanguage;
		this.encoding = encoding;
		this.sampleRateHertz = sampleRateHertz;
		this.audioChannelCount = audioChannelCount;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getModel() {
		return this.model != null ? this.model : DEFAULT_MODEL_NAME;
	}

	public @Nullable String getLanguage() {
		return this.language;
	}

	public @Nullable List<String> getLanguageCodes() {
		return this.languageCodes;
	}

	public @Nullable Boolean getEnableAutomaticPunctuation() {
		return this.enableAutomaticPunctuation;
	}

	public @Nullable Boolean getEnableSpokenPunctuation() {
		return this.enableSpokenPunctuation;
	}

	public @Nullable Boolean getEnableSpokenEmojis() {
		return this.enableSpokenEmojis;
	}

	public @Nullable Boolean getProfanityFilter() {
		return this.profanityFilter;
	}

	public @Nullable Boolean getEnableWordTimeOffsets() {
		return this.enableWordTimeOffsets;
	}

	public @Nullable Boolean getEnableWordConfidence() {
		return this.enableWordConfidence;
	}

	public @Nullable Integer getMaxAlternatives() {
		return this.maxAlternatives;
	}

	public @Nullable MultiChannelMode getMultiChannelMode() {
		return this.multiChannelMode;
	}

	public @Nullable Boolean getEnableSpeakerDiarization() {
		return this.enableSpeakerDiarization;
	}

	public @Nullable Integer getMinSpeakerCount() {
		return this.minSpeakerCount;
	}

	public @Nullable Integer getMaxSpeakerCount() {
		return this.maxSpeakerCount;
	}

	public @Nullable String getTranslationTargetLanguage() {
		return this.translationTargetLanguage;
	}

	public @Nullable AudioEncoding getEncoding() {
		return this.encoding;
	}

	public @Nullable Integer getSampleRateHertz() {
		return this.sampleRateHertz;
	}

	public @Nullable Integer getAudioChannelCount() {
		return this.audioChannelCount;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final GoogleGenAiAudioTranscriptionOptions that = (GoogleGenAiAudioTranscriptionOptions) o;
		return Objects.equals(this.model, that.model) && Objects.equals(this.language, that.language)
				&& Objects.equals(this.languageCodes, that.languageCodes)
				&& Objects.equals(this.enableAutomaticPunctuation, that.enableAutomaticPunctuation)
				&& Objects.equals(this.enableSpokenPunctuation, that.enableSpokenPunctuation)
				&& Objects.equals(this.enableSpokenEmojis, that.enableSpokenEmojis)
				&& Objects.equals(this.profanityFilter, that.profanityFilter)
				&& Objects.equals(this.enableWordTimeOffsets, that.enableWordTimeOffsets)
				&& Objects.equals(this.enableWordConfidence, that.enableWordConfidence)
				&& Objects.equals(this.maxAlternatives, that.maxAlternatives)
				&& Objects.equals(this.multiChannelMode, that.multiChannelMode)
				&& Objects.equals(this.enableSpeakerDiarization, that.enableSpeakerDiarization)
				&& Objects.equals(this.minSpeakerCount, that.minSpeakerCount)
				&& Objects.equals(this.maxSpeakerCount, that.maxSpeakerCount)
				&& Objects.equals(this.translationTargetLanguage, that.translationTargetLanguage)
				&& Objects.equals(this.encoding, that.encoding)
				&& Objects.equals(this.sampleRateHertz, that.sampleRateHertz)
				&& Objects.equals(this.audioChannelCount, that.audioChannelCount);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.language, this.languageCodes, this.enableAutomaticPunctuation,
				this.enableSpokenPunctuation, this.enableSpokenEmojis, this.profanityFilter, this.enableWordTimeOffsets,
				this.enableWordConfidence, this.maxAlternatives, this.multiChannelMode, this.enableSpeakerDiarization,
				this.minSpeakerCount, this.maxSpeakerCount, this.translationTargetLanguage, this.encoding,
				this.sampleRateHertz, this.audioChannelCount);
	}

	public static final class Builder {

		private @Nullable String model;

		private @Nullable String language;

		private @Nullable List<String> languageCodes;

		private @Nullable Boolean enableAutomaticPunctuation;

		private @Nullable Boolean enableSpokenPunctuation;

		private @Nullable Boolean enableSpokenEmojis;

		private @Nullable Boolean profanityFilter;

		private @Nullable Boolean enableWordTimeOffsets;

		private @Nullable Boolean enableWordConfidence;

		private @Nullable Integer maxAlternatives;

		private @Nullable MultiChannelMode multiChannelMode;

		private @Nullable Boolean enableSpeakerDiarization;

		private @Nullable Integer minSpeakerCount;

		private @Nullable Integer maxSpeakerCount;

		private @Nullable String translationTargetLanguage;

		private @Nullable AudioEncoding encoding;

		private @Nullable Integer sampleRateHertz;

		private @Nullable Integer audioChannelCount;

		private Builder() {
		}

		public Builder from(GoogleGenAiAudioTranscriptionOptions fromOptions) {
			if (StringUtils.hasText(fromOptions.model)) {
				this.model = fromOptions.model;
			}
			if (StringUtils.hasText(fromOptions.language)) {
				this.language = fromOptions.language;
			}
			if (Objects.nonNull(fromOptions.languageCodes)) {
				this.languageCodes = fromOptions.languageCodes;
			}
			if (Objects.nonNull(fromOptions.enableAutomaticPunctuation)) {
				this.enableAutomaticPunctuation = fromOptions.enableAutomaticPunctuation;
			}
			if (Objects.nonNull(fromOptions.enableSpokenPunctuation)) {
				this.enableSpokenPunctuation = fromOptions.enableSpokenPunctuation;
			}
			if (Objects.nonNull(fromOptions.enableSpokenEmojis)) {
				this.enableSpokenEmojis = fromOptions.enableSpokenEmojis;
			}
			if (Objects.nonNull(fromOptions.profanityFilter)) {
				this.profanityFilter = fromOptions.profanityFilter;
			}
			if (Objects.nonNull(fromOptions.enableWordTimeOffsets)) {
				this.enableWordTimeOffsets = fromOptions.enableWordTimeOffsets;
			}
			if (Objects.nonNull(fromOptions.enableWordConfidence)) {
				this.enableWordConfidence = fromOptions.enableWordConfidence;
			}
			if (Objects.nonNull(fromOptions.maxAlternatives)) {
				this.maxAlternatives = fromOptions.maxAlternatives;
			}
			if (Objects.nonNull(fromOptions.multiChannelMode)) {
				this.multiChannelMode = fromOptions.multiChannelMode;
			}
			if (Objects.nonNull(fromOptions.enableSpeakerDiarization)) {
				this.enableSpeakerDiarization = fromOptions.enableSpeakerDiarization;
			}
			if (Objects.nonNull(fromOptions.minSpeakerCount)) {
				this.minSpeakerCount = fromOptions.minSpeakerCount;
			}
			if (Objects.nonNull(fromOptions.maxSpeakerCount)) {
				this.maxSpeakerCount = fromOptions.maxSpeakerCount;
			}
			if (StringUtils.hasText(fromOptions.translationTargetLanguage)) {
				this.translationTargetLanguage = fromOptions.translationTargetLanguage;
			}
			if (Objects.nonNull(fromOptions.encoding)) {
				this.encoding = fromOptions.encoding;
			}
			if (Objects.nonNull(fromOptions.sampleRateHertz)) {
				this.sampleRateHertz = fromOptions.sampleRateHertz;
			}
			if (Objects.nonNull(fromOptions.audioChannelCount)) {
				this.audioChannelCount = fromOptions.audioChannelCount;
			}
			return this;
		}

		public Builder model(@Nullable String model) {
			this.model = model;
			return this;
		}

		public Builder language(@Nullable String language) {
			this.language = language;
			return this;
		}

		public Builder languageCodes(@Nullable List<String> languageCodes) {
			this.languageCodes = languageCodes;
			return this;
		}

		public Builder enableAutomaticPunctuation(@Nullable Boolean enableAutomaticPunctuation) {
			this.enableAutomaticPunctuation = enableAutomaticPunctuation;
			return this;
		}

		public Builder enableSpokenPunctuation(@Nullable Boolean enableSpokenPunctuation) {
			this.enableSpokenPunctuation = enableSpokenPunctuation;
			return this;
		}

		public Builder enableSpokenEmojis(@Nullable Boolean enableSpokenEmojis) {
			this.enableSpokenEmojis = enableSpokenEmojis;
			return this;
		}

		public Builder profanityFilter(@Nullable Boolean profanityFilter) {
			this.profanityFilter = profanityFilter;
			return this;
		}

		public Builder enableWordTimeOffsets(@Nullable Boolean enableWordTimeOffsets) {
			this.enableWordTimeOffsets = enableWordTimeOffsets;
			return this;
		}

		public Builder enableWordConfidence(@Nullable Boolean enableWordConfidence) {
			this.enableWordConfidence = enableWordConfidence;
			return this;
		}

		public Builder maxAlternatives(@Nullable Integer maxAlternatives) {
			this.maxAlternatives = maxAlternatives;
			return this;
		}

		public Builder multiChannelMode(@Nullable MultiChannelMode multiChannelMode) {
			this.multiChannelMode = multiChannelMode;
			return this;
		}

		public Builder enableSpeakerDiarization(@Nullable Boolean enableSpeakerDiarization) {
			this.enableSpeakerDiarization = enableSpeakerDiarization;
			return this;
		}

		public Builder minSpeakerCount(@Nullable Integer minSpeakerCount) {
			this.minSpeakerCount = minSpeakerCount;
			return this;
		}

		public Builder maxSpeakerCount(@Nullable Integer maxSpeakerCount) {
			this.maxSpeakerCount = maxSpeakerCount;
			return this;
		}

		public Builder translationTargetLanguage(@Nullable String translationTargetLanguage) {
			this.translationTargetLanguage = translationTargetLanguage;
			return this;
		}

		public Builder encoding(@Nullable AudioEncoding encoding) {
			this.encoding = encoding;
			return this;
		}

		public Builder sampleRateHertz(@Nullable Integer sampleRateHertz) {
			this.sampleRateHertz = sampleRateHertz;
			return this;
		}

		public Builder audioChannelCount(@Nullable Integer audioChannelCount) {
			this.audioChannelCount = audioChannelCount;
			return this;
		}

		public GoogleGenAiAudioTranscriptionOptions build() {
			return new GoogleGenAiAudioTranscriptionOptions(this.model, this.language, this.languageCodes,
					this.enableAutomaticPunctuation, this.enableSpokenPunctuation, this.enableSpokenEmojis,
					this.profanityFilter, this.enableWordTimeOffsets, this.enableWordConfidence, this.maxAlternatives,
					this.multiChannelMode, this.enableSpeakerDiarization, this.minSpeakerCount, this.maxSpeakerCount,
					this.translationTargetLanguage, this.encoding, this.sampleRateHertz, this.audioChannelCount);
		}

	}

}
