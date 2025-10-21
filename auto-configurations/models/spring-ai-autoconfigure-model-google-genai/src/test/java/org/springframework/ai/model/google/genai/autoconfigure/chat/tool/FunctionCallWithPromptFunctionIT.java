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

package org.springframework.ai.model.google.genai.autoconfigure.chat.tool;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for function calling with Google GenAI Chat using functions defined
 * in prompt options.
 */
public class FunctionCallWithPromptFunctionIT {

	private final Logger logger = LoggerFactory.getLogger(FunctionCallWithPromptFunctionIT.class);

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".*")
	void functionCallTestWithApiKey() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.google.genai.api-key=" + System.getenv("GOOGLE_API_KEY"))
			.withConfiguration(SpringAiTestAutoConfigurations.of(GoogleGenAiChatAutoConfiguration.class));

		contextRunner
			.withPropertyValues("spring.ai.google.genai.chat.options.model="
					+ GoogleGenAiChatModel.ChatModel.GEMINI_2_0_FLASH.getValue())
			.run(context -> {

				GoogleGenAiChatModel chatModel = context.getBean(GoogleGenAiChatModel.class);

				var userMessage = new UserMessage("""
						What's the weather like in San Francisco, Paris and in Tokyo?
						Return the temperature in Celsius.
						""");

				var promptOptions = GoogleGenAiChatOptions.builder()
					.toolCallbacks(
							List.of(FunctionToolCallback.builder("CurrentWeatherService", new MockWeatherService())
								.description("Get the weather in location")
								.inputType(MockWeatherService.Request.class)
								.build()))
					.build();

				ChatResponse response = chatModel.call(new Prompt(List.of(userMessage), promptOptions));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getText()).contains("30.5", "10.5", "15.5");

				// Verify that no function call is made.
				response = chatModel.call(new Prompt(List.of(userMessage), GoogleGenAiChatOptions.builder().build()));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getText()).doesNotContain("30.5", "10.5", "15.5");

			});
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".*")
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_LOCATION", matches = ".*")
	void functionCallTestWithVertexAi() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.google.genai.project-id=" + System.getenv("GOOGLE_CLOUD_PROJECT"),
					"spring.ai.google.genai.location=" + System.getenv("GOOGLE_CLOUD_LOCATION"))
			.withConfiguration(SpringAiTestAutoConfigurations.of(GoogleGenAiChatAutoConfiguration.class));

		contextRunner
			.withPropertyValues("spring.ai.google.genai.chat.options.model="
					+ GoogleGenAiChatModel.ChatModel.GEMINI_2_0_FLASH.getValue())
			.run(context -> {

				GoogleGenAiChatModel chatModel = context.getBean(GoogleGenAiChatModel.class);

				var userMessage = new UserMessage("""
						What's the weather like in San Francisco, Paris and in Tokyo?
						Return the temperature in Celsius.
						""");

				var promptOptions = GoogleGenAiChatOptions.builder()
					.toolCallbacks(
							List.of(FunctionToolCallback.builder("CurrentWeatherService", new MockWeatherService())
								.description("Get the weather in location")
								.inputType(MockWeatherService.Request.class)
								.build()))
					.build();

				ChatResponse response = chatModel.call(new Prompt(List.of(userMessage), promptOptions));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getText()).contains("30.5", "10.5", "15.5");

				// Verify that no function call is made.
				response = chatModel.call(new Prompt(List.of(userMessage), GoogleGenAiChatOptions.builder().build()));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getText()).doesNotContain("30.5", "10.5", "15.5");

			});
	}

}
