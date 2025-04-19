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
import org.springframework.util.Assert;

import java.util.List;

/**
 * A chat memory implementation that maintains a message window of a specified size. When
 * the number of messages exceeds the maximum size, older messages are removed while
 * preserving SystemMessages.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class MessageWindowChatMemory implements ChatMemory {

	private static final int DEFAULT_MAX_MESSAGES = 200;

	private static final ChatMemoryRepository DEFAULT_CHAT_MEMORY_REPOSITORY = new InMemoryChatMemoryRepository();

	private static final MessageWindowProcessingPolicy DEFAULT_MESSAGE_WINDOW_EVICTION_POLICY = new DefaultMessageWindowProcessingPolicy();

	private final ChatMemoryRepository chatMemoryRepository;

	private final MessageWindowProcessingPolicy messageWindowProcessingPolicy;

	private final int maxMessages;

	private MessageWindowChatMemory(ChatMemoryRepository chatMemoryRepository,
			MessageWindowProcessingPolicy messageWindowProcessingPolicy, int maxMessages) {
		this.chatMemoryRepository = chatMemoryRepository;
		this.messageWindowProcessingPolicy = messageWindowProcessingPolicy;
		this.maxMessages = maxMessages;
	}

	@Override
	public void add(String conversationId, List<Message> messages) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Assert.notNull(messages, "messages cannot be null");
		Assert.noNullElements(messages, "messages cannot contain null elements");

		List<Message> historyMessages = this.chatMemoryRepository.findById(conversationId);
		List<Message> processedMessages = this.messageWindowProcessingPolicy.process(historyMessages, messages,
				this.maxMessages);
		this.chatMemoryRepository.save(conversationId, processedMessages);
	}

	@Override
	public List<Message> get(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		return this.chatMemoryRepository.findById(conversationId);
	}

	@Override
	@Deprecated // in favor of get(conversationId)
	public List<Message> get(String conversationId, int lastN) {
		return get(conversationId);
	}

	@Override
	public void clear(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		this.chatMemoryRepository.deleteById(conversationId);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private ChatMemoryRepository chatMemoryRepository = DEFAULT_CHAT_MEMORY_REPOSITORY;

		private int maxMessages = DEFAULT_MAX_MESSAGES;

		private MessageWindowProcessingPolicy messageWindowProcessingPolicy = DEFAULT_MESSAGE_WINDOW_EVICTION_POLICY;

		private Builder() {
		}

		public Builder chatMemoryRepository(ChatMemoryRepository chatMemoryRepository) {
			this.chatMemoryRepository = chatMemoryRepository;
			return this;
		}

		public Builder messageWindowEvictionPolicy(MessageWindowProcessingPolicy messageWindowProcessingPolicy) {
			this.messageWindowProcessingPolicy = messageWindowProcessingPolicy;
			return this;
		}

		public Builder maxMessages(int maxMessages) {
			this.maxMessages = maxMessages;
			return this;
		}

		public MessageWindowChatMemory build() {
			return new MessageWindowChatMemory(chatMemoryRepository, messageWindowProcessingPolicy, maxMessages);
		}

	}

}
