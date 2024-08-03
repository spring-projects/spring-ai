/*
* Copyright 2024 - 2024 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
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
import org.springframework.ai.ollama.api.OllamaOptions;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
public class OllamaEmbeddingModelTests {

	@Mock
	OllamaApi ollamaApi;

	@Captor
	ArgumentCaptor<EmbeddingsRequest> embeddingsRequestCaptor;

	@Test
	public void options() {

		when(ollamaApi.embed(embeddingsRequestCaptor.capture()))
			.thenReturn(
					new EmbeddingsResponse("RESPONSE_MODEL_NAME", List.of(List.of(1d, 2d, 3d), List.of(4d, 5d, 6d))))
			.thenReturn(new EmbeddingsResponse("RESPONSE_MODEL_NAME2",
					List.of(List.of(7d, 8d, 9d), List.of(10d, 11d, 12d))));

		// Tests default options
		var defaultOptions = OllamaOptions.builder().withModel("DEFAULT_MODEL").build();

		var embeddingModel = new OllamaEmbeddingModel(ollamaApi, defaultOptions);

		EmbeddingResponse response = embeddingModel
			.call(new EmbeddingRequest(List.of("Input1", "Input2", "Input3"), EmbeddingOptions.EMPTY));

		assertThat(response.getResults()).hasSize(2);
		assertThat(response.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(response.getResults().get(0).getOutput()).isEqualTo(List.of(1d, 2d, 3d));
		assertThat(response.getResults().get(0).getMetadata()).isEqualTo(EmbeddingResultMetadata.EMPTY);
		assertThat(response.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(response.getResults().get(1).getOutput()).isEqualTo(List.of(4d, 5d, 6d));
		assertThat(response.getResults().get(1).getMetadata()).isEqualTo(EmbeddingResultMetadata.EMPTY);
		assertThat(response.getMetadata().getModel()).isEqualTo("RESPONSE_MODEL_NAME");

		assertThat(embeddingsRequestCaptor.getValue().keepAlive()).isNull();
		assertThat(embeddingsRequestCaptor.getValue().truncate()).isNull();
		assertThat(embeddingsRequestCaptor.getValue().input()).isEqualTo(List.of("Input1", "Input2", "Input3"));
		assertThat(embeddingsRequestCaptor.getValue().options()).isEqualTo(Map.of());
		assertThat(embeddingsRequestCaptor.getValue().model()).isEqualTo("DEFAULT_MODEL");

		// Tests runtime options
		var runtimeOptions = OllamaOptions.builder()
			.withModel("RUNTIME_MODEL")
			.withKeepAlive("10m")
			.withTruncate(false)
			.withMainGPU(666)
			.build();

		response = embeddingModel.call(new EmbeddingRequest(List.of("Input4", "Input5", "Input6"), runtimeOptions));

		assertThat(response.getResults()).hasSize(2);
		assertThat(response.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(response.getResults().get(0).getOutput()).isEqualTo(List.of(7d, 8d, 9d));
		assertThat(response.getResults().get(0).getMetadata()).isEqualTo(EmbeddingResultMetadata.EMPTY);
		assertThat(response.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(response.getResults().get(1).getOutput()).isEqualTo(List.of(10d, 11d, 12d));
		assertThat(response.getResults().get(1).getMetadata()).isEqualTo(EmbeddingResultMetadata.EMPTY);
		assertThat(response.getMetadata().getModel()).isEqualTo("RESPONSE_MODEL_NAME2");

		assertThat(embeddingsRequestCaptor.getValue().keepAlive()).isEqualTo(Duration.ofMinutes(10));
		assertThat(embeddingsRequestCaptor.getValue().truncate()).isFalse();
		assertThat(embeddingsRequestCaptor.getValue().input()).isEqualTo(List.of("Input4", "Input5", "Input6"));
		assertThat(embeddingsRequestCaptor.getValue().options()).isEqualTo(Map.of("main_gpu", 666));
		assertThat(embeddingsRequestCaptor.getValue().model()).isEqualTo("RUNTIME_MODEL");

	}

}
