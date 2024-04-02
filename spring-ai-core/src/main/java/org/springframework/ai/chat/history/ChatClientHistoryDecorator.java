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

package org.springframework.ai.chat.history;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.Assert;

/**
 * Implementations of the {@link ChatClient} and {@link StreamingChatClient} interfaces
 * that that manages the conversation history. It delegates the {@link ChatClient} and
 * {@link StreamingChatClient} calls to the underlying {@link ChatClient} and
 * {@link StreamingChatClient} instances.
 *
 * The Decorator uses a {@link ChatHistory} to store the conversation history. Also the
 * {@link PromptHistoryAugmenter} is used to augment the {@link Prompt} with the history
 * information retrieved from the {@link ChatHistory}.
 *
 * Use the {@link Builder} to create an instance of the
 * {@link ChatClientHistoryDecorator}.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class ChatClientHistoryDecorator implements ChatClient, StreamingChatClient {

	private final ChatClient chatClient;

	private final StreamingChatClient streamingChatClient;

	private final ChatHistory chatHistory;

	private final String sessionId;

	private final PromptHistoryAugmenter promptHistoryAugmenter;

	public ChatClientHistoryDecorator(ChatClient chatClient, StreamingChatClient streamingChatClient,
			ChatHistory chatHistory, String sessionId, PromptHistoryAugmenter promptHistoryAugmenter) {

		Assert.notNull(chatClient, "ChatClient must not be null");
		this.chatClient = chatClient;
		this.streamingChatClient = streamingChatClient;
		this.chatHistory = chatHistory;
		this.sessionId = sessionId;
		this.promptHistoryAugmenter = promptHistoryAugmenter;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		ChatResponse response = this.chatClient.call(toPromptWithHistory(prompt));

		this.chatHistory.add(this.sessionId, response.getResult().getOutput());

		return response;
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		if (this.streamingChatClient == null) {
			throw new UnsupportedOperationException("StreamingChatClient is not supported by this ChatClient");
		}

		return MessageAggregator.aggregate(this.streamingChatClient.stream(toPromptWithHistory(prompt)),
				assistantMessage -> {
					this.chatHistory.add(this.sessionId, assistantMessage);
				});
	}

	private Prompt toPromptWithHistory(Prompt prompt) {
		this.chatHistory.add(this.sessionId, prompt.getInstructions());
		return this.promptHistoryAugmenter.augment(prompt, this.sessionId, this.chatHistory);
	}

	public static Builder builder() {
		return new Builder();
	}

	public ChatHistory getChatHistory() {
		return this.chatHistory;
	}

	public static class Builder {

		private ChatClient chatClient;

		private StreamingChatClient streamingChatClient;

		private ChatHistory chatHistory = new TokenCountSlidingWindowChatHistory(2000);

		private String sessionId = "default-session";

		private PromptHistoryAugmenter promptHistoryAugmenter = new DefaultPromptHistoryAugmenter();

		public Builder withChatClient(ChatClient chatClient) {
			Assert.notNull(chatClient, "ChatClient must not be null");
			this.chatClient = chatClient;
			return this;
		}

		public Builder withStreamingChatClient(StreamingChatClient streamingChatClient) {
			Assert.notNull(streamingChatClient, "StreamingChatClient must not be null");
			this.streamingChatClient = streamingChatClient;
			return this;
		}

		public Builder withChatHistory(ChatHistory chatHistory) {
			Assert.notNull(chatHistory, "ChatHistory must not be null");
			this.chatHistory = chatHistory;
			return this;
		}

		public Builder withSessionId(String sessionId) {
			Assert.hasText(sessionId, "SessionId must not be empty");
			this.sessionId = sessionId;
			return this;
		}

		public Builder withPromptHistoryAugmenter(PromptHistoryAugmenter promptHistoryAugmenter) {
			Assert.notNull(promptHistoryAugmenter, "PromptHistoryAugmenter must not be null");
			this.promptHistoryAugmenter = promptHistoryAugmenter;
			return this;
		}

		public ChatClientHistoryDecorator build() {
			Assert.notNull(this.chatClient, "ChatClient must not be null");
			Assert.notNull(this.chatHistory, "ChatHistory must not be null");
			return new ChatClientHistoryDecorator(this.chatClient, this.streamingChatClient, this.chatHistory,
					this.sessionId, this.promptHistoryAugmenter);
		}

	}

}
