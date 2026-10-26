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

package org.springframework.ai.audio.tts;

import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.model.MutableResponseMetadata;

/**
 * Metadata associated with an audio transcription response.
 *
 * @author Alexandros Pappas
 */
public class TextToSpeechResponseMetadata extends MutableResponseMetadata {

	private Usage usage = new EmptyUsage();

	/**
	 * Returns AI provider specific metadata on API usage.
	 * @return AI provider specific metadata on API usage.
	 * @see Usage
	 * @since 2.0.2
	 */
	public Usage getUsage() {
		return this.usage;
	}

	/**
	 * Sets the AI provider specific metadata on API usage.
	 * @param usage the API usage metadata
	 * @since 2.0.2
	 */
	public void setUsage(Usage usage) {
		this.usage = usage;
	}

}
