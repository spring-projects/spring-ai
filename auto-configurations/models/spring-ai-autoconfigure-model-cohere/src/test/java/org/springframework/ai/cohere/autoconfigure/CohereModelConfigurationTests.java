/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.cohere.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.cohere.chat.CohereChatModel;
import org.springframework.ai.cohere.embedding.CohereEmbeddingModel;
import org.springframework.ai.cohere.embedding.CohereMultimodalEmbeddingModel;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for Cohere auto-configurations conditional enabling of models.
 *
 * @author Ricken Bazolo
 */
public class CohereModelConfigurationTests {

	private final ApplicationContextRunner chatContextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.cohere.apiKey=" + System.getenv("COHERE_API_KEY"))
		.withConfiguration(SpringAiTestAutoConfigurations.of(CohereChatAutoConfiguration.class));

	private final ApplicationContextRunner embeddingContextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.cohere.apiKey=" + System.getenv("COHERE_API_KEY"))
		.withConfiguration(SpringAiTestAutoConfigurations.of(CohereEmbeddingAutoConfiguration.class));

	private final ApplicationContextRunner embeddingMultimodalContextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.cohere.apiKey=" + System.getenv("COHERE_API_KEY"))
		.withConfiguration(SpringAiTestAutoConfigurations.of(CohereMultimodalEmbeddingAutoConfiguration.class));

	@Test
	void chatModelActivation() {
		this.chatContextRunner.run(context -> {
			assertThat(context.getBeansOfType(CohereChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(CohereChatModel.class)).isNotEmpty();
			assertThat(context.getBeansOfType(CohereEmbeddingProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(CohereEmbeddingModel.class)).isEmpty();
		});

		this.chatContextRunner.withPropertyValues("spring.ai.model.chat=none", "spring.ai.model.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(CohereChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(CohereChatModel.class)).isEmpty();
			});

		this.chatContextRunner.withPropertyValues("spring.ai.model.chat=cohere", "spring.ai.model.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(CohereChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(CohereChatModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(CohereEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(CohereEmbeddingModel.class)).isEmpty();
			});
	}

	@Test
	void embeddingModelActivation() {
		this.embeddingContextRunner
			.run(context -> assertThat(context.getBeansOfType(CohereEmbeddingModel.class)).isNotEmpty());

		this.embeddingContextRunner.withPropertyValues("spring.ai.model.embedding=none").run(context -> {
			assertThat(context.getBeansOfType(CohereEmbeddingProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(CohereEmbeddingModel.class)).isEmpty();
		});

		this.embeddingContextRunner.withPropertyValues("spring.ai.model.embedding=cohere").run(context -> {
			assertThat(context.getBeansOfType(CohereEmbeddingProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(CohereEmbeddingModel.class)).isNotEmpty();
		});
	}

	@Test
	void multimodalEmbeddingActivation() {
		this.embeddingMultimodalContextRunner
			.run(context -> assertThat(context.getBeansOfType(CohereMultimodalEmbeddingModel.class)).isNotEmpty());

		this.embeddingMultimodalContextRunner.withPropertyValues("spring.ai.model.embedding.multimodal=none")
			.run(context -> {
				assertThat(context.getBeansOfType(CohereMultimodalEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(CohereMultimodalEmbeddingModel.class)).isEmpty();
			});

		this.embeddingMultimodalContextRunner.withPropertyValues("spring.ai.model.embedding.multimodal=cohere")
			.run(context -> {
				assertThat(context.getBeansOfType(CohereMultimodalEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(CohereMultimodalEmbeddingModel.class)).isNotEmpty();
			});
	}

}
