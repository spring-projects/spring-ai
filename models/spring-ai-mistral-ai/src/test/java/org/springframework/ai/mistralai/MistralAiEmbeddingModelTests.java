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

package org.springframework.ai.mistralai;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MistralAiEmbeddingModel}.
 *
 * @author Mark Pollack
 * @author Nicolas Krier
 */
class MistralAiEmbeddingModelTests {

	@Test
	void testDimensionsForMistralEmbedModel() {
		MistralAiApi mockApi = createMockApiWithEmbeddingResponse(1024);

		MistralAiEmbeddingOptions options = MistralAiEmbeddingOptions.builder()
			.withModel(MistralAiApi.EmbeddingModel.EMBED.getValue())
			.build();

		MistralAiEmbeddingModel model = MistralAiEmbeddingModel.builder()
			.mistralAiApi(mockApi)
			.metadataMode(MetadataMode.EMBED)
			.options(options)
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.build();

		assertThat(model.dimensions()).isEqualTo(1024);
	}

	@Test
	void testDimensionsForCodestralEmbedModel() {
		MistralAiApi mockApi = createMockApiWithEmbeddingResponse(1536);

		MistralAiEmbeddingOptions options = MistralAiEmbeddingOptions.builder()
			.withModel(MistralAiApi.EmbeddingModel.CODESTRAL_EMBED.getValue())
			.build();

		MistralAiEmbeddingModel model = MistralAiEmbeddingModel.builder()
			.mistralAiApi(mockApi)
			.metadataMode(MetadataMode.EMBED)
			.options(options)
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.build();

		assertThat(model.dimensions()).isEqualTo(1536);
	}

	@Test
	void testDimensionsFallbackForUnknownModel() {
		MistralAiApi mockApi = createMockApiWithEmbeddingResponse(512);

		// Use a model name that doesn't exist in knownEmbeddingDimensions.
		MistralAiEmbeddingOptions options = MistralAiEmbeddingOptions.builder().withModel("unknown-model").build();

		MistralAiEmbeddingModel model = MistralAiEmbeddingModel.builder()
			.mistralAiApi(mockApi)
			.metadataMode(MetadataMode.EMBED)
			.options(options)
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.build();

		// For the first call, it should fall back to super.dimensions() which detects
		// dimensions from the API response.
		assertThat(model.dimensions()).isEqualTo(512);

		// For the second call, it should use the cache mechanism.
		assertThat(model.dimensions()).isEqualTo(512);

		// Verify that super.dimensions() has been called once.
		verify(mockApi).embeddings(any());
	}

	@Test
	void testAllEmbeddingModelsHaveDimensionMapping() {
		// This test ensures that knownEmbeddingDimensions map stays in sync with the
		// EmbeddingModel enum.
		// If a new model is added to the enum but not to the dimensions map, this test
		// will help catch it.

		for (MistralAiApi.EmbeddingModel embeddingModel : MistralAiApi.EmbeddingModel.values()) {
			MistralAiApi mockApi = createMockApiWithEmbeddingResponse(1024);
			MistralAiEmbeddingOptions options = MistralAiEmbeddingOptions.builder()
				.withModel(embeddingModel.getValue())
				.build();

			MistralAiEmbeddingModel model = MistralAiEmbeddingModel.builder()
				.mistralAiApi(mockApi)
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
		MistralAiApi mockApi = createMockApiWithEmbeddingResponse(1536);

		MistralAiEmbeddingModel model = MistralAiEmbeddingModel.builder()
			.mistralAiApi(mockApi)
			.options(MistralAiEmbeddingOptions.builder()
				.withModel(MistralAiApi.EmbeddingModel.CODESTRAL_EMBED.getValue())
				.build())
			.build();

		assertThat(model).isNotNull();
		assertThat(model.dimensions()).isEqualTo(1536);
	}

	private MistralAiApi createMockApiWithEmbeddingResponse(int dimensions) {
		MistralAiApi mockApi = Mockito.mock(MistralAiApi.class);

		// Create a mock embedding response with the specified dimensions
		float[] embedding = new float[dimensions];
		Arrays.fill(embedding, 0.1f);

		MistralAiApi.Embedding embeddingData = new MistralAiApi.Embedding(0, embedding, "embedding");

		MistralAiApi.Usage usage = new MistralAiApi.Usage(10, 0, 10);

		var embeddingList = new MistralAiApi.EmbeddingList<>("object", List.of(embeddingData), "model", usage);

		when(mockApi.embeddings(any())).thenReturn(ResponseEntity.ok(embeddingList));

		return mockApi;
	}

}
