/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.elevenlabs.metadata;

import java.util.List;

import org.springframework.ai.audio.transcription.AudioTranscriptionMetadata;
import org.springframework.ai.elevenlabs.api.ElevenLabsSpeechToTextApi;

/**
 * Rich metadata for ElevenLabs audio transcription results.
 *
 * @author Alexandros Pappas
 * @since 1.0.0
 */
public final class ElevenLabsAudioTranscriptionMetadata implements AudioTranscriptionMetadata {

	private final String transcriptionId;

	private final String languageCode;

	private final Double languageProbability;

	private final List<ElevenLabsSpeechToTextApi.Word> words;

	private ElevenLabsAudioTranscriptionMetadata(String transcriptionId, String languageCode,
			Double languageProbability, List<ElevenLabsSpeechToTextApi.Word> words) {
		this.transcriptionId = transcriptionId;
		this.languageCode = languageCode;
		this.languageProbability = languageProbability;
		this.words = words != null ? List.copyOf(words) : null;
	}

	/**
	 * Create metadata from an ElevenLabs Speech-to-Text response.
	 * @param response The API response.
	 * @return The metadata instance.
	 */
	public static ElevenLabsAudioTranscriptionMetadata from(ElevenLabsSpeechToTextApi.SpeechToTextResponse response) {
		if (response == null) {
			return new ElevenLabsAudioTranscriptionMetadata(null, null, null, null);
		}
		return new ElevenLabsAudioTranscriptionMetadata(response.transcriptionId(), response.languageCode(),
				response.languageProbability(), response.words());
	}

	/**
	 * Get the transcription ID (available when webhook mode is used).
	 * @return The transcription ID, or null for synchronous transcriptions.
	 */
	public String getTranscriptionId() {
		return this.transcriptionId;
	}

	/**
	 * Get the detected language code (ISO-639-1 or ISO-639-3).
	 * @return The language code.
	 */
	public String getLanguageCode() {
		return this.languageCode;
	}

	/**
	 * Get the language detection confidence (0.0 to 1.0).
	 * @return The language probability.
	 */
	public Double getLanguageProbability() {
		return this.languageProbability;
	}

	/**
	 * Get the word-level transcription details including timing and speaker info.
	 * @return The list of words with metadata.
	 */
	public List<ElevenLabsSpeechToTextApi.Word> getWords() {
		return this.words;
	}

	@Override
	public String toString() {
		return "ElevenLabsAudioTranscriptionMetadata{" + "transcriptionId='" + this.transcriptionId + '\''
				+ ", languageCode='" + this.languageCode + '\'' + ", languageProbability=" + this.languageProbability
				+ ", words=" + (this.words != null ? this.words.size() + " words" : "null") + '}';
	}

}
