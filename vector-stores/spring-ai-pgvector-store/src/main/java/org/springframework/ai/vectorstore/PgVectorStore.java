/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vectorstore;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.stream.IntStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.StatementCreatorUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * Uses the "vector_store" table to store the Spring AI vector data. The table and the
 * vector index will be auto-created if not available.
 *
 * @author Christian Tzolov
 * @author Josh Long
 */
public class PgVectorStore implements VectorStore, InitializingBean {

	private static final Logger logger = LoggerFactory.getLogger(PgVectorStore.class);

	public static final int OPENAI_EMBEDDING_DIMENSION_SIZE = 1536;

	public static final int INVALID_EMBEDDING_DIMENSION = -1;

	private final String vectorTableName;

	public final static String DEFAULT_VECTOR_TABLE_NAME = "vector_store";

	public final static String DEFAULT_VECTOR_INDEX_NAME = "spring_ai_vector_index";

	public final FilterExpressionConverter filterExpressionConverter = new PgVectorFilterExpressionConverter();

	private final JdbcTemplate jdbcTemplate;

	private final EmbeddingModel embeddingModel;

	private int dimensions;

	private PgDistanceType distanceType;

	private ObjectMapper objectMapper = new ObjectMapper();

	private boolean removeExistingVectorStoreTable;

	private PgIndexType createIndexMethod;

	private final boolean initializeSchema;

	/**
	 * By default, pgvector performs exact nearest neighbor search, which provides perfect
	 * recall. You can add an index to use approximate nearest neighbor search, which
	 * trades some recall for speed. Unlike typical indexes, you will see different
	 * results for queries after adding an approximate index.
	 */
	public enum PgIndexType {

		/**
		 * Performs exact nearest neighbor search, which provides perfect recall.
		 */
		NONE,
		/**
		 * An IVFFlat index divides vectors into lists, and then searches a subset of
		 * those lists that are closest to the query vector. It has faster build times and
		 * uses less memory than HNSW, but has lower query performance (in terms of
		 * speed-recall tradeoff).
		 */
		IVFFLAT,
		/**
		 * An HNSW index creates a multilayer graph. It has slower build times and uses
		 * more memory than IVFFlat, but has better query performance (in terms of
		 * speed-recall tradeoff). There’s no training step like IVFFlat, so the index can
		 * be created without any data in the table.
		 */
		HNSW;

	}

	/**
	 * Defaults to CosineDistance. But if vectors are normalized to length 1 (like OpenAI
	 * embeddings), use inner product (NegativeInnerProduct) for best performance.
	 */
	public enum PgDistanceType {

		// NOTE: works only if If vectors are normalized to length 1 (like OpenAI
		// embeddings), use inner product for best performance.
		// The Sentence transformers are NOT normalized:
		// https://github.com/UKPLab/sentence-transformers/issues/233
		EUCLIDEAN_DISTANCE("<->", "vector_l2_ops",
				"SELECT *, embedding <-> ? AS distance FROM %s WHERE embedding <-> ? < ? %s ORDER BY distance LIMIT ? "),

		// NOTE: works only if If vectors are normalized to length 1 (like OpenAI
		// embeddings), use inner product for best performance.
		// The Sentence transformers are NOT normalized:
		// https://github.com/UKPLab/sentence-transformers/issues/233
		NEGATIVE_INNER_PRODUCT("<#>", "vector_ip_ops",
				"SELECT *, (1 + (embedding <#> ?)) AS distance FROM %s WHERE (1 + (embedding <#> ?)) < ? %s ORDER BY distance LIMIT ? "),

		COSINE_DISTANCE("<=>", "vector_cosine_ops",
				"SELECT *, embedding <=> ? AS distance FROM %s WHERE embedding <=> ? < ? %s ORDER BY distance LIMIT ? ");

		public final String operator;

		public final String index;

		public final String similaritySearchSqlTemplate;

		PgDistanceType(String operator, String index, String sqlTemplate) {
			this.operator = operator;
			this.index = index;
			this.similaritySearchSqlTemplate = sqlTemplate;
		}

	}

	private static class DocumentRowMapper implements RowMapper<Document> {

		private static final String COLUMN_EMBEDDING = "embedding";

