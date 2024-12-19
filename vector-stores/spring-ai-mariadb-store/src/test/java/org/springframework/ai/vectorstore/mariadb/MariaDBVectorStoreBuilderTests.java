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

package org.springframework.ai.vectorstore.mariadb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore.MariaDBDistanceType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit tests for {@link MariaDBVectorStore.MariaDBBuilder}.
 *
 * @author Mark Pollack
 */
class MariaDBVectorStoreBuilderTests {

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

	private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

	@Test
	void shouldFailOnMissingEmbeddingModel() {
		assertThatThrownBy(() -> MariaDBVectorStore.builder(jdbcTemplate, null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("EmbeddingModel must be configured");
	}

	@Test
	void shouldFailOnMissingJdbcTemplate() {
		assertThatThrownBy(() -> MariaDBVectorStore.builder(null, embeddingModel).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("JdbcTemplate must not be null");
	}

	@Test
	void shouldUseDefaultValues() {
		MariaDBVectorStore vectorStore = MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).build();

		assertThat(vectorStore).hasFieldOrPropertyWithValue("vectorTableName", "vector_store")
			.hasFieldOrPropertyWithValue("schemaName", null)
			.hasFieldOrPropertyWithValue("schemaValidation", false)
			.hasFieldOrPropertyWithValue("dimensions", -1)
			.hasFieldOrPropertyWithValue("distanceType", MariaDBDistanceType.COSINE)
			.hasFieldOrPropertyWithValue("removeExistingVectorStoreTable", false)
			.hasFieldOrPropertyWithValue("initializeSchema", false)
			.hasFieldOrPropertyWithValue("maxDocumentBatchSize", 10000)
			.hasFieldOrPropertyWithValue("contentFieldName", "content")
			.hasFieldOrPropertyWithValue("embeddingFieldName", "embedding")
			.hasFieldOrPropertyWithValue("idFieldName", "id")
			.hasFieldOrPropertyWithValue("metadataFieldName", "metadata");
	}

	@Test
	void shouldConfigureCustomValues() {
		MariaDBVectorStore vectorStore = MariaDBVectorStore.builder(jdbcTemplate, embeddingModel)
			.schemaName("custom_schema")
			.vectorTableName("custom_vectors")
			.schemaValidation(true)
			.dimensions(512)
			.distanceType(MariaDBDistanceType.EUCLIDEAN)
			.removeExistingVectorStoreTable(true)
			.initializeSchema(true)
			.maxDocumentBatchSize(5000)
			.contentFieldName("text")
			.embeddingFieldName("vector")
			.idFieldName("doc_id")
			.metadataFieldName("meta")
			.build();

		assertThat(vectorStore).hasFieldOrPropertyWithValue("vectorTableName", "custom_vectors")
			.hasFieldOrPropertyWithValue("schemaName", "custom_schema")
			.hasFieldOrPropertyWithValue("schemaValidation", true)
			.hasFieldOrPropertyWithValue("dimensions", 512)
			.hasFieldOrPropertyWithValue("distanceType", MariaDBDistanceType.EUCLIDEAN)
			.hasFieldOrPropertyWithValue("removeExistingVectorStoreTable", true)
			.hasFieldOrPropertyWithValue("initializeSchema", true)
			.hasFieldOrPropertyWithValue("maxDocumentBatchSize", 5000)
			.hasFieldOrPropertyWithValue("contentFieldName", "text")
			.hasFieldOrPropertyWithValue("embeddingFieldName", "vector")
			.hasFieldOrPropertyWithValue("idFieldName", "doc_id")
			.hasFieldOrPropertyWithValue("metadataFieldName", "meta");
	}

	@Test
	void shouldValidateFieldNames() {
		assertThatThrownBy(() -> MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).contentFieldName("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("ContentFieldName must not be empty");

		assertThatThrownBy(
				() -> MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).embeddingFieldName("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("EmbeddingFieldName must not be empty");

		assertThatThrownBy(() -> MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).idFieldName("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("IdFieldName must not be empty");

		assertThatThrownBy(() -> MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).metadataFieldName("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("MetadataFieldName must not be empty");
	}

	@Test
	void shouldValidateMaxDocumentBatchSize() {
		assertThatThrownBy(
				() -> MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).maxDocumentBatchSize(0).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("MaxDocumentBatchSize must be positive");

		assertThatThrownBy(
				() -> MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).maxDocumentBatchSize(-1).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("MaxDocumentBatchSize must be positive");
	}

	@Test
	void shouldValidateDistanceType() {
		assertThatThrownBy(() -> MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).distanceType(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("DistanceType must not be null");
	}

	@Test
	void shouldValidateBatchingStrategy() {
		assertThatThrownBy(
				() -> MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).batchingStrategy(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("BatchingStrategy must not be null");
	}

}
