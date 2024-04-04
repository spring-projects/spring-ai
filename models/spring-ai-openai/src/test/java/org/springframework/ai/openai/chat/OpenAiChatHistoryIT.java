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
package org.springframework.ai.openai.chat;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.history.ChatEngine;
import org.springframework.ai.chat.history.ChatHistory;
import org.springframework.ai.chat.history.ChatHistoryRetriever;
import org.springframework.ai.chat.history.TokenWindowChatHistoryRetriever;
import org.springframework.ai.chat.history.EngineResponse;
import org.springframework.ai.chat.history.InMemoryChatHistory;
import org.springframework.ai.chat.history.TextPromptHistoryAugmenter;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@SpringBootTest(classes = OpenAiChatHistoryIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiChatHistoryIT {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private OpenAiChatClient openAiChatClient;

	@Test
	void chatHistory() {

		ChatHistory chatHistory = new InMemoryChatHistory();

		ChatHistoryRetriever chatHistoryRetriever = new TokenWindowChatHistoryRetriever(chatHistory, 4000);

		var chatEngine = new ChatEngine(openAiChatClient, openAiChatClient, chatHistory, "test-session-id",
				chatHistoryRetriever);

		EngineResponse response1 = chatEngine
			.call(new Prompt(List.of(new UserMessage("Hello my name is John Vincent Atanasoff?"))));
		logger.info("Response1: " + response1.getChatResponse().getResult().getOutput().getContent());
		assertThat(response1.getChatResponse().getResult().getOutput().getContent()).contains("John");

		EngineResponse response2 = chatEngine.call(new Prompt(List.of(new UserMessage("What is my name?"))));

		logger.info("Response2: " + response2.getChatResponse().getResult().getOutput().getContent());
		assertThat(response2.getChatResponse().getResult().getOutput().getContent()).contains("John Vincent Atanasoff");

	}

	@Test
	void chatHistoryTextPromptAugmenter() {

		ChatHistory chatHistory = new InMemoryChatHistory();

		ChatHistoryRetriever chatHistoryRetriever = new TokenWindowChatHistoryRetriever(chatHistory, 4000);

		var chatEngine = new ChatEngine(openAiChatClient, openAiChatClient, chatHistory, "test-session-id",
				chatHistoryRetriever, new TextPromptHistoryAugmenter());

		EngineResponse response1 = chatEngine
			.call(new Prompt(List.of(new UserMessage("Hello my name is John Vincent Atanasoff?"))));
		logger.info("Response1: " + response1.getChatResponse().getResult().getOutput().getContent());
		assertThat(response1.getChatResponse().getResult().getOutput().getContent()).contains("John");

		EngineResponse response2 = chatEngine.call(new Prompt(List.of(new UserMessage("What is my name?"))));

		logger.info("Response2: " + response2.getChatResponse().getResult().getOutput().getContent());
		assertThat(response2.getChatResponse().getResult().getOutput().getContent()).contains("John Vincent Atanasoff");

		EngineResponse response3 = chatEngine.call(new Prompt(List.of(new UserMessage("Tell me more about me?"))));
		logger.info("Response3: " + response3.getChatResponse().getResult().getOutput().getContent());
	}

	@Test
	void streamingChatHistory() {

		ChatHistory chatHistory = new InMemoryChatHistory();

		ChatHistoryRetriever chatHistoryRetriever = new TokenWindowChatHistoryRetriever(chatHistory, 4000);

		var chatEngine = new ChatEngine(openAiChatClient, openAiChatClient, chatHistory, "test-session-id",
				chatHistoryRetriever);

		Flux<EngineResponse> fluxResponse1 = chatEngine
			.stream(new Prompt(List.of(new UserMessage("Hello my name is John Vincent Atanasoff?"))));

		var response1 = fluxResponse1.collectList()
			.block()
			.stream()
			.map(fr -> fr.getChatResponse().getResult().getOutput().getContent())
			.collect(Collectors.joining());

		logger.info("Response1: " + response1);
		assertThat(response1).contains("John");

		Flux<EngineResponse> fluxResponse2 = chatEngine
			.stream(new Prompt(List.of(new UserMessage("What is my name?"))));

		var response2 = fluxResponse2.collectList()
			.block()
			.stream()
			.map(fr -> fr.getChatResponse().getResult().getOutput().getContent())
			.collect(Collectors.joining());

		logger.info("Response2: " + response2);
		assertThat(response2).contains("John Vincent Atanasoff");

	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiApi chatCompletionApi() {
			return new OpenAiApi(System.getenv("OPENAI_API_KEY"));
		}

		@Bean
		public OpenAiChatClient openAiClient(OpenAiApi openAiApi) {
			return new OpenAiChatClient(openAiApi);
		}

	}

}
