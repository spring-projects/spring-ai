/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.ollama.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for Ollama auto-configurations conditional enabling of models.
 *
 * @author Ilayaperumal Gopinathan
 */
public class OllamaModelConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

	@Test
	void chatModelActivation() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(OllamaChatAutoConfiguration.class)).run(context -> {
			assertThat(context.getBeansOfType(OllamaChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(OllamaChatModel.class)).isNotEmpty();
		});

		this.contextRunner.withConfiguration(AutoConfigurations.of(OllamaChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=none")
			.run(context -> {
				assertThat(context.getBeansOfType(OllamaChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OllamaChatModel.class)).isEmpty();
			});

		this.contextRunner.withConfiguration(AutoConfigurations.of(OllamaChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=ollama")
			.run(context -> {
				assertThat(context.getBeansOfType(OllamaChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OllamaChatModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OllamaEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OllamaEmbeddingModel.class)).isEmpty();
			});
	}

	@Test
	void embeddingModelActivation() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(OllamaEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(OllamaEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OllamaEmbeddingModel.class)).isNotEmpty();
			});

		this.contextRunner.withConfiguration(AutoConfigurations.of(OllamaEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(OllamaEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(OllamaEmbeddingModel.class)).isEmpty();
			});

		this.contextRunner.withConfiguration(AutoConfigurations.of(OllamaEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.embedding=ollama")
			.run(context -> {
				assertThat(context.getBeansOfType(OllamaEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(OllamaEmbeddingModel.class)).isNotEmpty();
			});
	}

}
