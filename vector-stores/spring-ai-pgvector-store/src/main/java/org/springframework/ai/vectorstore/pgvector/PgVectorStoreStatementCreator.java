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

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.pgvector.PGvector;
import tools.jackson.databind.ObjectMapper;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.InterruptibleBatchPreparedStatementSetter;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementCreatorFactory;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.StatementCreatorUtils;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.util.StringUtils;

/**
 * A creator for generating SQL statements for the PostgreSQL vector store.
 *
 * @author Martin Grofcik
 * @since 2.0.2
 */
public class PgVectorStoreStatementCreator implements SqlVectorStoreStatementCreator {

	private final PreparedStatementCreatorFactory statementFactory;

	private final PgVectorStore.PgDistanceType distanceType;

	private final String vectorTableName;

	private final String schemaName;

	private final PgVectorStore.PgIdType idType;

	private final FilterExpressionConverter filterExpressionConverter;

	private final BatchingStrategy batchingStrategy;

	private final int maxDocumentBatchSize;

	private final EmbeddingModel embeddingModel;

	private final ObjectMapper jsonMapper;

	public PgVectorStoreStatementCreator(PgVectorStore.PgDistanceType distanceType, String vectorTableName,
			String schemaName, EmbeddingModel embeddingModel, PgVectorStore.PgIdType idType,
			BatchingStrategy batchingStrategy, int maxDocumentBatchSize, ObjectMapper jsonMapper) {
		this.distanceType = distanceType;
		this.vectorTableName = vectorTableName;
		this.schemaName = schemaName;
		this.idType = idType;
		this.batchingStrategy = batchingStrategy;
		this.maxDocumentBatchSize = maxDocumentBatchSize;
		this.jsonMapper = jsonMapper;
		this.filterExpressionConverter = new PgVectorFilterExpressionConverter();
		this.embeddingModel = embeddingModel;
		this.statementFactory = new PreparedStatementCreatorFactory("");
	}

	@Override
	public PreparedStatementCreator similaritySearchStatement(SearchRequest request) {
		String nativeFilterExpression = (request.getFilterExpression() != null)
				? this.filterExpressionConverter.convertExpression(request.getFilterExpression()) : "";

		String jsonPathFilter = "";

		if (StringUtils.hasText(nativeFilterExpression)) {
			jsonPathFilter = " AND " + nativeFilterExpression + " ";
		}

		double distance = 1 - request.getSimilarityThreshold();

		PGvector queryEmbedding = getQueryEmbedding(request.getQuery());

		return this.statementFactory
			.newPreparedStatementCreator(
					String.format(this.distanceType.similaritySearchSqlTemplate, getFullyQualifiedTableName(),
							jsonPathFilter),
					new Object[] { queryEmbedding, queryEmbedding, distance, request.getTopK() });
	}

	@Override
	public PreparedStatementCreator deleteStatement(Filter.Expression filterExpression) {
		String filterClause = this.filterExpressionConverter.convertExpression(filterExpression);

		return this.statementFactory.newPreparedStatementCreator(
				"DELETE FROM " + getFullyQualifiedTableName() + " WHERE " + filterClause, new Object[0]);
	}

	@Override
	public Stream<SqlVectorStorePreparedStatement> deleteByIdStatement(List<String> idList, KeyHolder keyHolder) {
		return Stream.<SqlVectorStorePreparedStatement>builder()
			.add(new DefaultVectorStorePreparedStatement(
					this.statementFactory.newPreparedStatementCreator(
							"DELETE FROM " + getFullyQualifiedTableName() + " WHERE id = ?", new Object[0]),
					deleteByIdSetter(idList, keyHolder)))
			.build();
	}

	private BatchPreparedStatementSetter deleteByIdSetter(List<String> idList, KeyHolder keyHolder) {
		return new BatchPreparedStatementSetter() {

			@Override
			public void setValues(java.sql.PreparedStatement ps, int i) throws SQLException {
				var id = idList.get(i);
				StatementCreatorUtils.setParameterValue(ps, 1, SqlTypeValue.TYPE_UNKNOWN, convertIdToPgType(id));
			}

			@Override
			public int getBatchSize() {
				return idList.size();
			}
		};
	}

