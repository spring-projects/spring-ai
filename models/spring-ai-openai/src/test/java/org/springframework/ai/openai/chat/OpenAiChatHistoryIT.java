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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.history.TokenCountSlidingWindowChatHistory;
import org.springframework.ai.chat.history.ChatClientHistoryDecorator;
import org.springframework.ai.chat.history.ChatEngine;
import org.springframework.ai.chat.history.ChatHistory2;
import org.springframework.ai.chat.history.ChatHistoryRetriever;
import org.springframework.ai.chat.history.EngineResponse;
import org.springframework.ai.chat.history.InMemoryChatHistory2;
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
	void responseFormatTest() {

		var clientWithHistory = ChatClientHistoryDecorator.builder()
			.withChatClient(openAiChatClient)
			.withSessionId("test-session-id")
			.withChatHistory(new TokenCountSlidingWindowChatHistory(4000))
			.build();

		ChatResponse response1 = clientWithHistory
			.call(new Prompt(List.of(new UserMessage("Hello my name is John Vincent Atanasoff?"))));
		logger.info("Response1: " + response1.getResult().getOutput().getContent());
		assertThat(response1.getResult().getOutput().getContent()).contains("John");

		ChatResponse response2 = clientWithHistory.call(new Prompt(List.of(new UserMessage("What is my name?"))));
		logger.info("Response2: " + response2.getResult().getOutput().getContent());
		assertThat(response2.getResult().getOutput().getContent()).contains("John Vincent Atanasoff");
	}

	@Test
	void responseFormatTest2() {

		ChatHistory2 chatHistory = new InMemoryChatHistory2();

		var chatEngine = new ChatEngine(openAiChatClient, openAiChatClient, chatHistory, "test-session-id",
				new ChatHistoryRetriever(chatHistory, 4000));

		EngineResponse response1 = chatEngine
			.call(new Prompt(List.of(new UserMessage("Hello my name is John Vincent Atanasoff?"))));
		logger.info("Response1: " + response1.getChatResponse().getResult().getOutput().getContent());
		assertThat(response1.getChatResponse().getResult().getOutput().getContent()).contains("John");

		EngineResponse response2 = chatEngine.call(new Prompt(List.of(new UserMessage("What is my name?"))));

		logger.info("Response2: " + response2.getChatResponse().getResult().getOutput().getContent());
		assertThat(response2.getChatResponse().getResult().getOutput().getContent()).contains("John Vincent Atanasoff");

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
