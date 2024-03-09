/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.openai.audio.transcription;

import org.springframework.ai.model.ModelResult;
import org.springframework.ai.openai.metadata.audio.OpenAiAudioTranscriptionMetadata;
import org.springframework.lang.Nullable;

import java.util.Objects;

/**
 * Represents a response returned by the AI.
 *
 * @author Michael Lavelle
 * @since 0.8.1
 */
public class AudioTranscription implements ModelResult<String> {

	private String text;

	private OpenAiAudioTranscriptionMetadata transcriptionMetadata;

	public AudioTranscription(String text) {
		this.text = text;
	}

	@Override
	public String getOutput() {
		return this.text;
	}

	@Override
	public OpenAiAudioTranscriptionMetadata getMetadata() {
		return transcriptionMetadata != null ? transcriptionMetadata : OpenAiAudioTranscriptionMetadata.NULL;
	}

	public AudioTranscription withTranscriptionMetadata(
			@Nullable OpenAiAudioTranscriptionMetadata transcriptionMetadata) {
		this.transcriptionMetadata = transcriptionMetadata;
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof AudioTranscription that))
			return false;
		return Objects.equals(text, that.text) && Objects.equals(transcriptionMetadata, that.transcriptionMetadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(text, transcriptionMetadata);
	}

	@Override
	public String toString() {
		return "Transcript{" + "text=" + text + ", transcriptionMetadata=" + transcriptionMetadata + '}';
	}

}
