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

	private final ChatHistory2 chatHistory;

	private final String sessionId;

	private final PromptHistoryAugmenter promptHistoryAugmenter;

	public ChatEngine(ChatClient chatClient, StreamingChatClient streamingChatClient, ChatHistory2 chatHistory,
			String sessionId, PromptHistoryAugmenter promptHistoryAugmenter) {
		this.chatClient = chatClient;
		this.streamingChatClient = streamingChatClient;
		this.chatHistory = chatHistory;
		this.sessionId = sessionId;
		this.promptHistoryAugmenter = promptHistoryAugmenter;
	}

	private PromptHistoryAugmenter2 defaultHistoryAugmenter = new PromptHistoryAugmenter2() {
		@Override
		public Prompt augment(Prompt originalPrompt, List<ChatHistoryGroup> chatHistoryGroups) {
			List<Message> messages = chatHistoryGroups.stream()
				.map(g -> g.getMessages())
				.flatMap(List::stream)
				.toList();
			return new Prompt(messages, (ChatOptions) originalPrompt.getOptions());
		}
	};

	@Override
	public EngineResponse call(Prompt prompt) {

		this.chatHistory.add(new ChatHistoryGroup(sessionId, new ArrayList<>(prompt.getInstructions()),
				Instant.now().toEpochMilli()));

		List<ChatHistoryGroup> historyGroups = this.chatHistory.get(this.sessionId);

		Prompt augmentedPrompt = this.defaultHistoryAugmenter.augment(prompt, historyGroups);

		ChatResponse response = this.chatClient.call(augmentedPrompt);

		this.chatHistory
			.add(new ChatHistoryGroup(sessionId, response.getResult().getOutput(), Instant.now().toEpochMilli()));

		return new EngineResponse(response, null, historyGroups);
	}

	@Override
	public Flux<EngineResponse> stream(Prompt prompt) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'stream'");
	}

}
