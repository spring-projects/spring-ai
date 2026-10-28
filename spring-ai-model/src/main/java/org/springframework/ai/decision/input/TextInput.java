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

package org.springframework.ai.decision.input;

import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.util.Assert;

/**
 * A {@link DecisionInput} made of ordered content parts: typically a text, optionally
 * followed by media such as images. The parts are the same {@link MessagePart} types a
 * chat message carries, so content prepared for a
 * {@link org.springframework.ai.chat.messages.UserMessage} can be judged as it is.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class TextInput implements DecisionInput {

	private final List<MessagePart> parts;

	TextInput(List<? extends MessagePart> parts) {
		Assert.notEmpty(parts, "parts cannot be null or empty");
		Assert.noNullElements(parts, "parts cannot contain null elements");
		this.parts = List.copyOf(parts);
	}

	/**
	 * The text of the input: its {@link TextPart}s joined, or an empty string when it has
	 * none.
	 */
	public String getText() {
		return this.parts.stream()
			.filter(TextPart.class::isInstance)
			.map(TextPart.class::cast)
			.map(TextPart::text)
			.collect(Collectors.joining());
	}

	/**
	 * The ordered content parts of the input.
	 */
	public List<MessagePart> getParts() {
		return this.parts;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof TextInput that)) {
			return false;
		}
		return this.parts.equals(that.parts);
	}

	@Override
	public int hashCode() {
		return this.parts.hashCode();
	}

	@Override
	public String toString() {
		return "TextInput{text='" + getText() + "', parts=" + this.parts.size() + "}";
	}

}
