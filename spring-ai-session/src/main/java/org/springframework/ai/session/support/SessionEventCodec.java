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

package org.springframework.ai.session.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.session.EventType;
import org.springframework.ai.session.SessionEvent;
import org.springframework.util.Assert;

/**
 * The persisted shape of a {@link SessionEvent}, shared by every
 * {@link org.springframework.ai.session.SessionRepository} that stores events as columns
 * or fields rather than as Java objects.
 *
 * <p>
 * An event is stored as four fields: its {@link EventType}, the {@link MessageType} and
 * text of its message (when it carries one; the text is kept as its own field so a store
 * can search it), and a JSON {@code payload}. For an event with a message the payload is
 * the message's ordered {@link MessagePart}s and its metadata, serialized with the parts'
 * own polymorphic JSON, so media, tool calls, tool results and provider-signed reasoning
 * all survive a round trip; an event type of the application's own that carries a message
 * stores its own {@link SessionEvent#getPayload() payload} in the same JSON object. For
 * every other event type the payload is {@link SessionEvent#getPayload()}.
 * {@link #encode} and {@link #decode} convert between the two, and {@link #toJson} /
 * {@link #fromJsonMap} handle the metadata maps of sessions and events.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class SessionEventCodec {

	private static final Logger logger = LoggerFactory.getLogger(SessionEventCodec.class);

	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final JsonMapper jsonMapper;

	/** Creates a codec with a default {@link JsonMapper}. */
	public SessionEventCodec() {
		this(JsonMapper.builder().build());
	}

	/**
	 * Creates a codec that serializes with the given mapper.
	 * @param jsonMapper the mapper used for the payloads and the metadata maps
	 */
	public SessionEventCodec(JsonMapper jsonMapper) {
		Assert.notNull(jsonMapper, "jsonMapper must not be null");
		this.jsonMapper = jsonMapper;
	}

	/**
	 * Encodes an event into its persisted shape.
	 * @param event the event to encode
	 * @return the fields to store
	 * @throws IllegalStateException if the payload of a non-message event cannot be
	 * serialized
	 */
	public EncodedEvent encode(SessionEvent event) {
		Assert.notNull(event, "event must not be null");
		if (!event.hasMessage()) {
			return new EncodedEvent(event.getType(), null, null, toJson(event.getPayload()));
		}
		Message message = event.getMessage();
		return new EncodedEvent(event.getType(), message.getMessageType(), message.getText(),
				encodeMessageBody(message, event.getPayload()));
	}

	/**
	 * Rebuilds an event from its persisted shape, as a builder: the codec restores the
	 * type, the message or the payload, and the caller adds identity, timestamp and
	 * metadata. A payload that cannot be parsed is logged and treated as absent, so a
	 * damaged row still yields an event.
	 * @param encoded the stored fields
	 * @return a builder holding the decoded type and content
	 */
	public SessionEvent.Builder decode(EncodedEvent encoded) {
		Assert.notNull(encoded, "encoded must not be null");
		SessionEvent.Builder builder = SessionEvent.builder().type(encoded.type());
		MessageType messageType = encoded.messageType();
		if (!encoded.type().canCarryMessage() || (encoded.type() != EventType.MESSAGE && messageType == null)) {
			return builder.payload(fromJsonMap(encoded.payload()));
		}
		Assert.notNull(messageType, "messageType must not be null for a MESSAGE event");
		MessageBody body = parseMessageBody(encoded.payload());
		return builder.message(toMessage(messageType, encoded.text(), body)).payload(body.payload());
	}

	/**
	 * Serializes a value (typically a metadata map) to JSON.
	 * @param value the value, or {@code null}
	 * @return the JSON text, or {@code null} for a {@code null} value
	 * @throws IllegalStateException if the value cannot be serialized
	 */
	public @Nullable String toJson(@Nullable Object value) {
		if (value == null) {
			return null;
		}
		try {
			return this.jsonMapper.writeValueAsString(value);
		}
		catch (JacksonException ex) {
			throw new IllegalStateException("Failed to serialize value to JSON", ex);
		}
	}

	/**
	 * Parses a JSON object into a map. Blank input yields an empty map; input that cannot
	 * be parsed is logged and yields an empty map, so a damaged row still loads.
	 * @param json the JSON text, or {@code null}
	 * @return the map, never {@code null}
	 */
	public Map<String, Object> fromJsonMap(@Nullable String json) {
		if (json == null || json.isBlank()) {
			return Map.of();
		}
		try {
			return this.jsonMapper.readValue(json, MAP_TYPE);
		}
		catch (JacksonException ex) {
			logger.warn("Failed to deserialize JSON object; returning empty map", ex);
			return new HashMap<>();
		}
	}

	/**
	 * Serializes the parts and metadata of a message. Message metadata is whatever a chat
	 * model attached and is not guaranteed to be serializable; when it is not, the parts
	 * are stored without it rather than failing the append.
	 */
	private String encodeMessageBody(Message message, Map<String, Object> payload) {
		List<MessagePart> parts = partsOf(message);
		try {
			return this.jsonMapper.writeValueAsString(new MessageBody(parts, message.getMetadata(), payload));
		}
		catch (JacksonException ex) {
			logger.warn("Message metadata is not JSON-serializable; storing the message without it", ex);
			return writeMessageBody(new MessageBody(parts, Map.of(), payload));
		}
	}

	/**
	 * The ordered parts of a message. Every Spring AI message is an
	 * {@link AbstractMessage} and exposes them; a foreign {@link Message} implementation
	 * is reduced to its text.
	 */
	private static List<MessagePart> partsOf(Message message) {
		if (message instanceof AbstractMessage abstractMessage) {
			return abstractMessage.getParts();
		}
		String text = message.getText();
		return (text != null) ? List.of(TextPart.of(text)) : List.of();
	}

	private String writeMessageBody(MessageBody body) {
		try {
			return this.jsonMapper.writeValueAsString(body);
		}
		catch (JacksonException ex) {
			throw new IllegalStateException("Failed to serialize message parts to JSON", ex);
		}
	}

	private MessageBody parseMessageBody(@Nullable String json) {
		if (json == null || json.isBlank()) {
			return new MessageBody(List.of(), Map.of());
		}
		try {
			MessageBody body = this.jsonMapper.readValue(json, MessageBody.class);
			return new MessageBody(body.parts() != null ? body.parts() : List.of(),
					body.metadata() != null ? body.metadata() : Map.of(),
					body.payload() != null ? body.payload() : Map.of());
		}
		catch (JacksonException ex) {
			logger.warn("Failed to deserialize message parts from JSON; returning the text only", ex);
			return new MessageBody(List.of(), Map.of());
		}
	}

	/**
	 * Builds the message of the given type from its parts. A system message has no parts
	 * builder and is rebuilt from its text; a message whose parts are missing (a damaged
	 * payload) falls back to its text as well.
	 */
	private static Message toMessage(MessageType type, @Nullable String text, MessageBody body) {
		String safeText = (text != null) ? text : "";
		List<MessagePart> parts = body.parts();
		Map<String, Object> metadata = body.metadata();
		return switch (type) {
			case USER -> parts.isEmpty() ? UserMessage.builder().text(safeText).metadata(metadata).build()
					: UserMessage.builder().parts(parts).metadata(metadata).build();
			case ASSISTANT ->
				parts.isEmpty() ? AssistantMessage.builder().content(safeText).properties(metadata).build()
						: AssistantMessage.builder().parts(parts).properties(metadata).build();
			case TOOL -> ToolResponseMessage.builder().parts(parts).metadata(metadata).build();
			case SYSTEM -> SystemMessage.builder().text(safeText).metadata(metadata).build();
		};
	}

	/**
	 * The persisted shape of an event.
	 *
	 * @param type the event type
	 * @param messageType the message type of an event that carries a message, or
	 * {@code null} when it carries none
	 * @param text the text of that message, or {@code null} when there is none
	 * @param payload the JSON payload: the {@link MessageBody} of an event that carries a
	 * message, or the {@link SessionEvent#getPayload() payload} of any other event
	 */
	public record EncodedEvent(EventType type, @Nullable MessageType messageType, @Nullable String text,
			@Nullable String payload) {
	}

	/**
	 * The JSON payload of an event that carries a message.
	 *
	 * @param parts the ordered parts of the message
	 * @param metadata the message metadata, or an empty map when it could not be
	 * serialized
	 * @param payload the event's own {@link SessionEvent#getPayload() payload}; empty for
	 * a {@link EventType#MESSAGE} event
	 */
	public record MessageBody(List<MessagePart> parts, Map<String, Object> metadata, Map<String, Object> payload) {

		/** A body without an event payload, as a message event stores it. */
		public MessageBody(List<MessagePart> parts, Map<String, Object> metadata) {
			this(parts, metadata, Map.of());
		}

	}

}
