/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.audio.transcription;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.model.ModelResponse;

/**
 * A response containing an audio transcription result.
 *
 * @author Michael Lavelle
 * @author Piotr Olaszewski
 * @since 0.8.1
 */
public class AudioTranscriptionResponse implements ModelResponse<AudioTranscription> {

	private final AudioTranscription transcript;

	private final AudioTranscriptionResponseMetadata transcriptionResponseMetadata;

	private final Map<String,Object> transcriptionContext;

	public AudioTranscriptionResponse(AudioTranscription transcript) {
		this(transcript, new AudioTranscriptionResponseMetadata(),new HashMap<>());
	}

	public AudioTranscriptionResponse(AudioTranscription transcript,AudioTranscriptionResponseMetadata transcriptionResponseMetadata) {
		this(transcript, new AudioTranscriptionResponseMetadata(),new HashMap<>());
	}

	public AudioTranscriptionResponse(AudioTranscription transcript,
			AudioTranscriptionResponseMetadata transcriptionResponseMetadata, Map<String,Object> transcriptionContext) {
		this.transcript = transcript;
		this.transcriptionResponseMetadata = transcriptionResponseMetadata;
		this.transcriptionContext=transcriptionContext;
	}

	@Override
	public AudioTranscription getResult() {
		return this.transcript;
	}

	@Override
	public List<AudioTranscription> getResults() {
		return List.of(this.transcript);
	}

	@Override
	public AudioTranscriptionResponseMetadata getMetadata() {
		return this.transcriptionResponseMetadata;
	}

	@Override
	public Map<String, Object> getContext() {
		return transcriptionContext;
	}

	@Override
	public String toString() {
		return "AudioTranscriptionResponse{" +
				"transcript=" + transcript +
				", transcriptionResponseMetadata=" + transcriptionResponseMetadata +
				", transcriptionContext=" + transcriptionContext +
				'}';
	}
}
