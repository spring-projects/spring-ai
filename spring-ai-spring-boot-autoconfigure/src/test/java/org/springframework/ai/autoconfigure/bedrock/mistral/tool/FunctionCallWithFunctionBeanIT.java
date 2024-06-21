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
package org.springframework.ai.autoconfigure.bedrock.mistral.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.autoconfigure.bedrock.api.BedrockConverseApiAutoConfiguration;
import org.springframework.ai.autoconfigure.bedrock.mistral.BedrockMistralChatAutoConfiguration;
import org.springframework.ai.autoconfigure.bedrock.mistral.tool.MockWeatherService.Request;
import org.springframework.ai.autoconfigure.bedrock.mistral.tool.MockWeatherService.Response;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.bedrock.mistral.BedrockMistralChatModel;
import org.springframework.ai.bedrock.mistral.BedrockMistralChatModel.MistralChatModel;
import org.springframework.ai.bedrock.mistral.BedrockMistralChatOptions;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import software.amazon.awssdk.regions.Region;

/**
 * @author Wei Jiang
 * @since 1.0.0
 */
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
class FunctionCallWithFunctionBeanIT {

	private final Logger logger = LoggerFactory.getLogger(FunctionCallWithFunctionBeanIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.bedrock.mistral.chat.enabled=true",
				"spring.ai.bedrock.aws.access-key=" + System.getenv("AWS_ACCESS_KEY_ID"),
				"spring.ai.bedrock.aws.secret-key=" + System.getenv("AWS_SECRET_ACCESS_KEY"),
				"spring.ai.bedrock.aws.region=" + Region.US_EAST_1.id(),
				"spring.ai.bedrock.mistral.chat.model=" + MistralChatModel.MISTRAL_LARGE.id(),
				"spring.ai.bedrock.mistral.chat.options.temperature=0.5")
		.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
				BedrockConverseApiAutoConfiguration.class, BedrockMistralChatAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	@Test
	void functionCallTest() {

		contextRunner.run(context -> {

			BedrockMistralChatModel chatModel = context.getBean(BedrockMistralChatModel.class);

			var userMessage = new UserMessage(
					"What's the weather like in San Francisco, Tokyo and Paris? Return the result in Celsius.");

			ChatResponse response = chatModel.call(new Prompt(List.of(userMessage),
					BedrockMistralChatOptions.builder().withFunction("weatherFunction").build()));

			logger.info("Response: {}", response);

			assertThat(response.getResult().getOutput().getContent()).contains("30", "10", "15");

		});
	}

	@Configuration
	static class Config {

		@Bean
		@Description("Get the weather in location. Return temperature in 36°F or 36°C format.")
		public Function<Request, Response> weatherFunction() {
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