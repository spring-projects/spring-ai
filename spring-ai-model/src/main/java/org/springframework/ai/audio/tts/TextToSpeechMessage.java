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

/**
 * Implementation of the {@link TextToSpeechMessage} interface for the text to speech
 * message.
 *
 * @author Alexandros Pappas
 */
public class TextToSpeechMessage {

	private final String text;

	public TextToSpeechMessage(String text) {
		this.text = text;
	}

	public String getText() {
		return this.text;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof TextToSpeechMessage that)) {
			return false;
		}
		return Objects.equals(this.text, that.text);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.text);
	}

	@Override
	public String toString() {
		return "TextToSpeechMessage{" + "text='" + this.text + '\'' + '}';
	}

}
