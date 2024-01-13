/*
 * Copyright 2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.openai;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link OpenAiConnectionProperties}, {@link OpenAiChatProperties} and
 * {@link OpenAiEmbeddingProperties}.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class OpenAiPropertiesTests {

	@Test
	public void chatProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.openai.base-url=TEST_BASE_URL",
				"spring.ai.openai.api-key=abc123",
				"spring.ai.openai.chat.model=MODEL_XYZ",
				"spring.ai.openai.chat.temperature=0.55")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

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
				"spring.ai.openai.base-url=TEST_BASE_URL",
				"spring.ai.openai.api-key=abc123",
				"spring.ai.openai.chat.base-url=TEST_BASE_URL2",
				"spring.ai.openai.chat.api-key=456",
				"spring.ai.openai.chat.model=MODEL_XYZ",
				"spring.ai.openai.chat.temperature=0.55")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(OpenAiChatProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(chatProperties.getApiKey()).isEqualTo("456");
				assertThat(chatProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(chatProperties.getModel()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getTemperature()).isEqualTo(0.55);
			});
	}

	@Test
	public void embeddingProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.openai.base-url=TEST_BASE_URL",
				"spring.ai.openai.api-key=abc123",
				"spring.ai.openai.embedding.model=MODEL_XYZ")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(OpenAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(embeddingProperties.getApiKey()).isNull();
				assertThat(embeddingProperties.getBaseUrl()).isNull();

				assertThat(embeddingProperties.getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void embeddingOverrideConnectionProperties() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.openai.base-url=TEST_BASE_URL",
				"spring.ai.openai.api-key=abc123",
				"spring.ai.openai.embedding.base-url=TEST_BASE_URL2",
				"spring.ai.openai.embedding.api-key=456",
				"spring.ai.openai.embedding.model=MODEL_XYZ")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(OpenAiAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(OpenAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(embeddingProperties.getApiKey()).isEqualTo("456");
				assertThat(embeddingProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(embeddingProperties.getModel()).isEqualTo("MODEL_XYZ");
			});
	}

}
