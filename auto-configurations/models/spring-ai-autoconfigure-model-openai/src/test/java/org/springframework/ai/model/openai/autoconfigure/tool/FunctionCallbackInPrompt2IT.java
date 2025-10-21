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

package org.springframework.ai.model.openai.autoconfigure.tool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi.ChatModel;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class FunctionCallbackInPrompt2IT {

	private final Logger logger = LoggerFactory.getLogger(FunctionCallbackInPromptIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("OPENAI_API_KEY"))
		.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class));

	@Test
	void functionCallTest() {
		this.contextRunner.withPropertyValues("spring.ai.openai.chat.options.model=" + ChatModel.GPT_4_O_MINI.getName())
			.run(context -> {

				OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

				ChatClient chatClient = ChatClient.builder(chatModel).build();

			// @formatter:off
			chatClient.prompt()
					.user("Tell me a joke?")
					.call().content();

			String content = ChatClient.builder(chatModel).build().prompt()
					.user("What's the weather like in San Francisco, Tokyo, and Paris?")
					.toolCallbacks(FunctionToolCallback
						.builder("CurrentWeatherService", new MockWeatherService())
						.description("Get the weather in location")
						.inputType(MockWeatherService.Request.class)
						.build())
					.call().content();
			// @formatter:on

				logger.info("Response: {}", content);

				assertThat(content).contains("30", "10", "15");
			});
	}

	@Test
	void lambdaFunctionCallTest() {
		Map<String, Object> state = new ConcurrentHashMap<>();

		record LightInfo(String roomName, boolean isOn) {
		}

		this.contextRunner.run(context -> {

			OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			// @formatter:off
			String content = ChatClient.builder(chatModel).build().prompt()
					.user("Turn the light on in the kitchen and in the living room!")
					.toolCallbacks(FunctionToolCallback
						.builder("turnLight", (LightInfo lightInfo) -> {
							logger.info("Turning light to [" + lightInfo.isOn + "] in " + lightInfo.roomName());
							state.put(lightInfo.roomName(), lightInfo.isOn());
						})
						.description("Turn light on or off in a room")
						.inputType(LightInfo.class)
						.build())
					.call().content();
			// @formatter:on
			logger.info("Response: {}", content);
			assertThat(state).containsEntry("kitchen", Boolean.TRUE);
			assertThat(state).containsEntry("living room", Boolean.TRUE);
		});
	}

	@Test
	void functionCallTest2() {
		this.contextRunner.withPropertyValues("spring.ai.openai.chat.options.model=" + ChatModel.GPT_4_O_MINI.getName())
			.run(context -> {

				OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			// @formatter:off
			String content = ChatClient.builder(chatModel).build().prompt()
					.user("What's the weather like in Amsterdam?")
					.toolCallbacks(FunctionToolCallback
						.builder("CurrentWeatherService", input -> "18 degrees Celsius")
						.description("Get the weather in location")
						.inputType(MockWeatherService.Request.class)
					.build())
					.call().content();
			// @formatter:on
				logger.info("Response: {}", content);

				assertThat(content).contains("18");
			});
	}

	@Test
	void streamingFunctionCallTest() {

		this.contextRunner.withPropertyValues("spring.ai.openai.chat.options.model=" + ChatModel.GPT_4_O_MINI.getName())
			.run(context -> {

				OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			// @formatter:off
			String content = ChatClient.builder(chatModel).build().prompt()
					.user("What's the weather like in San Francisco, Tokyo, and Paris?")
					.toolCallbacks(FunctionToolCallback
						.builder("CurrentWeatherService", new MockWeatherService())
						.description("Get the weather in location")
						.inputType(MockWeatherService.Request.class)
						.build())
					.stream().content()
					.collectList().block().stream().collect(Collectors.joining());
			// @formatter:on

				logger.info("Response: {}", content);

				assertThat(content).contains("30", "10", "15");
			});
	}

}
