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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.IndexMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.cassandra.CassandraVectorStore.SchemaColumn;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CassandraVectorStoreTests {

	private final CqlSession session = mock(CqlSession.class);

	private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

	private final PreparedStatement deleteStatement = mock(PreparedStatement.class);

	private final Map<String, ColumnMetadata> columns = new LinkedHashMap<>();

	private final List<List<Object>> deletedKeys = new ArrayList<>();

	private SimpleStatement selectStatement;

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 1000, 1001, 2001 })
	void filterDeleteRemovesEveryMatch(int matches) throws Exception {
		Map<String, String> documents = new LinkedHashMap<>();
		for (int i = 0; i < matches; i++) {
			documents.put("obsolete-" + i, "obsolete");
		}
		documents.put("retained", "current");

		when(this.session.execute(any(SimpleStatement.class))).thenAnswer(invocation -> {
			this.selectStatement = invocation.getArgument(0);
			long limit = this.selectStatement.getQuery().contains("LIMIT 1000") ? 1000 : Long.MAX_VALUE;
			List<Row> rows = documents.entrySet()
				.stream()
				.filter(entry -> entry.getValue().equals("obsolete"))
				.limit(limit)
				.map(entry -> row(entry.getKey()))
				.toList();
			ResultSet resultSet = mock(ResultSet.class);
			when(resultSet.iterator()).thenReturn(rows.iterator());
			return resultSet;
		});
		when(this.session.executeAsync(any(BoundStatement.class))).thenAnswer(invocation -> {
			BoundStatement statement = invocation.getArgument(0);
			documents.remove(statement.getString("id"));
			return CompletableFuture.completedFuture(null);
		});

		try (CassandraVectorStore store = builder().build()) {
			store.delete(new FilterExpressionBuilder().eq("category", "obsolete").build());
		}

		assertThat(documents).containsExactly(Map.entry("retained", "current"));
		assertThat(this.selectStatement.getQuery()).contains("WHERE \"category\" = 'obsolete'")
			.doesNotContain("ANN OF", "LIMIT", "similarity_", "content", "embedding");
		assertThat(this.deletedKeys).hasSize(matches);
		verify(this.embeddingModel, never()).embed(anyString());
	}

	@Test
	void filterDeletePreservesCompositePrimaryKeysAndMetadataTypes() throws Exception {
		CassandraVectorStore.Builder builder = builder();
		addColumn("tenant", DataTypes.TEXT);
		addColumn("sequence", DataTypes.INT);
		addColumn("active", DataTypes.BOOLEAN);
		addColumn("year", DataTypes.INT);
		builder.partitionKeys(List.of(new SchemaColumn("tenant", DataTypes.TEXT)))
			.clusteringKeys(List.of(new SchemaColumn("sequence", DataTypes.INT)))
			.documentIdTranslator(id -> {
				String[] keys = id.split("/");
				return List.of(keys[0], Integer.valueOf(keys[1]));
			})
			.primaryKeyTranslator(keys -> keys.isEmpty() ? "tenant/7" : keys.get(0) + "/" + keys.get(1));

		Row row = mock(Row.class);
		when(row.get("tenant", GenericType.STRING)).thenReturn("tenant");
		when(row.get("sequence", GenericType.INTEGER)).thenReturn(7);
		when(row.getFloat(0)).thenReturn(1f);
		when(row.getString("content")).thenReturn("content");
		when(this.session.execute(any(SimpleStatement.class))).thenAnswer(invocation -> {
			this.selectStatement = invocation.getArgument(0);
			ResultSet resultSet = mock(ResultSet.class);
			when(resultSet.iterator()).thenReturn(List.of(row).iterator());
			return resultSet;
		});
		when(this.session.executeAsync(any(BoundStatement.class))).thenReturn(CompletableFuture.completedFuture(null));

		try (CassandraVectorStore store = builder.build()) {
			var filter = new FilterExpressionBuilder();
			store.delete(filter.and(filter.eq("active", false), filter.gte("year", 2020)).build());
		}

		assertThat(this.selectStatement.getQuery())
			.contains("SELECT tenant,sequence", "\"active\" = false", "\"year\" >= 2020")
			.doesNotContain("ANN OF", "LIMIT");
		assertThat(this.deletedKeys).containsExactly(List.of("tenant", 7));
		verify(this.embeddingModel, never()).embed(anyString());
	}

	@Test
	void filterDeletePropagatesQueryFailure() throws Exception {
		CassandraVectorStore.Builder builder = builder();
		var failure = new IllegalArgumentException("Query rejected");
		when(this.session.execute(any(SimpleStatement.class))).thenThrow(failure);

		try (CassandraVectorStore store = builder.build()) {
			assertThatThrownBy(() -> store.delete(new FilterExpressionBuilder().eq("category", "obsolete").build()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Failed to delete documents by filter")
				.hasCause(failure);
		}
		verify(this.embeddingModel, never()).embed(anyString());
	}

	@Test
	void filterDeletePropagatesDeleteFailure() throws Exception {
		CassandraVectorStore.Builder builder = builder();
		ResultSet resultSet = mock(ResultSet.class);
		Row row = row("obsolete");
		when(resultSet.iterator()).thenReturn(List.of(row).iterator());
		when(this.session.execute(any(SimpleStatement.class))).thenReturn(resultSet);
		var failure = new IllegalArgumentException("Delete rejected");
		when(this.session.executeAsync(any(BoundStatement.class))).thenReturn(CompletableFuture.failedFuture(failure));

		try (CassandraVectorStore store = builder.build()) {
			assertThatThrownBy(() -> store.delete(new FilterExpressionBuilder().eq("category", "obsolete").build()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Failed to delete documents by filter")
				.hasRootCauseInstanceOf(IllegalArgumentException.class)
				.hasRootCauseMessage("Delete rejected");
		}
	}

	@Test
	void filterDeleteRejectsNullExpression() throws Exception {
		try (CassandraVectorStore store = builder().build()) {
			clearInvocations(this.session);
			assertThatThrownBy(() -> store.delete((Filter.Expression) null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Filter expression must not be null");
			verifyNoInteractions(this.session);
		}
	}

	private CassandraVectorStore.Builder builder() {
		Metadata metadata = mock(Metadata.class);
		KeyspaceMetadata keyspace = mock(KeyspaceMetadata.class);
		TableMetadata table = mock(TableMetadata.class);
		IndexMetadata index = mock(IndexMetadata.class);
		when(this.embeddingModel.dimensions()).thenReturn(3);
		when(this.embeddingModel.embed("")).thenReturn(new float[] { 1, 0, 0 });
		when(this.session.getMetadata()).thenReturn(metadata);
		when(metadata.getKeyspace("test_keyspace")).thenReturn(Optional.of(keyspace));
		when(keyspace.getTable("documents")).thenReturn(Optional.of(table));
		when(table.getIndex("embedding_index")).thenReturn(Optional.of(index));
		when(index.getOptions()).thenReturn(Map.of());
		when(table.getColumn(anyString()))
			.thenAnswer(invocation -> Optional.ofNullable(this.columns.get(invocation.getArgument(0))));
		when(table.getColumns()).thenAnswer(invocation -> {
			Map<CqlIdentifier, ColumnMetadata> result = new LinkedHashMap<>();
			this.columns.forEach((name, column) -> result.put(CqlIdentifier.fromCql(name), column));
			return result;
		});
		addColumn("id", DataTypes.TEXT);
		addColumn("content", DataTypes.TEXT);
		addColumn("embedding", DataTypes.vectorOf(DataTypes.FLOAT, 3));
		addColumn("category", DataTypes.TEXT);
		when(this.session.prepare(any(SimpleStatement.class))).thenReturn(this.deleteStatement);
		when(this.deleteStatement.bind(any(Object[].class))).thenAnswer(invocation -> {
			Object[] values = (Object[]) invocation.getRawArguments()[0];
			this.deletedKeys.add(List.of(values));
			BoundStatement statement = mock(BoundStatement.class);
			when(statement.getString("id")).thenReturn(String.valueOf(values[0]));
			return statement;
		});
		return CassandraVectorStore.builder(this.embeddingModel)
			.session(this.session)
			.keyspace("test_keyspace")
			.table("documents")
			.indexName("embedding_index")
			.initializeSchema(false);
	}

	private void addColumn(String name, DataType type) {
		ColumnMetadata column = mock(ColumnMetadata.class);
		when(column.getName()).thenReturn(CqlIdentifier.fromCql(name));
		when(column.getType()).thenReturn(type);
		this.columns.put(name, column);
	}

	private Row row(String id) {
		Row row = mock(Row.class);
		when(row.get("id", GenericType.STRING)).thenReturn(id);
		when(row.getFloat(0)).thenReturn(1f);
		when(row.getString("content")).thenReturn("content");
		return row;
	}

}
