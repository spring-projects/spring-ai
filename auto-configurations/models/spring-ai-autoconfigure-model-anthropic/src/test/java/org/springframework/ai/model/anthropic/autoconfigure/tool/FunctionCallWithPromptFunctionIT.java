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

package org.springframework.ai.model.anthropic.autoconfigure.tool;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for tool calling via prompt-level function callbacks using
 * user-controlled tool execution.
 *
 * @author Soby Chacko
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class FunctionCallWithPromptFunctionIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.anthropic.api-key=" + System.getenv("ANTHROPIC_API_KEY"))
		.withConfiguration(
				AutoConfigurations.of(AnthropicChatAutoConfiguration.class, ToolCallingAutoConfiguration.class));

	@Test
	void functionCallTest() {
		this.contextRunner.run(context -> {

			AnthropicChatModel chatModel = context.getBean(AnthropicChatModel.class);
			ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

			AnthropicChatOptions options = AnthropicChatOptions.builder()
				.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
					.description("Get the weather in location. Return temperature in 36°F or 36°C format.")
					.inputType(MockWeatherService.Request.class)
					.build()))
				.build();

			Prompt prompt = new Prompt(
					List.of(new UserMessage("What's the weather like in San Francisco, in Paris and in Tokyo?"
							+ " Return the temperature in Celsius.")),
					options);

			ChatResponse response = chatModel.call(prompt);

			while (response.hasToolCalls()) {
				ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
				prompt = new Prompt(toolExecutionResult.conversationHistory(), options);
				response = chatModel.call(prompt);
			}

			assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
		});
	}

}
