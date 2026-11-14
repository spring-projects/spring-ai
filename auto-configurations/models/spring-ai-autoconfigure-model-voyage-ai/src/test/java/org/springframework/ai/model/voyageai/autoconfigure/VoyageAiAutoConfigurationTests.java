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

package org.springframework.ai.model.voyageai.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.voyageai.VoyageAiContextualizedEmbeddingModel;
import org.springframework.ai.voyageai.VoyageAiDocumentReranker;
import org.springframework.ai.voyageai.VoyageAiEmbeddingModel;
import org.springframework.ai.voyageai.VoyageAiMultimodalEmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class VoyageAiAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(VoyageAiEmbeddingAutoConfiguration.class,
				VoyageAiContextualizedEmbeddingAutoConfiguration.class,
				VoyageAiMultimodalEmbeddingAutoConfiguration.class, VoyageAiRerankAutoConfiguration.class,
				RestClientAutoConfiguration.class, SpringAiRetryAutoConfiguration.class));

	@Test
	void textEmbeddingFailsToStartWithoutAModel() {
		this.contextRunner.withPropertyValues("spring.ai.voyageai.api-key=test").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.hasMessageContaining("spring.ai.voyageai.embedding.model, or spring.ai.model.embedding=none");
		});
	}

	@Test
	void textEmbeddingFailsToStartForAModelOfAnotherEndpoint() {
		this.contextRunner
			.withPropertyValues("spring.ai.voyageai.api-key=test", "spring.ai.voyageai.embedding.model=rerank-3")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).rootCause()
					.hasMessageContaining("'rerank-3' is not a Voyage AI text embedding model");
			});
	}

	@Test
	void textEmbeddingFailsToStartWithoutAnApiKey() {
		this.contextRunner.withPropertyValues("spring.ai.voyageai.embedding.model=voyage-4").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.hasMessageContaining("The Voyage AI API key must be set");
		});
	}

	@Test
	void onlyTheTextEmbeddingModelIsCreatedFromItsModel() {
		this.contextRunner
			.withPropertyValues("spring.ai.voyageai.api-key=test", "spring.ai.voyageai.embedding.model=voyage-4",
					"spring.ai.voyageai.embedding.dimensions=256", "spring.ai.voyageai.embedding.input-type=query")
			.run(context -> {
				assertThat(context).hasSingleBean(VoyageAiEmbeddingModel.class);
				assertThat(context.getBean(VoyageAiEmbeddingModel.class).dimensions()).isEqualTo(256);
				assertThat(context).doesNotHaveBean(VoyageAiContextualizedEmbeddingModel.class);
				assertThat(context).doesNotHaveBean(VoyageAiMultimodalEmbeddingModel.class);
				assertThat(context).doesNotHaveBean(VoyageAiDocumentReranker.class);
			});
	}

	@Test
	void theOtherModelsAreCreatedOnlyWhenTheirModelIsSet() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=none", "spring.ai.voyageai.api-key=test",
					"spring.ai.voyageai.contextualized-embedding.model=voyage-context-4",
					"spring.ai.voyageai.multimodal-embedding.model=voyage-multimodal-3.5",
					"spring.ai.voyageai.rerank.model=rerank-3", "spring.ai.voyageai.rerank.top-k=3")
			.run(context -> {
				assertThat(context).doesNotHaveBean(VoyageAiEmbeddingModel.class);
				assertThat(context).hasSingleBean(VoyageAiContextualizedEmbeddingModel.class);
				assertThat(context).hasSingleBean(VoyageAiMultimodalEmbeddingModel.class);
				assertThat(context).hasSingleBean(VoyageAiDocumentReranker.class);
			});
	}

	@Test
	void anInvalidModelOfTheOtherModelsFailsTheStartup() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=none", "spring.ai.voyageai.api-key=test",
					"spring.ai.voyageai.rerank.model=voyage-4")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).rootCause()
					.hasMessageContaining("'voyage-4' is not a Voyage AI rerank model");
			});
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=none", "spring.ai.voyageai.api-key=test",
					"spring.ai.voyageai.multimodal-embedding.model=voyage-4")
			.run(context -> assertThat(context).hasFailed());
		this.contextRunner
			.withPropertyValues("spring.ai.model.embedding=none", "spring.ai.voyageai.api-key=test",
					"spring.ai.voyageai.contextualized-embedding.model=voyage-4")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void anotherProviderCanBeSelectedForTheTextEmbeddingModel() {
		this.contextRunner.withPropertyValues("spring.ai.model.embedding=openai").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(VoyageAiEmbeddingModel.class);
		});
	}

}
