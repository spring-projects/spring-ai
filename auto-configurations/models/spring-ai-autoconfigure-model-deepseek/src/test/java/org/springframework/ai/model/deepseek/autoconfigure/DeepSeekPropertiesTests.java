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

package org.springframework.ai.model.deepseek.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 * @author Hyunsang Han
 * @author Issam El-atif
 * @author Sebastien Deleuze
 */
public class DeepSeekPropertiesTests {

	@Test
	public void chatProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.deepseek.base-url=TEST_BASE_URL",
				"spring.ai.deepseek.api-key=abc123",
				"spring.ai.deepseek.chat.model=MODEL_XYZ",
				"spring.ai.deepseek.chat.temperature=0.55")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(DeepSeekChatAutoConfiguration.class,
					RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
					ToolCallingAutoConfiguration.class, WebClientAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(DeepSeekChatProperties.class);
				var connectionProperties = context.getBean(DeepSeekConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getApiKey()).isNull();
				assertThat(chatProperties.getBaseUrl()).isNull();

				assertThat(chatProperties.getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void chatOverrideConnectionProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.deepseek.base-url=TEST_BASE_URL",
				"spring.ai.deepseek.api-key=abc123",
				"spring.ai.deepseek.chat.base-url=TEST_BASE_URL2",
				"spring.ai.deepseek.chat.api-key=456",
				"spring.ai.deepseek.chat.model=MODEL_XYZ",
				"spring.ai.deepseek.chat.temperature=0.55")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(DeepSeekChatAutoConfiguration.class,
					RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
					ToolCallingAutoConfiguration.class, WebClientAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(DeepSeekChatProperties.class);
				var connectionProperties = context.getBean(DeepSeekConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getApiKey()).isEqualTo("456");
				assertThat(chatProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(chatProperties.getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void chatOptionsTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.deepseek.api-key=API_KEY",
				"spring.ai.deepseek.base-url=TEST_BASE_URL",

				"spring.ai.deepseek.chat.model=MODEL_XYZ",
				"spring.ai.deepseek.chat.frequencyPenalty=-1.5",
				"spring.ai.deepseek.chat.logitBias.myTokenId=-5",
				"spring.ai.deepseek.chat.maxTokens=123",
				"spring.ai.deepseek.chat.presencePenalty=0",
				"spring.ai.deepseek.chat.responseFormat.type=json_object",
				"spring.ai.deepseek.chat.seed=66",
				"spring.ai.deepseek.chat.stop=boza,koza",
				"spring.ai.deepseek.chat.temperature=0.55",
				"spring.ai.deepseek.chat.topP=0.56",
				"spring.ai.deepseek.chat.user=userXYZ"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(DeepSeekChatAutoConfiguration.class,
					RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
					ToolCallingAutoConfiguration.class, WebClientAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(DeepSeekChatProperties.class);
				var connectionProperties = context.getBean(DeepSeekConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(chatProperties.getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getFrequencyPenalty()).isEqualTo(-1.5);
				assertThat(chatProperties.getMaxTokens()).isEqualTo(123);
				assertThat(chatProperties.getPresencePenalty()).isEqualTo(0);
				assertThat(chatProperties.getStop()).contains("boza", "koza");
				assertThat(chatProperties.getTemperature()).isEqualTo(0.55);
				assertThat(chatProperties.getTopP()).isEqualTo(0.56);
			});
	}

	@Test
	void chatActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.deepseek.api-key=API_KEY", "spring.ai.deepseek.base-url=TEST_BASE_URL",
					"spring.ai.model.chat=none")
			.withConfiguration(AutoConfigurations.of(DeepSeekChatAutoConfiguration.class,
					RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
					ToolCallingAutoConfiguration.class, WebClientAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(DeepSeekChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(DeepSeekChatModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.deepseek.api-key=API_KEY", "spring.ai.deepseek.base-url=TEST_BASE_URL")
			.withConfiguration(AutoConfigurations.of(DeepSeekChatAutoConfiguration.class,
					RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
					ToolCallingAutoConfiguration.class, WebClientAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(DeepSeekChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(DeepSeekChatModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.deepseek.api-key=API_KEY", "spring.ai.deepseek.base-url=TEST_BASE_URL",
					"spring.ai.model.chat=deepseek")
			.withConfiguration(AutoConfigurations.of(DeepSeekChatAutoConfiguration.class,
					RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class,
					ToolCallingAutoConfiguration.class, WebClientAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(DeepSeekChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(DeepSeekChatModel.class)).isNotEmpty();
			});
	}

}
