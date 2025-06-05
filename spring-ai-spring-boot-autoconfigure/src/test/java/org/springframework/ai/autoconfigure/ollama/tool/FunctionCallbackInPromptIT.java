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

package org.springframework.ai.autoconfigure.ollama.tool;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.autoconfigure.ollama.BaseOllamaIT;
import org.springframework.ai.autoconfigure.ollama.OllamaAutoConfiguration;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.log.LogAccessor;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionCallbackInPromptIT extends BaseOllamaIT {

	private static final LogAccessor logger = new LogAccessor(FunctionCallbackInPromptIT.class);

	private static final String MODEL_NAME = "qwen2.5:3b";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
	// @formatter:off
				"spring.ai.ollama.baseUrl=" + getBaseUrl(),
				"spring.ai.ollama.chat.options.model=" + MODEL_NAME,
				"spring.ai.ollama.chat.options.temperature=0.5",
				"spring.ai.ollama.chat.options.topK=10")
				// @formatter:on
		.withConfiguration(AutoConfigurations.of(OllamaAutoConfiguration.class));

	@BeforeAll
	public static void beforeAll() {
		initializeOllama(MODEL_NAME);
	}

	@Test
	void functionCallTest() {
		this.contextRunner.run(context -> {

			OllamaChatModel chatModel = context.getBean(OllamaChatModel.class);

			UserMessage userMessage = new UserMessage(
					"What are the weather conditions in San Francisco, Tokyo, and Paris? Find the temperature in Celsius for each of the three locations.");

			var promptOptions = OllamaOptions.builder()
				.functionCallbacks(List.of(FunctionCallback.builder()
					.function("CurrentWeatherService", new MockWeatherService())
					.description(
							"Find the weather conditions, forecasts, and temperatures for a location, like a city or state.")
					.inputType(MockWeatherService.Request.class)
					.build()))
				.build();

			ChatResponse response = chatModel.call(new Prompt(List.of(userMessage), promptOptions));

			logger.info("Response: " + response);

			assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
		});
	}

	@Test
	void streamingFunctionCallTest() {
		this.contextRunner.run(context -> {

			OllamaChatModel chatModel = context.getBean(OllamaChatModel.class);

			UserMessage userMessage = new UserMessage(
					"What are the weather conditions in San Francisco, Tokyo, and Paris? Find the temperature in Celsius for each of the three locations.");

			var promptOptions = OllamaOptions.builder()
				.functionCallbacks(List.of(FunctionCallback.builder()
					.function("CurrentWeatherService", new MockWeatherService())
					.description(
							"Find the weather conditions, forecasts, and temperatures for a location, like a city or state.")
					.inputType(MockWeatherService.Request.class)
					.build()))
				.build();

			Flux<ChatResponse> response = chatModel.stream(new Prompt(List.of(userMessage), promptOptions));

			String content = response.collectList()
				.block()
				.stream()
				.map(ChatResponse::getResults)
				.flatMap(List::stream)
				.map(Generation::getOutput)
				.map(AssistantMessage::getText)
				.collect(Collectors.joining());
			logger.info("Response: " + content);

			assertThat(content).containsAnyOf("30.0", "30");
			assertThat(content).containsAnyOf("10.0", "10");
			assertThat(content).containsAnyOf("15.0", "15");
		});
	}

}