		private static final String COLUMN_METADATA = "metadata";

		private static final String COLUMN_ID = "id";

		private static final String COLUMN_CONTENT = "content";

		private static final String COLUMN_DISTANCE = "distance";

		private ObjectMapper objectMapper;

		public DocumentRowMapper(ObjectMapper objectMapper) {
			this.objectMapper = objectMapper;
		}

		@Override
		public Document mapRow(ResultSet rs, int rowNum) throws SQLException {
			String id = rs.getString(COLUMN_ID);
			String content = rs.getString(COLUMN_CONTENT);
			PGobject pgMetadata = rs.getObject(COLUMN_METADATA, PGobject.class);
			PGobject embedding = rs.getObject(COLUMN_EMBEDDING, PGobject.class);
			Float distance = rs.getFloat(COLUMN_DISTANCE);

			Map<String, Object> metadata = toMap(pgMetadata);
			metadata.put(COLUMN_DISTANCE, distance);

			Document document = new Document(id, content, metadata);
			document.setEmbedding(toDoubleList(embedding));

			return document;
		}

		private List<Double> toDoubleList(PGobject embedding) throws SQLException {
			float[] floatArray = new PGvector(embedding.getValue()).toArray();
			return IntStream.range(0, floatArray.length).mapToDouble(i -> floatArray[i]).boxed().toList();
		}

		private Map<String, Object> toMap(PGobject pgObject) {

			String source = pgObject.getValue();
			try {
				return (Map<String, Object>) objectMapper.readValue(source, Map.class);
			}
			catch (JsonProcessingException e) {
				throw new RuntimeException(e);
			}
		}

	}

