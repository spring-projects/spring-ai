/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.autoconfigure.azure.tool;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.autoconfigure.azure.openai.AzureOpenAiAutoConfiguration;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_ENDPOINT", matches = ".+")
class FunctionCallWithFunctionBeanIT {

	private final Logger logger = LoggerFactory.getLogger(FunctionCallWithFunctionBeanIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
	// @formatter:off
			"spring.ai.azure.openai.api-key=" + System.getenv("AZURE_OPENAI_API_KEY"),
			"spring.ai.azure.openai.endpoint=" + System.getenv("AZURE_OPENAI_ENDPOINT"))
			// @formatter:onn
		.withConfiguration(AutoConfigurations.of(AzureOpenAiAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	@Test
	void functionCallTest() {
		this.contextRunner
			.withPropertyValues("spring.ai.azure.openai.chat.options..deployment-name="
					+ org.springframework.ai.autoconfigure.azure.tool.DeploymentNameUtil.getDeploymentName())
			.run(context -> {

				ChatModel chatModel = context.getBean(AzureOpenAiChatModel.class);

				UserMessage userMessage = new UserMessage(
						"What's the weather like in San Francisco, Paris and in Tokyo? Use Multi-turn function calling.");

				ChatResponse response = chatModel.call(new Prompt(List.of(userMessage),
						AzureOpenAiChatOptions.builder().function("weatherFunction").build()));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");

				response = chatModel.call(new Prompt(List.of(userMessage),
						AzureOpenAiChatOptions.builder().function("weatherFunction3").build()));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");

			});
	}

	@Test
	void functionCallWithPortableFunctionCallingOptions() {
		this.contextRunner
			.withPropertyValues("spring.ai.azure.openai.chat.options..deployment-name="
					+ org.springframework.ai.autoconfigure.azure.tool.DeploymentNameUtil.getDeploymentName())
			.run(context -> {

				ChatModel chatModel = context.getBean(AzureOpenAiChatModel.class);

				UserMessage userMessage = new UserMessage(
						"What's the weather like in San Francisco, Paris and in Tokyo? Use Multi-turn function calling.");

				ChatResponse response = chatModel.call(new Prompt(List.of(userMessage),
						ToolCallingChatOptions.builder().toolNames("weatherFunction").build()));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");

			});
	}

	@Configuration
	static class Config {

		@Bean
		@Description("Get the weather in location")
		public Function<MockWeatherService.Request, MockWeatherService.Response> weatherFunction() {
			return new MockWeatherService();
		}

		// Relies on the Request's JsonClassDescription annotation to provide the
		// function description.
		@Bean
		public Function<MockWeatherService.Request, MockWeatherService.Response> weatherFunction3() {
			MockWeatherService weatherService = new MockWeatherService();
			return (weatherService::apply);
		}

	}

}
