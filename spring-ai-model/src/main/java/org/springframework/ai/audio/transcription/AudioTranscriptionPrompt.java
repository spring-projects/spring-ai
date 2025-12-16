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

import org.springframework.ai.model.ModelRequest;
import org.springframework.core.io.Resource;

/**
 * Represents an audio transcription prompt for an AI model. It implements the
 * {@link ModelRequest} interface and provides the necessary information required to
 * interact with an AI model, including the audio resource and model options.
 *
 * @author Michael Lavelle
 * @author Piotr Olaszewski
 * @since 0.8.1
 */
public class AudioTranscriptionPrompt implements ModelRequest<Resource> {

	private final Resource audioResource;

	private @Nullable AudioTranscriptionOptions modelOptions;

	/**
	 * Construct a new AudioTranscriptionPrompt given the resource representing the audio
	 * file. The following input file types are supported: mp3, mp4, mpeg, mpga, m4a, wav,
	 * and webm.
	 * @param audioResource resource of the audio file.
	 */
	public AudioTranscriptionPrompt(Resource audioResource) {
		this.audioResource = audioResource;
	}

	/**
	 * Construct a new AudioTranscriptionPrompt given the resource representing the audio
	 * file. The following input file types are supported: mp3, mp4, mpeg, mpga, m4a, wav,
	 * and webm.
	 * @param audioResource resource of the audio file.
	 * @param modelOptions
	 */
	public AudioTranscriptionPrompt(Resource audioResource, @Nullable AudioTranscriptionOptions modelOptions) {
		this.audioResource = audioResource;
		this.modelOptions = modelOptions;
	}

	@Override
	public Resource getInstructions() {
		return this.audioResource;
	}

	@Override
	public @Nullable AudioTranscriptionOptions getOptions() {
		return this.modelOptions;
	}

}
