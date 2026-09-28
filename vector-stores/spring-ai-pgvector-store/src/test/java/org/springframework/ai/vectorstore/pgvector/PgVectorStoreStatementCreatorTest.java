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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgVectorStoreStatementCreatorTest {

	EmbeddingModel embeddingModel = Mockito.mock(EmbeddingModel.class);

	PgVectorStoreStatementCreator creator = new PgVectorStoreStatementCreator(
			PgVectorStore.PgDistanceType.COSINE_DISTANCE, "vector", "public", this.embeddingModel,
			PgVectorStore.PgIdType.TEXT, new TokenCountBatchingStrategy(), 20,
			JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build());

	@Test
	void similaritySearchDoublesSingleQuotesInsideJsonPathSqlLiteral() throws SQLException {
		var jdbcTemplate = mock(JdbcTemplate.class);
		when(this.embeddingModel.dimensions()).thenReturn(3);
		when(this.embeddingModel.embed(anyString())).thenReturn(new float[] { 0.1f, 0.2f, 0.3f });
		when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<Document>>any(), any(), any(), any(), any()))
			.thenReturn(List.of());

		var expression = new FilterExpressionTextParser().parse("\"O'Brien\" == 'x'");
		var request = SearchRequest.builder()
			.query("hello")
			.topK(5)
			.similarityThresholdAll()
			.filterExpression(expression)
			.build();
		PreparedStatementCreator preparedStatementCreator = this.creator.similaritySearchStatement(request);

		Connection connection = mock(Connection.class);
		when(connection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));

		preparedStatementCreator.createPreparedStatement(connection);

		verify(connection, times(1)).prepareStatement(contains("metadata::jsonb @@ '"));
		verify(connection, times(1)).prepareStatement(contains("O''Brien"));
	}

	@Test
	void deleteByFilterDoublesSingleQuotesWhenMetadataKeyContainsApostrophe() throws SQLException {
		var jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.update(anyString())).thenReturn(1);

		var expression = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("O'Brien"),
				new Filter.Value("n"));

		PreparedStatementCreator preparedStatementCreator = this.creator.deleteStatement(expression);

		Connection connection = mock(Connection.class);
		when(connection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));

		preparedStatementCreator.createPreparedStatement(connection);

		verify(connection, times(1)).prepareStatement(contains("$.\"" + "O''Brien\" == \"n\""));
		verify(connection, times(1)).prepareStatement(contains("O''Brien"));
	}

	@Test
	void deleteByFilterDoublesSingleQuotesWhenStringValueContainsApostrophe() throws SQLException {
		var jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.update(anyString())).thenReturn(1);

		var expression = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("author"),
				new Filter.Value("O'Connor"));

		PreparedStatementCreator preparedStatementCreator = this.creator.deleteStatement(expression);

		Connection connection = mock(Connection.class);
		when(connection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));

		preparedStatementCreator.createPreparedStatement(connection);

		verify(connection, times(1)).prepareStatement(contains("O''Connor"));
	}

	@Test
	void deleteByFilterFromTextParserDoublesSingleQuotesForQuotedKeyWithApostrophe() throws SQLException {
		var jdbcTemplate = mock(JdbcTemplate.class);
		when(jdbcTemplate.update(anyString())).thenReturn(1);

		var expression = new FilterExpressionTextParser().parse("\"vendor\" == \"ACME's\"");

		PreparedStatementCreator preparedStatementCreator = this.creator.deleteStatement(expression);

		Connection connection = mock(Connection.class);
		when(connection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));

		preparedStatementCreator.createPreparedStatement(connection);

		verify(connection, times(1)).prepareStatement(contains("ACME''s"));
	}

}
