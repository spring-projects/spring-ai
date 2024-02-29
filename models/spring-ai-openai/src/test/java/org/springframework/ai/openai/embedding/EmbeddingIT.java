/*
 * Copyright 2023-2023 the original author or authors.
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
package org.springframework.ai.openai.embedding;

import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingClient;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EmbeddingIT {

	@Autowired
	private OpenAiEmbeddingClient embeddingClient;

	@Test
	void defaultEmbedding() {
		assertThat(embeddingClient).isNotNull();

		EmbeddingResponse embeddingResponse = embeddingClient.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1536);
		assertThat(embeddingResponse.getMetadata()).containsEntry("model", "text-embedding-ada-002");
		assertThat(embeddingResponse.getMetadata()).containsEntry("total-tokens", 2);
		assertThat(embeddingResponse.getMetadata()).containsEntry("prompt-tokens", 2);

		assertThat(embeddingClient.dimensions()).isEqualTo(1536);
	}

	@Test
	void embedding3Large() {

		EmbeddingResponse embeddingResponse = embeddingClient.call(new EmbeddingRequest(List.of("Hello World"),
				OpenAiEmbeddingOptions.builder().withModel("text-embedding-3-large").build()));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(3072);
		assertThat(embeddingResponse.getMetadata()).containsEntry("model", "text-embedding-3-large");
		assertThat(embeddingResponse.getMetadata()).containsEntry("total-tokens", 2);
		assertThat(embeddingResponse.getMetadata()).containsEntry("prompt-tokens", 2);

		// assertThat(embeddingClient.dimensions()).isEqualTo(3072);
	}

	@Test
	void textEmbeddingAda002() {

		EmbeddingResponse embeddingResponse = embeddingClient.call(new EmbeddingRequest(List.of("Hello World"),
				OpenAiEmbeddingOptions.builder().withModel("text-embedding-3-small").build()));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1536);

		assertThat(embeddingResponse.getMetadata()).containsEntry("model", "text-embedding-3-small");
		assertThat(embeddingResponse.getMetadata()).containsEntry("total-tokens", 2);
		assertThat(embeddingResponse.getMetadata()).containsEntry("prompt-tokens", 2);

		// assertThat(embeddingClient.dimensions()).isEqualTo(3072);
	}

}
