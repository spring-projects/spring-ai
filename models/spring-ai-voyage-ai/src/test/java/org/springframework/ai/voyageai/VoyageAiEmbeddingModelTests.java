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

package org.springframework.ai.voyageai;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.voyageai.api.VoyageAiApi;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VoyageAiEmbeddingModel}.
 *
 * @author Spring AI
 */
class VoyageAiEmbeddingModelTests {

	@Test
	void dimensionsResolvedFromKnownModel() {
		VoyageAiApi mockApi = createMockApiWithEmbeddingResponse(1024);

		VoyageAiEmbeddingOptions options = VoyageAiEmbeddingOptions.builder()
			.model(VoyageAiEmbeddingModelName.VOYAGE_3_5.getName())
			.build();

		VoyageAiEmbeddingModel model = VoyageAiEmbeddingModel.builder()
			.voyageAiApi(mockApi)
			.metadataMode(MetadataMode.EMBED)
			.options(options)
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.build();

		assertThat(model.dimensions()).isEqualTo(1024);
	}

	@Test
	void dimensionsUsesDefaultModelWithoutApiCall() {
		VoyageAiApi mockApi = createMockApiWithEmbeddingResponse(1024);

		VoyageAiEmbeddingModel model = VoyageAiEmbeddingModel.builder()
			.voyageAiApi(mockApi)
			.options(VoyageAiEmbeddingOptions.builder().build())
			.build();

		assertThat(model.dimensions()).isEqualTo(1024);
		verify(mockApi, never()).embeddings(any());
	}

	@Test
	void dimensionsPreferOutputDimensionOption() {
		VoyageAiApi mockApi = createMockApiWithEmbeddingResponse(1024);

		VoyageAiEmbeddingOptions options = VoyageAiEmbeddingOptions.builder()
			.model(VoyageAiEmbeddingModelName.VOYAGE_3_5.getName())
			.outputDimension(256)
			.build();

		VoyageAiEmbeddingModel model = VoyageAiEmbeddingModel.builder().voyageAiApi(mockApi).options(options).build();

		assertThat(model.dimensions()).isEqualTo(256);
	}

	@Test
	void dimensionsFallbackForUnknownModel() {
		VoyageAiApi mockApi = createMockApiWithEmbeddingResponse(512);

		VoyageAiEmbeddingOptions options = VoyageAiEmbeddingOptions.builder().model("unknown-model").build();

		VoyageAiEmbeddingModel model = VoyageAiEmbeddingModel.builder()
			.voyageAiApi(mockApi)
			.metadataMode(MetadataMode.EMBED)
			.options(options)
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.build();

		assertThat(model.dimensions()).isEqualTo(512);
	}

	@Test
	void allModelNamesHaveDimensionMapping() {
		for (VoyageAiEmbeddingModelName modelName : VoyageAiEmbeddingModelName.values()) {
			VoyageAiApi mockApi = createMockApiWithEmbeddingResponse(1024);
			VoyageAiEmbeddingModel model = VoyageAiEmbeddingModel.builder()
				.voyageAiApi(mockApi)
				.options(VoyageAiEmbeddingOptions.builder().model(modelName.getName()).build())
				.build();

			assertThat(model.dimensions()).as("Model %s should have a dimension mapping", modelName.getName())
				.isGreaterThan(0);
		}
	}

	@Test
	void createRequestPropagatesOptions() {
		VoyageAiApi mockApi = createMockApiWithEmbeddingResponse(1024);

		VoyageAiEmbeddingOptions options = VoyageAiEmbeddingOptions.builder()
			.model("voyage-3.5")
			.inputType("document")
			.outputDimension(512)
			.truncation(true)
			.build();

		VoyageAiEmbeddingModel model = VoyageAiEmbeddingModel.builder().voyageAiApi(mockApi).options(options).build();

		model.call(new EmbeddingRequest(List.of("hello"), null));

		ArgumentCaptor<VoyageAiApi.EmbeddingRequest> captor = ArgumentCaptor
			.forClass(VoyageAiApi.EmbeddingRequest.class);
		Mockito.verify(mockApi).embeddings(captor.capture());
		VoyageAiApi.EmbeddingRequest sent = captor.getValue();

		assertThat(sent.model()).isEqualTo("voyage-3.5");
		assertThat(sent.inputType()).isEqualTo("document");
		assertThat(sent.outputDimension()).isEqualTo(512);
		assertThat(sent.truncation()).isTrue();
		assertThat(sent.input()).containsExactly("hello");
	}

	private VoyageAiApi createMockApiWithEmbeddingResponse(int dimensions) {
		VoyageAiApi mockApi = Mockito.mock(VoyageAiApi.class);

		float[] embedding = new float[dimensions];
		for (int i = 0; i < dimensions; i++) {
			embedding[i] = 0.1f;
		}

		VoyageAiApi.Embedding embeddingData = new VoyageAiApi.Embedding(0, embedding, "embedding");
		VoyageAiApi.Usage usage = new VoyageAiApi.Usage(10);
		VoyageAiApi.EmbeddingList embeddingList = new VoyageAiApi.EmbeddingList("list", List.of(embeddingData), "model",
				usage);

		when(mockApi.embeddings(any())).thenReturn(ResponseEntity.ok(embeddingList));

		return mockApi;
	}

}
