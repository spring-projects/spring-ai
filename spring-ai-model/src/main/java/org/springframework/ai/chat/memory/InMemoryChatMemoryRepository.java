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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.messages.Message;
import org.springframework.util.Assert;

/**
 * An in-memory implementation of {@link ChatMemoryRepository}.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class InMemoryChatMemoryRepository implements ChatMemoryRepository {

	Map<String, List<Message>> chatMemoryStore = new ConcurrentHashMap<>();

	@Override
	public List<String> findConversationIds() {
		return new ArrayList<>(this.chatMemoryStore.keySet());
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		List<Message> messages = this.chatMemoryStore.get(conversationId);
		return messages != null ? new ArrayList<>(messages) : List.of();
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Assert.notNull(messages, "messages cannot be null");
		Assert.noNullElements(messages, "messages cannot contain null elements");
		this.chatMemoryStore.put(conversationId, messages);
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		this.chatMemoryStore.remove(conversationId);
	}

	@Override
	public void refresh(String conversationId, List<Message> deletes, List<Message> adds) {
		this.chatMemoryStore.compute(conversationId, (key, currentMessages) -> {
			if (currentMessages == null) {
				return new ArrayList<>(adds);
			}
			List<Message> updatedMessages = new ArrayList<>(currentMessages);
			updatedMessages.removeAll(deletes);
			updatedMessages.addAll(adds);
			return updatedMessages;
		});
	}

}
