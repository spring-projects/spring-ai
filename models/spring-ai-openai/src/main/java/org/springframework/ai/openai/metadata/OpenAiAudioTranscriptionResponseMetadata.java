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

package org.springframework.ai.openai.metadata;

import java.util.List;

import com.openai.models.audio.transcriptions.Transcription;
import com.openai.models.audio.transcriptions.TranscriptionDiarized;
import com.openai.models.audio.transcriptions.TranscriptionVerbose;
import com.openai.models.audio.transcriptions.TranscriptionWord;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata;

/**
 * Audio transcription metadata implementation for OpenAI using the OpenAI Java SDK.
 * <p>
 * {@code duration}, {@code usage} and segment/word-level timestamps are only returned by
 * the OpenAI API for the {@code verbose_json} and {@code diarized_json}
 * {@code response_format} values; for other formats these accessors return {@code null}.
 *
 * @author Christian Tzolov
 */
public class OpenAiAudioTranscriptionResponseMetadata extends AudioTranscriptionResponseMetadata {

	private final @Nullable Double duration;

	private final @Nullable String language;

	private final @Nullable Object usage;

	private final @Nullable List<?> segments;

	private final @Nullable List<TranscriptionWord> words;

	public OpenAiAudioTranscriptionResponseMetadata() {
		this(null, null, null, null, null);
	}

	public OpenAiAudioTranscriptionResponseMetadata(@Nullable Double duration, @Nullable String language,
			@Nullable Object usage, @Nullable List<?> segments, @Nullable List<TranscriptionWord> words) {
		this.duration = duration;
		this.language = language;
		this.usage = usage;
		this.segments = segments;
		this.words = words;
	}

	/**
	 * Returns the audio duration in seconds, or {@code null} if not returned by the API.
	 */
	public @Nullable Double getDuration() {
		return this.duration;
	}

	/**
	 * Returns the detected input language, or {@code null} if not returned by the API.
	 */
	public @Nullable String getLanguage() {
		return this.language;
	}

	/**
	 * Returns the provider-native usage object for this transcription, or {@code null} if
	 * not returned by the API. The concrete type depends on the response format
	 * requested: {@link TranscriptionVerbose.Usage} for {@code verbose_json},
	 * {@link TranscriptionDiarized.Usage} for {@code diarized_json}, or
	 * {@link Transcription.Usage} otherwise.
	 */
	public @Nullable Object getNativeUsage() {
		return this.usage;
	}

	/**
	 * Returns the segment-level transcription breakdown, or {@code null} if not returned
	 * by the API. Elements are
	 * {@link com.openai.models.audio.transcriptions.TranscriptionSegment} for
	 * {@code verbose_json}, or
	 * {@link com.openai.models.audio.transcriptions.TranscriptionDiarizedSegment}
	 * (carrying per-speaker attribution) for {@code diarized_json}.
	 */
	public @Nullable List<?> getSegments() {
		return this.segments;
	}

	/**
	 * Returns the word-level timestamps, or {@code null} if not returned by the API. Only
	 * populated for {@code verbose_json} when word-level {@code timestamp_granularities}
	 * were requested.
	 */
	public @Nullable List<TranscriptionWord> getWords() {
		return this.words;
	}

	@Override
	public String toString() {
		return "{ @type: %1$s, duration: %2$s, language: %3$s, usage: %4$s, segments: %5$s, words: %6$s }"
			.formatted(getClass().getName(), getDuration(), getLanguage(), getNativeUsage(), getSegments(), getWords());
	}

}
