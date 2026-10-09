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

import org.jspecify.annotations.Nullable;

import org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;

/**
 * {@link AudioTranscriptionResponseMetadata} implementation for the Google GenAI (Gemini)
 * transcription API.
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
public class GoogleGenAiAudioTranscriptionMetadata extends AudioTranscriptionResponseMetadata {

	private final @Nullable String modelVersion;

	public GoogleGenAiAudioTranscriptionMetadata(@Nullable String modelVersion, @Nullable Usage usage) {
		this.modelVersion = modelVersion;
		if (usage != null) {
			this.setUsage(usage);
		}
	}

	/**
	 * Returns the name of the model that generated the transcription, if available.
	 * @return the model version, or {@code null}
	 */
	public @Nullable String getModelVersion() {
		return this.modelVersion;
	}

}
