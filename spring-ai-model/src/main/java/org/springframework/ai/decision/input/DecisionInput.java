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
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.content.Media;

/**
 * The input a decision model decides about: a text, a set of named fields, or a
 * conversation.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public sealed interface DecisionInput permits TextInput, FieldsInput, ConversationInput {

	/**
	 * A text input.
	 */
	static TextInput text(String text) {
		return new TextInput(text, List.of());
	}

	/**
	 * A text input with attached media, such as images.
	 */
	static TextInput text(String text, List<Media> media) {
		return new TextInput(text, media);
	}

	/**
	 * A structured input made of named fields. Values can be strings, numbers, booleans,
	 * nulls, maps, lists, or {@link Media} at the top level.
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
