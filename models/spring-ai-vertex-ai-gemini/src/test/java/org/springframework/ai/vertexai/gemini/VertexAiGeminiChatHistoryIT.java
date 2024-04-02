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
package org.springframework.ai.vertexai.gemini;

import java.util.List;

import com.google.cloud.vertexai.VertexAI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.history.ChatClientHistoryDecorator;
import org.springframework.ai.chat.history.TokenCountSlidingWindowChatHistory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions.TransportType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@SpringBootTest(classes = VertexAiGeminiChatHistoryIT.TestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_LOCATION", matches = ".*")
public class VertexAiGeminiChatHistoryIT {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private VertexAiGeminiChatClient openAiChatClient;

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

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public VertexAI vertexAiApi() {
			String projectId = System.getenv("VERTEX_AI_GEMINI_PROJECT_ID");
			String location = System.getenv("VERTEX_AI_GEMINI_LOCATION");
			return new VertexAI(projectId, location);
		}

		@Bean
		public VertexAiGeminiChatClient vertexAiEmbedding(VertexAI vertexAi) {
			return new VertexAiGeminiChatClient(vertexAi,
					VertexAiGeminiChatOptions.builder()
						.withModel(VertexAiGeminiChatClient.ChatModel.GEMINI_PRO_VISION.getValue())
						.withTransportType(TransportType.REST)
						.build());
		}

	}

}
