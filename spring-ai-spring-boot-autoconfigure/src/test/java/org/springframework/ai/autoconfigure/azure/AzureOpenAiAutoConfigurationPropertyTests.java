/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.azure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.autoconfigure.azure.openai.AzureOpenAiAutoConfiguration;
import org.springframework.ai.autoconfigure.azure.openai.AzureOpenAiChatProperties;
import org.springframework.ai.autoconfigure.azure.openai.AzureOpenAiConnectionProperties;
import org.springframework.ai.autoconfigure.azure.openai.AzureOpenAiEmbeddingProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class AzureOpenAiAutoConfigurationPropertyTests {

	@Test
	public void chatPropertiesTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.azure.openai.api-key=TEST_API_KEY",
				"spring.ai.azure.openai.endpoint=TEST_ENDPOINT",
				"spring.ai.azure.openai.chat.model=MODEL_XYZ",
				"spring.ai.azure.openai.chat.temperature=0.55",
				"spring.ai.azure.openai.chat.topP=0.56",
				"spring.ai.azure.openai.chat.maxTokens=123")
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(AzureOpenAiAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(AzureOpenAiChatProperties.class);
				var connectionProperties = context.getBean(AzureOpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("TEST_API_KEY");
				assertThat(connectionProperties.getEndpoint()).isEqualTo("TEST_ENDPOINT");

				assertThat(chatProperties.getModel()).isEqualTo("MODEL_XYZ");

				assertThat(chatProperties.getTemperature()).isEqualTo(0.55);
				assertThat(chatProperties.getTopP()).isEqualTo(0.56);
				assertThat(chatProperties.getMaxTokens()).isEqualTo(123);
			});
	}

	@Test
	public void embeddingPropertiesTest() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.azure.openai.api-key=TEST_API_KEY",
					"spring.ai.azure.openai.endpoint=TEST_ENDPOINT", "spring.ai.azure.openai.embedding.model=MODEL_XYZ")
			.withConfiguration(AutoConfigurations.of(AzureOpenAiAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(AzureOpenAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(AzureOpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("TEST_API_KEY");
				assertThat(connectionProperties.getEndpoint()).isEqualTo("TEST_ENDPOINT");

				assertThat(chatProperties.getModel()).isEqualTo("MODEL_XYZ");
			});
	}

}