	public PgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
		this(jdbcTemplate, embeddingModel, INVALID_EMBEDDING_DIMENSION, PgDistanceType.COSINE_DISTANCE, false,
				PgIndexType.NONE, false);
	}

	public PgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, int dimensions) {
		this(jdbcTemplate, embeddingModel, dimensions, PgDistanceType.COSINE_DISTANCE, false, PgIndexType.NONE, false);
	}

	public PgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, int dimensions,
			PgDistanceType distanceType, boolean removeExistingVectorStoreTable, PgIndexType createIndexMethod,
			boolean initializeSchema) {

		this(DEFAULT_VECTOR_TABLE_NAME, jdbcTemplate, embeddingModel, dimensions, distanceType,
				removeExistingVectorStoreTable, createIndexMethod, initializeSchema);
	}

	public PgVectorStore(String vectorTableName, JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
			int dimensions, PgDistanceType distanceType, boolean removeExistingVectorStoreTable,
			PgIndexType createIndexMethod, boolean initializeSchema) {

		this.vectorTableName = (null == vectorTableName || vectorTableName.isEmpty()) ? DEFAULT_VECTOR_TABLE_NAME
				: vectorTableName.trim();

		this.jdbcTemplate = jdbcTemplate;
		this.embeddingModel = embeddingModel;
		this.dimensions = dimensions;
		this.distanceType = distanceType;
		this.removeExistingVectorStoreTable = removeExistingVectorStoreTable;
		this.createIndexMethod = createIndexMethod;
		this.initializeSchema = initializeSchema;
	}

	public PgDistanceType getDistanceType() {
		return distanceType;
	}

	@Override
	public void add(List<Document> documents) {

		int size = documents.size();

		this.jdbcTemplate.batchUpdate(
				"INSERT INTO " + getVectorTableName()
						+ " (id, content, metadata, embedding) VALUES (?, ?, ?::jsonb, ?) " + "ON CONFLICT (id) DO "
						+ "UPDATE SET content = ? , metadata = ?::jsonb , embedding = ? ",
				new BatchPreparedStatementSetter() {
					@Override
					public void setValues(PreparedStatement ps, int i) throws SQLException {

						var document = documents.get(i);
						var content = document.getContent();
						var json = toJson(document.getMetadata());
						var pGvector = new PGvector(toFloatArray(embeddingModel.embed(document)));

						StatementCreatorUtils.setParameterValue(ps, 1, SqlTypeValue.TYPE_UNKNOWN,
								UUID.fromString(document.getId()));
						StatementCreatorUtils.setParameterValue(ps, 2, SqlTypeValue.TYPE_UNKNOWN, content);
						StatementCreatorUtils.setParameterValue(ps, 3, SqlTypeValue.TYPE_UNKNOWN, json);
						StatementCreatorUtils.setParameterValue(ps, 4, SqlTypeValue.TYPE_UNKNOWN, pGvector);
						StatementCreatorUtils.setParameterValue(ps, 5, SqlTypeValue.TYPE_UNKNOWN, content);
						StatementCreatorUtils.setParameterValue(ps, 6, SqlTypeValue.TYPE_UNKNOWN, json);
						StatementCreatorUtils.setParameterValue(ps, 7, SqlTypeValue.TYPE_UNKNOWN, pGvector);
					}

					@Override
					public int getBatchSize() {
						return size;
					}
				});
	}

	private String toJson(Map<String, Object> map) {
		try {
			return objectMapper.writeValueAsString(map);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	private float[] toFloatArray(List<Double> embeddingDouble) {
		float[] embeddingFloat = new float[embeddingDouble.size()];
		int i = 0;
		for (Double d : embeddingDouble) {
			embeddingFloat[i++] = d.floatValue();
		}
		return embeddingFloat;
	}

	@Override
	public Optional<Boolean> delete(List<String> idList) {
		int updateCount = 0;
		for (String id : idList) {
			int count = jdbcTemplate.update("DELETE FROM " + getVectorTableName() + " WHERE id = ?",
					UUID.fromString(id));
			updateCount = updateCount + count;
		}

		return Optional.of(updateCount == idList.size());
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {

		String nativeFilterExpression = (request.getFilterExpression() != null)
				? this.filterExpressionConverter.convertExpression(request.getFilterExpression()) : "";

		String jsonPathFilter = "";

		if (StringUtils.hasText(nativeFilterExpression)) {
			jsonPathFilter = " AND metadata::jsonb @@ '" + nativeFilterExpression + "'::jsonpath ";
		}

		double distance = 1 - request.getSimilarityThreshold();

		PGvector queryEmbedding = getQueryEmbedding(request.getQuery());

		return this.jdbcTemplate.query(
				String.format(this.getDistanceType().similaritySearchSqlTemplate, getVectorTableName(), jsonPathFilter),
				new DocumentRowMapper(this.objectMapper), queryEmbedding, queryEmbedding, distance, request.getTopK());
	}

	public List<Double> embeddingDistance(String query) {
		return this.jdbcTemplate.query(
				"SELECT embedding " + this.comparisonOperator() + " ? AS distance FROM " + getVectorTableName(),
				new RowMapper<Double>() {
					@Override
					@Nullable
					public Double mapRow(ResultSet rs, int rowNum) throws SQLException {
						return rs.getDouble(DocumentRowMapper.COLUMN_DISTANCE);
					}

				}, getQueryEmbedding(query));
	}

	private PGvector getQueryEmbedding(String query) {
		List<Double> embedding = this.embeddingModel.embed(query);
		return new PGvector(toFloatArray(embedding));
	}

	private String comparisonOperator() {
		return this.getDistanceType().operator;
	}

	static boolean isValidTableName(String tableName) {

		if (tableName == null) {
			return false;
		}

		// Check if the table Only alphanumeric characters and underscores and should be
		// less than 64 characters
		if (!tableName.matches("^[a-zA-Z0-9_]{1,64}$")) {
			return false;
		}

		// Check to ensure the table name is not purely numeric
		if (tableName.matches("^[0-9]+$")) {
			return false;
		}

		return true;

	}

	void validateTableSchema() {

		String tableName = getVectorTableName();

		if (isValidTableName(tableName) == false) {
			throw new IllegalArgumentException(
					"Table name should only contain alphanumeric characters and underscores");
		}

		try {
			logger.info("Validating PGVectorStore schema");

			List<String> expectedColumns = new ArrayList<>();
			expectedColumns.add("id");
			expectedColumns.add("content");
			expectedColumns.add("metadata");
			expectedColumns.add("embedding");

			// Query to check if the table exists with the required fields and types
			List<Map<String, Object>> columns = jdbcTemplate.queryForList(
					"SELECT column_name, data_type FROM information_schema.columns WHERE table_name = ?",
					new Object[] { tableName });

			if (columns.isEmpty()) {
				throw new IllegalStateException(
						"Error while validating table schema, Table " + tableName + " does not exist");

			}

			// Check each column against expected fields
			List<String> availableColumns = new ArrayList<>();
			for (Map<String, Object> column : columns) {
				String columnName = (String) column.get("column_name");
				availableColumns.add(columnName);

			}

			expectedColumns.removeAll(availableColumns);

			if (expectedColumns.isEmpty()) {
				logger.info("PG VectorStore schema validation successful");
			}
			else {
				throw new IllegalStateException("Missing fields " + expectedColumns);
			}

		}
		catch (DataAccessException | IllegalStateException e) {
			logger.error("Error while validating table schema" + e.getMessage());
			logger
				.error("Failed to operate with the specified table in the database. To resolve this issue, please ensure the following steps are completed:\n"
						+ "1. Ensure the necessary PostgreSQL extensions are enabled. Run the following SQL commands:\n"
						+ "   CREATE EXTENSION IF NOT EXISTS vector;\n" + "   CREATE EXTENSION IF NOT EXISTS hstore;\n"
						+ "   CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";\n"
						+ "2. Verify that the table exists with the appropriate structure. If it does not exist, create it using a SQL command similar to the following, replacing 'embedding_dimensions' with the appropriate size based on your vector embeddings:\n"
						+ String.format("   CREATE TABLE IF NOT EXISTS %s (\n"
								+ "       id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,\n" + "       content text,\n"
								+ "       metadata json,\n"
								+ "       embedding vector(embedding_dimensions)  // Replace 'embedding_dimensions' with your specific value\n"
								+ "   );\n", tableName)
						+ "3. Create an appropriate index for the vector embedding to optimize performance. Adjust the index type and options based on your usage. Example SQL for creating an index:\n"
						+ String.format("   CREATE INDEX ON %s USING HNSW (embedding vector_cosine_ops);\n", tableName)
						+ "\nPlease adjust these commands based on your specific configuration and the capabilities of your vector database system.");
			throw new IllegalStateException(e);

		}
	}

	// ---------------------------------------------------------------------------------
	// Initialize
	// ---------------------------------------------------------------------------------
	@Override
	public void afterPropertiesSet() throws Exception {

		if (!this.initializeSchema || !this.getVectorTableName().equalsIgnoreCase(DEFAULT_VECTOR_TABLE_NAME)) {

			logger.debug("Skipping the schema initialization for the table: " + this.getVectorTableName());

			validateTableSchema();
			return;
		}

		// Enable the PGVector, JSONB and UUID support.
		this.jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
		this.jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS hstore");
		this.jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");

		// Remove existing VectorStoreTable
		if (this.removeExistingVectorStoreTable) {
			this.jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEFAULT_VECTOR_TABLE_NAME);
		}

		this.jdbcTemplate.execute(String.format("""
				CREATE TABLE IF NOT EXISTS %s (
					id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
					content text,
					metadata json,
					embedding vector(%d)
				)
				""", DEFAULT_VECTOR_TABLE_NAME, this.embeddingDimensions()));

		if (this.createIndexMethod != PgIndexType.NONE) {
			this.jdbcTemplate.execute(String.format("""
					CREATE INDEX IF NOT EXISTS %s ON %s USING %s (embedding %s)
					""", DEFAULT_VECTOR_INDEX_NAME, DEFAULT_VECTOR_TABLE_NAME, this.createIndexMethod,
					this.getDistanceType().index));
		}
	}

	private String getVectorTableName() {
		return this.vectorTableName;
	}

	int embeddingDimensions() {
		// The manually set dimensions have precedence over the computed one.
		if (this.dimensions > 0) {
			return this.dimensions;
		}

		try {
			int embeddingDimensions = this.embeddingModel.dimensions();
			if (embeddingDimensions > 0) {
				return embeddingDimensions;
			}
		}
		catch (Exception e) {
			logger.warn("Failed to obtain the embedding dimensions from the embedding model and fall backs to default:"
					+ OPENAI_EMBEDDING_DIMENSION_SIZE, e);
		}
		return OPENAI_EMBEDDING_DIMENSION_SIZE;
	}

}