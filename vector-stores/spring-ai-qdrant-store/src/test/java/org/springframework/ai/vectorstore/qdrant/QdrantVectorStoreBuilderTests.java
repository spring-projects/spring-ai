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
		assertThat(vectorStore).hasFieldOrPropertyWithValue("contentFieldName", "doc_content");
		assertThat(vectorStore).hasFieldOrPropertyWithValue("initializeSchema", false);
		assertThat(vectorStore).hasFieldOrPropertyWithValue("batchingStrategy.class", TokenCountBatchingStrategy.class);
	}

	@Test
	void customConfiguration() {
		QdrantVectorStore vectorStore = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel)
			.collectionName("custom_collection")
			.contentFieldName("custom_content_field")
			.initializeSchema(true)
			.batchingStrategy(new TokenCountBatchingStrategy())
			.build();

		assertThat(vectorStore).hasFieldOrPropertyWithValue("collectionName", "custom_collection");
		assertThat(vectorStore).hasFieldOrPropertyWithValue("contentFieldName", "custom_content_field");
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

	@Test
	void builderShouldPreserveMockedDependencies() {
		QdrantVectorStore vectorStore = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel).build();

		assertThat(vectorStore).hasFieldOrPropertyWithValue("qdrantClient", this.qdrantClient);
		assertThat(vectorStore).hasFieldOrPropertyWithValue("embeddingModel", this.embeddingModel);
	}

	@Test
	void builderShouldCreateImmutableConfiguration() {
		QdrantVectorStore.Builder builder = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel)
			.collectionName("test_collection")
			.initializeSchema(true);

		QdrantVectorStore vectorStore1 = builder.build();

		// Modify builder after first build
		builder.collectionName("different_collection").initializeSchema(false);
		QdrantVectorStore vectorStore2 = builder.build();

		// First vector store should remain unchanged
		assertThat(vectorStore1).hasFieldOrPropertyWithValue("collectionName", "test_collection");
		assertThat(vectorStore1).hasFieldOrPropertyWithValue("initializeSchema", true);

		// Second vector store should have new values
		assertThat(vectorStore2).hasFieldOrPropertyWithValue("collectionName", "different_collection");
		assertThat(vectorStore2).hasFieldOrPropertyWithValue("initializeSchema", false);
	}

	@Test
	void builderShouldHandleNullQdrantClientCorrectly() {
		assertThatThrownBy(() -> QdrantVectorStore.builder(null, this.embeddingModel))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("QdrantClient must not be null");
	}

	@Test
	void builderShouldValidateConfigurationOnBuild() {
		QdrantVectorStore.Builder builder = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel);

		// Should succeed with valid configuration
		assertThat(builder.build()).isNotNull();

		// Should fail when trying to build with invalid configuration set later
		assertThatThrownBy(() -> builder.collectionName("").build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("collectionName must not be empty");
	}

	@Test
	void builderShouldRetainLastSetBatchingStrategy() {
		TokenCountBatchingStrategy strategy1 = new TokenCountBatchingStrategy();
		TokenCountBatchingStrategy strategy2 = new TokenCountBatchingStrategy();
		TokenCountBatchingStrategy strategy3 = new TokenCountBatchingStrategy();

		QdrantVectorStore vectorStore = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel)
			.batchingStrategy(strategy1)
			.batchingStrategy(strategy2)
			.batchingStrategy(strategy3)
			.build();

		assertThat(vectorStore).hasFieldOrPropertyWithValue("batchingStrategy", strategy3);
	}

	@Test
	void builderShouldHandleCollectionNameEdgeCases() {
		// Test single character collection name
		QdrantVectorStore vectorStore1 = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel)
			.collectionName("a")
			.build();
		assertThat(vectorStore1).hasFieldOrPropertyWithValue("collectionName", "a");

		// Test collection name with numbers only
		QdrantVectorStore vectorStore2 = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel)
			.collectionName("12345")
			.build();
		assertThat(vectorStore2).hasFieldOrPropertyWithValue("collectionName", "12345");

		// Test collection name starting with number
		QdrantVectorStore vectorStore3 = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel)
			.collectionName("1collection")
			.build();
		assertThat(vectorStore3).hasFieldOrPropertyWithValue("collectionName", "1collection");
	}

	@Test
	void builderShouldMaintainBuilderPattern() {
		QdrantVectorStore.Builder builder = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel);

		// Each method should return the builder for chaining
		QdrantVectorStore.Builder result = builder.collectionName("test")
			.initializeSchema(true)
			.batchingStrategy(new TokenCountBatchingStrategy());

		assertThat(result).isSameAs(builder);
	}

	@Test
	void builderShouldHandleRepeatedConfigurationCalls() {
		QdrantVectorStore.Builder builder = QdrantVectorStore.builder(this.qdrantClient, this.embeddingModel);

		// Call configuration methods multiple times in different orders
		builder.initializeSchema(true)
			.collectionName("first")
			.initializeSchema(false)
			.collectionName("second")
			.initializeSchema(true);

		QdrantVectorStore vectorStore = builder.build();

		// Should use the last set values
		assertThat(vectorStore).hasFieldOrPropertyWithValue("collectionName", "second");
		assertThat(vectorStore).hasFieldOrPropertyWithValue("initializeSchema", true);

		// Verify builder can still be used after build
		QdrantVectorStore anotherVectorStore = builder.collectionName("third").build();
		assertThat(anotherVectorStore).hasFieldOrPropertyWithValue("collectionName", "third");
		assertThat(anotherVectorStore).hasFieldOrPropertyWithValue("initializeSchema", true);
	}

}
