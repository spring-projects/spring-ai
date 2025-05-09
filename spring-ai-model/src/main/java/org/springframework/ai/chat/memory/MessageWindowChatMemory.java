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
		List<Message> processedMessages = process(memoryMessages, messages);
		this.chatMemoryRepository.saveAll(conversationId, processedMessages);
	}

	@Override
	public List<Message> get(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		return this.chatMemoryRepository.findByConversationId(conversationId);
	}

	@Override
	@Deprecated // in favor of get(conversationId)
	public List<Message> get(String conversationId, int lastN) {
		return get(conversationId);
	}

	@Override
	public void clear(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		this.chatMemoryRepository.deleteByConversationId(conversationId);
	}

	private List<Message> process(List<Message> memoryMessages, List<Message> newMessages) {
		List<Message> processedMessages = new ArrayList<>();

		Set<Message> memoryMessagesSet = new HashSet<>(memoryMessages);
		boolean hasNewSystemMessage = newMessages.stream()
			.filter(SystemMessage.class::isInstance)
			.anyMatch(message -> !memoryMessagesSet.contains(message));

		memoryMessages.stream()
			.filter(message -> !(hasNewSystemMessage && message instanceof SystemMessage))
			.forEach(processedMessages::add);

		processedMessages.addAll(newMessages);

		if (processedMessages.size() <= this.maxMessages) {
			return processedMessages;
		}

		int messagesToRemove = processedMessages.size() - this.maxMessages;

		List<Message> trimmedMessages = new ArrayList<>();
		int removed = 0;
		for (Message message : processedMessages) {
			if (message instanceof SystemMessage || removed >= messagesToRemove) {
				trimmedMessages.add(message);
			}
			else {
				removed++;
			}
		}

		return trimmedMessages;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private ChatMemoryRepository chatMemoryRepository;

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
			if (this.chatMemoryRepository == null) {
				this.chatMemoryRepository = new InMemoryChatMemoryRepository();
			}
			return new MessageWindowChatMemory(this.chatMemoryRepository, this.maxMessages);
		}

	}

}
