/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.chat;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.engine.ChatEngine;
import org.springframework.ai.chat.engine.EngineRequest;
import org.springframework.ai.chat.engine.EngineResponse;
import org.springframework.ai.chat.history.ChatHistory;
import org.springframework.ai.chat.history.ChatHistoryRetriever;
import org.springframework.ai.chat.history.InMemoryChatHistory;
import org.springframework.ai.chat.history.MessageListPromptHistoryAugmenter;
import org.springframework.ai.chat.history.TextPromptHistoryAugmenter;
import org.springframework.ai.chat.history.TokenWindowChatHistoryRetriever;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class BaseChatEngineTests {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	protected final ChatClient chatClient;

	protected final StreamingChatClient streamingChatClient;

	public BaseChatEngineTests(ChatClient chatClient, StreamingChatClient streamingChatClient) {
		this.chatClient = chatClient;
		this.streamingChatClient = streamingChatClient;
	}

	@Test
	void messageListPromptAugmenter() {

		ChatHistory chatHistory = new InMemoryChatHistory();

		ChatHistoryRetriever chatHistoryRetriever = new TokenWindowChatHistoryRetriever(chatHistory, 4000);

		var chatEngine = new ChatEngine(chatClient, streamingChatClient, chatHistory, chatHistoryRetriever,
				new MessageListPromptHistoryAugmenter(), new JTokkitTokenCountEstimator());

		EngineResponse response1 = chatEngine.call(new EngineRequest("test-session-id",
				new Prompt(List.of(new UserMessage("Hello my name is John Vincent Atanasoff")))));
		logger.info("Response1: " + response1.getChatResponse().getResult().getOutput().getContent());
		assertThat(response1.getChatResponse().getResult().getOutput().getContent()).contains("John");

		EngineResponse response2 = chatEngine
			.call(new EngineRequest("test-session-id", new Prompt(List.of(new UserMessage("What is my name?")))));

		logger.info("Response2: " + response2.getChatResponse().getResult().getOutput().getContent());
		assertThat(response2.getChatResponse().getResult().getOutput().getContent()).contains("John Vincent Atanasoff");
	}

	@Test
	void textPromptAugmenter() {

		ChatHistory chatHistory = new InMemoryChatHistory();

		ChatHistoryRetriever chatHistoryRetriever = new TokenWindowChatHistoryRetriever(chatHistory, 4000);

		var chatEngine = new ChatEngine(chatClient, streamingChatClient, chatHistory, chatHistoryRetriever,
				new TextPromptHistoryAugmenter(), new JTokkitTokenCountEstimator());

		EngineResponse response1 = chatEngine.call(new EngineRequest("test-session-id",
				new Prompt(List.of(new UserMessage("Hello my name is John Vincent Atanasoff")))));
		logger.info("Response1: " + response1.getChatResponse().getResult().getOutput().getContent());
		assertThat(response1.getChatResponse().getResult().getOutput().getContent()).contains("John");

		EngineResponse response2 = chatEngine
			.call(new EngineRequest("test-session-id", new Prompt(List.of(new UserMessage("What is my name?")))));

		logger.info("Response2: " + response2.getChatResponse().getResult().getOutput().getContent());
		assertThat(response2.getChatResponse().getResult().getOutput().getContent()).contains("John Vincent Atanasoff");

		EngineResponse response3 = chatEngine
			.call(new EngineRequest("test-session-id", new Prompt(List.of(new UserMessage("Tell me more about me?")))));
		logger.info("Response3: " + response3.getChatResponse().getResult().getOutput().getContent());
	}

	@Test
	void streamingMessageListPromptAugmenter() {

		ChatHistory chatHistory = new InMemoryChatHistory();

		ChatHistoryRetriever chatHistoryRetriever = new TokenWindowChatHistoryRetriever(chatHistory, 4000);

		var chatEngine = new ChatEngine(chatClient, streamingChatClient, chatHistory, chatHistoryRetriever,
				new MessageListPromptHistoryAugmenter(), new JTokkitTokenCountEstimator());

		Flux<EngineResponse> fluxResponse1 = chatEngine.stream(new EngineRequest("test-session-id",
				new Prompt(List.of(new UserMessage("Hello my name is John Vincent Atanasoff")))));

		var response1 = fluxResponse1.collectList()
			.block()
			.stream()
			.filter(fr -> fr.getChatResponse().getResult() != null)
			.map(fr -> fr.getChatResponse().getResult().getOutput().getContent())
			.collect(Collectors.joining());

		logger.info("Response1: " + response1);
		assertThat(response1).contains("John");

		Flux<EngineResponse> fluxResponse2 = chatEngine.stream(
				new EngineRequest("test-session-id", new Prompt(List.of(new UserMessage("What is my full name?")))));

		var response2 = fluxResponse2.collectList()
			.block()
			.stream()
			.filter(fr -> fr.getChatResponse().getResult() != null)
			.map(fr -> fr.getChatResponse().getResult().getOutput().getContent())
			.collect(Collectors.joining());

		logger.info("Response2: " + response2);
		assertThat(response2).contains("John Vincent Atanasoff");
	}

	@Test
	void streamingTextPromptAugmenter() {

		ChatHistory chatHistory = new InMemoryChatHistory();

		ChatHistoryRetriever chatHistoryRetriever = new TokenWindowChatHistoryRetriever(chatHistory, 4000);

		var chatEngine = new ChatEngine(chatClient, streamingChatClient, chatHistory, chatHistoryRetriever,
				new TextPromptHistoryAugmenter(), new JTokkitTokenCountEstimator());

		Flux<EngineResponse> fluxResponse1 = chatEngine.stream(new EngineRequest("test-session-id",
				new Prompt(List.of(new UserMessage("Hello my name is John Vincent Atanasoff.")))));

		var response1 = fluxResponse1.collectList()
			.block()
			.stream()
			.filter(fr -> fr.getChatResponse().getResult() != null)
			.map(fr -> fr.getChatResponse().getResult().getOutput().getContent())
			.collect(Collectors.joining());

		logger.info("Response1: " + response1);
		assertThat(response1).contains("John");

		Flux<EngineResponse> fluxResponse2 = chatEngine
			.stream(new EngineRequest("test-session-id", new Prompt(List.of(new UserMessage("What is my name?")))));

		var response2 = fluxResponse2.collectList()
			.block()
			.stream()
			.filter(fr -> fr.getChatResponse().getResult() != null)
			.map(fr -> fr.getChatResponse().getResult().getOutput().getContent())
			.collect(Collectors.joining());

		logger.info("Response2: " + response2);
		assertThat(response2).contains("John Vincent Atanasoff");
	}

}
