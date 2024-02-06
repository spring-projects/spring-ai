/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.openai.audio.transcription;

import org.springframework.ai.model.ModelResponse;
import org.springframework.ai.openai.metadata.audio.OpenAiAudioTranscriptionResponseMetadata;

import java.util.Arrays;
import java.util.List;

/**
 * @author Michael Lavelle
 * @since 0.8.1
 */
public class AudioTranscriptionResponse implements ModelResponse<AudioTranscription> {

	private AudioTranscription transcript;

	private OpenAiAudioTranscriptionResponseMetadata transcriptionResponseMetadata;

	public AudioTranscriptionResponse(AudioTranscription transcript) {
		this(transcript, OpenAiAudioTranscriptionResponseMetadata.NULL);
	}

	public AudioTranscriptionResponse(AudioTranscription transcript,
			OpenAiAudioTranscriptionResponseMetadata transcriptionResponseMetadata) {
		this.transcript = transcript;
		this.transcriptionResponseMetadata = transcriptionResponseMetadata;
	}

	@Override
	public AudioTranscription getResult() {
		return transcript;
	}

	@Override
	public List<AudioTranscription> getResults() {
		return Arrays.asList(transcript);
	}

	@Override
	public OpenAiAudioTranscriptionResponseMetadata getMetadata() {
		return transcriptionResponseMetadata;
	}

}
