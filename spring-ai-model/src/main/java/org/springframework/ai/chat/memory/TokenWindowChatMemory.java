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
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A chat memory implementation that maintains a message window of a specified size,
 * ensuring that the total number of tokens does not exceed the specified limit. Messages
 * are treated as indivisible units; when eviction is necessary due to exceeding the token
 * limit, the oldest complete message is removed.
 * <p>
 * Messages of type {@link SystemMessage} are treated specially: if a new
 * {@link SystemMessage} is added, all previous {@link SystemMessage} instances are
 * removed from the memory.
 *
 * @author Sun Yuhan
 * @since 1.1.0
 */
public final class TokenWindowChatMemory implements ChatMemory {

	private static final long DEFAULT_MAX_TOKENS = 128000L;

	private final ChatMemoryRepository chatMemoryRepository;

	private final TokenCountEstimator tokenCountEstimator;

	private final long maxTokens;

	public TokenWindowChatMemory(ChatMemoryRepository chatMemoryRepository, TokenCountEstimator tokenCountEstimator,
			Long maxTokens) {
		Assert.notNull(chatMemoryRepository, "chatMemoryRepository cannot be null");
		Assert.notNull(tokenCountEstimator, "tokenCountEstimator cannot be null");
		Assert.isTrue(maxTokens > 0, "maxTokens must be greater than 0");
		this.chatMemoryRepository = chatMemoryRepository;
		this.tokenCountEstimator = tokenCountEstimator;
		this.maxTokens = maxTokens;
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

		int tokens = processedMessages.stream()
			.mapToInt(processedMessage -> tokenCountEstimator.estimate(processedMessage.getText()))
			.sum();

		if (tokens <= this.maxTokens) {
			return processedMessages;
		}

		int removeMessageIndex = 0;
		while (tokens > this.maxTokens && !processedMessages.isEmpty()
				&& removeMessageIndex < processedMessages.size()) {
			if (processedMessages.get(removeMessageIndex) instanceof SystemMessage) {
				if (processedMessages.size() == 1) {
					break;
				}
				removeMessageIndex += 1;
				continue;
			}
			Message removedMessage = processedMessages.remove(removeMessageIndex);
			tokens -= tokenCountEstimator.estimate(removedMessage.getText());
		}

		return processedMessages;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private ChatMemoryRepository chatMemoryRepository;

		private TokenCountEstimator tokenCountEstimator;

		private long maxTokens = DEFAULT_MAX_TOKENS;

		private Builder() {
		}

		public Builder chatMemoryRepository(ChatMemoryRepository chatMemoryRepository) {
			this.chatMemoryRepository = chatMemoryRepository;
			return this;
		}

		public Builder tokenCountEstimator(TokenCountEstimator tokenCountEstimator) {
			this.tokenCountEstimator = tokenCountEstimator;
			return this;
		}

		public Builder maxTokens(long maxTokens) {
			this.maxTokens = maxTokens;
			return this;
		}

		public TokenWindowChatMemory build() {
			if (this.chatMemoryRepository == null) {
				this.chatMemoryRepository = new InMemoryChatMemoryRepository();
			}
			if (this.tokenCountEstimator == null) {
				this.tokenCountEstimator = new JTokkitTokenCountEstimator();
			}
			return new TokenWindowChatMemory(this.chatMemoryRepository, this.tokenCountEstimator, this.maxTokens);
		}

	}

}
