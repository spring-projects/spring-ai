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

package org.springframework.ai.watsonx;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.watsonx.api.WatsonxAiApi;
import org.springframework.ai.watsonx.api.WatsonxAiEmbeddingRequest;
import org.springframework.ai.watsonx.api.WatsonxAiEmbeddingResponse;
import org.springframework.ai.watsonx.api.WatsonxAiEmbeddingResults;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class WatsonxAiEmbeddingModelTest {

	private final WatsonxAiEmbeddingModel embeddingModel;

	private WatsonxAiApi watsonxAiApiMock;

	public WatsonxAiEmbeddingModelTest() {
		this.watsonxAiApiMock = mock(WatsonxAiApi.class);
		this.embeddingModel = new WatsonxAiEmbeddingModel(this.watsonxAiApiMock);
	}

	@Test
	void createRequestWithOptions() {
		String MODEL = "custom-model";
		List<String> inputs = List.of("test");
		WatsonxAiEmbeddingOptions options = WatsonxAiEmbeddingOptions.create().withModel(MODEL);

		WatsonxAiEmbeddingRequest request = this.embeddingModel.watsonxAiEmbeddingRequest(inputs, options);

		assertThat(request.getModel()).isEqualTo(MODEL);
		assertThat(request.getInputs().size()).isEqualTo(inputs.size());
	}

	@Test
	void createRequestWithOptionsAndInvalidModel() {
		String MODEL = "";
		List<String> inputs = List.of("test");
		WatsonxAiEmbeddingOptions options = WatsonxAiEmbeddingOptions.create().withModel(MODEL);

		WatsonxAiEmbeddingRequest request = this.embeddingModel.watsonxAiEmbeddingRequest(inputs, options);

		assertThat(request.getModel()).isEqualTo(WatsonxAiEmbeddingOptions.DEFAULT_MODEL);
		assertThat(request.getInputs().size()).isEqualTo(inputs.size());
	}

	@Test
	void createRequestWithNoOptions() {
		List<String> inputs = List.of("test");
		WatsonxAiEmbeddingRequest request = this.embeddingModel.watsonxAiEmbeddingRequest(inputs,
				EmbeddingOptionsBuilder.builder().build());

		assertThat(request.getModel()).isEqualTo(WatsonxAiEmbeddingOptions.DEFAULT_MODEL);
		assertThat(request.getInputs().size()).isEqualTo(inputs.size());
	}

	@Test
	void singleEmbeddingWithOptions() {
		List<String> inputs = List.of("test");

		String modelId = "mockId";
		Integer inputTokenCount = 2;
		float[] vector = new float[] { 1.0f, 2.0f };
		List<WatsonxAiEmbeddingResults> mockResults = List.of(new WatsonxAiEmbeddingResults(vector));
		WatsonxAiEmbeddingResponse mockResponse = new WatsonxAiEmbeddingResponse(modelId, new Date(), mockResults,
				inputTokenCount);

		ResponseEntity<WatsonxAiEmbeddingResponse> mockResponseEntity = ResponseEntity.ok(mockResponse);
		given(this.watsonxAiApiMock.embeddings(any(WatsonxAiEmbeddingRequest.class))).willReturn(mockResponseEntity);

		assertThat(this.embeddingModel).isNotNull();

		EmbeddingResponse embeddingResponse = this.embeddingModel.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(this.embeddingModel.dimensions()).isEqualTo(2);
	}

}
