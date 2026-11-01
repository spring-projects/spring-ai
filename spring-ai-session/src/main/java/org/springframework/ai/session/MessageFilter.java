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

package org.springframework.ai.session;

import java.util.Locale;
import java.util.Set;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Decides which messages are stored in a session: the write-side counterpart of
 * {@link EventFilter}, which selects the stored events a prompt reads. A message the
 * filter rejects is never stored and so never replayed; the outgoing prompt is
 * unaffected. {@code SessionMemoryAdvisor} stores tool calls and tool results whatever
 * the filter says, since either half alone is unusable; hide them on read with an
 * {@link EventFilter}'s {@code messageTypes} instead.
 * <p>
 * Filters compose with {@link #and(MessageFilter)}, {@link #or(MessageFilter)} and
 * {@link #negate()}: <pre>{@code
 * MessageFilter filter = MessageFilter.byMessageType(MessageType.USER, MessageType.ASSISTANT)
 *     .and(MessageFilter.skipEmptyMessages());
 * }</pre>
 *
 * @author Sukhrob Tokhirov
 * @since 2.1.0
 */
@FunctionalInterface
public interface MessageFilter {

	/**
	 * Whether the message is stored.
	 * @param message the message about to be stored
	 */
	boolean shouldPersist(Message message);

	/**
	 * A filter that stores a message only if both this filter and {@code other} accept
	 * it.
	 */
	default MessageFilter and(MessageFilter other) {
		Assert.notNull(other, "other must not be null");
		return message -> this.shouldPersist(message) && other.shouldPersist(message);
	}

	/** A filter that stores a message if this filter or {@code other} accepts it. */
	default MessageFilter or(MessageFilter other) {
		Assert.notNull(other, "other must not be null");
		return message -> this.shouldPersist(message) || other.shouldPersist(message);
	}

	/** A filter that stores what this filter rejects, and the other way round. */
	default MessageFilter negate() {
		return message -> !this.shouldPersist(message);
	}

	/** A filter that stores every message. */
	static MessageFilter all() {
		return message -> true;
	}

	/**
	 * A filter that skips an {@link AssistantMessage} with no content, no text, no tool
	 * calls and no media, and stores every other message; the default of {@code
	 * SessionMemoryAdvisor}. Some models emit such an empty frame after a tool-call
	 * sequence and reject it when it is replayed as history.
	 */
	static MessageFilter skipEmptyMessages() {
		return message -> !(message instanceof AssistantMessage am && (am.getText() == null || am.getText().isBlank())
				&& !am.hasToolCalls() && CollectionUtils.isEmpty(am.getMedia()));
	}

	/**
	 * A filter that stores only the messages of the given types.
	 * @param types the message types to store; at least one
	 */
	static MessageFilter byMessageType(MessageType... types) {
		Assert.notEmpty(types, "types must not be empty");
		Set<MessageType> typeSet = Set.of(types);
		return message -> typeSet.contains(message.getMessageType());
	}

	/**
	 * A filter that stores only the messages whose text contains the keyword,
	 * case-insensitively; a message without text is rejected.
	 * @param keyword the text to look for; not blank
	 */
	static MessageFilter containsText(String keyword) {
		Assert.hasText(keyword, "keyword must not be blank");
		String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
		return message -> {
			String text = message.getText();
			return text != null && text.toLowerCase(Locale.ROOT).contains(lowerKeyword);
		};
	}

}
