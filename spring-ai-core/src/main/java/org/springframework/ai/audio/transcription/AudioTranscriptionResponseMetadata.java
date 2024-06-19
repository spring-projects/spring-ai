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
package org.springframework.ai.audio.transcription;

import org.springframework.ai.model.ResponseMetadata;

import java.util.HashMap;

/**
 * @author Piotr Olaszewski
 */
public interface AudioTranscriptionResponseMetadata extends ResponseMetadata {

	class DefaultAudioTranscriptionResponseMetadata extends HashMap<String, Object>
			implements AudioTranscriptionResponseMetadata {

	}

	AudioTranscriptionResponseMetadata NULL = new AudioTranscriptionResponseMetadata.DefaultAudioTranscriptionResponseMetadata();

}
