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

package org.springframework.ai.model.vertexai.autoconfigure.gemini.tool;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.vertexai.autoconfigure.gemini.VertexAiGeminiChatAutoConfiguration;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
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
		.withConfiguration(SpringAiTestAutoConfigurations.of(VertexAiGeminiChatAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	@Test
	void functionCallTest() {
		this.contextRunner
			.withPropertyValues("spring.ai.vertex.ai.gemini.chat.options.model="
					+ VertexAiGeminiChatModel.ChatModel.GEMINI_2_0_FLASH.getValue())
			.run(context -> {

				VertexAiGeminiChatModel chatModel = context.getBean(VertexAiGeminiChatModel.class);

				var userMessage = new UserMessage("""
						What's the weather like in San Francisco, Paris and in Tokyo?
						Return the temperature in Celsius.
						""");

				ChatResponse response = chatModel.call(new Prompt(List.of(userMessage),
						VertexAiGeminiChatOptions.builder().toolName("WeatherInfo").build()));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
			});
	}

	@Configuration
	static class Config {

		@Bean
		public ToolCallback weatherFunctionInfo() {

			return FunctionToolCallback.builder("WeatherInfo", new MockWeatherService())
				.description("Get the current weather in a given location")
				.inputType(MockWeatherService.Request.class)
				.build();
		}

	}

}
