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

package org.springframework.ai.cohere.embedding;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.ai.cohere.CohereTestConfiguration;
import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ricken Bazolo
 */
@SpringBootTest(classes = CohereTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "COHERE_API_KEY", matches = ".+")
class CohereEmbeddingIT {

	private static final int EMBED_DIMENSIONS = 384;

	@Autowired
	private CohereApi cohereApi;

	@Autowired
	private CohereEmbeddingModel cohereEmbeddingModel;

	@Test
	void defaultEmbedding() {
		var embeddingResponse = this.cohereEmbeddingModel.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(EMBED_DIMENSIONS);
		assertThat(this.cohereEmbeddingModel.dimensions()).isEqualTo(EMBED_DIMENSIONS);
	}

	@ParameterizedTest
	@CsvSource({ "embed-multilingual-light-v3.0, 384", "embed-english-light-v3.0, 384" })
	void defaultOptionsEmbedding(String model, int dimensions) {
		var cohereEmbeddingOptions = CohereEmbeddingOptions.builder().model(model).build();
		var anotherCohereEmbeddingModel = CohereEmbeddingModel.builder()
			.cohereApi(this.cohereApi)
			.options(cohereEmbeddingOptions)
			.build();
		var embeddingResponse = anotherCohereEmbeddingModel.embedForResponse(List.of("Hello World", "World is big"));
		assertThat(embeddingResponse.getResults()).hasSize(2);
		embeddingResponse.getResults().forEach(result -> {
			assertThat(result).isNotNull();
			assertThat(result.getOutput()).hasSize(dimensions);
		});
		assertThat(anotherCohereEmbeddingModel.dimensions()).isEqualTo(dimensions);
	}

	@ParameterizedTest
	@CsvSource({ "embed-multilingual-light-v3.0, 384", "embed-english-light-v3.0, 384" })
	void calledOptionsEmbedding(String model, int dimensions) {
		var cohereEmbeddingOptions = CohereEmbeddingOptions.builder().model(model).build();
		var embeddingRequest = new EmbeddingRequest(List.of("Hello World", "World is big", "We are small"),
				cohereEmbeddingOptions);
		var embeddingResponse = this.cohereEmbeddingModel.call(embeddingRequest);
		assertThat(embeddingResponse.getResults()).hasSize(3);
		embeddingResponse.getResults().forEach(result -> {
			assertThat(result).isNotNull();
			assertThat(result.getOutput()).hasSize(dimensions);
		});
		assertThat(this.cohereEmbeddingModel.dimensions()).isEqualTo(EMBED_DIMENSIONS);
	}

}
