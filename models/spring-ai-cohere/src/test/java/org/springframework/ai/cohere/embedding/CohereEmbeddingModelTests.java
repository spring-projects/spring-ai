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

package org.springframework.ai.cohere.embedding;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CohereEmbeddingModel}.
 *
 * @author Ricken Bazolo
 */
class CohereEmbeddingModelTests {

	@Test
	void testDimensionsForEmbedV4Model() {
		CohereApi mockApi = createMockApiWithEmbeddingResponse(1024);

		CohereEmbeddingOptions options = CohereEmbeddingOptions.builder()
			.model(CohereApi.EmbeddingModel.EMBED_V4.getValue())
			.build();

		CohereEmbeddingModel model = CohereEmbeddingModel.builder()
			.cohereApi(mockApi)
			.metadataMode(MetadataMode.EMBED)
			.options(options)
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.build();

		assertThat(model.dimensions()).isEqualTo(1536);
	}

	@Test
	void testDimensionsForMultilingualV3Model() {
		CohereApi mockApi = createMockApiWithEmbeddingResponse(1024);

		CohereEmbeddingOptions options = CohereEmbeddingOptions.builder()
			.model(CohereApi.EmbeddingModel.EMBED_MULTILINGUAL_V3.getValue())
			.build();

		CohereEmbeddingModel model = CohereEmbeddingModel.builder()
			.cohereApi(mockApi)
			.metadataMode(MetadataMode.EMBED)
			.options(options)
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.build();

		assertThat(model.dimensions()).isEqualTo(1024);
	}

	@Test
	void testDimensionsFallbackForUnknownModel() {
		CohereApi mockApi = createMockApiWithEmbeddingResponse(512);

		// Use a model name that doesn't exist in KNOWN_EMBEDDING_DIMENSIONS
		CohereEmbeddingOptions options = CohereEmbeddingOptions.builder().model("unknown-model").build();

		CohereEmbeddingModel model = CohereEmbeddingModel.builder()
			.cohereApi(mockApi)
			.metadataMode(MetadataMode.EMBED)
			.options(options)
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.build();

		// Should fall back to super.dimensions() which detects dimensions from the API
		// response
		assertThat(model.dimensions()).isEqualTo(1024);
	}

	@Test
	void testAllEmbeddingModelsHaveDimensionMapping() {
		// This test ensures that KNOWN_EMBEDDING_DIMENSIONS map stays in sync with the
		// EmbeddingModel enum
		// If a new model is added to the enum but not to the dimensions map, this test
		// will help catch it

		for (CohereApi.EmbeddingModel embeddingModel : CohereApi.EmbeddingModel.values()) {
			CohereApi mockApi = createMockApiWithEmbeddingResponse(1024);
			CohereEmbeddingOptions options = CohereEmbeddingOptions.builder().model(embeddingModel.getValue()).build();

			CohereEmbeddingModel model = CohereEmbeddingModel.builder()
				.cohereApi(mockApi)
				.metadataMode(MetadataMode.EMBED)
				.options(options)
				.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
				.build();

			// Each model should have a valid dimension (not the fallback -1)
			assertThat(model.dimensions()).as("Model %s should have a dimension mapping", embeddingModel.getValue())
				.isGreaterThan(0);
		}
	}

	@Test
	void testBuilderCreatesValidModel() {
		CohereApi mockApi = createMockApiWithEmbeddingResponse(1024);

		CohereEmbeddingModel model = CohereEmbeddingModel.builder()
			.cohereApi(mockApi)
			.options(CohereEmbeddingOptions.builder()
				.model(CohereApi.EmbeddingModel.EMBED_MULTILINGUAL_V3.getValue())
				.build())
			.build();

		assertThat(model).isNotNull();
		assertThat(model.dimensions()).isEqualTo(1024);
	}

	private CohereApi createMockApiWithEmbeddingResponse(int dimensions) {
		CohereApi mockApi = Mockito.mock(CohereApi.class);

		// Create a mock embedding response with the specified dimensions
		// Cohere returns List<List<Double>> for embeddings
		List<Double> embedding = new java.util.ArrayList<>(dimensions);
		for (int i = 0; i < dimensions; i++) {
			embedding.add(0.1);
		}

		// Cohere can return embeddings for multiple texts
		List<List<Double>> embeddings = List.of(embedding);

		CohereApi.EmbeddingResponse embeddingResponse = new CohereApi.EmbeddingResponse("test-id", embeddings,
				List.of("test text"), "embeddings_floats");

		when(mockApi.embeddings(any())).thenReturn(ResponseEntity.ok(embeddingResponse));

		return mockApi;
	}

}
