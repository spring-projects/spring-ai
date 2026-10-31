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

package org.springframework.ai.vertexai.embedding.text;

import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import org.junit.jupiter.api.Test;

import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingConnectionDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link VertexAiTextEmbeddingModel#dimensions()}.
 *
 * @author Akshit Khandelwal
 */
class VertexAiTextEmbeddingModelDimensionsTests {

	private final PredictionServiceClient mockPredictionServiceClient = mock(PredictionServiceClient.class);

	@Test
	void dimensionsOfKnownModelDoNotCallTheApi() {
		TestVertexAiTextEmbeddingModel embeddingModel = createModel(
				VertexAiTextEmbeddingOptions.builder().model("text-embedding-004").build());

		assertThat(embeddingModel.dimensions()).isEqualTo(768);
		verify(this.mockPredictionServiceClient, never()).predict(any());
	}

	@Test
	void dimensionsUseConfiguredOutputDimensionality() {
		TestVertexAiTextEmbeddingModel embeddingModel = createModel(
				VertexAiTextEmbeddingOptions.builder().model("text-embedding-004").dimensions(256).build());

		assertThat(embeddingModel.dimensions()).isEqualTo(256);
		verify(this.mockPredictionServiceClient, never()).predict(any());
	}

	private TestVertexAiTextEmbeddingModel createModel(VertexAiTextEmbeddingOptions options) {
		TestVertexAiTextEmbeddingModel embeddingModel = new TestVertexAiTextEmbeddingModel(
				mock(VertexAiEmbeddingConnectionDetails.class), options, RetryUtils.SHORT_RETRY_TEMPLATE);
		embeddingModel.setMockPredictionServiceClient(this.mockPredictionServiceClient);
		return embeddingModel;
	}

}
