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

import org.jspecify.annotations.Nullable;

import org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.TranscriptionSegment;

/**
 * Metadata of a Mistral AI transcription response. The usage is available from
 * {@link #getUsage()}, with the provider-native usage as its native usage.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public class MistralAiTranscriptionResponseMetadata extends AudioTranscriptionResponseMetadata {

	private final @Nullable String model;

	private final @Nullable String language;

	private final List<TranscriptionSegment> segments;

	public MistralAiTranscriptionResponseMetadata(@Nullable String model, @Nullable String language,
			@Nullable List<TranscriptionSegment> segments) {
		this.model = model;
		this.language = language;
		this.segments = (segments != null ? List.copyOf(segments) : List.of());
	}

	/**
	 * Returns the model used for the transcription.
	 */
	public @Nullable String getModel() {
		return this.model;
	}

	/**
	 * Returns the language of the audio, or {@code null} if not returned by the API.
	 */
	public @Nullable String getLanguage() {
		return this.language;
	}

	/**
	 * Returns the timestamped segments of the transcription, carrying the speaker of each
	 * when diarization was requested. Empty unless segment timestamps were requested.
	 */
	public List<TranscriptionSegment> getSegments() {
		return this.segments;
	}

	@Override
	public String toString() {
		return "{ @type: %1$s, model: %2$s, language: %3$s, usage: %4$s, segments: %5$s }"
			.formatted(getClass().getName(), getModel(), getLanguage(), getUsage(), getSegments());
	}

}
