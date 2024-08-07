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
package org.springframework.ai.autoconfigure.ollama.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.autoconfigure.ollama.OllamaAutoConfiguration;
import org.springframework.ai.autoconfigure.ollama.OllamaImage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;

import reactor.core.publisher.Flux;

@Disabled("For manual smoke testing only.")
@Testcontainers
public class FunctionCallbackWrapperIT {

	private static final Log logger = LogFactory.getLog(FunctionCallbackWrapperIT.class);

	private static String MODEL_NAME = "mistral";

	@Container
	static OllamaContainer ollamaContainer = new OllamaContainer(OllamaImage.IMAGE);

	static String baseUrl = "http://localhost:11434";

	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		logger.info("Start pulling the '" + MODEL_NAME + " ' generative ... would take several minutes ...");
		ollamaContainer.execInContainer("ollama", "pull", MODEL_NAME);
		logger.info(MODEL_NAME + " pulling competed!");

		baseUrl = "http://" + ollamaContainer.getHost() + ":" + ollamaContainer.getMappedPort(11434);
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
	// @formatter:off
				"spring.ai.ollama.baseUrl=" + baseUrl,
				"spring.ai.ollama.chat.options.model=" + MODEL_NAME,
				"spring.ai.ollama.chat.options.temperature=0.5",
				"spring.ai.ollama.chat.options.topK=10")
				// @formatter:on
		.withConfiguration(AutoConfigurations.of(OllamaAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	@Test
	void functionCallTest() {
		contextRunner.run(context -> {

			OllamaChatModel chatModel = context.getBean(OllamaChatModel.class);

			UserMessage userMessage = new UserMessage(
					"What's the weather like in San Francisco, Tokyo, and Paris? Return the temperature in Celsius.");

			ChatResponse response = chatModel
				.call(new Prompt(List.of(userMessage), OllamaOptions.builder().withFunction("WeatherInfo").build()));

			logger.info("Response: " + response);

			assertThat(response.getResult().getOutput().getContent()).contains("30", "10", "15");

		});
	}

	@Disabled("Ollama API does not support streaming function calls yet")
	@Test
	void streamFunctionCallTest() {
		contextRunner.run(context -> {

			OllamaChatModel chatModel = context.getBean(OllamaChatModel.class);

			UserMessage userMessage = new UserMessage(
					"What's the weather like in San Francisco, Tokyo, and Paris? You can call the following functions 'WeatherInfo'");

			Flux<ChatResponse> response = chatModel
				.stream(new Prompt(List.of(userMessage), OllamaOptions.builder().withFunction("WeatherInfo").build()));

			String content = response.collectList()
				.block()
				.stream()
				.map(ChatResponse::getResults)
				.flatMap(List::stream)
				.map(Generation::getOutput)
				.map(AssistantMessage::getContent)
				.collect(Collectors.joining());
			logger.info("Response: " + content);

			assertThat(content).contains("30", "10", "15");
		});
	}

	@Configuration
	static class Config {

		@Bean
		public FunctionCallback weatherFunctionInfo() {

			return FunctionCallbackWrapper.builder(new MockWeatherService())
				.withName("WeatherInfo")
				.withDescription("Get the weather in location")
				.withResponseConverter((response) -> "" + response.temp() + response.unit())
				.build();
		}

	}

}