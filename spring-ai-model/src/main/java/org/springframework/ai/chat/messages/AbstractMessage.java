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

package org.springframework.ai.chat.messages;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.TextPart;
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
	 * The text content of this message, derived from its {@link TextPart}s.
	 * @deprecated since 2.1.0 in favor of {@link #getText()}; kept so existing subclasses
	 * that read the field keep compiling. Will be removed in a future release.
	 */
	@Deprecated(since = "2.1.0", forRemoval = true)
	protected final @Nullable String textContent;

	/**
	 * Additional options for the message to influence the response, not a generative map.
	 */
	protected final Map<String, Object> metadata;

	/**
	 * The ordered content parts of the message.
	 */
	private final List<MessagePart> parts;

	/**
	 * Create a new AbstractMessage with the given message type, text content, and
	 * metadata.
	 * @param messageType the message type
	 * @param textContent the text content
	 * @param metadata the metadata
	 */
	protected AbstractMessage(MessageType messageType, @Nullable String textContent, Map<String, Object> metadata) {
		this(messageType, legacyParts(textContent), metadata);
	}

	/**
	 * Create a new AbstractMessage from an ordered list of parts. The text content is
	 * derived by joining the {@link TextPart}s in the list, without a separator.
	 * @param messageType the message type
	 * @param parts the parts, in order
	 * @param metadata the metadata
	 * @since 2.1.0
	 */
	protected AbstractMessage(MessageType messageType, List<MessagePart> parts, Map<String, Object> metadata) {
		Assert.notNull(messageType, "Message type must not be null");
		Assert.notNull(parts, "Parts must not be null");
		Assert.notNull(metadata, "Metadata must not be null");
		this.parts = List.copyOf(parts);
		if (messageType == MessageType.SYSTEM || messageType == MessageType.USER) {
			Assert.notEmpty(this.parts, "Content must not be null for SYSTEM or USER messages");
		}
		this.messageType = messageType;
		this.textContent = joinText(this.parts);
		this.metadata = new HashMap<>(metadata);
		this.metadata.put(MESSAGE_TYPE, messageType);
	}

	protected static List<MessagePart> legacyParts(@Nullable String textContent) {
		return (textContent != null) ? List.of(TextPart.of(textContent)) : List.of();
	}

	protected <P extends MessagePart> Stream<P> select(Class<P> type) {
		return getParts().stream().filter(type::isInstance).map(type::cast);
	}

	/**
	 * Joins the text parts without a separator, matching the historical concatenation of
	 * provider text blocks. A message with parts but no text part (reasoning only, tool
	 * calls only) has an empty text, so streamed chunks and tool-call turns never report
	 * {@code null} and the common {@code map(getText).collect(joining())} idiom keeps
	 * working; only a message without any part has a {@code null} text, as a legacy
	 * message built from a {@code null} content always had.
	 */
	private static @Nullable String joinText(List<MessagePart> parts) {
		Assert.notNull(parts, "Parts must not be null");
		if (parts.isEmpty()) {
			return null;
		}
		return parts.stream()
			.filter(TextPart.class::isInstance)
			.map(TextPart.class::cast)
			.map(TextPart::text)
			.collect(Collectors.joining());
	}

	/**
	 * The ordered content parts of this message.
	 * @return an unmodifiable list of parts
	 * @since 2.1.0
	 */
	public List<MessagePart> getParts() {
		return this.parts;
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
	 * Get the text content of the message.
	 * @return the text content of the message
	 */
	@Nullable
	public String getTextContent() {
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
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AbstractMessage that)) {
			return false;
		}
		return this.messageType == that.messageType && Objects.equals(this.metadata, that.metadata)
				&& Objects.equals(this.parts, that.parts);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.messageType, this.metadata, this.parts);
	}

}
