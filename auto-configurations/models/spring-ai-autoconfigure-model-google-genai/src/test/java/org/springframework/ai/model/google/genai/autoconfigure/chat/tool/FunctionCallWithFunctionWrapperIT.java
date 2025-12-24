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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for function calling with Google GenAI Chat using
 * FunctionToolCallback wrapper.
 */
public class FunctionCallWithFunctionWrapperIT {

	private static final Logger logger = LoggerFactory.getLogger(FunctionCallWithFunctionWrapperIT.class);

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".*")
	void functionCallWithApiKey() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.google.genai.api-key=" + System.getenv("GOOGLE_API_KEY"))
			.withConfiguration(SpringAiTestAutoConfigurations.of(GoogleGenAiChatAutoConfiguration.class));

		contextRunner.run(context -> {

			GoogleGenAiChatModel chatModel = context.getBean(GoogleGenAiChatModel.class);

			Function<MockWeatherService.Request, MockWeatherService.Response> weatherFunction = new MockWeatherService();

			List<ToolCallback> toolCallbacks = new ArrayList<>();
			toolCallbacks.add(FunctionToolCallback.builder("currentWeather", weatherFunction)
				.description("Get the current weather for a location")
				.inputType(MockWeatherService.Request.class)
				.build());

			var options = GoogleGenAiChatOptions.builder()
				.model(GoogleGenAiChatModel.ChatModel.GEMINI_2_0_FLASH.getValue())
				.toolCallbacks(toolCallbacks)
				.build();

			Prompt prompt = new Prompt("What's the weather like in San Francisco, Paris and in Tokyo?"
					+ "Return the temperature in Celsius.", options);

			ChatResponse response = chatModel.call(prompt);

			logger.info("Response: {}", response);

			assertThat(response.getResult().getOutput().getText()).contains("30.5", "10.5", "15.5");
		});
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_PROJECT", matches = ".*")
	@EnabledIfEnvironmentVariable(named = "GOOGLE_CLOUD_LOCATION", matches = ".*")
	void functionCallWithVertexAi() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.google.genai.project-id=" + System.getenv("GOOGLE_CLOUD_PROJECT"),
					"spring.ai.google.genai.location=" + System.getenv("GOOGLE_CLOUD_LOCATION"))
			.withConfiguration(SpringAiTestAutoConfigurations.of(GoogleGenAiChatAutoConfiguration.class));

		contextRunner.run(context -> {

			GoogleGenAiChatModel chatModel = context.getBean(GoogleGenAiChatModel.class);

			Function<MockWeatherService.Request, MockWeatherService.Response> weatherFunction = new MockWeatherService();

			List<ToolCallback> toolCallbacks = new ArrayList<>();
			toolCallbacks.add(FunctionToolCallback.builder("currentWeather", weatherFunction)
				.description("Get the current weather for a location")
				.inputType(MockWeatherService.Request.class)
				.build());

			var options = GoogleGenAiChatOptions.builder()
				.model(GoogleGenAiChatModel.ChatModel.GEMINI_2_0_FLASH.getValue())
				.toolCallbacks(toolCallbacks)
				.build();

			Prompt prompt = new Prompt("What's the weather like in San Francisco, Paris and in Tokyo?"
					+ "Return the temperature in Celsius.", options);

			ChatResponse response = chatModel.call(prompt);

			logger.info("Response: {}", response);

			assertThat(response.getResult().getOutput().getText()).contains("30.5", "10.5", "15.5");
		});
	}

	// public static class MockWeatherService implements
	// Function<MockWeatherService.Request, MockWeatherService.Response> {
	//
	// public record Request(String location, String unit) {
	// }
	//
	// public record Response(double temperature, String unit, String description) {
	// }
	//
	// @Override
	// public Response apply(Request request) {
	// double temperature = 0;
	// if (request.location.contains("Paris")) {
	// temperature = 15.5;
	// }
	// else if (request.location.contains("Tokyo")) {
	// temperature = 10.5;
	// }
	// else if (request.location.contains("San Francisco")) {
	// temperature = 30.5;
	// }
	// return new Response(temperature, request.unit != null ? request.unit : "°C",
	// "sunny");
	// }
	//
	// }

}
