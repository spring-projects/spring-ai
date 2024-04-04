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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * @author Christian Tzolov
 */
public class ChatEngine implements Engine, StreamingEngine {

	private final ChatClient chatClient;

	private final StreamingChatClient streamingChatClient;

	private final ChatHistory chatHistory;

	private final String sessionId;

	private final PromptHistoryAugmenter promptHistoryAugmenter;

	private final ChatHistoryRetriever chatHistoryRetriever;

	private static final PromptHistoryAugmenter DEFAULT_HISTORY_AUGMENTER = new PromptHistoryAugmenter() {

		@Override
		public Prompt augment(Prompt originalPrompt, List<ChatExchange> retrievedChatMessages) {

			List<Message> messages = retrievedChatMessages.stream()
				.map(g -> g.getMessages())
				.flatMap(List::stream)
				.toList();

			var promptMessages = new ArrayList<>(messages);

			promptMessages.addAll(originalPrompt.getInstructions());

			return new Prompt(promptMessages, (ChatOptions) originalPrompt.getOptions());
		}
	};

	public ChatEngine(ChatClient chatClient, StreamingChatClient streamingChatClient, ChatHistory chatHistory,
			String sessionId, ChatHistoryRetriever chatHistoryRetriever) {
		this.chatClient = chatClient;
		this.streamingChatClient = streamingChatClient;
		this.chatHistory = chatHistory;
		this.sessionId = sessionId;
		this.promptHistoryAugmenter = DEFAULT_HISTORY_AUGMENTER;
		this.chatHistoryRetriever = chatHistoryRetriever;
	}

	@Override
	public EngineResponse call(Prompt originalPrompt) {

		List<ChatExchange> retrievedHistory = this.chatHistoryRetriever.retrieve(this.sessionId, originalPrompt);

		Prompt augmentedPrompt = this.promptHistoryAugmenter.augment(originalPrompt, retrievedHistory);

		ChatResponse response = this.chatClient.call(augmentedPrompt);

		List<Message> historyMessages = new ArrayList<>(originalPrompt.getInstructions());
		historyMessages.add(response.getResult().getOutput());

		this.chatHistory.add(new ChatExchange(this.sessionId, historyMessages));

		return new EngineResponse(response, null, retrievedHistory);
	}

	@Override
	public Flux<EngineResponse> stream(Prompt originalPrompt) {

		List<ChatExchange> retrievedHistory = this.chatHistoryRetriever.retrieve(this.sessionId, originalPrompt);

		Prompt augmentedPrompt = this.promptHistoryAugmenter.augment(originalPrompt, retrievedHistory);

		return MessageAggregator.aggregate(this.streamingChatClient.stream(augmentedPrompt), assistantMessage -> {
			List<Message> historyMessages = new ArrayList<>(originalPrompt.getInstructions());
			historyMessages.add(assistantMessage);

			this.chatHistory.add(new ChatExchange(this.sessionId, historyMessages));
		}).map(chatResponse -> new EngineResponse(chatResponse, null, retrievedHistory));
	}

}
