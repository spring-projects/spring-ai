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

package org.springframework.ai.vertexai.embedding.text;

import java.util.List;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingConnectionDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = VertexAiTextEmbeddingModelIT.Config.class)
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_LOCATION", matches = ".*")
class VertexAiTextEmbeddingModelIT {

	// https://console.cloud.google.com/vertex-ai/publishers/google/model-garden/textembedding-gecko?project=gen-lang-client-0587361272

	@Autowired
	private VertexAiTextEmbeddingModel embeddingModel;

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "text-embedding-004", "text-multilingual-embedding-002" })
	void defaultEmbedding(String modelName) {
		assertThat(this.embeddingModel).isNotNull();

		var options = VertexAiTextEmbeddingOptions.builder().model(modelName).build();

		EmbeddingResponse embeddingResponse = this.embeddingModel
			.call(new EmbeddingRequest(List.of("Hello World", "World is Big"), options));

		assertThat(embeddingResponse.getResults()).hasSize(2);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(768);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).hasSize(768);
		assertThat(embeddingResponse.getMetadata().getModel()).as("Model name in metadata should match expected model")
			.isEqualTo(modelName);

		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens())
			.as("Total tokens in metadata should be 5")
			.isEqualTo(5L);

		assertThat(this.embeddingModel.dimensions()).isEqualTo(768);
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public VertexAiEmbeddingConnectionDetails connectionDetails() {
			return VertexAiEmbeddingConnectionDetails.builder()
				.projectId(System.getenv("VERTEX_AI_GEMINI_PROJECT_ID"))
				.location(System.getenv("VERTEX_AI_GEMINI_LOCATION"))
				.build();
		}

		@Bean
		public VertexAiTextEmbeddingModel vertexAiEmbeddingModel(VertexAiEmbeddingConnectionDetails connectionDetails) {

			VertexAiTextEmbeddingOptions options = VertexAiTextEmbeddingOptions.builder()
				.model(VertexAiTextEmbeddingOptions.DEFAULT_MODEL_NAME)
				.build();

			return new VertexAiTextEmbeddingModel(connectionDetails, options);
		}

	}

}
