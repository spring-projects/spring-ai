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

package org.springframework.ai.moderation;

import java.util.Objects;

/**
 * Represents a single message intended for moderation, encapsulating the text content.
 * This class provides a basic structure for messages that can be submitted to moderation
 * processes.
 *
 * @author Ahmed Yousri
 * @since 1.0.0
 */
public class ModerationMessage {

	private String text;

	public ModerationMessage(String text) {
		this.text = text;
	}

	public String getText() {
		return this.text;
	}

	public void setText(String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return "ModerationMessage{" + "text='" + this.text + '\'' + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ModerationMessage)) {
			return false;
		}
		ModerationMessage that = (ModerationMessage) o;
		return Objects.equals(this.text, that.text);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.text);
	}

}
