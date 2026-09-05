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

package org.springframework.ai.chat.memory.repository.mongo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A record representing a conversation in MongoDB.
 *
 * @author Lukasz Jernas
 * @author kezhenxu94
 * @since 1.1.0
 */
@Document("ai_chat_memory")
public record Conversation(String conversationId, Message message, Instant timestamp, int sequenceId) {

	/**
	 * Creates a conversation with a default sequence identifier of {@code 0}. Retained
	 * for backwards compatibility; prefer the canonical constructor so that the ordering
	 * of messages within a conversation is deterministic.
	 * @param conversationId the conversation identifier
	 * @param message the message
	 * @param timestamp the timestamp
	 */
	public Conversation(String conversationId, Message message, Instant timestamp) {
		this(conversationId, message, timestamp, 0);
	}

	/**
	 * Creates a conversation, defaulting the sequence identifier to {@code 0} when it is
	 * absent. Used when reading documents written before the sequence identifier was
	 * introduced, which have no such field.
	 * @param conversationId the conversation identifier
	 * @param message the message
	 * @param timestamp the timestamp
	 * @param sequenceId the sequence identifier, or {@code null} if absent
	 */
	@PersistenceCreator
	Conversation(String conversationId, Message message, Instant timestamp, @Nullable Integer sequenceId) {
		this(conversationId, message, timestamp, sequenceId != null ? sequenceId : 0);
	}

	/**
	 * The persisted representation of a single chat message.
	 *
	 * @param content the textual content of the message, if any
	 * @param type the {@link org.springframework.ai.chat.messages.MessageType} name
	 * @param metadata the message metadata
	 * @param toolCalls the tool calls requested by an assistant message, if any. Absent
	 * from documents written before tool call persistence was supported.
	 * @param toolResponses the tool responses carried by a tool message, if any. Absent
	 * from documents written before tool call persistence was supported.
	 */
	public record Message(@Nullable String content, String type, Map<String, Object> metadata,
			@Nullable List<AssistantMessage.ToolCall> toolCalls,
			@Nullable List<ToolResponseMessage.ToolResponse> toolResponses) {

		/**
		 * Creates a message without any tool calls or tool responses. Retained for
		 * backwards compatibility.
		 * @param content the textual content of the message, if any
		 * @param type the message type name
		 * @param metadata the message metadata
		 */
		public Message(@Nullable String content, String type, Map<String, Object> metadata) {
			this(content, type, metadata, List.of(), List.of());
		}

	}
}
