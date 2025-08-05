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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link QdrantVectorStore.Builder}.
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
		QdrantVectorStore vectorStore = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel).build();

		// Verify default values
		assertThat(vectorStore).hasFieldOrPropertyWithValue("collectionName", "vector_store");
		assertThat(vectorStore).hasFieldOrPropertyWithValue("initializeSchema", false);
		assertThat(vectorStore).hasFieldOrPropertyWithValue("batchingStrategy.class", TokenCountBatchingStrategy.class);
	}

	@Test
	void customConfiguration() {
		QdrantVectorStore vectorStore = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel)
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
		assertThatThrownBy(() -> QdrantVectorStore.builder(null, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("EmbeddingModel must be configured");
	}

	@Test
	void nullEmbeddingModelShouldThrowException() {
		assertThatThrownBy(() -> QdrantVectorStore.builder(this.qdrantClient, null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("EmbeddingModel must be configured");
	}

	@Test
	void emptyCollectionNameShouldThrowException() {
		assertThatThrownBy(
				() -> QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel).collectionName("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("collectionName must not be empty");
	}

	@Test
	void nullBatchingStrategyShouldThrowException() {
		assertThatThrownBy(
				() -> QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel).batchingStrategy(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("BatchingStrategy must not be null");
	}

	@Test
	void nullCollectionNameShouldThrowException() {
		assertThatThrownBy(
				() -> QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel).collectionName(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("collectionName must not be empty");
	}

	@Test
	void whitespaceOnlyCollectionNameShouldThrowException() {
		assertThatThrownBy(
				() -> QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel).collectionName("   ").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("collectionName must not be empty");
	}

	@Test
	void builderShouldReturnNewInstanceOnEachBuild() {
		QdrantVectorStore.Builder builder = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel);

		QdrantVectorStore vectorStore1 = builder.build();
		QdrantVectorStore vectorStore2 = builder.build();

		assertThat(vectorStore1).isNotSameAs(vectorStore2);
	}

	@Test
	void builderShouldAllowMethodChaining() {
		QdrantVectorStore vectorStore = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel)
			.collectionName("test_collection")
			.initializeSchema(true)
			.batchingStrategy(new TokenCountBatchingStrategy())
			.build();

		assertThat(vectorStore).isNotNull();
		assertThat(vectorStore).hasFieldOrPropertyWithValue("collectionName", "test_collection");
		assertThat(vectorStore).hasFieldOrPropertyWithValue("initializeSchema", true);
	}

	@Test
	void builderShouldMaintainStateAcrossMultipleCalls() {
		QdrantVectorStore.Builder builder = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel)
			.collectionName("persistent_collection");

		QdrantVectorStore vectorStore1 = builder.build();
		QdrantVectorStore vectorStore2 = builder.initializeSchema(true).build();

		// Both should have the same collection name
		assertThat(vectorStore1).hasFieldOrPropertyWithValue("collectionName", "persistent_collection");
		assertThat(vectorStore2).hasFieldOrPropertyWithValue("collectionName", "persistent_collection");

		// But different initializeSchema values
		assertThat(vectorStore1).hasFieldOrPropertyWithValue("initializeSchema", false);
		assertThat(vectorStore2).hasFieldOrPropertyWithValue("initializeSchema", true);
	}

	@Test
	void builderShouldOverridePreviousValues() {
		QdrantVectorStore vectorStore = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel)
			.collectionName("first_collection")
			.collectionName("second_collection")
			.initializeSchema(true)
			.initializeSchema(false)
			.build();

		assertThat(vectorStore).hasFieldOrPropertyWithValue("collectionName", "second_collection");
		assertThat(vectorStore).hasFieldOrPropertyWithValue("initializeSchema", false);
	}

	@Test
	void builderWithMinimalConfiguration() {
		QdrantVectorStore vectorStore = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel).build();

		assertThat(vectorStore).isNotNull();
		// Should use default values
		assertThat(vectorStore).hasFieldOrPropertyWithValue("collectionName", "vector_store");
		assertThat(vectorStore).hasFieldOrPropertyWithValue("initializeSchema", false);
	}

	@Test
	void builderWithDifferentBatchingStrategies() {
		TokenCountBatchingStrategy strategy1 = new TokenCountBatchingStrategy();
		TokenCountBatchingStrategy strategy2 = new TokenCountBatchingStrategy();

		QdrantVectorStore vectorStore1 = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel)
			.batchingStrategy(strategy1)
			.build();

		QdrantVectorStore vectorStore2 = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel)
			.batchingStrategy(strategy2)
			.build();

		assertThat(vectorStore1).hasFieldOrPropertyWithValue("batchingStrategy", strategy1);
		assertThat(vectorStore2).hasFieldOrPropertyWithValue("batchingStrategy", strategy2);
	}

	@Test
	void builderShouldAcceptValidCollectionNames() {
		String[] validNames = { "collection_with_underscores", "collection-with-dashes", "collection123", "Collection",
				"c", "very_long_collection_name_that_should_still_be_valid_according_to_most_naming_conventions" };

		for (String name : validNames) {
			QdrantVectorStore vectorStore = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel)
				.collectionName(name)
				.build();

			assertThat(vectorStore).hasFieldOrPropertyWithValue("collectionName", name);
		}
	}

	@Test
	void builderStateShouldBeIndependentBetweenInstances() {
		QdrantVectorStore.Builder builder1 = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel)
			.collectionName("collection1");

		QdrantVectorStore.Builder builder2 = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel)
			.collectionName("collection2");

		QdrantVectorStore vectorStore1 = builder1.build();
		QdrantVectorStore vectorStore2 = builder2.build();

		assertThat(vectorStore1).hasFieldOrPropertyWithValue("collectionName", "collection1");
		assertThat(vectorStore2).hasFieldOrPropertyWithValue("collectionName", "collection2");
	}

	@Test
	void builderShouldHandleBooleanToggling() {
		QdrantVectorStore.Builder builder = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel);

		// Test toggling initializeSchema
		QdrantVectorStore vectorStore1 = builder.initializeSchema(true).build();
		QdrantVectorStore vectorStore2 = builder.initializeSchema(false).build();
		QdrantVectorStore vectorStore3 = builder.initializeSchema(true).build();

		assertThat(vectorStore1).hasFieldOrPropertyWithValue("initializeSchema", true);
		assertThat(vectorStore2).hasFieldOrPropertyWithValue("initializeSchema", false);
		assertThat(vectorStore3).hasFieldOrPropertyWithValue("initializeSchema", true);
	}

}
