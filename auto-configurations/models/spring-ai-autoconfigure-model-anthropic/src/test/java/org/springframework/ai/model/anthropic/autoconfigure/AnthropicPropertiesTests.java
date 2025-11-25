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

package org.springframework.ai.model.anthropic.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.api.AnthropicApi.ToolChoiceTool;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link AnthropicChatProperties}, {@link AnthropicConnectionProperties}.
 */
public class AnthropicPropertiesTests {

	@Test
	public void connectionProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
					"spring.ai.anthropic.base-url=TEST_BASE_URL",
					"spring.ai.anthropic.completions-path=message-path",
					"spring.ai.anthropic.api-key=abc123",
					"spring.ai.anthropic.version=6666",
					"spring.ai.anthropic.beta-version=7777",
					"spring.ai.anthropic.chat.options.model=MODEL_XYZ",
					"spring.ai.anthropic.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(AnthropicChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(AnthropicChatProperties.class);
				var connectionProperties = context.getBean(AnthropicConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getVersion()).isEqualTo("6666");
				assertThat(connectionProperties.getBetaVersion()).isEqualTo("7777");
				assertThat(connectionProperties.getCompletionsPath()).isEqualTo("message-path");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void chatOptionsTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.anthropic.api-key=API_KEY",
				"spring.ai.anthropic.base-url=TEST_BASE_URL",

				"spring.ai.anthropic.chat.options.model=MODEL_XYZ",
				"spring.ai.anthropic.chat.options.max-tokens=123",
				"spring.ai.anthropic.chat.options.metadata.user-id=MyUserId",
				"spring.ai.anthropic.chat.options.stop_sequences=boza,koza",

				"spring.ai.anthropic.chat.options.temperature=0.55",
				"spring.ai.anthropic.chat.options.top-p=0.56",
				"spring.ai.anthropic.chat.options.top-k=100",

				"spring.ai.anthropic.chat.options.toolChoice={\"name\":\"toolChoiceFunctionName\",\"type\":\"tool\"}"
				)
			// @formatter:on
			.withConfiguration(SpringAiTestAutoConfigurations.of(AnthropicChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(AnthropicChatProperties.class);
				var connectionProperties = context.getBean(AnthropicConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");
				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(123);
				assertThat(chatProperties.getOptions().getStopSequences()).contains("boza", "koza");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.56);
				assertThat(chatProperties.getOptions().getTopK()).isEqualTo(100);

				assertThat(chatProperties.getOptions().getMetadata().userId()).isEqualTo("MyUserId");

				assertThat(chatProperties.getOptions().getToolChoice()).isNotNull();
				assertThat(chatProperties.getOptions().getToolChoice().type()).isEqualTo("tool");
				assertThat(((ToolChoiceTool) chatProperties.getOptions().getToolChoice()).name())
					.isEqualTo("toolChoiceFunctionName");
			});
	}

	@Test
	public void chatCompletionDisabled() {

		// It is enabled by default
		new ApplicationContextRunner().withPropertyValues("spring.ai.anthropic.api-key=API_KEY")
			.withConfiguration(SpringAiTestAutoConfigurations.of(AnthropicChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(AnthropicChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AnthropicChatModel.class)).isNotEmpty();
			});

		// Explicitly enable the chat auto-configuration.
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.anthropic.api-key=API_KEY", "spring.ai.model.chat=anthropic")
			.withConfiguration(SpringAiTestAutoConfigurations.of(AnthropicChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(AnthropicChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AnthropicChatModel.class)).isNotEmpty();
			});

		// Explicitly disable the chat auto-configuration.
		new ApplicationContextRunner().withPropertyValues("spring.ai.model.chat=none")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, AnthropicChatAutoConfiguration.class))
			.run(context -> assertThat(context.getBeansOfType(AnthropicChatModel.class)).isEmpty());
	}

}
