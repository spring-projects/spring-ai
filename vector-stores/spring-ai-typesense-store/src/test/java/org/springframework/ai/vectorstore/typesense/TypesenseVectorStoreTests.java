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

package org.springframework.ai.vectorstore.typesense;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TypesenseVectorStore}.
 *
 * @author chabinhwang
 */
class TypesenseVectorStoreTests {

	@Test
	void buildVectorQuery() {
		String vectorQuery = TypesenseVectorStore.buildVectorQuery(new float[] { 0.1f, -2.5f, 3.0E-4f }, 7, 0.75);

		assertThat(vectorQuery).isEqualTo("embedding:([0.1,-2.5,3.0E-4], k: 7, distance_threshold: 0.25)");
	}

	@Test
	void buildVectorQueryWithEmptyEmbedding() {
		String vectorQuery = TypesenseVectorStore.buildVectorQuery(new float[0], 3, 0.5);

		assertThat(vectorQuery).isEqualTo("embedding:([], k: 3, distance_threshold: 0.5)");
	}

	@Test
	void buildVectorQueryWithSpecialValues() {
		String vectorQuery = TypesenseVectorStore
			.buildVectorQuery(new float[] { -0.0f, Float.MIN_VALUE, Float.MAX_VALUE }, 1, 0.0);

		assertThat(vectorQuery).isEqualTo("embedding:([-0.0,1.4E-45,3.4028235E38], k: 1, distance_threshold: 1.0)");
	}

	@Test
	void buildVectorQueryKeepsEveryEmbeddingValue() {
		float[] embedding = new float[TypesenseVectorStore.OPENAI_EMBEDDING_DIMENSION_SIZE];
		for (int i = 0; i < embedding.length; i++) {
			embedding[i] = i / 1000.0f;
		}

		String vectorQuery = TypesenseVectorStore.buildVectorQuery(embedding, 10, 0.75);

		assertThat(vectorQuery).startsWith("embedding:([0.0,0.001,0.002,")
			.endsWith(",1.535], k: 10, distance_threshold: 0.25)");
		assertThat(vectorQuery.chars().filter(c -> c == ',').count()).isEqualTo(embedding.length + 1);
	}

}
