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

package org.springframework.ai.vectorstore.pgvector;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PgVectorStore} configurable distance type behavior.
 *
 * @author Spring AI Team
 */
class PgVectorStoreDistanceTypeTests {

	public static final PgDistanceType CUSTOM_DISTANCE_TYPE = new PgDistanceType("CUSTOM", "<custom>", "custom_ops",
			"SELECT CUSTOM ?");
	private final FilterExpressionTextParser filterParser = new FilterExpressionTextParser();

	@Test
	void shouldUseCustomDistanceType() {
		// Given
		var jdbcTemplate = mock(JdbcTemplate.class);
		var embeddingModel = mock(EmbeddingModel.class);

		// When
		var vectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
				.distanceType(CUSTOM_DISTANCE_TYPE)
				.build();

		// Then
		assertThat(vectorStore.getDistanceType()).isEqualTo(CUSTOM_DISTANCE_TYPE);
		assertThat(vectorStore.getDistanceType().operator()).isEqualTo("<custom>");
		assertThat(vectorStore.getDistanceType().index()).isEqualTo("custom_ops");
		assertThat(vectorStore.getDistanceType().similaritySearchSqlTemplate()).isEqualTo("SELECT CUSTOM ?");
	}

	@Test
	void similaritySearchShouldUseConfiguredDistanceTypeOperator() {
		// Given
		var jdbcTemplate = mock(JdbcTemplate.class);
		var embeddingModel = mock(EmbeddingModel.class);
		when(embeddingModel.dimensions()).thenReturn(3);
		when(embeddingModel.embed(anyString())).thenReturn(new float[] { 0.1f, 0.2f, 0.3f });
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any()))
				.thenReturn(List.of());

		var vectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
				.distanceType(CUSTOM_DISTANCE_TYPE)
				.initializeSchema(false)
				.build();

		var request = SearchRequest.builder()
				.query("test query")
				.topK(5)
				.similarityThresholdAll()
				.build();

		// When
		vectorStore.doSimilaritySearch(request);

		// Then
		var sqlCaptor = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(), any(), any(), any());
		String sql = sqlCaptor.getValue();

		// Verify that the custom distance operator is used in the SQL
		assertThat(sql).contains("SELECT CUSTOM ?");
		assertThat(sql).doesNotContain("<->");
		assertThat(sql).doesNotContain("<=>");
		assertThat(sql).doesNotContain("<#>");
	}

}
