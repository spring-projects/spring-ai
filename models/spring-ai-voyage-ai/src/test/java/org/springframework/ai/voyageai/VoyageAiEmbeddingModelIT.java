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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.voyageai.api.VoyageAiApi;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link VoyageAiEmbeddingModel}.
 *
 * @author Spring AI
 */
@EnabledIfEnvironmentVariable(named = "VOYAGE_API_KEY", matches = ".+")
class VoyageAiEmbeddingModelIT {

	private final VoyageAiEmbeddingModel embeddingModel = VoyageAiEmbeddingModel.builder()
		.voyageAiApi(VoyageAiApi.builder().apiKey(System.getenv("VOYAGE_API_KEY")).build())
		.options(VoyageAiEmbeddingOptions.builder()
			.model(VoyageAiEmbeddingModelName.VOYAGE_3_5.getName())
			.inputType("document")
			.build())
		.build();

	@Test
	void embedSingleText() {
		EmbeddingResponse response = this.embeddingModel.embedForResponse(List.of("Hello World"));

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(response.getMetadata().getModel()).containsIgnoringCase("voyage");
	}

	@Test
	void embedMultipleTexts() {
		EmbeddingResponse response = this.embeddingModel
			.call(new EmbeddingRequest(List.of("Hello World", "Spring AI rocks!"), null));

		assertThat(response.getResults()).hasSize(2);
		assertThat(this.embeddingModel.dimensions()).isEqualTo(1024);
	}

}
