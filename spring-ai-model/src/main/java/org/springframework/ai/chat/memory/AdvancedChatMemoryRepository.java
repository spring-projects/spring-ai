/*
 * Copyright 2023-2025 the original author or authors.
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
package org.springframework.ai.chat.memory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import java.time.Instant;
import java.util.List;

/**
 * Extended interface for ChatMemoryRepository with advanced query capabilities.
 * 
 * @author Brian Sam-Bodden
 * @since 1.0.0
 */
public interface AdvancedChatMemoryRepository extends ChatMemoryRepository {

	/**
	 * Find messages by content across all conversations.
	 * @param contentPattern The text pattern to search for in message content
	 * @param limit Maximum number of results to return
	 * @return List of messages matching the pattern
	 */
	List<MessageWithConversation> findByContent(String contentPattern, int limit);

	/**
	 * Find messages by type across all conversations.
	 * @param messageType The message type to filter by
	 * @param limit Maximum number of results to return
	 * @return List of messages of the specified type
	 */
	List<MessageWithConversation> findByType(MessageType messageType, int limit);

	/**
	 * Find messages by timestamp range.
	 * @param conversationId Optional conversation ID to filter by (null for all
	 * conversations)
	 * @param fromTime Start of time range (inclusive)
	 * @param toTime End of time range (inclusive)
	 * @param limit Maximum number of results to return
	 * @return List of messages within the time range
	 */
	List<MessageWithConversation> findByTimeRange(String conversationId, Instant fromTime, Instant toTime, int limit);

	/**
	 * Find messages with a specific metadata key-value pair.
	 * @param metadataKey The metadata key to search for
	 * @param metadataValue The metadata value to match
	 * @param limit Maximum number of results to return
	 * @return List of messages with matching metadata
	 */
	List<MessageWithConversation> findByMetadata(String metadataKey, Object metadataValue, int limit);

	/**
	 * Execute a custom query using Redis Search syntax.
	 * @param query The Redis Search query string
	 * @param limit Maximum number of results to return
	 * @return List of messages matching the query
	 */
	List<MessageWithConversation> executeQuery(String query, int limit);

	/**
	 * A wrapper class to return messages with their conversation context
	 */
	record MessageWithConversation(String conversationId, Message message, long timestamp) {
	}

}