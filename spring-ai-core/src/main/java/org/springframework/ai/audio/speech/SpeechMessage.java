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
package org.springframework.ai.audio.speech;

import org.springframework.util.Assert;

import java.util.Objects;

/**
 * The {@link SpeechMessage} class represents a single text message to be converted to
 * speech.
 *
 * @author Ahmed Yousri
 * @author Thomas Vitale
 * @since 1.0.0-M1
 */
public class SpeechMessage {

	private final String text;

	public SpeechMessage(String text) {
		Assert.hasText(text, "text cannot be null or empty");
		this.text = text;
	}

	public String getText() {
		return text;
	}

	@Override
	public String toString() {
		return "SpeechMessage{" + "text='" + text + "'}";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof SpeechMessage that))
			return false;
		return Objects.equals(text, that.text);
	}

	@Override
	public int hashCode() {
		return Objects.hash(text);
	}

}
