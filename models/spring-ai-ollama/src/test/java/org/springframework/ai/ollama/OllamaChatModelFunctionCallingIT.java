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

package org.springframework.ai.ollama;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.api.tool.MockWeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = OllamaChatModelFunctionCallingIT.Config.class)
@DisabledIf("isDisabled")
class OllamaChatModelFunctionCallingIT extends BaseOllamaIT {

	private static final Logger logger = LoggerFactory.getLogger(OllamaChatModelFunctionCallingIT.class);

	private static final String MODEL = "qwen2.5:3b";

	@Autowired
	ChatModel chatModel;

	@Test
	void functionCallTest() {
		UserMessage userMessage = new UserMessage(
				"What are the weather conditions in San Francisco, Tokyo, and Paris? Find the temperature in Celsius for each of the three locations.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = OllamaOptions.builder()
			.withModel(MODEL)
			.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MockWeatherService())
				.withName("getCurrentWeather")
				.withDescription(
						"Find the weather conditions, forecasts, and temperatures for a location, like a city or state.")
				.withResponseConverter((response) -> "" + response.temp() + response.unit())
				.build()))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		assertThat(response.getResult().getOutput().getContent()).contains("30", "10", "15");
	}

	@Disabled("Ollama API does not support streaming function calls yet")
	@Test
	void streamFunctionCallTest() {
		UserMessage userMessage = new UserMessage(
				"What are the weather conditions in San Francisco, Tokyo, and Paris? Find the temperature in Celsius for each of the three locations.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = OllamaOptions.builder()
			.withModel(MODEL)
			.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MockWeatherService())
				.withName("getCurrentWeather")
				.withDescription(
						"Find the weather conditions, forecasts, and temperatures for a location, like a city or state.")
				.withResponseConverter((response) -> "" + response.temp() + response.unit())
				.build()))
			.build();

		Flux<ChatResponse> response = this.chatModel.stream(new Prompt(messages, promptOptions));

		String content = response.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).contains("30", "10", "15");
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OllamaApi ollamaApi() {
			return buildOllamaApiWithModel(MODEL);
		}

		@Bean
		public OllamaChatModel ollamaChat(OllamaApi ollamaApi) {
			return OllamaChatModel.builder()
				.withOllamaApi(ollamaApi)
				.withDefaultOptions(OllamaOptions.create().withModel(MODEL).withTemperature(0.9))
				.build();
		}

	}

}
