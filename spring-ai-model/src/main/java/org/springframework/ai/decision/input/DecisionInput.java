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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.util.Assert;

/**
 * The input a decision model decides about: a text or other content, a set of named
 * fields, or a conversation.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @since 2.1.0
 */
public sealed interface DecisionInput permits TextInput, FieldsInput, ConversationInput {

	/**
	 * A text input.
	 */
	static TextInput text(String text) {
		Assert.hasText(text, "text cannot be null or empty");
		return new TextInput(List.of(TextPart.of(text)));
	}

	/**
	 * An input made of ordered content parts, such as a {@link TextPart} followed by the
	 * {@link org.springframework.ai.chat.messages.part.MediaPart}s of the images it
	 * refers to.
	 */
	static TextInput content(List<? extends MessagePart> parts) {
		return new TextInput(parts);
	}

	/**
	 * An input made of ordered content parts, such as a {@link TextPart} followed by the
	 * {@link org.springframework.ai.chat.messages.part.MediaPart}s of the images it
	 * refers to.
	 */
	static TextInput content(MessagePart... parts) {
		Assert.notNull(parts, "parts cannot be null");
		return new TextInput(Arrays.asList(parts));
	}

	/**
	 * A structured input made of named fields. Values can be strings, numbers, booleans,
	 * nulls, maps, lists, or, at the top level, a {@link MessagePart} or a list of them.
	 */
	static FieldsInput fields(Map<String, @Nullable Object> fields) {
		return new FieldsInput(fields);
	}

	/**
	 * A conversation input, such as the one a chat model is about to answer or has just
	 * answered.
	 */
	static ConversationInput conversation(List<? extends Message> messages) {
		return new ConversationInput(messages);
	}

}
