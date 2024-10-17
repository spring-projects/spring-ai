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
package org.springframework.ai.ollama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisabledIf("isDisabled")
@Testcontainers
class OllamaEmbeddingModelIT extends BaseOllamaIT {

	private static final String MODEL = OllamaModel.NOMIC_EMBED_TEXT.getName();

	@Autowired
	private OllamaEmbeddingModel embeddingModel;

	@Autowired
	private OllamaApi ollamaApi;

	@Test
	void embeddings() {
		assertThat(embeddingModel).isNotNull();
		EmbeddingResponse embeddingResponse = embeddingModel.call(new EmbeddingRequest(
				List.of("Hello World", "Something else"), OllamaOptions.builder().withTruncate(false).build()));
		assertThat(embeddingResponse.getResults()).hasSize(2);
		assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo(MODEL);
		assertThat(embeddingResponse.getMetadata().getUsage().getPromptTokens()).isEqualTo(4);
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(4);

		assertThat(embeddingModel.dimensions()).isEqualTo(768);
	}

	@Test
	void autoPullModel() {
		var model = "all-minilm";
		assertThat(embeddingModel).isNotNull();

		var modelManager = new OllamaModelManager(ollamaApi);
		modelManager.deleteModel(model);
		assertThat(modelManager.isModelAvailable(model)).isFalse();

		EmbeddingResponse embeddingResponse = embeddingModel
			.call(new EmbeddingRequest(List.of("Hello World", "Something else"),
					OllamaOptions.builder()
						.withModel(model)
						.withPullModelStrategy(PullModelStrategy.WHEN_MISSING)
						.withTruncate(false)
						.build()));

		assertThat(modelManager.isModelAvailable(model)).isTrue();

		assertThat(embeddingResponse.getResults()).hasSize(2);
		assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getMetadata().getModel()).contains(model);
		assertThat(embeddingResponse.getMetadata().getUsage().getPromptTokens()).isEqualTo(4);
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(4);

		assertThat(embeddingModel.dimensions()).isEqualTo(768);

		modelManager.deleteModel(model);
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public OllamaApi ollamaApi() {
			return buildOllamaApiWithModel(MODEL);
		}

		@Bean
		public OllamaEmbeddingModel ollamaEmbedding(OllamaApi ollamaApi) {
			return OllamaEmbeddingModel.builder()
				.withOllamaApi(ollamaApi)
				.withDefaultOptions(OllamaOptions.create().withModel(MODEL))
				.build();
		}

	}

}