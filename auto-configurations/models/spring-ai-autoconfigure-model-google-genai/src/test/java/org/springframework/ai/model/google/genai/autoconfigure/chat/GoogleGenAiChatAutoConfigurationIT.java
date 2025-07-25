/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.google.genai.autoconfigure.chat;

import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Google GenAI Chat autoconfiguration.
 *
 * This test can run in two modes: 1. With GOOGLE_API_KEY environment variable (Gemini
 * Developer API mode) 2. With GOOGLE_CLOUD_PROJECT and GOOGLE_CLOUD_LOCATION environment
 * variables (Vertex AI mode)
 */
public class GoogleGenAiChatAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(GoogleGenAiChatAutoConfigurationIT.class);

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".*")
	void generateWithApiKey() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.google.genai.api-key=" + System.getenv("GOOGLE_API_KEY"))
			.withConfiguration(AutoConfigurations.of(GoogleGenAiChatAutoConfiguration.class));

		contextRunner.run(context -> {
			GoogleGenAiChatModel chatModel = context.getBean(GoogleGenAiChatModel.class);
			String response = chatModel.call("Hello");
			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".*")
	void generateStreamingWithApiKey() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.google.genai.api-key=" + System.getenv("GOOGLE_API_KEY"))
			.withConfiguration(AutoConfigurations.of(GoogleGenAiChatAutoConfiguration.class));

		contextRunner.run(context -> {
			GoogleGenAiChatModel chatModel = context.getBean(GoogleGenAiChatModel.class);
			Flux<ChatResponse> responseFlux = chatModel.stream(new Prompt(new UserMessage("Hello")));
			String response = responseFlux.collectList()
				.block()
				.stream()
				.map(chatResponse -> chatResponse.getResults().get(0).getOutput().getText())
				.collect(Collectors.joining());

			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".*")
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_LOCATION", matches = ".*")
	void generateWithVertexAi() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.google.genai.project-id=" + System.getenv("GOOGLE_CLOUD_PROJECT"),
					"spring.ai.google.genai.location=" + System.getenv("GOOGLE_CLOUD_LOCATION"))
			.withConfiguration(AutoConfigurations.of(GoogleGenAiChatAutoConfiguration.class));

		contextRunner.run(context -> {
			GoogleGenAiChatModel chatModel = context.getBean(GoogleGenAiChatModel.class);
			String response = chatModel.call("Hello");
			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".*")
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_LOCATION", matches = ".*")
	void generateStreamingWithVertexAi() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.google.genai.project-id=" + System.getenv("GOOGLE_CLOUD_PROJECT"),
					"spring.ai.google.genai.location=" + System.getenv("GOOGLE_CLOUD_LOCATION"))
			.withConfiguration(AutoConfigurations.of(GoogleGenAiChatAutoConfiguration.class));

		contextRunner.run(context -> {
			GoogleGenAiChatModel chatModel = context.getBean(GoogleGenAiChatModel.class);
			Flux<ChatResponse> responseFlux = chatModel.stream(new Prompt(new UserMessage("Hello")));
			String response = responseFlux.collectList()
				.block()
				.stream()
				.map(chatResponse -> chatResponse.getResults().get(0).getOutput().getText())
				.collect(Collectors.joining());

			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

}