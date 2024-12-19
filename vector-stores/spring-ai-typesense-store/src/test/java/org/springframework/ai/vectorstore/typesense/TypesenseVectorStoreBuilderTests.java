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

package org.springframework.ai.vectorstore.typesense;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.typesense.api.Client;
import org.typesense.api.Configuration;
import org.typesense.resources.Node;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link TypesenseVectorStore.TypesenseBuilder}.
 *
 * @author Mark Pollack
 */
class TypesenseVectorStoreBuilderTests {

	private final Client client;

	private final EmbeddingModel embeddingModel;

	TypesenseVectorStoreBuilderTests() {
		List<Node> nodes = new ArrayList<>();
		nodes.add(new Node("http", "localhost", "8108"));
		this.client = new Client(new Configuration(nodes, Duration.ofSeconds(5), "xyz"));
		this.embeddingModel = mock(EmbeddingModel.class);
	}

	@Test
	void defaultConfiguration() {
		TypesenseVectorStore vectorStore = TypesenseVectorStore.builder(client, embeddingModel).build();

		// Verify default values
		assertThat(vectorStore).hasFieldOrPropertyWithValue("collectionName", "vector_store");
		assertThat(vectorStore).hasFieldOrPropertyWithValue("embeddingDimension", -1);
		assertThat(vectorStore).hasFieldOrPropertyWithValue("initializeSchema", false);
		assertThat(vectorStore).hasFieldOrPropertyWithValue("batchingStrategy.class", TokenCountBatchingStrategy.class);
	}

	@Test
	void customConfiguration() {
		TypesenseVectorStore vectorStore = TypesenseVectorStore.builder(client, embeddingModel)
			.collectionName("custom_collection")
			.embeddingDimension(1536)
			.initializeSchema(true)
			.build();

		assertThat(vectorStore).hasFieldOrPropertyWithValue("collectionName", "custom_collection");
		assertThat(vectorStore).hasFieldOrPropertyWithValue("embeddingDimension", 1536);
		assertThat(vectorStore).hasFieldOrPropertyWithValue("initializeSchema", true);
	}

	@Test
	void nullClientShouldThrowException() {
		assertThatThrownBy(() -> TypesenseVectorStore.builder(null, embeddingModel).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("client must not be null");
	}

	@Test
	void nullEmbeddingModelShouldThrowException() {
		assertThatThrownBy(() -> TypesenseVectorStore.builder(client, null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("EmbeddingModel must be configured");
	}

	@Test
	void invalidEmbeddingDimensionShouldThrowException() {
		assertThatThrownBy(() -> TypesenseVectorStore.builder(client, embeddingModel).embeddingDimension(0).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Embedding dimension must be greater than 0");
	}

	@Test
	void emptyCollectionNameShouldThrowException() {
		assertThatThrownBy(() -> TypesenseVectorStore.builder(client, embeddingModel).collectionName("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("collectionName must not be empty");
	}

	@Test
	void nullBatchingStrategyShouldThrowException() {
		assertThatThrownBy(() -> TypesenseVectorStore.builder(client, embeddingModel).batchingStrategy(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("batchingStrategy must not be null");
	}

}
