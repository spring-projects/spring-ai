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

package org.springframework.ai.mistralai;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Nicolas Krier
 */
@SpringBootTest(classes = MistralAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiEmbeddingIT {

	private static final int MISTRAL_EMBED_DIMENSIONS = 1024;

	@Autowired
	private MistralAiApi mistralAiApi;

	@Autowired
	private MistralAiEmbeddingModel mistralAiEmbeddingModel;

	@Test
	void defaultEmbedding() {
		var embeddingResponse = this.mistralAiEmbeddingModel.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(MISTRAL_EMBED_DIMENSIONS);
		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo("mistral-embed");
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(4);
		assertThat(embeddingResponse.getMetadata().getUsage().getPromptTokens()).isEqualTo(4);
		assertThat(this.mistralAiEmbeddingModel.dimensions()).isEqualTo(MISTRAL_EMBED_DIMENSIONS);
	}

	@ParameterizedTest
	@CsvSource({ "mistral-embed, 1024", "codestral-embed, 1536" })
	void defaultOptionsEmbedding(String model, int dimensions) {
		var mistralAiEmbeddingOptions = MistralAiEmbeddingOptions.builder().withModel(model).build();
		var anotherMistralAiEmbeddingModel = MistralAiEmbeddingModel.builder()
			.mistralAiApi(this.mistralAiApi)
			.options(mistralAiEmbeddingOptions)
			.build();
		var embeddingResponse = anotherMistralAiEmbeddingModel.embedForResponse(List.of("Hello World", "World is big"));
		assertThat(embeddingResponse.getResults()).hasSize(2);
		embeddingResponse.getResults().forEach(result -> {
			assertThat(result).isNotNull();
			assertThat(result.getOutput()).hasSize(dimensions);
		});
		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo(model);
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(9);
		assertThat(embeddingResponse.getMetadata().getUsage().getPromptTokens()).isEqualTo(9);
		assertThat(anotherMistralAiEmbeddingModel.dimensions()).isEqualTo(dimensions);
	}

	@ParameterizedTest
	@CsvSource({ "mistral-embed, 1024", "codestral-embed, 1536" })
	void calledOptionsEmbedding(String model, int dimensions) {
		var mistralAiEmbeddingOptions = MistralAiEmbeddingOptions.builder().withModel(model).build();
		var embeddingRequest = new EmbeddingRequest(List.of("Hello World", "World is big", "We are small"),
				mistralAiEmbeddingOptions);
		var embeddingResponse = this.mistralAiEmbeddingModel.call(embeddingRequest);
		assertThat(embeddingResponse.getResults()).hasSize(3);
		embeddingResponse.getResults().forEach(result -> {
			assertThat(result).isNotNull();
			assertThat(result.getOutput()).hasSize(dimensions);
		});
		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo(model);
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(14);
		assertThat(embeddingResponse.getMetadata().getUsage().getPromptTokens()).isEqualTo(14);
		assertThat(this.mistralAiEmbeddingModel.dimensions()).isEqualTo(MISTRAL_EMBED_DIMENSIONS);
	}

}
