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
package org.springframework.ai.autoconfigure.vertexai.anthropic.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.autoconfigure.vertexai.anthropic.VertexAiAnthropicAutoConfiguration;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.ai.model.function.FunctionCallbackWrapper.Builder.SchemaType;
import org.springframework.ai.vertexai.anthropic.VertexAiAnthropicChatModel;
import org.springframework.ai.vertexai.anthropic.VertexAiAnthropicChatOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "VERTEX_AI_ANTHROPIC_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_ANTHROPIC_LOCATION", matches = ".*")
public class FunctionCallWithPromptFunctionIT {

	private final Logger logger = LoggerFactory.getLogger(FunctionCallWithPromptFunctionIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues(
				"spring.ai.vertex.ai.anthropic.project-id=" + System.getenv("VERTEX_AI_ANTHROPIC_PROJECT_ID"),
				"spring.ai.vertex.ai.anthropic.location=" + System.getenv("VERTEX_AI_ANTHROPIC_LOCATION"))
		.withConfiguration(AutoConfigurations.of(VertexAiAnthropicAutoConfiguration.class));

	@Test
	void functionCallTest() {
		contextRunner
			.withPropertyValues("spring.ai.vertex.ai.anthropic.chat.options.model="
					+ VertexAiAnthropicChatModel.ChatModel.CLAUDE_3_5_SONNET.getValue())
			.run(context -> {

				VertexAiAnthropicChatModel chatModel = context.getBean(VertexAiAnthropicChatModel.class);

				// var systemMessage = new SystemMessage("""
				// Use Multi-turn function calling.
				// Answer for all listed locations.
				// If the information was not fetched call the function again. Repeat at
				// most 3 times.
				// """);
				var userMessage = new UserMessage("""
						What's the weather like in San Francisco, Paris and in Tokyo?
						Return the temperature in Celsius.
						""");

				var promptOptions = VertexAiAnthropicChatOptions.builder()
					.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MockWeatherService())
						.withName("CurrentWeatherService")
						.withSchemaType(SchemaType.OPEN_API_SCHEMA) // IMPORTANT!!
						.withDescription("Get the weather in location")
						.build()))
					.build();

				ChatResponse response = chatModel.call(new Prompt(List.of(userMessage), promptOptions));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getContent()).contains("30", "10", "15");

				// Verify that no function call is made.
				response = chatModel
					.call(new Prompt(List.of(userMessage), VertexAiAnthropicChatOptions.builder().build()));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getContent()).doesNotContain("30", "10", "15");

			});
	}

}