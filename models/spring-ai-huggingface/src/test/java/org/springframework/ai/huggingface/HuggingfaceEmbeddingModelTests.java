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

package org.springframework.ai.huggingface;

import java.util.List;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResultMetadata;
import org.springframework.ai.huggingface.api.HuggingfaceApi;
import org.springframework.ai.huggingface.api.HuggingfaceApi.EmbeddingsRequest;
import org.springframework.ai.huggingface.api.HuggingfaceApi.EmbeddingsResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for {@link HuggingfaceEmbeddingModel}.
 *
 * @author Myeongdeok Kang
 */
@ExtendWith(MockitoExtension.class)
class HuggingfaceEmbeddingModelTests {

	@Mock
	HuggingfaceApi huggingfaceApi;

	@Captor
	ArgumentCaptor<EmbeddingsRequest> embeddingsRequestCaptor;

	@Test
	void options() {
		given(this.huggingfaceApi.embeddings(this.embeddingsRequestCaptor.capture()))
			.willReturn(new EmbeddingsResponse("RESPONSE_MODEL_NAME",
					List.of(new float[] { 1f, 2f, 3f }, new float[] { 4f, 5f, 6f })))
			.willReturn(new EmbeddingsResponse("RESPONSE_MODEL_NAME2",
					List.of(new float[] { 7f, 8f, 9f }, new float[] { 10f, 11f, 12f })));

		// Tests default options
		var defaultOptions = HuggingfaceEmbeddingOptions.builder().model("DEFAULT_MODEL").build();

		var embeddingModel = HuggingfaceEmbeddingModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.defaultOptions(defaultOptions)
			.build();

		EmbeddingResponse response = embeddingModel
			.call(new EmbeddingRequest(List.of("Input1", "Input2", "Input3"), EmbeddingOptions.builder().build()));

		assertThat(response.getResults()).hasSize(2);
		assertThat(response.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(response.getResults().get(0).getOutput()).isEqualTo(new float[] { 1f, 2f, 3f });
		assertThat(response.getResults().get(0).getMetadata()).isEqualTo(EmbeddingResultMetadata.EMPTY);
		assertThat(response.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(response.getResults().get(1).getOutput()).isEqualTo(new float[] { 4f, 5f, 6f });
		assertThat(response.getResults().get(1).getMetadata()).isEqualTo(EmbeddingResultMetadata.EMPTY);
		assertThat(response.getMetadata().getModel()).isEqualTo("RESPONSE_MODEL_NAME");

		assertThat(this.embeddingsRequestCaptor.getValue().inputs()).isEqualTo(List.of("Input1", "Input2", "Input3"));
		assertThat(this.embeddingsRequestCaptor.getValue().model()).isEqualTo("DEFAULT_MODEL");

		// Tests runtime options
		var runtimeOptions = HuggingfaceEmbeddingOptions.builder().model("RUNTIME_MODEL").build();

		response = embeddingModel.call(new EmbeddingRequest(List.of("Input4", "Input5", "Input6"), runtimeOptions));

		assertThat(response.getResults()).hasSize(2);
		assertThat(response.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(response.getResults().get(0).getOutput()).isEqualTo(new float[] { 7f, 8f, 9f });
		assertThat(response.getResults().get(0).getMetadata()).isEqualTo(EmbeddingResultMetadata.EMPTY);
		assertThat(response.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(response.getResults().get(1).getOutput()).isEqualTo(new float[] { 10f, 11f, 12f });
		assertThat(response.getResults().get(1).getMetadata()).isEqualTo(EmbeddingResultMetadata.EMPTY);
		assertThat(response.getMetadata().getModel()).isEqualTo("RESPONSE_MODEL_NAME2");

		assertThat(this.embeddingsRequestCaptor.getValue().inputs()).isEqualTo(List.of("Input4", "Input5", "Input6"));
		assertThat(this.embeddingsRequestCaptor.getValue().model()).isEqualTo("RUNTIME_MODEL");
	}

	@Test
	void singleInputEmbedding() {
		given(this.huggingfaceApi.embeddings(this.embeddingsRequestCaptor.capture()))
			.willReturn(new EmbeddingsResponse("TEST_MODEL", List.of(new float[] { 0.1f, 0.2f, 0.3f })));

		var embeddingModel = HuggingfaceEmbeddingModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.defaultOptions(HuggingfaceEmbeddingOptions.builder().model("TEST_MODEL").build())
			.build();

		EmbeddingResponse response = embeddingModel
			.call(new EmbeddingRequest(List.of("Single input text"), EmbeddingOptions.builder().build()));

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(response.getResults().get(0).getOutput()).isEqualTo(new float[] { 0.1f, 0.2f, 0.3f });
		assertThat(response.getMetadata().getModel()).isEqualTo("TEST_MODEL");

		assertThat(this.embeddingsRequestCaptor.getValue().inputs()).isEqualTo(List.of("Single input text"));
		assertThat(this.embeddingsRequestCaptor.getValue().model()).isEqualTo("TEST_MODEL");
	}

	@Test
	void embeddingWithNullOptions() {
		given(this.huggingfaceApi.embeddings(this.embeddingsRequestCaptor.capture()))
			.willReturn(new EmbeddingsResponse("NULL_OPTIONS_MODEL", List.of(new float[] { 0.5f })));

		var embeddingModel = HuggingfaceEmbeddingModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.defaultOptions(HuggingfaceEmbeddingOptions.builder().model("NULL_OPTIONS_MODEL").build())
			.build();

		EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of("Null options test"), null));

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getMetadata().getModel()).isEqualTo("NULL_OPTIONS_MODEL");

		assertThat(this.embeddingsRequestCaptor.getValue().model()).isEqualTo("NULL_OPTIONS_MODEL");
	}

	@Test
	void embeddingWithMultipleLargeInputs() {
		List<String> largeInputs = List.of(
				"This is a very long text input that might be used for document embedding scenarios",
				"Another substantial piece of text content that could represent a paragraph or section",
				"A third lengthy input to test batch processing capabilities of the embedding model");

		given(this.huggingfaceApi.embeddings(this.embeddingsRequestCaptor.capture()))
			.willReturn(new EmbeddingsResponse("BATCH_MODEL", List.of(new float[] { 0.1f, 0.2f, 0.3f, 0.4f },
					new float[] { 0.5f, 0.6f, 0.7f, 0.8f }, new float[] { 0.9f, 1.0f, 1.1f, 1.2f })));

		var embeddingModel = HuggingfaceEmbeddingModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.defaultOptions(HuggingfaceEmbeddingOptions.builder().model("BATCH_MODEL").build())
			.build();

		EmbeddingResponse response = embeddingModel
			.call(new EmbeddingRequest(largeInputs, EmbeddingOptions.builder().build()));

		assertThat(response.getResults()).hasSize(3);
		assertThat(response.getResults().get(0).getOutput()).hasSize(4);
		assertThat(response.getResults().get(1).getOutput()).hasSize(4);
		assertThat(response.getResults().get(2).getOutput()).hasSize(4);

		assertThat(this.embeddingsRequestCaptor.getValue().inputs()).isEqualTo(largeInputs);
	}

	@Test
	void embeddingResponseMetadata() {
		given(this.huggingfaceApi.embeddings(this.embeddingsRequestCaptor.capture()))
			.willReturn(new EmbeddingsResponse("METADATA_MODEL", List.of(new float[] { 0.1f, 0.2f })));

		var embeddingModel = HuggingfaceEmbeddingModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.defaultOptions(HuggingfaceEmbeddingOptions.builder().model("METADATA_MODEL").build())
			.build();

		EmbeddingResponse response = embeddingModel
			.call(new EmbeddingRequest(List.of("Metadata test"), EmbeddingOptions.builder().build()));

		assertThat(response.getMetadata().getModel()).isEqualTo("METADATA_MODEL");
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getMetadata()).isEqualTo(EmbeddingResultMetadata.EMPTY);
	}

