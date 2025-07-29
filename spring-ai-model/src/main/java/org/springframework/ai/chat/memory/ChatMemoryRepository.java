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

import java.util.List;

import org.springframework.ai.chat.messages.Message;

/**
 * A repository for storing and retrieving chat messages.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface ChatMemoryRepository {

	List<String> findConversationIds();

	List<Message> findByConversationId(String conversationId);

	/**
	 * Replaces all the existing messages for the given conversation ID with the provided
	 * messages.
	 */
	void saveAll(String conversationId, List<Message> messages);

	void deleteByConversationId(String conversationId);

	/**
	 * Find a message by its unique identifier within a conversation.
	 * @param conversationId the conversation ID
	 * @param messageId the unique message ID
	 * @return the message if found, null otherwise
	 */
	default Message findByMessageId(String conversationId, String messageId) {
		return findByConversationId(conversationId).stream()
			.filter(message -> messageId.equals(message.getMetadata().get("messageId")))
			.findFirst()
			.orElse(null);
	}

	/**
	 * Delete a specific message and its subsequent assistant response if any. This is
	 * used when a user message is updated to remove the old message pair.
	 * @param conversationId the conversation ID
	 * @param messageId the unique message ID to delete
	 */
	default void deleteMessageAndResponse(String conversationId, String messageId) {
		List<Message> messages = findByConversationId(conversationId);
		List<Message> updatedMessages = new java.util.ArrayList<>();

		boolean skipNext = false;
		for (int i = 0; i < messages.size(); i++) {
			Message message = messages.get(i);
			String currentMessageId = (String) message.getMetadata().get("messageId");

			if (skipNext) {
				skipNext = false;
				continue;
			}

			if (messageId.equals(currentMessageId)) {
				// Skip this message and potentially the next assistant response
				if (i + 1 < messages.size() && messages.get(i + 1)
					.getMessageType() == org.springframework.ai.chat.messages.MessageType.ASSISTANT) {
					skipNext = true;
				}
				continue;
			}

			updatedMessages.add(message);
		}

		saveAll(conversationId, updatedMessages);
	}

}
