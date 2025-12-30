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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.util.Assert;

/**
 * A chat memory implementation that maintains a message window of a specified size,
 * ensuring that the total number of messages does not exceed the specified limit. When
 * the number of messages exceeds the maximum size, older messages are evicted.
 * <p>
 * Messages of type {@link SystemMessage} are treated specially: if a new
 * {@link SystemMessage} is added, all previous {@link SystemMessage} instances are
 * removed from the memory. Also, if the total number of messages exceeds the limit, the
 * {@link SystemMessage} messages are preserved while evicting other types of messages.
 *
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
public final class MessageWindowChatMemory implements ChatMemory {

	private static final int DEFAULT_MAX_MESSAGES = 20;

	private final ChatMemoryRepository chatMemoryRepository;

	private final int maxMessages;

	private MessageWindowChatMemory(ChatMemoryRepository chatMemoryRepository, int maxMessages) {
		Assert.notNull(chatMemoryRepository, "chatMemoryRepository cannot be null");
		Assert.isTrue(maxMessages > 0, "maxMessages must be greater than 0");
		this.chatMemoryRepository = chatMemoryRepository;
		this.maxMessages = maxMessages;
	}

	@Override
	public void add(String conversationId, List<Message> messages) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Assert.notNull(messages, "messages cannot be null");
		Assert.noNullElements(messages, "messages cannot contain null elements");

		List<Message> memoryMessages = this.chatMemoryRepository.findByConversationId(conversationId);
		MessageChanges changes = process(memoryMessages, messages);
		if (!changes.toDelete.isEmpty() || !changes.toAdd.isEmpty()) {
			this.chatMemoryRepository.refresh(conversationId, changes.toDelete, changes.toAdd);
		}
	}

	@Override
	public List<Message> get(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		return this.chatMemoryRepository.findByConversationId(conversationId);
	}

	@Override
	public void clear(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		this.chatMemoryRepository.deleteByConversationId(conversationId);
	}

	private MessageChanges process(List<Message> memoryMessages, List<Message> newMessages) {
		Set<Message> originalMessageSet = new LinkedHashSet<>(memoryMessages);
		List<Message> uniqueNewMessages = newMessages.stream()
			.filter(msg -> !originalMessageSet.contains(msg))
			.toList();
		boolean hasNewSystemMessage = uniqueNewMessages.stream().anyMatch(SystemMessage.class::isInstance);

		List<Message> finalMessages = new ArrayList<>();
		if (hasNewSystemMessage) {
			memoryMessages.stream().filter(msg -> !(msg instanceof SystemMessage)).forEach(finalMessages::add);
			finalMessages.addAll(uniqueNewMessages);
		}
		else {
			finalMessages.addAll(memoryMessages);
			finalMessages.addAll(uniqueNewMessages);
		}

		if (finalMessages.size() > this.maxMessages) {
			List<Message> trimmedMessages = new ArrayList<>();
			int messagesToRemove = finalMessages.size() - this.maxMessages;
			int removed = 0;
			for (Message message : finalMessages) {
				if (message instanceof SystemMessage || removed >= messagesToRemove) {
					trimmedMessages.add(message);
				}
				else {
					removed++;
				}
			}
			finalMessages = trimmedMessages;
		}

		Set<Message> finalMessageSet = new LinkedHashSet<>(finalMessages);

		List<Message> toDelete = originalMessageSet.stream().filter(m -> !finalMessageSet.contains(m)).toList();

		List<Message> toAdd = finalMessageSet.stream().filter(m -> !originalMessageSet.contains(m)).toList();

		return new MessageChanges(toDelete, toAdd);
	}

	private static class MessageChanges {

		final List<Message> toDelete;

		final List<Message> toAdd;

		MessageChanges(List<Message> toDelete, List<Message> toAdd) {
			this.toDelete = toDelete;
			this.toAdd = toAdd;
		}

	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private ChatMemoryRepository chatMemoryRepository = new InMemoryChatMemoryRepository();

		private int maxMessages = DEFAULT_MAX_MESSAGES;

		private Builder() {
		}

		public Builder chatMemoryRepository(ChatMemoryRepository chatMemoryRepository) {
			this.chatMemoryRepository = chatMemoryRepository;
			return this;
		}

		public Builder maxMessages(int maxMessages) {
			this.maxMessages = maxMessages;
			return this;
		}

		public MessageWindowChatMemory build() {
			return new MessageWindowChatMemory(this.chatMemoryRepository, this.maxMessages);
		}

	}

}