	@Override
	public Stream<SqlVectorStorePreparedStatement> insertUpdateStatement(List<Document> documents) {
		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
				this.batchingStrategy);
		List<SqlVectorStorePreparedStatement> batches = new ArrayList<>();
		for (int i = 0; i < documents.size(); i += this.maxDocumentBatchSize) {
			int offset = Math.min(this.maxDocumentBatchSize, documents.size() - i);
			batches.add(new DefaultVectorStorePreparedStatement(
					this.statementFactory.newPreparedStatementCreator("INSERT INTO " + getFullyQualifiedTableName()
							+ " (id, content, metadata, embedding) VALUES (?, ?, ?::jsonb, ?) " + "ON CONFLICT (id) DO "
							+ "UPDATE SET content = ? , metadata = ?::jsonb , embedding = ? ", null),
					new InsertBatchPreparedStatementSetter(documents, embeddings, i, offset)));
		}
		return batches.stream();
	}

	private Object convertIdToPgType(String id) {
		return switch (this.idType) {
			case UUID -> UUID.fromString(id);
			case TEXT -> id;
			case INTEGER, SERIAL -> Integer.valueOf(id);
			case BIGSERIAL -> Long.valueOf(id);
		};
	}

	private PGvector getQueryEmbedding(String query) {
		float[] embedding = this.embeddingModel.embed(query);
		return new PGvector(embedding);
	}

	private String getFullyQualifiedTableName() {
		return this.schemaName + "." + this.vectorTableName;
	}

	private class InsertBatchPreparedStatementSetter implements InterruptibleBatchPreparedStatementSetter {

		private final List<Document> documents;

		private final List<float[]> embeddings;

		private final int start;

		private final int offset;

		InsertBatchPreparedStatementSetter(List<Document> documents, List<float[]> embeddings, int start, int offset) {
			this.documents = documents;
			this.embeddings = embeddings;
			this.start = start;
			this.offset = offset;
		}

		@Override
		public void setValues(PreparedStatement ps, int i) throws SQLException {
			if (i >= this.offset) {
				throw new IndexOutOfBoundsException(i);
			}
			var document = this.documents.get(this.start + i);
			var id = convertIdToPgType(document.getId());
			var content = document.getText();
			var json = toJson(document.getMetadata());
			var embedding = this.embeddings.get(this.start + i);
			var pGvector = new PGvector(embedding);

			StatementCreatorUtils.setParameterValue(ps, 1, SqlTypeValue.TYPE_UNKNOWN, id);
			StatementCreatorUtils.setParameterValue(ps, 2, SqlTypeValue.TYPE_UNKNOWN, content);
			StatementCreatorUtils.setParameterValue(ps, 3, SqlTypeValue.TYPE_UNKNOWN, json);
			StatementCreatorUtils.setParameterValue(ps, 4, SqlTypeValue.TYPE_UNKNOWN, pGvector);
			StatementCreatorUtils.setParameterValue(ps, 5, SqlTypeValue.TYPE_UNKNOWN, content);
			StatementCreatorUtils.setParameterValue(ps, 6, SqlTypeValue.TYPE_UNKNOWN, json);
			StatementCreatorUtils.setParameterValue(ps, 7, SqlTypeValue.TYPE_UNKNOWN, pGvector);
		}

		@Override
		public int getBatchSize() {
			return this.offset;
		}

		private String toJson(Map<String, Object> map) {
			return jsonMapper.writeValueAsString(map);
		}

		@Override
		public boolean isBatchExhausted(int i) {
			return this.offset <= i;
		}

	}

	private record DefaultVectorStorePreparedStatement(PreparedStatementCreator creator,
			BatchPreparedStatementSetter setter) implements SqlVectorStorePreparedStatement {

		@Override
		public PreparedStatementCreator getCreator() {
			return this.creator;
		}

		@Override
		public BatchPreparedStatementSetter getSetter() {
			return this.setter;
		}
	}

}