	@Test
	void embeddingWithZeroLengthVectors() {
		given(this.huggingfaceApi.embeddings(this.embeddingsRequestCaptor.capture()))
			.willReturn(new EmbeddingsResponse("ZERO_MODEL", List.of(new float[] {})));

		var embeddingModel = HuggingfaceEmbeddingModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.defaultOptions(HuggingfaceEmbeddingOptions.builder().model("ZERO_MODEL").build())
			.build();

		EmbeddingResponse response = embeddingModel
			.call(new EmbeddingRequest(List.of("Zero length test"), EmbeddingOptions.builder().build()));

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput()).isEmpty();
	}

	@Test
	void builderValidation() {
		// Test that builder requires huggingfaceApi
		assertThatThrownBy(() -> HuggingfaceEmbeddingModel.builder().build())
			.isInstanceOf(IllegalArgumentException.class);

		// Test successful builder with minimal required parameters
		var model = HuggingfaceEmbeddingModel.builder().huggingfaceApi(this.huggingfaceApi).build();

		assertThat(model).isNotNull();
	}

	@Test
	void builderWithAllOptions() {
		HuggingfaceEmbeddingOptions options = HuggingfaceEmbeddingOptions.builder()
			.model("test-model")
			.normalize(true)
			.promptName("query")
			.truncate(true)
			.truncationDirection("Right")
			.build();

		var embeddingModel = HuggingfaceEmbeddingModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.defaultOptions(options)
			.observationRegistry(ObservationRegistry.NOOP)
			.build();

		assertThat(embeddingModel).isNotNull();
	}

	@Test
	void embedDocument() {
		given(this.huggingfaceApi.embeddings(this.embeddingsRequestCaptor.capture()))
			.willReturn(new EmbeddingsResponse("DOC_MODEL", List.of(new float[] { 0.1f, 0.2f, 0.3f })));

		var embeddingModel = HuggingfaceEmbeddingModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.defaultOptions(HuggingfaceEmbeddingOptions.builder().model("DOC_MODEL").build())
			.build();

		Document document = new Document("Document content for embedding");
		float[] embedding = embeddingModel.embed(document);

		assertThat(embedding).isEqualTo(new float[] { 0.1f, 0.2f, 0.3f });
	}

	@Test
	void buildEmbeddingRequestMergesOptions() {
		var defaultOptions = HuggingfaceEmbeddingOptions.builder().model("DEFAULT_MODEL").build();

		var embeddingModel = HuggingfaceEmbeddingModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.defaultOptions(defaultOptions)
			.build();

		var runtimeOptions = HuggingfaceEmbeddingOptions.builder().model("RUNTIME_MODEL").build();

		var originalRequest = new EmbeddingRequest(List.of("Test text"), runtimeOptions);
		var builtRequest = embeddingModel.buildEmbeddingRequest(originalRequest);

		assertThat(builtRequest.getOptions()).isInstanceOf(HuggingfaceEmbeddingOptions.class);
		HuggingfaceEmbeddingOptions mergedOptions = (HuggingfaceEmbeddingOptions) builtRequest.getOptions();
		assertThat(mergedOptions.getModel()).isEqualTo("RUNTIME_MODEL");
	}

	@Test
	void buildEmbeddingRequestValidatesModel() {
		var defaultOptions = HuggingfaceEmbeddingOptions.builder().build();

		var embeddingModel = HuggingfaceEmbeddingModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.defaultOptions(defaultOptions)
			.build();

		var request = new EmbeddingRequest(List.of("Test text"), null);

		assertThatThrownBy(() -> embeddingModel.buildEmbeddingRequest(request))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("model cannot be null or empty");
	}

	@Test
	void setObservationConventionValidation() {
		var embeddingModel = HuggingfaceEmbeddingModel.builder().huggingfaceApi(this.huggingfaceApi).build();

		assertThatThrownBy(() -> embeddingModel.setObservationConvention(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("observationConvention cannot be null");
	}

}
