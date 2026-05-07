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

import org.springframework.ai.document.MetadataMode;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link OpenAiConnectionProperties} and
 * {@link OpenAiEmbeddingProperties}.
 *
 * @author Christian Tzolov
 */
public class OpenAiEmbeddingPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

	@Test
	public void embeddingProperties() {

		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.openai.base-url=http://TEST.BASE.URL",
				"spring.ai.openai.api-key=abc123",
				"spring.ai.openai.embedding.options.model=MODEL_XYZ",
				"spring.ai.openai.embedding.options.dimensions=512")
				// @formatter:on
			.withConfiguration(AutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(OpenAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getApiKey()).isEqualTo("abc123");
				assertThat(connectionProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(embeddingProperties.getOptions().getDimensions()).isEqualTo(512);
			});
	}

	@Test
	public void embeddingOptionsTest() {

		this.contextRunner
			.withPropertyValues(// @formatter:off
				"spring.ai.openai.api-key=API_KEY",
				"spring.ai.openai.base-url=http://TEST.BASE.URL",

				"spring.ai.openai.embedding.options.model=MODEL_XYZ",
				"spring.ai.openai.embedding.options.user=userXYZ",
				"spring.ai.openai.embedding.options.dimensions=1024",
				"spring.ai.openai.embedding.metadata-mode=NONE"
			)
			// @formatter:on
			.withConfiguration(AutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				var embeddingProperties = context.getBean(OpenAiEmbeddingProperties.class);
				var connectionProperties = context.getBean(OpenAiConnectionProperties.class);

				assertThat(connectionProperties.getBaseUrl()).isEqualTo("http://TEST.BASE.URL");
				assertThat(connectionProperties.getApiKey()).isEqualTo("API_KEY");

				assertThat(embeddingProperties.getOptions().getModel()).isEqualTo("MODEL_XYZ");
				assertThat(embeddingProperties.getOptions().getUser()).isEqualTo("userXYZ");
				assertThat(embeddingProperties.getOptions().getDimensions()).isEqualTo(1024);
				assertThat(embeddingProperties.getMetadataMode()).isEqualTo(MetadataMode.NONE);
			});
	}

}
