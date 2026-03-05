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

package org.springframework.ai.model.bedrock.converse.autoconfigure.tool;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.bedrock.converse.BedrockChatOptions;
import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.bedrock.autoconfigure.BedrockTestUtils;
import org.springframework.ai.model.bedrock.autoconfigure.RequiresAwsCredentials;
import org.springframework.ai.model.bedrock.converse.autoconfigure.BedrockConverseProxyChatAutoConfiguration;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RequiresAwsCredentials
public class FunctionCallWithPromptFunctionIT {

	private final Logger logger = LoggerFactory.getLogger(FunctionCallWithPromptFunctionIT.class);

	private final ApplicationContextRunner contextRunner = BedrockTestUtils.getContextRunner()
		.withConfiguration(SpringAiTestAutoConfigurations.of(BedrockConverseProxyChatAutoConfiguration.class));

	@Test
	void functionCallTest() {
		this.contextRunner
			.withPropertyValues(
					"spring.ai.bedrock.converse.chat.options.model=" + "anthropic.claude-haiku-4-5-20251001-v1:0")
			.run(context -> {

				BedrockProxyChatModel chatModel = context.getBean(BedrockProxyChatModel.class);

				UserMessage userMessage = new UserMessage(
						"What's the weather like in San Francisco, in Paris and in Tokyo? Return the temperature in Celsius.");

				var promptOptions = BedrockChatOptions.builder()
					.toolCallbacks(
							List.of(FunctionToolCallback.builder("CurrentWeatherService", new MockWeatherService())
								.description("Get the weather in location. Return temperature in 36°F or 36°C format.")
								.inputType(MockWeatherService.Request.class)
								.build()))
					.build();

				ChatResponse response = chatModel.call(new Prompt(List.of(userMessage), promptOptions));

				logger.info("Response: {}", response);

				assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
			});
	}

}
