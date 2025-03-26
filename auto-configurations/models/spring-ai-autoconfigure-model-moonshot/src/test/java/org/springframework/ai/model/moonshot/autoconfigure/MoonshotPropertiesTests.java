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

package org.springframework.ai.model.moonshot.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.moonshot.MoonshotChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 */
public class MoonshotPropertiesTests {

	@Test
	public void chatProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.moonshot.base-url=TEST_BASE_URL",
				"spring.ai.moonshot.api-key=abc123",
				"spring.ai.moonshot.chat.options.model=MODEL_XYZ",
				"spring.ai.moonshot.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, MoonshotChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(MoonshotChatProperties.class);
				var connectionProperties = context.getBean(MoonshotCommonProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getApiKey()).isNull();
				assertThat(chatProperties.getBaseUrl()).isNull();

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void chatOverrideConnectionProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.moonshot.base-url=TEST_BASE_URL",
				"spring.ai.moonshot.api-key=abc123",
				"spring.ai.moonshot.chat.base-url=TEST_BASE_URL2",
				"spring.ai.moonshot.chat.api-key=456",
				"spring.ai.moonshot.chat.options.model=MODEL_XYZ",
				"spring.ai.moonshot.chat.options.temperature=0.55")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, MoonshotChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(MoonshotChatProperties.class);
				var connectionProperties = context.getBean(MoonshotCommonProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getApiKey()).isEqualTo("456");
				assertThat(chatProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void chatOptionsTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.moonshot.api-key=API_KEY",
				"spring.ai.moonshot.base-url=TEST_BASE_URL",

				"spring.ai.moonshot.chat.options.model=MODEL_XYZ",
				"spring.ai.moonshot.chat.options.frequencyPenalty=-1.5",
				"spring.ai.moonshot.chat.options.logitBias.myTokenId=-5",
				"spring.ai.moonshot.chat.options.maxTokens=123",
				"spring.ai.moonshot.chat.options.n=10",
				"spring.ai.moonshot.chat.options.presencePenalty=0",
				"spring.ai.moonshot.chat.options.responseFormat.type=json",
				"spring.ai.moonshot.chat.options.seed=66",
				"spring.ai.moonshot.chat.options.stop=boza,koza",
				"spring.ai.moonshot.chat.options.temperature=0.55",
				"spring.ai.moonshot.chat.options.topP=0.56",
				"spring.ai.moonshot.chat.options.user=userXYZ"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, MoonshotChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(MoonshotChatProperties.class);
				var connectionProperties = context.getBean(MoonshotCommonProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(chatProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getFrequencyPenalty()).isEqualTo(-1.5);
				assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(123);
				assertThat(chatProperties.getOptions().getN()).isEqualTo(10);
				assertThat(chatProperties.getOptions().getPresencePenalty()).isEqualTo(0);
				assertThat(chatProperties.getOptions().getStop()).contains("boza", "koza");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.56);

				assertThat(chatProperties.getOptions().getUser()).isEqualTo("userXYZ");
			});
	}

	@Test
	void chatActivation() {
		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.moonshot.api-key=API_KEY", "spring.ai.moonshot.base-url=TEST_BASE_URL",
					"spring.ai.model.chat=none")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, MoonshotChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(MoonshotChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(MoonshotChatModel.class)).isEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.moonshot.api-key=API_KEY", "spring.ai.moonshot.base-url=TEST_BASE_URL")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, MoonshotChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(MoonshotChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MoonshotChatModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.moonshot.api-key=API_KEY", "spring.ai.moonshot.base-url=TEST_BASE_URL",
					"spring.ai.model.chat=moonshot")
			.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
					RestClientAutoConfiguration.class, MoonshotChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(MoonshotChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MoonshotChatModel.class)).isNotEmpty();
			});
	}

}
