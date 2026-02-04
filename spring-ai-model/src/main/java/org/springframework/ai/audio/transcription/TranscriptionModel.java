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

import org.jspecify.annotations.Nullable;

import org.springframework.ai.model.Model;
import org.springframework.core.io.Resource;

/**
 * A transcription model is a type of AI model that converts audio to text. This is also
 * known as Speech-to-Text.
 *
 * @author Mudabir Hussain
 * @since 1.0.0
 */
public interface TranscriptionModel extends Model<AudioTranscriptionPrompt, AudioTranscriptionResponse> {

	/**
	 * Transcribes the audio from the given prompt.
	 * @param transcriptionPrompt The prompt containing the audio resource and options.
	 * @return The transcription response.
	 */
	AudioTranscriptionResponse call(AudioTranscriptionPrompt transcriptionPrompt);

	/**
	 * A convenience method for transcribing an audio resource.
	 * @param resource The audio resource to transcribe.
	 * @return The transcribed text.
	 */
	default String transcribe(Resource resource) {
		return this.transcribe(resource, null);
	}

	/**
	 * A convenience method for transcribing an audio resource with the given options.
	 * @param resource The audio resource to transcribe.
	 * @param options The transcription options.
	 * @return The transcribed text.
	 */
	default String transcribe(Resource resource, @Nullable AudioTranscriptionOptions options) {
		AudioTranscriptionPrompt prompt = (options != null ? new AudioTranscriptionPrompt(resource, options)
				: new AudioTranscriptionPrompt(resource));
		AudioTranscription result = this.call(prompt).getResult();
		return result != null ? result.getOutput() : "";
	}

}
