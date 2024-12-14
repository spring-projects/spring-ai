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

package org.springframework.ai.vectorstore.qdrant;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link QdrantVectorStore.QdrantBuilder}.
 *
 * @author Mark Pollack
 */
class QdrantVectorStoreBuilderTests {

	private QdrantClient qdrantClient;

	private EmbeddingModel embeddingModel;

	@BeforeEach
	void setUp() {
		this.qdrantClient = mock(QdrantClient.class);
		this.embeddingModel = mock(EmbeddingModel.class);
	}

	@Test
	void defaultConfiguration() {
		QdrantVectorStore vectorStore = QdrantVectorStore.builder(qdrantClient).embeddingModel(embeddingModel).build();

		// Verify default values
		assertThat(vectorStore).hasFieldOrPropertyWithValue("collectionName", "vector_store");
		assertThat(vectorStore).hasFieldOrPropertyWithValue("initializeSchema", false);
		assertThat(vectorStore).hasFieldOrPropertyWithValue("batchingStrategy.class", TokenCountBatchingStrategy.class);
	}

	@Test
	void customConfiguration() {
		QdrantVectorStore vectorStore = QdrantVectorStore.builder(qdrantClient)
			.embeddingModel(embeddingModel)
			.collectionName("custom_collection")
			.initializeSchema(true)
			.batchingStrategy(new TokenCountBatchingStrategy())
			.build();

		assertThat(vectorStore).hasFieldOrPropertyWithValue("collectionName", "custom_collection");
		assertThat(vectorStore).hasFieldOrPropertyWithValue("initializeSchema", true);
		assertThat(vectorStore).hasFieldOrPropertyWithValue("batchingStrategy.class", TokenCountBatchingStrategy.class);
	}

	@Test
	void nullQdrantClientInConstructorShouldThrowException() {
		assertThatThrownBy(() -> QdrantVectorStore.builder(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("QdrantClient must not be null");
	}

	@Test
	void nullEmbeddingModelShouldThrowException() {
		assertThatThrownBy(() -> QdrantVectorStore.builder(qdrantClient).embeddingModel(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("EmbeddingModel must not be null");
	}

	@Test
	void emptyCollectionNameShouldThrowException() {
		assertThatThrownBy(
				() -> QdrantVectorStore.builder(qdrantClient).embeddingModel(embeddingModel).collectionName("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("collectionName must not be empty");
	}

	@Test
	void nullBatchingStrategyShouldThrowException() {
		assertThatThrownBy(() -> QdrantVectorStore.builder(qdrantClient)
			.embeddingModel(embeddingModel)
			.batchingStrategy(null)
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessage("BatchingStrategy must not be null");
	}

}
