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

package org.springframework.ai.vectorstore.pgvector;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test to demonstrate the schema quoting fix for GitHub issue #4130. Verifies that
 * hyphenated schema names are properly quoted in SQL generation.
 *
 * @author Claude Code Assistant
 */
public class PgVectorStoreSchemaQuotingTest {

	/**
	 * Verifies that hyphenated schema names (issue #4130) are properly quoted in SQL
	 * identifiers.
	 */
	@Test
	public void shouldProperlyQuoteHyphenatedSchemaNames() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(embeddingModel.dimensions()).thenReturn(1536);

		// Create PgVectorStore with the problematic hyphenated schema from issue #4130
		PgVectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
			.schemaName("demo-1998")
			.vectorTableName("vector_store")
			.initializeSchema(false)
			.build();

		// Access the private method to verify quoting behavior
		Method getFullyQualifiedTableNameMethod = PgVectorStore.class.getDeclaredMethod("getFullyQualifiedTableName");
		getFullyQualifiedTableNameMethod.setAccessible(true);
		String fullyQualifiedTableName = (String) getFullyQualifiedTableNameMethod.invoke(vectorStore);

		// Verify proper PostgreSQL identifier quoting
		assertThat(fullyQualifiedTableName).isEqualTo("\"demo-1998\".\"vector_store\"");
	}

	/**
	 * Verifies that similarity search generates properly quoted SQL for hyphenated
	 * schemas.
	 */
	@Test
	public void shouldGenerateQuotedSQLInSimilaritySearch() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

		when(embeddingModel.dimensions()).thenReturn(1536);
		when(embeddingModel.embed(anyString())).thenReturn(new float[] { 1.0f, 2.0f, 3.0f });
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any()))
			.thenReturn(List.of(Document.builder().id("1").text("test").metadata(Map.of("distance", 0.5)).build()));

		PgVectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
			.schemaName("demo-1998")
			.vectorTableName("vector_store")
			.initializeSchema(false)
			.build();

		// Execute similarity search
		vectorStore.doSimilaritySearch(SearchRequest.builder().query("test").topK(5).build());

		// Verify the generated SQL contains properly quoted identifiers
		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(), any(), any(), any());

		String generatedSQL = sqlCaptor.getValue();
		assertThat(generatedSQL).contains("\"demo-1998\".\"vector_store\"");
	}

}