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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.util.Assert;

/**
 * A chat memory implementation that maintains a token-based window, ensuring that the
 * total estimated token count of messages does not exceed a specified limit. When the
 * token count exceeds the maximum, older messages are evicted.
 * <p>
 * This complements {@link MessageWindowChatMemory} which evicts by message count. In
 * production LLM applications, token-based eviction is more accurate because a single
 * long message may consume more context window than many short messages combined.
 * <p>
 * Messages of type {@link SystemMessage} are treated specially: if a new
 * {@link SystemMessage} is added, all previous {@link SystemMessage} instances are
 * removed from the memory. Also, if the total token count exceeds the limit, the
 * {@link SystemMessage} messages are preserved while evicting other types of messages.
 * Eviction snaps to turn boundaries so that an assistant reply is never kept without the
 * user message that originated the turn.
 *
 * @author JiaWeiDong
 * @since 2.0.0
 * @see MessageWindowChatMemory
 * @see TokenCountEstimator
 */
public final class TokenWindowChatMemory implements ChatMemory {

	private static final int DEFAULT_MAX_TOKEN_SIZE = 8192;

	private final ChatMemoryRepository chatMemoryRepository;

	private final TokenCountEstimator tokenCountEstimator;

	private final int maxTokenSize;

	private TokenWindowChatMemory(ChatMemoryRepository chatMemoryRepository, TokenCountEstimator tokenCountEstimator,
			int maxTokenSize) {
		Assert.notNull(chatMemoryRepository, "chatMemoryRepository cannot be null");
		Assert.notNull(tokenCountEstimator, "tokenCountEstimator cannot be null");
		Assert.isTrue(maxTokenSize > 0, "maxTokenSize must be greater than 0");
		this.chatMemoryRepository = chatMemoryRepository;
		this.tokenCountEstimator = tokenCountEstimator;
		this.maxTokenSize = maxTokenSize;
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

		int totalTokens = estimateTotalTokens(processedMessages);
		if (totalTokens <= this.maxTokenSize) {
			return processedMessages;
		}

		// Collect indices of non-system messages; SystemMessages are always preserved.
		List<Integer> nonSystemIndices = new ArrayList<>();
		for (int i = 0; i < processedMessages.size(); i++) {
			if (!(processedMessages.get(i) instanceof SystemMessage)) {
				nonSystemIndices.add(i);
			}
		}

		// Evict oldest non-system messages until token count is within limit.
		Set<Integer> removeIndices = new HashSet<>();
		int currentTokens = totalTokens;
		int cutIndex = 0;

		while (cutIndex < nonSystemIndices.size() && currentTokens > this.maxTokenSize) {
			int idx = nonSystemIndices.get(cutIndex);
			Message message = processedMessages.get(idx);
			currentTokens -= estimateTokens(message);
			removeIndices.add(idx);
			cutIndex++;
		}

		// Snap the cut forward to the nearest USER message so the kept window always
		// starts at a complete turn. This prevents keeping an assistant reply or tool
		// result without the user message that originated its turn.
		while (cutIndex < nonSystemIndices.size()
				&& processedMessages.get(nonSystemIndices.get(cutIndex)).getMessageType() != MessageType.USER) {
			int idx = nonSystemIndices.get(cutIndex);
			removeIndices.add(idx);
			cutIndex++;
		}

		List<Message> trimmedMessages = new ArrayList<>();
		for (int i = 0; i < processedMessages.size(); i++) {
			if (!removeIndices.contains(i)) {
				trimmedMessages.add(processedMessages.get(i));
			}
		}
		return trimmedMessages;
	}

	private int estimateTotalTokens(List<Message> messages) {
		int total = 0;
		for (Message message : messages) {
			total += estimateTokens(message);
		}
		return total;
	}

	private int estimateTokens(Message message) {
		if (message instanceof MediaContent mediaContent) {
			return this.tokenCountEstimator.estimate(mediaContent);
		}
		return this.tokenCountEstimator.estimate(message.getText());
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private ChatMemoryRepository chatMemoryRepository = new InMemoryChatMemoryRepository();

		private @Nullable TokenCountEstimator tokenCountEstimator;

		private int maxTokenSize = DEFAULT_MAX_TOKEN_SIZE;

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

		public Builder maxTokenSize(int maxTokenSize) {
			this.maxTokenSize = maxTokenSize;
			return this;
		}

		public TokenWindowChatMemory build() {
			Assert.state(this.tokenCountEstimator != null, "tokenCountEstimator must be set");
			return new TokenWindowChatMemory(this.chatMemoryRepository, this.tokenCountEstimator, this.maxTokenSize);
		}

	}

}
