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

package org.springframework.ai.chat.engine;

import java.util.ArrayList;
import java.util.List;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.history.ChatExchange;
import org.springframework.ai.chat.history.ChatHistory;
import org.springframework.ai.chat.history.ChatHistoryRetriever;
import org.springframework.ai.chat.history.InMemoryChatHistory;
import org.springframework.ai.chat.history.MessageAggregator;
import org.springframework.ai.chat.history.PromptHistoryAugmenter;
import org.springframework.ai.chat.history.RetrieverRequest;
import org.springframework.ai.chat.history.TextPromptHistoryAugmenter;
import org.springframework.ai.chat.history.TokenWindowChatHistoryRetriever;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 */
public class ChatEngine implements Engine, StreamingEngine {

	private final ChatClient chatClient;

	private final StreamingChatClient streamingChatClient;

	private final ChatHistory chatHistory;

	private final PromptHistoryAugmenter promptHistoryAugmenter;

	private final ChatHistoryRetriever chatHistoryRetriever;

	private final TokenCountEstimator tokenCountEstimator;

	public ChatEngine(ChatClient chatClient, StreamingChatClient streamingChatClient, ChatHistory chatHistory,
			ChatHistoryRetriever chatHistoryRetriever, PromptHistoryAugmenter promptHistoryAugmenter,
			TokenCountEstimator tokenCountEstimator) {

		this.chatClient = chatClient;
		this.streamingChatClient = streamingChatClient;
		this.chatHistory = chatHistory;
		this.promptHistoryAugmenter = promptHistoryAugmenter;
		this.chatHistoryRetriever = chatHistoryRetriever;
		this.tokenCountEstimator = tokenCountEstimator;
	}

	@Override
	public EngineResponse call(EngineRequest engineRequest) {

		var originalPrompt = engineRequest.getPrompt();

		var retrievalRequest = new RetrieverRequest(engineRequest.getConversationId());

		// Shouldn't be dealing with tokens at that level. This should be part of the
		// retriever strategy.
		// Maybe the retriever request should keep the original prompt and the token count
		// should be estimated by the
		// retriever?
		retrievalRequest.addTokenCount(this.tokenCountEstimator.estimate(originalPrompt.getInstructions()));

		List<ChatExchange> retrievedHistory = this.chatHistoryRetriever.retrieve(retrievalRequest);

		Prompt augmentedPrompt = this.promptHistoryAugmenter.augment(originalPrompt, retrievedHistory);

		ChatResponse response = this.chatClient.call(augmentedPrompt);

		// Order is important. Easy to make mistakes here.
		List<Message> historyMessages = new ArrayList<>(originalPrompt.getInstructions());
		historyMessages.add(response.getResult().getOutput());

		this.chatHistory.add(new ChatExchange(engineRequest.getConversationId(), historyMessages));

		return new EngineResponse(response, null, retrievedHistory);
	}

	@Override
	public Flux<EngineResponse> stream(EngineRequest engineRequest) {

		var originalPrompt = engineRequest.getPrompt();
		var retrievalRequest = new RetrieverRequest(engineRequest.getConversationId());
		retrievalRequest.addTokenCount(this.tokenCountEstimator.estimate(originalPrompt.getInstructions()));

		List<ChatExchange> retrievedHistory = this.chatHistoryRetriever.retrieve(retrievalRequest);

		Prompt augmentedPrompt = this.promptHistoryAugmenter.augment(originalPrompt, retrievedHistory);

		return new MessageAggregator().aggregate(this.streamingChatClient.stream(augmentedPrompt), assistantMessage -> {
			List<Message> historyMessages = new ArrayList<>(originalPrompt.getInstructions());
			historyMessages.add(assistantMessage);
			this.chatHistory.add(new ChatExchange(engineRequest.getConversationId(), historyMessages));
		}).map(chatResponse -> new EngineResponse(chatResponse, null, retrievedHistory));
	}

	public static class Builder {

		private ChatClient chatClient;

		private StreamingChatClient streamingChatClient;

		private ChatHistory chatHistory = new InMemoryChatHistory();

		private PromptHistoryAugmenter promptHistoryAugmenter = new TextPromptHistoryAugmenter();

		private ChatHistoryRetriever chatHistoryRetriever = new TokenWindowChatHistoryRetriever(chatHistory, 100);

		private TokenCountEstimator tokenCountEstimator = new JTokkitTokenCountEstimator();

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

		public Builder withPromptHistoryAugmenter(PromptHistoryAugmenter promptHistoryAugmenter) {
			Assert.notNull(promptHistoryAugmenter, "PromptHistoryAugmenter must not be null");
			this.promptHistoryAugmenter = promptHistoryAugmenter;
			return this;
		}

		public Builder withChatHistoryRetriever(ChatHistoryRetriever chatHistoryRetriever) {
			Assert.notNull(chatHistoryRetriever, "ChatHistoryRetriever must not be null");
			this.chatHistoryRetriever = chatHistoryRetriever;
			return this;
		}

		public Builder withTokenCountEstimator(TokenCountEstimator tokenCountEstimator) {
			Assert.notNull(tokenCountEstimator, "TokenCountEstimator must not be null");
			this.tokenCountEstimator = tokenCountEstimator;
			return this;
		}

		public ChatEngine build() {
			Assert.isTrue(this.chatClient != null || this.streamingChatClient != null,
					"The ChatClient or StreamingChatClient must not be null");
			Assert.notNull(this.chatHistory, "ChatHistory must not be null");
			Assert.notNull(this.promptHistoryAugmenter, "PromptHistoryAugmenter must not be null");
			Assert.notNull(this.chatHistoryRetriever, "ChatHistoryRetriever must not be null");
			Assert.notNull(this.tokenCountEstimator, "TokenCountEstimator must not be null");

			return new ChatEngine(this.chatClient, this.streamingChatClient, this.chatHistory,
					this.chatHistoryRetriever, this.promptHistoryAugmenter, this.tokenCountEstimator);
		}

	}

}
