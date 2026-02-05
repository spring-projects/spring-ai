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

package org.springframework.ai.ollama;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OllamaEmbeddingModelIT extends BaseOllamaIT {

	private static final String MODEL = OllamaModel.NOMIC_EMBED_TEXT.getName();

	private static final String ADDITIONAL_MODEL = "all-minilm";

	@Autowired
	private OllamaEmbeddingModel embeddingModel;

	@Autowired
	private OllamaApi ollamaApi;

	@Test
	void embeddings() {
		assertThat(this.embeddingModel).isNotNull();
		EmbeddingResponse embeddingResponse = this.embeddingModel.call(new EmbeddingRequest(
				List.of("Hello World", "Something else"), OllamaEmbeddingOptions.builder().build()));
		assertThat(embeddingResponse.getResults()).hasSize(2);
		assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo(MODEL);
		// Token count varies by Ollama version and tokenizer implementation
		assertThat(embeddingResponse.getMetadata().getUsage().getPromptTokens()).isGreaterThan(0)
			.isLessThanOrEqualTo(10);
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isGreaterThan(0)
			.isLessThanOrEqualTo(10);

		assertThat(this.embeddingModel.dimensions()).isEqualTo(768);
	}

	@Test
	void autoPullModelAtStartupTime() {
		var model = "all-minilm";
		assertThat(this.embeddingModel).isNotNull();

		var modelManager = new OllamaModelManager(this.ollamaApi);
		assertThat(modelManager.isModelAvailable(ADDITIONAL_MODEL)).isTrue();

		EmbeddingResponse embeddingResponse = this.embeddingModel.call(new EmbeddingRequest(
				List.of("Hello World", "Something else"), OllamaEmbeddingOptions.builder().model(model).build()));

		assertThat(embeddingResponse.getResults()).hasSize(2);
		assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(0).getOutput().length).isEqualTo(384);
		assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(1).getOutput().length).isEqualTo(384);
		assertThat(embeddingResponse.getMetadata().getModel()).contains(ADDITIONAL_MODEL);
		// Token count varies by Ollama version and tokenizer implementation
		assertThat(embeddingResponse.getMetadata().getUsage().getPromptTokens()).isGreaterThan(0)
			.isLessThanOrEqualTo(20);
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isGreaterThan(0)
			.isLessThanOrEqualTo(20);

		assertThat(this.embeddingModel.dimensions()).isEqualTo(768);
		if (!KEEP_ADDITIONAL_MODELS)
			modelManager.deleteModel(ADDITIONAL_MODEL);
	}

	@Test
	void embeddingsSimilarityComparision() {
		var model = "all-minilm";
		assertThat(this.embeddingModel).isNotNull();

		var modelManager = new OllamaModelManager(this.ollamaApi);
		assertThat(modelManager.isModelAvailable(ADDITIONAL_MODEL)).isTrue();

		EmbeddingResponse embeddingResponse = this.embeddingModel
			.call(new EmbeddingRequest(List.of("apple", "banana", "orange", "train", "bus", "taxi"),
					OllamaOptions.builder().model(model).truncate(false).build()));

		assertThat(embeddingResponse.getResults()).hasSize(6);
		assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(2).getIndex()).isEqualTo(2);
		assertThat(embeddingResponse.getResults().get(2).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(3).getIndex()).isEqualTo(3);
		assertThat(embeddingResponse.getResults().get(3).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(4).getIndex()).isEqualTo(4);
		assertThat(embeddingResponse.getResults().get(4).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(5).getIndex()).isEqualTo(5);
		assertThat(embeddingResponse.getResults().get(5).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getMetadata().getModel()).contains(ADDITIONAL_MODEL);

		List<Double> similarities = embeddingResponse.getResults()
			.stream()
			.map(e -> cosineSimilarity(embeddingResponse.getResults().get(0).getOutput(), e.getOutput()))
			.collect(Collectors.toList());
		assertTrue(similarities.get(1) > similarities.get(3));
		assertTrue(similarities.get(1) > similarities.get(4));
		assertTrue(similarities.get(1) > similarities.get(5));
		assertTrue(similarities.get(2) > similarities.get(3));
		assertTrue(similarities.get(2) > similarities.get(4));
		assertTrue(similarities.get(2) > similarities.get(5));
		List<Double> similarities2 = embeddingResponse.getResults()
			.stream()
			.map(e -> cosineSimilarity(embeddingResponse.getResults().get(3).getOutput(), e.getOutput()))
			.collect(Collectors.toList());
		assertTrue(similarities2.get(4) > similarities2.get(0));
		assertTrue(similarities2.get(4) > similarities2.get(1));
		assertTrue(similarities2.get(4) > similarities2.get(2));
		assertTrue(similarities2.get(5) > similarities2.get(0));
		assertTrue(similarities2.get(5) > similarities2.get(1));
		assertTrue(similarities2.get(5) > similarities2.get(2));
		assertThat(this.embeddingModel.dimensions()).isEqualTo(768);
		if (!KEEP_ADDITIONAL_MODELS)
			modelManager.deleteModel(ADDITIONAL_MODEL);
	}

	private double cosineSimilarity(float[] vecA, float[] vecB) {
		double dot = 0.0, magA = 0.0, magB = 0.0;
		for (int i = 0; i < 384; i++) {
			dot += vecA[i] * vecB[i];
			magA += vecA[i] * vecA[i];
			magB += vecB[i] * vecB[i];
		}
		return dot / (Math.sqrt(magA) * Math.sqrt(magB));
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public OllamaApi ollamaApi() {
			return initializeOllama(MODEL);
		}

		@Bean
		public OllamaEmbeddingModel ollamaEmbedding(OllamaApi ollamaApi) {
			return OllamaEmbeddingModel.builder()
				.ollamaApi(ollamaApi)
				.defaultOptions(OllamaEmbeddingOptions.builder().model(MODEL).build())
				.modelManagementOptions(ModelManagementOptions.builder()
					.pullModelStrategy(PullModelStrategy.WHEN_MISSING)
					.additionalModels(List.of(ADDITIONAL_MODEL))
					.build())
				.build();
		}

	}

}
