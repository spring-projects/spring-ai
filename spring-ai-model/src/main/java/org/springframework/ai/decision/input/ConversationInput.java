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

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.util.Assert;

/**
 * A {@link DecisionInput} made of a conversation, such as the one a chat model is about
 * to answer or has just answered. The messages are the same {@link Message} types a chat
 * model takes.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public final class ConversationInput implements DecisionInput {

	private final List<Message> messages;

	ConversationInput(List<? extends Message> messages) {
		Assert.notEmpty(messages, "messages cannot be null or empty");
		this.messages = List.copyOf(messages);
	}

	public List<Message> getMessages() {
		return this.messages;
	}

	/**
	 * The parts of a message, in order, for a provider to map. A message that does not
	 * keep parts has its text as a single text part, or no part when it has no text.
	 * @param message the message
	 * @return the parts of the message
	 * @since 2.1.0
	 */
	public static List<MessagePart> parts(Message message) {
		if (message instanceof AbstractMessage abstractMessage) {
			return abstractMessage.getParts();
		}
		String text = message.getText();
		return (text != null) ? List.of(TextPart.of(text)) : List.of();
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ConversationInput that)) {
			return false;
		}
		return this.messages.equals(that.messages);
	}

	@Override
	public int hashCode() {
		return this.messages.hashCode();
	}

	@Override
	public String toString() {
		return "ConversationInput{messages=" + this.messages.size() + "}";
	}

}
