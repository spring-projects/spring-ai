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

package org.springframework.ai.model.deepseek.autoconfigure.tool;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 * @author Hyunsang Han
 * @author Issam El-atif
 */
// @Disabled("the deepseek-chat model's Function Calling capability is unstable see:
// https://api-docs.deepseek.com/guides/function_calling")
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
public class FunctionCallbackInPromptIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.deepseek.api-key=" + System.getenv("DEEPSEEK_API_KEY"))
		.withConfiguration(AutoConfigurations.of(DeepSeekChatAutoConfiguration.class, RestClientAutoConfiguration.class,
				SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class,
				WebClientAutoConfiguration.class));

	@Test
	void functionCallTest() {
		this.contextRunner.run(context -> {

			DeepSeekChatModel chatModel = context.getBean(DeepSeekChatModel.class);

			UserMessage userMessage = new UserMessage(
					"What's the weather like in San Francisco, Tokyo, and Paris? Return the temperature in Celsius");

			var promptOptions = DeepSeekChatOptions.builder()
				.toolCallbacks(List.of(FunctionToolCallback.builder("CurrentWeatherService", new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build()))
				.build();

			ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);

			Prompt prompt = new Prompt(List.of(userMessage), promptOptions);

			ChatResponse response = chatModel.call(prompt);

			while (response.hasToolCalls()) {
				ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
				prompt = new Prompt(toolExecutionResult.conversationHistory(), promptOptions);
				response = chatModel.call(prompt);
			}

			assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
		});
	}

	@Test
	void streamingFunctionCallTest() {

		this.contextRunner.run(context -> {

			DeepSeekChatModel chatModel = context.getBean(DeepSeekChatModel.class);

			UserMessage userMessage = new UserMessage(
					"What's the weather like in San Francisco, Tokyo, and Paris? Return the temperature in Celsius");

			var promptOptions = DeepSeekChatOptions.builder()
				.toolCallbacks(List.of(FunctionToolCallback.builder("CurrentWeatherService", new MockWeatherService())
					.description("Get the weather in location")
					.inputType(MockWeatherService.Request.class)
					.build()))
				.build();

			ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);

			Prompt prompt = new Prompt(List.of(userMessage), promptOptions);

			AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
			new MessageAggregator().aggregate(chatModel.stream(prompt), aggregatedRef::set).collectList().block();

			while (aggregatedRef.get().hasToolCalls()) {
				ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt,
						aggregatedRef.get());
				prompt = new Prompt(toolExecutionResult.conversationHistory(), promptOptions);
				aggregatedRef.set(null);
				new MessageAggregator().aggregate(chatModel.stream(prompt), aggregatedRef::set).collectList().block();
			}

			String content = aggregatedRef.get().getResult().getOutput().getText();

			assertThat(content).containsAnyOf("30.0", "30");
			assertThat(content).containsAnyOf("10.0", "10");
			assertThat(content).containsAnyOf("15.0", "15");
		});
	}

}
