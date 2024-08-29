/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.client.advisor;

import java.util.Map;

import org.springframework.ai.chat.client.advisor.api.ResponseAdvisor;
import org.springframework.ai.chat.client.advisor.api.RequestAdvisor;
import org.springframework.util.Assert;

/**
 * Abstract class that serves as a base for chat memory advisors.
 *
 * @param <T> the type of the chat memory.
 * @author Christian Tzolov
 * @since 1.0.0 M1
 */
public abstract class AbstractChatMemoryAdvisor<T> implements RequestAdvisor, ResponseAdvisor {

	public static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";

	public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";

	public static final String DEFAULT_CHAT_MEMORY_CONVERSATION_ID = "default";

	public static final int DEFAULT_CHAT_MEMORY_RESPONSE_SIZE = 100;

	protected final T chatMemoryStore;

	protected final String defaultConversationId;

	protected final int defaultChatMemoryRetrieveSize;

	public AbstractChatMemoryAdvisor(T chatMemory) {
		this(chatMemory, DEFAULT_CHAT_MEMORY_CONVERSATION_ID, DEFAULT_CHAT_MEMORY_RESPONSE_SIZE);
	}

	public AbstractChatMemoryAdvisor(T chatMemory, String defaultConversationId, int defaultChatMemoryRetrieveSize) {

		Assert.notNull(chatMemory, "The chatMemory must not be null!");
		Assert.hasText(defaultConversationId, "The conversationId must not be empty!");
		Assert.isTrue(defaultChatMemoryRetrieveSize > 0, "The defaultChatMemoryRetrieveSize must be greater than 0!");

		this.chatMemoryStore = chatMemory;
		this.defaultConversationId = defaultConversationId;
		this.defaultChatMemoryRetrieveSize = defaultChatMemoryRetrieveSize;
	}

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	protected T getChatMemoryStore() {
		return this.chatMemoryStore;
	}

	protected String doGetConversationId(Map<String, Object> context) {

		return context.containsKey(CHAT_MEMORY_CONVERSATION_ID_KEY)
				? context.get(CHAT_MEMORY_CONVERSATION_ID_KEY).toString() : this.defaultConversationId;
	}

	protected int doGetChatMemoryRetrieveSize(Map<String, Object> context) {
		return context.containsKey(CHAT_MEMORY_RETRIEVE_SIZE_KEY)
				? Integer.parseInt(context.get(CHAT_MEMORY_RETRIEVE_SIZE_KEY).toString())
				: this.defaultChatMemoryRetrieveSize;
	}

}
