/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.openai.autoconfigure.tool;

import java.util.List;
import java.util.stream.Collectors;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
/**
 * @author Sebastien Deleuze
 */
public class OpenAiFunctionCallbackIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.api-key=" + System.getenv("OPENAI_API_KEY"),
				"spring.ai.openai.chat.model=" + "gpt-4o-mini")
		.withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class,
				org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	@Test
	void functionCallTest() {
		this.contextRunner.withPropertyValues("spring.ai.openai.chat.temperature=1").run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
			ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);

			var chatClient = ChatClient
				.builder(chatModel, ObservationRegistry.NOOP, null, null,
						ToolCallAdvisor.builder().toolCallingManager(toolCallingManager))
				.build();

			UserMessage userMessage = new UserMessage(
					"What's the weather like in San Francisco, Tokyo, and Paris? Please use the provided tools to get the weather for all 3 cities.");

			ChatResponse response = chatClient
				.prompt(new Prompt(List.of(userMessage), OpenAiChatOptions.builder().toolNames("WeatherInfo").build()))
				.call()
				.chatResponse();

			assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");

		});
	}

	@Test
	void streamFunctionCallTest() {
		this.contextRunner.withPropertyValues("spring.ai.openai.chat.temperature=1").run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
			ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);

			var chatClient = ChatClient
				.builder(chatModel, ObservationRegistry.NOOP, null, null,
						ToolCallAdvisor.builder().toolCallingManager(toolCallingManager))
				.build();

			UserMessage userMessage = new UserMessage(
					"What's the weather like in San Francisco, Tokyo, and Paris? Please use the provided tools to get the weather for all 3 cities. You can call the following functions 'WeatherInfo'");

			Flux<ChatResponse> response = chatClient
				.prompt(new Prompt(List.of(userMessage), OpenAiChatOptions.builder().toolNames("WeatherInfo").build()))
				.stream()
				.chatResponse();

			String content = response.collectList()
				.block()
				.stream()
				.map(ChatResponse::getResults)
				.flatMap(List::stream)
				.map(Generation::getOutput)
				.map(AssistantMessage::getText)
				.collect(Collectors.joining());

			assertThat(content).containsAnyOf("30.0", "30");
			assertThat(content).containsAnyOf("10.0", "10");
			assertThat(content).containsAnyOf("15.0", "15");

		});
	}

	@Configuration
	static class Config {

		@Bean
		public ToolCallback weatherFunctionInfo() {

			return FunctionToolCallback.builder("WeatherInfo", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build();
		}

	}

}
