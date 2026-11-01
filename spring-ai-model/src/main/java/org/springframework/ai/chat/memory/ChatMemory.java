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

package org.springframework.ai.chat.memory;

import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.util.Assert;

/**
 * The contract for storing and managing the memory of chat conversations.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 * @deprecated since 2.1.0, for removal in 3.0.0, in favor of session memory,
 * {@code org.springframework.ai.session.SessionService} in {@code spring-ai-session}; the
 * context key is {@code MemoryAdvisor.CONVERSATION_ID} in {@code spring-ai-client-chat}
 */
@Deprecated(since = "2.1.0", forRemoval = true)
public interface ChatMemory {

	/**
	 * The key to retrieve the chat memory conversation id from the context.
	 */
	String CONVERSATION_ID = "chat_memory_conversation_id";

	/**
	 * Save the specified message in the chat memory for the specified conversation.
	 */
	default void add(String conversationId, Message message) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Assert.notNull(message, "message cannot be null");
		this.add(conversationId, List.of(message));
	}

	/**
	 * Save the specified messages in the chat memory for the specified conversation.
	 */
	void add(String conversationId, List<Message> messages);

	/**
	 * Get the messages in the chat memory for the specified conversation.
	 */
	List<Message> get(String conversationId);

	/**
	 * Clear the chat memory for the specified conversation.
	 */
	void clear(String conversationId);

}
