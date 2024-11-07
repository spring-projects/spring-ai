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

package org.springframework.ai.oci;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = org.springframework.ai.oci.BaseEmbeddingModelTest.OCI_COMPARTMENT_ID_KEY,
		matches = ".+")
class OCIEmbeddingModelIT extends BaseEmbeddingModelTest {

	private final OCIEmbeddingModel embeddingModel = getEmbeddingModel();

	private final List<String> content = List.of("How many states are in the USA?", "How many states are in India?");

	@Test
	void embed() {
		float[] embedding = this.embeddingModel.embed(new Document("How many provinces are in Canada?"));
		assertThat(embedding).hasSize(1024);
	}

	@Test
	void call() {
		EmbeddingResponse response = this.embeddingModel.call(new EmbeddingRequest(this.content, null));
		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(2);
		assertThat(response.getMetadata().getModel()).isEqualTo(EMBEDDING_MODEL_V2);
	}

	@Test
	void callWithOptions() {
		EmbeddingResponse response = this.embeddingModel.call(new EmbeddingRequest(this.content,
				OCIEmbeddingOptions.builder().withModel(EMBEDDING_MODEL_V3).build()));
		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(2);
		assertThat(response.getMetadata().getModel()).isEqualTo(EMBEDDING_MODEL_V3);
	}

}
