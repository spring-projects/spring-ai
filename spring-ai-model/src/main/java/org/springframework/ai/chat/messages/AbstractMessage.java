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

package org.springframework.ai.chat.messages;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * The AbstractMessage class is an abstract implementation of the Message interface. It
 * provides a base implementation for message content, media attachments, metadata, and
 * message type.
 *
 * @see Message
 */
public abstract class AbstractMessage implements Message {

	/**
	 * The key for the message type in the metadata.
	 */
	public static final String MESSAGE_TYPE = "messageType";

	/**
	 * The message type of the message.
	 */
	protected final MessageType messageType;

	/**
	 * The content of the message.
	 */
	protected final @Nullable String textContent;

	/**
	 * Additional options for the message to influence the response, not a generative map.
	 */
	protected final Map<String, Object> metadata;

	/**
	 * Create a new AbstractMessage with the given message type, text content, and
	 * metadata.
	 * @param messageType the message type
	 * @param textContent the text content
	 * @param metadata the metadata
	 */
	protected AbstractMessage(MessageType messageType, @Nullable String textContent, Map<String, Object> metadata) {
		Assert.notNull(messageType, "Message type must not be null");
		if (messageType == MessageType.SYSTEM || messageType == MessageType.USER) {
			Assert.notNull(textContent, "Content must not be null for SYSTEM or USER messages");
		}
		Assert.notNull(metadata, "Metadata must not be null");
		this.messageType = messageType;
		this.textContent = textContent;
		this.metadata = new HashMap<>(metadata);
		this.metadata.put(MESSAGE_TYPE, messageType);
	}

	/**
	 * Get the content of the message.
	 * @return the content of the message
	 */
	@Override
	public @Nullable String getText() {
		return this.textContent;
	}

	/**
	 * Get the metadata of the message.
	 * @return the metadata of the message
	 */
	@Override
	public Map<String, Object> getMetadata() {
		return this.metadata;
	}

	/**
	 * Get the message type of the message.
	 * @return the message type of the message
	 */
	@Override
	public MessageType getMessageType() {
		return this.messageType;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AbstractMessage that)) {
			return false;
		}
		return this.messageType == that.messageType && Objects.equals(this.textContent, that.textContent)
				&& Objects.equals(this.metadata, that.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.messageType, this.textContent, this.metadata);
	}

}
