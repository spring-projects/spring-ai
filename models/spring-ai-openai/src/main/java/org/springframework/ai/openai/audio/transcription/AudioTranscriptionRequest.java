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

import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.model.ModelRequest;
import org.springframework.core.io.Resource;

/**
 * @author Michael Lavelle
 * @since 0.8.1
 */
public class AudioTranscriptionRequest implements ModelRequest<Resource> {

	private Resource audioResource;

	private ModelOptions modelOptions;

	public AudioTranscriptionRequest(Resource audioResource) {
		this.audioResource = audioResource;
	}

	public AudioTranscriptionRequest(Resource audioResource, ModelOptions modelOptions) {
		this.audioResource = audioResource;
		this.modelOptions = modelOptions;
	}

	@Override
	public Resource getInstructions() {
		return audioResource;
	}

	@Override
	public ModelOptions getOptions() {
		return modelOptions;
	}

}
