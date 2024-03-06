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
package org.springframework.ai.autoconfigure.mistralai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link MistralAiCommonProperties}, {@link MistralAiEmbeddingProperties}.
 */
public class MistralAiPropertiesTests {

	@Test
	public void embeddingProperties() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.mistralai.base-url=TEST_BASE_URL", "spring.ai.mistralai.api-key=abc123",
					"spring.ai.mistralai.embedding.options.model=MODEL_XYZ")
			.withConfiguration(
					AutoConfigurations.of(RestClientAutoConfiguration.class, MistralAiAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(MistralAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(MistralAiCommonProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(embeddingProperties.getApiKey()).isNull();
				assertThat(embeddingProperties.getBaseUrl()).isEqualTo(MistralAiCommonProperties.DEFAULT_BASE_URL);

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void embeddingOverrideConnectionProperties() {

		new ApplicationContextRunner().withPropertyValues("spring.ai.mistralai.base-url=TEST_BASE_URL",
				"spring.ai.mistralai.api-key=abc123", "spring.ai.mistralai.embedding.base-url=TEST_BASE_URL2",
				"spring.ai.mistralai.embedding.api-key=456", "spring.ai.mistralai.embedding.options.model=MODEL_XYZ")
			.withConfiguration(
					AutoConfigurations.of(RestClientAutoConfiguration.class, MistralAiAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(MistralAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(MistralAiCommonProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");

				assertThat(embeddingProperties.getApiKey()).isEqualTo("456");
				assertThat(embeddingProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL2");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
			});
	}

	@Test
	public void embeddingOptionsTest() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.mistralai.api-key=API_KEY", "spring.ai.mistralai.base-url=TEST_BASE_URL",

					"spring.ai.mistralai.embedding.options.model=MODEL_XYZ",
					"spring.ai.mistralai.embedding.options.encodingFormat=MyEncodingFormat")
			.withConfiguration(
					AutoConfigurations.of(RestClientAutoConfiguration.class, MistralAiAutoConfiguration.class))
			.run(context -> {
				var connectionProperties = context.getBean(MistralAiCommonProperties.class);
				var embeddingProperties = context.getBean(MistralAiEmbeddingProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(embeddingProperties.getOptions().getEncodingFormat()).isEqualTo("MyEncodingFormat");
			});
	}

}
