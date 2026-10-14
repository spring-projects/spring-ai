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
 * Decides which {@link Message}s get appended (persisted) to session memory.
 *
 * <p>
 * This is the write-side counterpart of {@link EventFilter}: an {@code EventFilter}
 * selects which <em>stored</em> events are loaded into the next prompt, while a
 * {@code MessageFilter} decides whether a message is stored at all. A message rejected by
 * this filter is never persisted and therefore never replayed on later requests.
 * {@code SessionMemoryAdvisor} stores tool calls and tool results whatever the filter
 * says, since either half alone is unusable; hide them on read with an
 * {@link EventFilter}'s {@code messageTypes} instead.
 *
 * <p>
 * Filters can consider the message type as well as the message content, and compose via
 * {@link #and(MessageFilter)}, {@link #or(MessageFilter)} and {@link #negate()}:
 *
 * <pre>{@code
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
	 * Returns {@code true} if the given message should be appended to session memory.
	 * @param message the message about to be persisted
	 */
	boolean shouldPersist(Message message);

	/**
	 * Returns a composed filter that persists a message only if both this filter and
	 * {@code other} accept it.
	 */
	default MessageFilter and(MessageFilter other) {
		Assert.notNull(other, "other must not be null");
		return message -> this.shouldPersist(message) && other.shouldPersist(message);
	}

	/**
	 * Returns a composed filter that persists a message if either this filter or
	 * {@code other} accepts it.
	 */
	default MessageFilter or(MessageFilter other) {
		Assert.notNull(other, "other must not be null");
		return message -> this.shouldPersist(message) || other.shouldPersist(message);
	}

	/** Returns a filter that represents the logical negation of this filter. */
	default MessageFilter negate() {
		return message -> !this.shouldPersist(message);
	}

	/** Returns a filter that persists every message (no filtering). */
	static MessageFilter all() {
		return message -> true;
	}

	/**
	 * Skips {@link AssistantMessage}s that carry no content — blank or {@code null} text,
	 * no tool calls, and no media. Some models emit such empty frames (e.g. AWS Bedrock
	 * Converse produces an empty {@code end_turn} frame after a tool-call sequence) and
	 * reject them when replayed as history on the next request. All other message types
	 * are persisted unconditionally.
	 *
	 * <p>
	 * This is the default filter of {@code SessionMemoryAdvisor}.
	 */
	static MessageFilter skipEmptyMessages() {
		return message -> !(message instanceof AssistantMessage am && (am.getText() == null || am.getText().isBlank())
				&& !am.hasToolCalls() && CollectionUtils.isEmpty(am.getMedia()));
	}

	/**
	 * Persists only messages whose {@link Message#getMessageType()} is among the given
	 * types.
	 * @param types the message types to persist; must not be empty
	 */
	static MessageFilter byMessageType(MessageType... types) {
		Assert.notEmpty(types, "types must not be empty");
		Set<MessageType> typeSet = Set.of(types);
		return message -> typeSet.contains(message.getMessageType());
	}

	/**
	 * Persists only messages whose text contains the given keyword (case-insensitive
	 * substring match). Messages with {@code null} text are rejected.
	 * @param keyword the search term; must not be blank
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
