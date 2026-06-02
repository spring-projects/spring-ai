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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class FunctionCallbackInPromptIT {

	private final Logger logger = LoggerFactory.getLogger(FunctionCallbackInPromptIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.api-key=" + System.getenv("OPENAI_API_KEY"))
		.withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class,
				org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration.class));

	@Test
	void functionCallTest() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai.chat.model=" + "gpt-4o-mini", "spring.ai.openai.chat.temperature=1")
			.run(context -> {

				OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
				ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

				UserMessage userMessage = new UserMessage(
						"What's the weather like in San Francisco, Tokyo, and Paris? Please use the provided tools to get the weather for all 3 cities.");

				var promptOptions = OpenAiChatOptions.builder()
					.toolCallbacks(
							List.of(FunctionToolCallback.builder("CurrentWeatherService", new MockWeatherService())
								.description("Get the weather in location")
								.inputType(MockWeatherService.Request.class)
								.build()))
					.build();

				Prompt prompt = new Prompt(List.of(userMessage), promptOptions);

				ChatResponse response = chatModel.call(prompt);

				while (response.hasToolCalls()) {
					ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
					prompt = new Prompt(toolExecutionResult.conversationHistory(), promptOptions);
					response = chatModel.call(prompt);
				}

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
			});
	}

	@Test
	void streamingFunctionCallTest() {

		this.contextRunner
			.withPropertyValues("spring.ai.openai.chat.model=" + "gpt-4o-mini", "spring.ai.openai.chat.temperature=1")
			.run(context -> {

				OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
				ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);

				UserMessage userMessage = new UserMessage(
						"What's the weather like in San Francisco, Tokyo, and Paris? Please use the provided tools to get the weather for all 3 cities.");

				var promptOptions = OpenAiChatOptions.builder()
					.toolCallbacks(
							List.of(FunctionToolCallback.builder("CurrentWeatherService", new MockWeatherService())
								.description("Get the weather in location")
								.inputType(MockWeatherService.Request.class)
								.build()))
					.build();

				Prompt prompt = new Prompt(List.of(userMessage), promptOptions);

				AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
				new MessageAggregator().aggregate(chatModel.stream(prompt), aggregatedRef::set).collectList().block();
				while (aggregatedRef.get().hasToolCalls()) {
					ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt,
							aggregatedRef.get());
					prompt = new Prompt(toolExecutionResult.conversationHistory(), promptOptions);
					aggregatedRef.set(null);
					new MessageAggregator().aggregate(chatModel.stream(prompt), aggregatedRef::set)
						.collectList()
						.block();
				}

				String content = aggregatedRef.get().getResult().getOutput().getText();
				logger.info("Response: {}", content);

				assertThat(content).contains("30", "10", "15");
			});
	}

}
