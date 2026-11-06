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

package org.springframework.ai.model.openai.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionDetailsConfiguration.PropertiesOpenAiConnectionDetails;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiDecisionModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenAiConnectionDetailsConfiguration}.
 *
 * @author Thomas Vitale
 */
class OpenAiConnectionDetailsAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class));

	@Test
	void propertiesBackedConnectionDetailsAreRegisteredByDefault() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai.base-url=http://TEST.BASE.URL", "spring.ai.openai.api-key=abc123")
			.run(context -> {
				assertThat(context).hasSingleBean(OpenAiConnectionDetails.class);
				assertThat(context).hasSingleBean(OpenAiChatModel.class);
				OpenAiConnectionDetails connectionDetails = context.getBean(OpenAiConnectionDetails.class);
				assertThat(connectionDetails).isInstanceOf(PropertiesOpenAiConnectionDetails.class);
				assertThat(connectionDetails.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");
				assertThat(connectionDetails.getApiKey()).isEqualTo("abc123");
			});
	}

	@Test
	void customConnectionDetailsBackOffThePropertiesBean() {
		this.contextRunner.withBean(OpenAiConnectionDetails.class, () -> details("http://custom:1234/v1", "custom-key"))
			.run(context -> {
				assertThat(context).hasSingleBean(OpenAiConnectionDetails.class);
				assertThat(context).doesNotHaveBean(PropertiesOpenAiConnectionDetails.class);
				assertThat(context).hasSingleBean(OpenAiChatModel.class);
				var resolved = OpenAiAutoConfigurationUtil.resolveCommonProperties(
						context.getBean(OpenAiConnectionDetails.class), context.getBean(OpenAiCommonProperties.class),
						context.getBean(OpenAiChatProperties.class));
				assertThat(resolved.getBaseUrl()).isEqualTo("http://custom:1234/v1");
				assertThat(resolved.getApiKey()).isEqualTo("custom-key");
			});
	}

	@Test
	void customConnectionDetailsSupersedeCommonProperties() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai.base-url=http://TEST.BASE.URL", "spring.ai.openai.api-key=abc123")
			.withBean(OpenAiConnectionDetails.class, () -> details("http://custom:1234/v1", "custom-key"))
			.run(context -> {
				var resolved = OpenAiAutoConfigurationUtil.resolveCommonProperties(
						context.getBean(OpenAiConnectionDetails.class), context.getBean(OpenAiCommonProperties.class),
						context.getBean(OpenAiChatProperties.class));
				assertThat(resolved.getBaseUrl()).isEqualTo("http://custom:1234/v1");
				assertThat(resolved.getApiKey()).isEqualTo("custom-key");
			});
	}

	@Test
	void modelLevelPropertiesOverrideConnectionDetails() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai.chat.base-url=http://model:9/v1",
					"spring.ai.openai.chat.api-key=model-key")
			.withBean(OpenAiConnectionDetails.class, () -> details("http://custom:1234/v1", "custom-key"))
			.run(context -> {
				var resolved = OpenAiAutoConfigurationUtil.resolveCommonProperties(
						context.getBean(OpenAiConnectionDetails.class), context.getBean(OpenAiCommonProperties.class),
						context.getBean(OpenAiChatProperties.class));
				assertThat(resolved.getBaseUrl()).isEqualTo("http://model:9/v1");
				assertThat(resolved.getApiKey()).isEqualTo("model-key");
			});
	}

	@Test
	void connectionDetailsAreSharedAcrossAutoConfigurations() {
		new ApplicationContextRunner().withPropertyValues("spring.ai.openai.api-key=abc123")
			.withConfiguration(
					AutoConfigurations.of(OpenAiChatAutoConfiguration.class, OpenAiEmbeddingAutoConfiguration.class,
							OpenAiDecisionAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> {
				assertThat(context).hasSingleBean(OpenAiConnectionDetails.class);
				assertThat(context).hasSingleBean(OpenAiChatModel.class);
				assertThat(context).hasSingleBean(OpenAiEmbeddingModel.class);
				assertThat(context).hasSingleBean(OpenAiDecisionModel.class);
			});
	}

	private static OpenAiConnectionDetails details(String baseUrl, String apiKey) {
		return new OpenAiConnectionDetails() {

			@Override
			public String getBaseUrl() {
				return baseUrl;
			}

			@Override
			public String getApiKey() {
				return apiKey;
			}

		};
	}

}
