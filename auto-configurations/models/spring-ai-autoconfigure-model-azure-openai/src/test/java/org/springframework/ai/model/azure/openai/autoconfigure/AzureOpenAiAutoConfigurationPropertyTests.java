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

package org.springframework.ai.model.azure.openai.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class AzureOpenAiAutoConfigurationPropertyTests {

	@Test
	public void embeddingPropertiesTest() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.azure.openai.api-key=TEST_API_KEY",
					"spring.ai.azure.openai.endpoint=TEST_ENDPOINT",
					"spring.ai.azure.openai.embedding.options.deployment-name=MODEL_XYZ")
			.withConfiguration(AutoConfigurations.of(AzureOpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(AzureOpenAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(AzureOpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("TEST_API_KEY");
				assertThat(connectionProperties.getEndpoint()).isEqualTo("TEST_ENDPOINT");

				assertThat(chatProperties.getOptions().getDeploymentName()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void chatPropertiesTest() {

		new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.azure.openai.api-key=API_KEY",
				"spring.ai.azure.openai.endpoint=ENDPOINT",

				"spring.ai.azure.openai.chat.options.deployment-name=MODEL_XYZ",
				"spring.ai.azure.openai.chat.options.frequencyPenalty=-1.5",
				"spring.ai.azure.openai.chat.options.logitBias.myTokenId=-5",
				"spring.ai.azure.openai.chat.options.maxTokens=123",
				"spring.ai.azure.openai.chat.options.n=10",
				"spring.ai.azure.openai.chat.options.presencePenalty=0",
				"spring.ai.azure.openai.chat.options.stop=boza,koza",
				"spring.ai.azure.openai.chat.options.temperature=0.55",
				"spring.ai.azure.openai.chat.options.topP=0.56",
				"spring.ai.azure.openai.chat.options.user=userXYZ"
				)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(AzureOpenAiChatAutoConfiguration.class,
					AzureOpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(AzureOpenAiChatProperties.class);
				var connectionProperties = context.getBean(AzureOpenAiConnectionProperties.class);
				var embeddingProperties = context.getBean(AzureOpenAiEmbeddingProperties.class);

				assertThat(connectionProperties.getEndpoint()).isEqualTo("ENDPOINT");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(embeddingProperties.getOptions().getDeploymentName()).isEqualTo("text-embedding-ada-002");

				assertThat(chatProperties.getOptions().getDeploymentName()).isEqualTo("MODEL_XYZ");
				assertThat(chatProperties.getOptions().getFrequencyPenalty()).isEqualTo(-1.5);
				assertThat(chatProperties.getOptions().getLogitBias().get("myTokenId")).isEqualTo(-5);
				assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(123);
				assertThat(chatProperties.getOptions().getN()).isEqualTo(10);
				assertThat(chatProperties.getOptions().getPresencePenalty()).isEqualTo(0);
				assertThat(chatProperties.getOptions().getStop()).contains("boza", "koza");
				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.56);

				assertThat(chatProperties.getOptions().getUser()).isEqualTo("userXYZ");
			});
	}

}
