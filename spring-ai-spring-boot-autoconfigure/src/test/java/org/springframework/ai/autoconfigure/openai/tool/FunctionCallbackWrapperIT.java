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
package org.springframework.ai.autoconfigure.openai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.core.publisher.Flux;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class FunctionCallbackWrapperIT {

	private final Logger logger = LoggerFactory.getLogger(FunctionCallbackWrapperIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("OPENAI_API_KEY"),
				"spring.ai.openai.chat.options.model=" + ChatModel.GPT_4_O_MINI.getName())
		.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	@Test
	void functionCallTest() {
		contextRunner.withPropertyValues("spring.ai.openai.chat.options.temperature=0.1").run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			UserMessage userMessage = new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?");

			ChatResponse response = chatModel.call(
					new Prompt(List.of(userMessage), OpenAiChatOptions.builder().withFunction("WeatherInfo").build()));

			logger.info("Response: {}", response);

			assertThat(response.getResult().getOutput().getContent()).contains("30", "10", "15");

		});
	}

	@Test
	void streamFunctionCallTest() {
		contextRunner.withPropertyValues("spring.ai.openai.chat.options.temperature=0.1").run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			UserMessage userMessage = new UserMessage(
					"What's the weather like in San Francisco, Tokyo, and Paris? You can call the following functions 'WeatherInfo'");

			Flux<ChatResponse> response = chatModel.stream(
					new Prompt(List.of(userMessage), OpenAiChatOptions.builder().withFunction("WeatherInfo").build()));

			String content = response.collectList()
				.block()
				.stream()
				.map(ChatResponse::getResults)
				.flatMap(List::stream)
				.map(Generation::getOutput)
				.map(AssistantMessage::getContent)
				.collect(Collectors.joining());
			logger.info("Response: {}", content);

			assertThat(content).containsAnyOf("30.0", "30");
			assertThat(content).containsAnyOf("10.0", "10");
			assertThat(content).containsAnyOf("15.0", "15");

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