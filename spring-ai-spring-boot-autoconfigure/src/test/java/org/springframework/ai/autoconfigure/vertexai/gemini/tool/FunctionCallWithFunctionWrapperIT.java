/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.vertexai.gemini.tool;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.autoconfigure.vertexai.gemini.VertexAiGeminiAutoConfiguration;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.ai.model.function.FunctionCallbackWrapper.Builder.SchemaType;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatClient;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_LOCATION", matches = ".*")
public class FunctionCallWithFunctionWrapperIT {

	private final Logger logger = LoggerFactory.getLogger(FunctionCallWithFunctionWrapperIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.vertex.ai.gemini.project-id=" + System.getenv("VERTEX_AI_GEMINI_PROJECT_ID"),
				"spring.ai.vertex.ai.gemini.location=" + System.getenv("VERTEX_AI_GEMINI_LOCATION"))
		.withConfiguration(AutoConfigurations.of(VertexAiGeminiAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	@Test
	void functionCallTest() {
		contextRunner
			.withPropertyValues("spring.ai.vertex.ai.gemini.chat.options.model="
					+ VertexAiGeminiChatClient.ChatModel.GEMINI_PRO.getValue())
			.run(context -> {

				VertexAiGeminiChatClient chatClient = context.getBean(VertexAiGeminiChatClient.class);

				var systemMessage = new SystemMessage("""
						Use Multi-turn function calling.
						Answer for all listed locations.
						If the information was not fetched call the function again. Repeat at most 3 times.
						""");
				var userMessage = new UserMessage("What's the weather like in San Francisco, Paris and in Tokyo?");

				ChatResponse response = chatClient.call(new Prompt(List.of(systemMessage, userMessage),
						VertexAiGeminiChatOptions.builder().withFunction("WeatherInfo").build()));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getContent()).containsAnyOf("30.0", "30");
				assertThat(response.getResult().getOutput().getContent()).containsAnyOf("10.0", "10");
				assertThat(response.getResult().getOutput().getContent()).containsAnyOf("15", "15.0");

			});
	}

	@Configuration
	static class Config {

		@Bean
		public FunctionCallback weatherFunctionInfo() {

			return FunctionCallbackWrapper.builder(new MockWeatherService())
				.withName("WeatherInfo")
				.withSchemaType(SchemaType.OPEN_API_SCHEMA)
				.withDescription("Get the current weather in a given location")
				.build();
		}

	}

}