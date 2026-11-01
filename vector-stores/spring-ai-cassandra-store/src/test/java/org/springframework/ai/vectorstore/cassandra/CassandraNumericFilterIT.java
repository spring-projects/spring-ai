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

package org.springframework.ai.vectorstore.cassandra;

import java.util.List;
import java.util.Map;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.type.DataTypes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.cassandra.CassandraVectorStore.SchemaColumn;
import org.springframework.ai.vectorstore.cassandra.CassandraVectorStore.SchemaColumnTags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CassandraNumericFilterIT {

	@Container
	static CassandraContainer cassandraContainer = new CassandraContainer(CassandraImage.DEFAULT_IMAGE);

	private CqlSession session;

	private CassandraVectorStore store;

	@BeforeAll
	void setUp() {
		this.session = CqlSession.builder()
			.addContactPoint(cassandraContainer.getContactPoint())
			.withLocalDatacenter(cassandraContainer.getLocalDatacenter())
			.build();

		// Identical embeddings make these tests depend on filtering, not model output.
		float[] vector = { 1.0f, 0.0f, 0.0f };
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(embeddingModel.dimensions()).thenReturn(vector.length);
		when(embeddingModel.embed(anyString())).thenReturn(vector);
		when(embeddingModel.embed(anyList(), any(EmbeddingOptions.class), any(BatchingStrategy.class)))
			.thenReturn(List.of(vector, vector, vector));

		this.store = CassandraVectorStore.builder(embeddingModel)
			.session(this.session)
			.keyspace("test_numeric_filters")
			.addMetadataColumns(new SchemaColumn("count", DataTypes.BIGINT, SchemaColumnTags.INDEXED),
					new SchemaColumn("rating", DataTypes.FLOAT, SchemaColumnTags.INDEXED))
			.build();

		this.store.add(List.of(new Document("A", "Java introduction", Map.of("count", 42L, "rating", 4.5f)),
				new Document("B", "Java introduction", Map.of("count", 100L, "rating", 3.5f)),
				new Document("C", "Java introduction", Map.of("count", 2147483648L, "rating", 2.5f))));
	}

	@AfterAll
	void tearDown() throws Exception {
		try {
			if (this.store != null) {
				this.store.close();
			}
		}
		finally {
			if (this.session != null) {
				this.session.close();
			}
		}
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "count == 42 | A", "count == 2147483648 | C", "rating >= 4.5 | A",
			"count == 999 | ''", "rating > 5.0 | ''" })
	void searchWithNumericFilter(String filterExpression, String expectedIds) {
		var results = this.store.similaritySearch(SearchRequest.builder()
			.query("Java introduction")
			.filterExpression(filterExpression)
			.topK(10)
			.similarityThresholdAll()
			.build());

		assertThat(results).extracting(Document::getId)
			.containsExactlyInAnyOrder(expectedIds.isEmpty() ? new String[0] : expectedIds.split(","));
	}

}
