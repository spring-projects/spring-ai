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

package org.springframework.ai.audio.tts;

import java.util.Objects;

import org.springframework.ai.model.ModelRequest;

/**
 * Implementation of the {@link ModelRequest} interface for the text to speech prompt.
 *
 * @author Alexandros Pappas
 */
public class TextToSpeechPrompt implements ModelRequest<TextToSpeechMessage> {

	private final TextToSpeechMessage message;

	private TextToSpeechOptions options;

	public TextToSpeechPrompt(String text) {
		this(new TextToSpeechMessage(text), TextToSpeechOptions.builder().build());
	}

	public TextToSpeechPrompt(String text, TextToSpeechOptions options) {
		this(new TextToSpeechMessage(text), options);
	}

	public TextToSpeechPrompt(TextToSpeechMessage message) {
		this(message, TextToSpeechOptions.builder().build());
	}

	public TextToSpeechPrompt(TextToSpeechMessage message, TextToSpeechOptions options) {
		this.message = message;
		this.options = options;
	}

	@Override
	public TextToSpeechMessage getInstructions() {
		return this.message;
	}

	@Override
	public TextToSpeechOptions getOptions() {
		return this.options;
	}

	public void setOptions(TextToSpeechOptions options) {
		this.options = options;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof TextToSpeechPrompt that)) {
			return false;
		}
		return Objects.equals(this.message, that.message) && Objects.equals(this.options, that.options);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.message, this.options);
	}

	@Override
	public String toString() {
		return "TextToSpeechPrompt{" + "message=" + this.message + ", options=" + this.options + '}';
	}

}
