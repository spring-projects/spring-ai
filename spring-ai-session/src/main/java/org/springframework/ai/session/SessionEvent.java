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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.util.Assert;

/**
 * An entry of a session's log. An event has an {@link EventType}: a
 * {@link EventType#MESSAGE} event wraps a Spring AI {@link Message} and adds what the
 * message intentionally lacks, identity, ordering and the session it belongs to; every
 * other type records runtime state, a compaction or an application's own event, in
 * {@link #getPayload()} instead of a message, and {@link #getMessage()} throws for it.
 * Message events are the conversation: they alone take part in prompt assembly and
 * compaction.
 * <p>
 * An event is never changed once appended. A compaction records what left the active view
 * in a {@link EventType#COMPACTION} event of its own, and a covered event stays in the
 * log, readable through {@link EventFilter#all()}. A {@link #isSynthetic() synthetic}
 * event is one a compaction wrote: the user message that opens a summary turn and the
 * assistant message that closes it.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class SessionEvent {

	/** Metadata key of the synthetic flag, a {@link Boolean}. */
	public static final String METADATA_SYNTHETIC = "synthetic";

	/** Metadata key naming the strategy that wrote a synthetic event. */
	public static final String METADATA_COMPACTION_SOURCE = "compactionSource";

	/**
	 * Metadata key of a synthetic event: the id of the {@link EventType#COMPACTION}
	 * boundary it belongs to.
	 */
	public static final String METADATA_COMPACTION_ID = "compactionId";

	private final String id;

	private final String sessionId;

	private final Instant timestamp;

	private final EventType type;

	@Nullable private final Message message;

	private final Map<String, Object> payload;

	private final Map<String, Object> metadata;

	private SessionEvent(String id, String sessionId, Instant timestamp, EventType type, @Nullable Message message,
			Map<String, Object> payload, Map<String, Object> metadata) {
		this.id = id;
		this.sessionId = sessionId;
		this.timestamp = timestamp;
		this.type = type;
		this.message = message;
		this.payload = Map.copyOf(payload);
		this.metadata = Map.copyOf(metadata);
	}

	/** The identity of the event, unique across sessions. */
	public String getId() {
		return this.id;
	}

	/** The session the event belongs to. */
	public String getSessionId() {
		return this.sessionId;
	}

	/** When the event was recorded. A log is ordered by insertion, not by this. */
	public Instant getTimestamp() {
		return this.timestamp;
	}

	/** What the event records; {@link EventType#MESSAGE} for a conversation message. */
	public EventType getType() {
		return this.type;
	}

	/**
	 * Whether the event carries a message, so that {@link #getMessage()} can be called: a
	 * {@link EventType#MESSAGE} event does; see {@link EventType#canCarryMessage()}.
	 */
	public boolean hasMessage() {
		return this.message != null;
	}

	/**
	 * The wrapped Spring AI message.
	 * @throws IllegalStateException if the event carries no message; see
	 * {@link #hasMessage()}
	 */
	public Message getMessage() {
		if (this.message == null) {
			throw new IllegalStateException(
					"Event '" + this.id + "' is a " + this.type + " event and carries no message");
		}
		return this.message;
	}

	/**
	 * The data of an event that is not a message, the ids a {@link EventType#COMPACTION}
	 * boundary names for example; empty for a message event. Values must be
	 * JSON-serializable.
	 */
	public Map<String, Object> getPayload() {
		return this.payload;
	}

	/**
	 * Framework flags such as {@link #METADATA_SYNTHETIC} and
	 * {@link #METADATA_COMPACTION_SOURCE}, and whatever the application adds.
	 */
	public Map<String, Object> getMetadata() {
		return this.metadata;
	}

	/**
	 * The type of the wrapped message.
	 * @throws IllegalStateException if the event carries no message; see
	 * {@link #hasMessage()}
	 */
	public MessageType getMessageType() {
		return getMessage().getMessageType();
	}

	/**
	 * Whether a compaction wrote this event, a summary rather than something a
	 * participant said.
	 */
	public boolean isSynthetic() {
		return (boolean) this.metadata.getOrDefault(METADATA_SYNTHETIC, false);
	}

	/**
	 * Whether the event starts a turn: a {@link MessageType#USER} message event. A turn
	 * is a user message plus everything up to the next one, the assistant replies, the
	 * tool calls and their results, so it is the unit no read or compaction cut may
	 * split. A synthetic shadow prompt is a user message too, so a summary turn is never
	 * separated from its summary. An event that is not a {@link EventType#MESSAGE} never
	 * starts a turn.
	 */
	public boolean isTurnStart() {
		return this.type == EventType.MESSAGE && this.message != null
				&& this.message.getMessageType() == MessageType.USER;
	}

	/**
	 * Whether the event is an assistant message with tool calls; {@code false} for an
	 * event without a message.
	 */
	public boolean hasToolCalls() {
		return this.message instanceof AssistantMessage am && am.hasToolCalls();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		SessionEvent other = (SessionEvent) obj;
		return this.id.equals(other.id) && this.sessionId.equals(other.sessionId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.sessionId);
	}

	/** Returns a new {@link Builder}. */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builds a {@link SessionEvent}. The id is a random UUID, the timestamp
	 * {@link Instant#now()}, the payload and the metadata empty, unless set; the session
	 * id is required. The type is {@link EventType#MESSAGE} when a message is set, which
	 * that type requires and no other type allows.
	 */
	public static final class Builder {

		private String id = UUID.randomUUID().toString();

		private String sessionId = "";

		private Instant timestamp = Instant.now();

		@Nullable private EventType type;

		@Nullable private Message message;

		private Map<String, Object> payload = new HashMap<>();

		private Map<String, Object> metadata = new HashMap<>();

		private Builder() {
		}

		/** The event id; a random UUID by default. */
		public Builder id(String id) {
			this.id = id;
			return this;
		}

		/** The session the event belongs to; required. */
		public Builder sessionId(String sessionId) {
			this.sessionId = sessionId;
			return this;
		}

		/** When the event was recorded; {@link Instant#now()} by default. */
		public Builder timestamp(Instant timestamp) {
			this.timestamp = timestamp;
			return this;
		}

		/**
		 * What the event records; {@link EventType#MESSAGE} by default when a message is
		 * set.
		 */
		public Builder type(EventType type) {
			this.type = type;
			return this;
		}

		/**
		 * The message a {@link EventType#MESSAGE} event wraps; required for that type,
		 * not allowed for any other.
		 */
		public Builder message(Message message) {
			this.message = message;
			return this;
		}

		/** Replaces the payload of an event that is not a message. */
		public Builder payload(Map<String, Object> payload) {
			this.payload = new HashMap<>(payload);
			return this;
		}

		/** Adds one payload entry. */
		public Builder payload(String key, Object value) {
			this.payload.put(key, value);
			return this;
		}

		/** Replaces the metadata. */
		public Builder metadata(Map<String, Object> metadata) {
			this.metadata = new HashMap<>(metadata);
			return this;
		}

		/** Adds one metadata entry. */
		public Builder metadata(String key, Object value) {
			this.metadata.put(key, value);
			return this;
		}

		/** Builds the event, checking that the type and the message agree. */
		public SessionEvent build() {
			Assert.hasText(this.id, "id must not be null or empty");
			Assert.hasText(this.sessionId, "sessionId must not be null or empty");
			Assert.notNull(this.timestamp, "timestamp must not be null");
			EventType resolvedType = (this.type != null) ? this.type : EventType.MESSAGE;
			if (resolvedType == EventType.MESSAGE) {
				Assert.notNull(this.message, "message must not be null for a MESSAGE event");
			}
			else if (!resolvedType.canCarryMessage()) {
				Assert.isNull(this.message, "a " + resolvedType + " event must not carry a message");
			}
			return new SessionEvent(this.id, this.sessionId, this.timestamp, resolvedType, this.message, this.payload,
					this.metadata);
		}

	}

}
