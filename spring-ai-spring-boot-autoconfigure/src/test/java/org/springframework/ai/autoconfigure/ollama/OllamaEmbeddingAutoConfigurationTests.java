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

package org.springframework.ai.autoconfigure.ollama;

import org.junit.jupiter.api.Test;

import org.springframework.ai.ollama.OllamaEmbeddingClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class OllamaEmbeddingAutoConfigurationTests {

	@Test
	public void propertiesTest() {

		new ApplicationContextRunner().withPropertyValues("spring.ai.ollama.embedding.enabled=true",
				"spring.ai.ollama.embedding.base-url=TEST_BASE_URL", "spring.ai.ollama.embedding.model=MODEL_XYZ",
				"spring.ai.ollama.embedding.options.temperature=0.13" // TODO: Fix the
																		// float parsing
		).withConfiguration(AutoConfigurations.of(OllamaEmbeddingAutoConfiguration.class)).run(context -> {
			var properties = context.getBean(OllamaEmbeddingProperties.class);

			// java.lang.Float.valueOf(0.13f)
			assertThat(properties.isEnabled()).isTrue();
			assertThat(properties.getModel()).isEqualTo("MODEL_XYZ");
			assertThat(properties.getBaseUrl()).isEqualTo("TEST_BASE_URL");
			assertThat(properties.getOptions()).containsKeys("temperature");
			assertThat(properties.getOptions().get("temperature")).isEqualTo("0.13");

		});
	}

	@Test
	public void enablingDisablingTest() {

		// It is enabled by default
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(OllamaEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OllamaEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OllamaEmbeddingClient.class)).isNotEmpty();
			});

		// Explicitly enable the embedding auto-configuration.
		new ApplicationContextRunner().withPropertyValues("spring.ai.ollama.embedding.enabled=true")
			.withConfiguration(AutoConfigurations.of(OllamaEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OllamaEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OllamaEmbeddingClient.class)).isNotEmpty();
			});

		// Explicitly disable the embedding auto-configuration.
		new ApplicationContextRunner().withPropertyValues("spring.ai.ollama.embedding.enabled=false")
			.withConfiguration(AutoConfigurations.of(OllamaEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OllamaEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OllamaEmbeddingClient.class)).isEmpty();
			});
	}

}
