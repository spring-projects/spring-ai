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

package org.springframework.ai.model.mistralai.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiEmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for Mistral AI auto-configurations conditional enabling of models.
 *
 * @author Ilayaperumal Gopinathan
 */
public class MistralModelConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mistralai.apiKey=" + System.getenv("MISTRAL_AI_API_KEY"));

	@Test
	void chatModelActivation() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(MistralAiChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(MistralAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MistralAiChatModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MistralAiEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(MistralAiEmbeddingModel.class)).isEmpty();
			});

		this.contextRunner.withConfiguration(AutoConfigurations.of(MistralAiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=none", "spring.ai.model.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(MistralAiChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(MistralAiChatModel.class)).isEmpty();
			});

		this.contextRunner
			.withConfiguration(AutoConfigurations.of(MistralAiChatAutoConfiguration.class,
					MistralAiEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=mistral", "spring.ai.model.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(MistralAiChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MistralAiChatModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MistralAiEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(MistralAiEmbeddingModel.class)).isEmpty();
			});
	}

	@Test
	void embeddingModelActivation() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(MistralAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(MistralAiEmbeddingModel.class)).isNotEmpty();
			});

		this.contextRunner.withConfiguration(AutoConfigurations.of(MistralAiEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(MistralAiEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(MistralAiEmbeddingModel.class)).isEmpty();
			});

		this.contextRunner.withConfiguration(AutoConfigurations.of(MistralAiEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.embedding=mistral")
			.run(context -> {
				assertThat(context.getBeansOfType(MistralAiEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(MistralAiEmbeddingModel.class)).isNotEmpty();
			});
	}

}
