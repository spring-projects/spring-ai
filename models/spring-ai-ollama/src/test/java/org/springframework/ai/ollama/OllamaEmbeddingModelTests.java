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
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResultMetadata;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApi.EmbeddingsRequest;
import org.springframework.ai.ollama.api.OllamaApi.EmbeddingsResponse;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class OllamaEmbeddingModelTests {

	@Mock
	OllamaApi ollamaApi;

	@Captor
	ArgumentCaptor<EmbeddingsRequest> embeddingsRequestCaptor;

	@Test
	void options() {

		given(this.ollamaApi.embed(this.embeddingsRequestCaptor.capture()))
			.willReturn(new EmbeddingsResponse("RESPONSE_MODEL_NAME",
					List.of(new float[] { 1f, 2f, 3f }, new float[] { 4f, 5f, 6f }), 0L, 0L, 0))
			.willReturn(new EmbeddingsResponse("RESPONSE_MODEL_NAME2",
					List.of(new float[] { 7f, 8f, 9f }, new float[] { 10f, 11f, 12f }), 0L, 0L, 0));

		// Tests default options
		var defaultOptions = OllamaEmbeddingOptions.builder().model("DEFAULT_MODEL").build();

		var embeddingModel = OllamaEmbeddingModel.builder()
			.ollamaApi(this.ollamaApi)
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

		assertThat(this.embeddingsRequestCaptor.getValue().keepAlive()).isNull();
		assertThat(this.embeddingsRequestCaptor.getValue().truncate()).isNull();
		assertThat(this.embeddingsRequestCaptor.getValue().input()).isEqualTo(List.of("Input1", "Input2", "Input3"));
		assertThat(this.embeddingsRequestCaptor.getValue().options()).isEqualTo(Map.of());
		assertThat(this.embeddingsRequestCaptor.getValue().model()).isEqualTo("DEFAULT_MODEL");

		// Tests runtime options
		var runtimeOptions = OllamaEmbeddingOptions.builder().model("RUNTIME_MODEL").build();

		response = embeddingModel.call(new EmbeddingRequest(List.of("Input4", "Input5", "Input6"), runtimeOptions));

		assertThat(response.getResults()).hasSize(2);
		assertThat(response.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(response.getResults().get(0).getOutput()).isEqualTo(new float[] { 7f, 8f, 9f });
		assertThat(response.getResults().get(0).getMetadata()).isEqualTo(EmbeddingResultMetadata.EMPTY);
		assertThat(response.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(response.getResults().get(1).getOutput()).isEqualTo(new float[] { 10f, 11f, 12f });
		assertThat(response.getResults().get(1).getMetadata()).isEqualTo(EmbeddingResultMetadata.EMPTY);
		assertThat(response.getMetadata().getModel()).isEqualTo("RESPONSE_MODEL_NAME2");

		assertThat(this.embeddingsRequestCaptor.getValue().input()).isEqualTo(List.of("Input4", "Input5", "Input6"));
		assertThat(this.embeddingsRequestCaptor.getValue().model()).isEqualTo("RUNTIME_MODEL");

	}

	@Test
	void singleInputEmbedding() {
		given(this.ollamaApi.embed(this.embeddingsRequestCaptor.capture()))
			.willReturn(new EmbeddingsResponse("TEST_MODEL", List.of(new float[] { 0.1f, 0.2f, 0.3f }), 10L, 5L, 1));

		var embeddingModel = OllamaEmbeddingModel.builder()
			.ollamaApi(this.ollamaApi)
			.defaultOptions(OllamaEmbeddingOptions.builder().model("TEST_MODEL").build())
			.build();

		EmbeddingResponse response = embeddingModel
			.call(new EmbeddingRequest(List.of("Single input text"), EmbeddingOptions.builder().build()));

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(response.getResults().get(0).getOutput()).isEqualTo(new float[] { 0.1f, 0.2f, 0.3f });
		assertThat(response.getMetadata().getModel()).isEqualTo("TEST_MODEL");

		assertThat(this.embeddingsRequestCaptor.getValue().input()).isEqualTo(List.of("Single input text"));
		assertThat(this.embeddingsRequestCaptor.getValue().model()).isEqualTo("TEST_MODEL");
	}

	@Test
	void embeddingWithNullOptions() {
		given(this.ollamaApi.embed(this.embeddingsRequestCaptor.capture()))
			.willReturn(new EmbeddingsResponse("NULL_OPTIONS_MODEL", List.of(new float[] { 0.5f }), 5L, 2L, 1));

		var embeddingModel = OllamaEmbeddingModel.builder()
			.ollamaApi(this.ollamaApi)
			.defaultOptions(OllamaEmbeddingOptions.builder().model("NULL_OPTIONS_MODEL").build())
			.build();

		EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of("Null options test"), null));

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getMetadata().getModel()).isEqualTo("NULL_OPTIONS_MODEL");

		assertThat(this.embeddingsRequestCaptor.getValue().model()).isEqualTo("NULL_OPTIONS_MODEL");
		assertThat(this.embeddingsRequestCaptor.getValue().options()).isEqualTo(Map.of());
	}

	@Test
	void embeddingWithMultipleLargeInputs() {
		List<String> largeInputs = List.of(
				"This is a very long text input that might be used for document embedding scenarios",
				"Another substantial piece of text content that could represent a paragraph or section",
				"A third lengthy input to test batch processing capabilities of the embedding model");

		given(this.ollamaApi.embed(this.embeddingsRequestCaptor.capture()))
			.willReturn(new EmbeddingsResponse(
					"BATCH_MODEL", List.of(new float[] { 0.1f, 0.2f, 0.3f, 0.4f },
							new float[] { 0.5f, 0.6f, 0.7f, 0.8f }, new float[] { 0.9f, 1.0f, 1.1f, 1.2f }),
					150L, 75L, 3));

		var embeddingModel = OllamaEmbeddingModel.builder()
			.ollamaApi(this.ollamaApi)
			.defaultOptions(OllamaEmbeddingOptions.builder().model("BATCH_MODEL").build())
			.build();

		EmbeddingResponse response = embeddingModel
			.call(new EmbeddingRequest(largeInputs, EmbeddingOptions.builder().build()));

		assertThat(response.getResults()).hasSize(3);
		assertThat(response.getResults().get(0).getOutput()).hasSize(4);
		assertThat(response.getResults().get(1).getOutput()).hasSize(4);
		assertThat(response.getResults().get(2).getOutput()).hasSize(4);

		assertThat(this.embeddingsRequestCaptor.getValue().input()).isEqualTo(largeInputs);
	}

	@Test
	void embeddingWithCustomKeepAliveFormats() {
		given(this.ollamaApi.embed(this.embeddingsRequestCaptor.capture()))
			.willReturn(new EmbeddingsResponse("KEEPALIVE_MODEL", List.of(new float[] { 1.0f }), 5L, 2L, 1));

		var embeddingModel = OllamaEmbeddingModel.builder()
			.ollamaApi(this.ollamaApi)
			.defaultOptions(OllamaEmbeddingOptions.builder().model("KEEPALIVE_MODEL").build())
			.build();

		// Test with seconds format
		var secondsOptions = OllamaEmbeddingOptions.builder().model("KEEPALIVE_MODEL").keepAlive("300s").build();

		embeddingModel.call(new EmbeddingRequest(List.of("Keep alive seconds"), secondsOptions));
		assertThat(this.embeddingsRequestCaptor.getValue().keepAlive()).isEqualTo("300s");

		// Test with hours format
		var hoursOptions = OllamaEmbeddingOptions.builder().model("KEEPALIVE_MODEL").keepAlive("2h").build();

		embeddingModel.call(new EmbeddingRequest(List.of("Keep alive hours"), hoursOptions));
		assertThat(this.embeddingsRequestCaptor.getValue().keepAlive()).isEqualTo("2h");
	}

	@Test
	void embeddingResponseMetadata() {
		given(this.ollamaApi.embed(this.embeddingsRequestCaptor.capture()))
			.willReturn(new EmbeddingsResponse("METADATA_MODEL", List.of(new float[] { 0.1f, 0.2f }), 100L, 50L, 25));

		var embeddingModel = OllamaEmbeddingModel.builder()
			.ollamaApi(this.ollamaApi)
			.defaultOptions(OllamaEmbeddingOptions.builder().model("METADATA_MODEL").build())
			.build();

		EmbeddingResponse response = embeddingModel
			.call(new EmbeddingRequest(List.of("Metadata test"), EmbeddingOptions.builder().build()));

		assertThat(response.getMetadata().getModel()).isEqualTo("METADATA_MODEL");
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getMetadata()).isEqualTo(EmbeddingResultMetadata.EMPTY);
	}

	@Test
	void embeddingWithZeroLengthVectors() {
		given(this.ollamaApi.embed(this.embeddingsRequestCaptor.capture()))
			.willReturn(new EmbeddingsResponse("ZERO_MODEL", List.of(new float[] {}), 0L, 0L, 1));

		var embeddingModel = OllamaEmbeddingModel.builder()
			.ollamaApi(this.ollamaApi)
			.defaultOptions(OllamaEmbeddingOptions.builder().model("ZERO_MODEL").build())
			.build();

		EmbeddingResponse response = embeddingModel
			.call(new EmbeddingRequest(List.of("Zero length test"), EmbeddingOptions.builder().build()));

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput()).isEmpty();
	}

	@Test
	void builderValidation() {
		// Test that builder requires ollamaApi
		assertThatThrownBy(() -> OllamaEmbeddingModel.builder().build()).isInstanceOf(IllegalStateException.class);

		// Test successful builder with minimal required parameters
		var model = OllamaEmbeddingModel.builder().ollamaApi(this.ollamaApi).build();

		assertThat(model).isNotNull();
	}

}
