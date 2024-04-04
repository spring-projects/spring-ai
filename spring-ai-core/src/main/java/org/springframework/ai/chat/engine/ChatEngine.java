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
import org.springframework.ai.chat.history.MessageAggregator;
import org.springframework.ai.chat.history.PromptHistoryAugmenter;
import org.springframework.ai.chat.history.RetrieverRequest;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;

/**
 * @author Christian Tzolov
 */
public class ChatEngine implements Engine, StreamingEngine {

	private final ChatClient chatClient;

	private final StreamingChatClient streamingChatClient;

	private final ChatHistory chatHistory;

	private final String conversationId;

	private final PromptHistoryAugmenter promptHistoryAugmenter;

	private final ChatHistoryRetriever chatHistoryRetriever;

	private final TokenCountEstimator tokenCountEstimator;

	public ChatEngine(ChatClient chatClient, StreamingChatClient streamingChatClient, ChatHistory chatHistory,
			String sessionId, ChatHistoryRetriever chatHistoryRetriever, PromptHistoryAugmenter promptHistoryAugmenter,
			TokenCountEstimator tokenCountEstimator) {

		this.chatClient = chatClient;
		this.streamingChatClient = streamingChatClient;
		this.chatHistory = chatHistory;
		this.conversationId = sessionId;
		this.promptHistoryAugmenter = promptHistoryAugmenter;
		this.chatHistoryRetriever = chatHistoryRetriever;
		this.tokenCountEstimator = tokenCountEstimator;
	}

	@Override
	public EngineResponse call(Prompt originalPrompt) {

		var retrievalRequest = new RetrieverRequest(this.conversationId);

		int originalPromptTokenCount = this.tokenCountEstimator.estimate(originalPrompt.getInstructions());

		retrievalRequest.addTokenCount(originalPromptTokenCount);

		List<ChatExchange> retrievedHistory = this.chatHistoryRetriever.retrieve(retrievalRequest);

		Prompt augmentedPrompt = this.promptHistoryAugmenter.augment(originalPrompt, retrievedHistory);

		ChatResponse response = this.chatClient.call(augmentedPrompt);

		List<Message> historyMessages = new ArrayList<>(originalPrompt.getInstructions());
		historyMessages.add(response.getResult().getOutput());

		this.chatHistory.add(new ChatExchange(this.conversationId, historyMessages));

		return new EngineResponse(response, null, retrievedHistory);
	}

	@Override
	public Flux<EngineResponse> stream(Prompt originalPrompt) {

		var retrievalRequest = new RetrieverRequest(this.conversationId);

		List<ChatExchange> retrievedHistory = this.chatHistoryRetriever.retrieve(retrievalRequest);

		Prompt augmentedPrompt = this.promptHistoryAugmenter.augment(originalPrompt, retrievedHistory);

		return new MessageAggregator().aggregate(this.streamingChatClient.stream(augmentedPrompt), assistantMessage -> {
			List<Message> historyMessages = new ArrayList<>(originalPrompt.getInstructions());
			historyMessages.add(assistantMessage);
			this.chatHistory.add(new ChatExchange(this.conversationId, historyMessages));
		}).map(chatResponse -> new EngineResponse(chatResponse, null, retrievedHistory));
	}

}
