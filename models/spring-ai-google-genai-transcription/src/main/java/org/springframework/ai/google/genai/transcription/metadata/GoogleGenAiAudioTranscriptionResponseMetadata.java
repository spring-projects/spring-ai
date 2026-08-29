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

package org.springframework.ai.google.genai.transcription.metadata;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata;

/**
 * Audio transcription metadata implementation for Google Cloud Speech-to-Text V2.
 * <p>
 * Unlike OpenAI Whisper, the Speech-to-Text V2 {@code Recognize} API is billed by audio
 * duration rather than tokens, so no token-based usage is available. The {@code duration}
 * exposed here is the billed duration reported by the API, in seconds.
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
public class GoogleGenAiAudioTranscriptionResponseMetadata extends AudioTranscriptionResponseMetadata {

	private final @Nullable Double duration;

	private final @Nullable String language;

	private final @Nullable String model;

	private final @Nullable String requestId;

	public GoogleGenAiAudioTranscriptionResponseMetadata() {
		this(null, null, null, null);
	}

	public GoogleGenAiAudioTranscriptionResponseMetadata(@Nullable Double duration, @Nullable String language,
			@Nullable String model, @Nullable String requestId) {
		this.duration = duration;
		this.language = language;
		this.model = model;
		this.requestId = requestId;
	}

	/**
	 * Returns the billed audio duration in seconds, or {@code null} if not returned by
	 * the API.
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
	 * Returns the model used for the transcription, or {@code null} if not available.
	 */
	public @Nullable String getModel() {
		return this.model;
	}

	/**
	 * Returns the API request identifier, or {@code null} if not returned by the API.
	 */
	public @Nullable String getRequestId() {
		return this.requestId;
	}

	@Override
	public String toString() {
		return "{ @type: %1$s, duration: %2$s, language: %3$s, model: %4$s, requestId: %5$s }"
			.formatted(getClass().getName(), getDuration(), getLanguage(), getModel(), getRequestId());
	}

}
