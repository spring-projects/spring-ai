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

import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.StatementCreatorUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;

import io.micrometer.observation.ObservationRegistry;

/**
 * Uses the "vector_store" table to store the Spring AI vector data. The table and the
 * vector index will be auto-created if not available.
 *
 * @author Christian Tzolov
 * @author Josh Long
 * @author Muthukumaran Navaneethakrishnan
 */
public class PgVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	private static final Logger logger = LoggerFactory.getLogger(PgVectorStore.class);

	public static final int OPENAI_EMBEDDING_DIMENSION_SIZE = 1536;

	public static final int INVALID_EMBEDDING_DIMENSION = -1;

	public static final String DEFAULT_TABLE_NAME = "vector_store";

	public static final String DEFAULT_VECTOR_INDEX_NAME = "spring_ai_vector_index";

	public static final String DEFAULT_SCHEMA_NAME = "public";

	public static final boolean DEFAULT_SCHEMA_VALIDATION = false;

	public final FilterExpressionConverter filterExpressionConverter = new PgVectorFilterExpressionConverter();

	private final String vectorTableName;

	private final String vectorIndexName;

	private final JdbcTemplate jdbcTemplate;

	private final EmbeddingModel embeddingModel;

	private final String schemaName;

	private final boolean schemaValidation;

	private final boolean initializeSchema;

	private int dimensions;

	private PgDistanceType distanceType;

	private ObjectMapper objectMapper = new ObjectMapper();

	private boolean removeExistingVectorStoreTable;

	private PgIndexType createIndexMethod;

	private PgVectorSchemaValidator schemaValidator;

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

		this(DEFAULT_TABLE_NAME, jdbcTemplate, embeddingModel, dimensions, distanceType, removeExistingVectorStoreTable,
				createIndexMethod, initializeSchema);
	}

	public PgVectorStore(String vectorTableName, JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
			int dimensions, PgDistanceType distanceType, boolean removeExistingVectorStoreTable,
			PgIndexType createIndexMethod, boolean initializeSchema) {

		this(DEFAULT_SCHEMA_NAME, vectorTableName, DEFAULT_SCHEMA_VALIDATION, jdbcTemplate, embeddingModel, dimensions,
				distanceType, removeExistingVectorStoreTable, createIndexMethod, initializeSchema);

	}

	private PgVectorStore(String schemaName, String vectorTableName, boolean vectorTableValidationsEnabled,
			JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, int dimensions, PgDistanceType distanceType,
			boolean removeExistingVectorStoreTable, PgIndexType createIndexMethod, boolean initializeSchema) {

		this(schemaName, vectorTableName, vectorTableValidationsEnabled, jdbcTemplate, embeddingModel, dimensions,
				distanceType, removeExistingVectorStoreTable, createIndexMethod, initializeSchema,
				ObservationRegistry.NOOP, null);
	}

	private PgVectorStore(String schemaName, String vectorTableName, boolean vectorTableValidationsEnabled,
			JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, int dimensions, PgDistanceType distanceType,
			boolean removeExistingVectorStoreTable, PgIndexType createIndexMethod, boolean initializeSchema,
			ObservationRegistry observationRegistry,
			VectorStoreObservationConvention customSearchObservationConvention) {

		super(observationRegistry, customSearchObservationConvention);

		this.vectorTableName = (null == vectorTableName || vectorTableName.isEmpty()) ? DEFAULT_TABLE_NAME
				: vectorTableName.trim();
		logger.info("Using the vector table name: {}. Is empty: {}", this.vectorTableName,
				(vectorTableName == null || vectorTableName.isEmpty()));

		this.vectorIndexName = this.vectorTableName.equals(DEFAULT_TABLE_NAME) ? DEFAULT_VECTOR_INDEX_NAME
				: this.vectorTableName + "_index";

		this.schemaName = schemaName;
		this.schemaValidation = vectorTableValidationsEnabled;

		this.jdbcTemplate = jdbcTemplate;
		this.embeddingModel = embeddingModel;
		this.dimensions = dimensions;
		this.distanceType = distanceType;
		this.removeExistingVectorStoreTable = removeExistingVectorStoreTable;
		this.createIndexMethod = createIndexMethod;
		this.initializeSchema = initializeSchema;
		this.schemaValidator = new PgVectorSchemaValidator(jdbcTemplate);
	}

	public PgDistanceType getDistanceType() {
		return distanceType;
	}

	@Override
	public void doAdd(List<Document> documents) {

		int size = documents.size();

		this.jdbcTemplate.batchUpdate(
				"INSERT INTO " + getFullyQualifiedTableName()
						+ " (id, content, metadata, embedding) VALUES (?, ?, ?::jsonb, ?) " + "ON CONFLICT (id) DO "
						+ "UPDATE SET content = ? , metadata = ?::jsonb , embedding = ? ",
				new BatchPreparedStatementSetter() {
					@Override
					public void setValues(PreparedStatement ps, int i) throws SQLException {

						var document = documents.get(i);
						var content = document.getContent();
						var json = toJson(document.getMetadata());
						var embedding = embeddingModel.embed(document);
						document.setEmbedding(embedding);
						var pGvector = new PGvector(embedding);

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

	@Override
	public Optional<Boolean> doDelete(List<String> idList) {
		int updateCount = 0;
		for (String id : idList) {
			int count = jdbcTemplate.update("DELETE FROM " + getFullyQualifiedTableName() + " WHERE id = ?",
					UUID.fromString(id));
			updateCount = updateCount + count;
		}

		return Optional.of(updateCount == idList.size());
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {

		String nativeFilterExpression = (request.getFilterExpression() != null)
				? this.filterExpressionConverter.convertExpression(request.getFilterExpression()) : "";

		String jsonPathFilter = "";

		if (StringUtils.hasText(nativeFilterExpression)) {
			jsonPathFilter = " AND metadata::jsonb @@ '" + nativeFilterExpression + "'::jsonpath ";
		}

		double distance = 1 - request.getSimilarityThreshold();

		PGvector queryEmbedding = getQueryEmbedding(request.getQuery());

		return this.jdbcTemplate.query(
				String.format(this.getDistanceType().similaritySearchSqlTemplate, getFullyQualifiedTableName(),
						jsonPathFilter),
				new DocumentRowMapper(this.objectMapper), queryEmbedding, queryEmbedding, distance, request.getTopK());
	}

	public List<Double> embeddingDistance(String query) {
		return this.jdbcTemplate.query(
				"SELECT embedding " + this.comparisonOperator() + " ? AS distance FROM " + getFullyQualifiedTableName(),
				new RowMapper<Double>() {
					@Override
					@Nullable
					public Double mapRow(ResultSet rs, int rowNum) throws SQLException {
						return rs.getDouble(DocumentRowMapper.COLUMN_DISTANCE);
					}

				}, getQueryEmbedding(query));
	}

	private PGvector getQueryEmbedding(String query) {
		float[] embedding = this.embeddingModel.embed(query);
		return new PGvector(embedding);
	}

	private String comparisonOperator() {
		return this.getDistanceType().operator;
	}

	// ---------------------------------------------------------------------------------
	// Initialize
	// ---------------------------------------------------------------------------------
	@Override
	public void afterPropertiesSet() throws Exception {

		logger.info("Initializing PGVectorStore schema for table: {} in schema: {}", this.getVectorTableName(),
				this.getSchemaName());

		logger.info("vectorTableValidationsEnabled {}", this.schemaValidation);

		if (this.schemaValidation) {
			this.schemaValidator.validateTableSchema(this.getSchemaName(), this.getVectorTableName());
		}

		if (!this.initializeSchema) {
			logger.debug("Skipping the schema initialization for the table: {}", this.getFullyQualifiedTableName());
			return;
		}

		// Enable the PGVector, JSONB and UUID support.
		this.jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
		this.jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS hstore");
		this.jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");

		this.jdbcTemplate.execute(String.format("CREATE SCHEMA IF NOT EXISTS %s", this.getSchemaName()));

		// Remove existing VectorStoreTable
		if (this.removeExistingVectorStoreTable) {
			this.jdbcTemplate.execute(String.format("DROP TABLE IF EXISTS %s", this.getFullyQualifiedTableName()));
		}

		this.jdbcTemplate.execute(String.format("""
				CREATE TABLE IF NOT EXISTS %s (
					id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
					content text,
					metadata json,
					embedding vector(%d)
				)
				""", this.getFullyQualifiedTableName(), this.embeddingDimensions()));

		if (this.createIndexMethod != PgIndexType.NONE) {
			this.jdbcTemplate.execute(String.format("""
					CREATE INDEX IF NOT EXISTS %s ON %s USING %s (embedding %s)
					""", this.getVectorIndexName(), this.getFullyQualifiedTableName(), this.createIndexMethod,
					this.getDistanceType().index));
		}
	}

	private String getFullyQualifiedTableName() {
		return this.schemaName + "." + this.vectorTableName;
	}

	private String getVectorTableName() {
		return this.vectorTableName;
	}

	private String getSchemaName() {
		return this.schemaName;
	}

	private String getVectorIndexName() {
		return this.vectorIndexName;
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
			document.setEmbedding(toFloatArray(embedding));

			return document;
		}

		private float[] toFloatArray(PGobject embedding) throws SQLException {
			return new PGvector(embedding.getValue()).toArray();
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

	public static class Builder {

		private final JdbcTemplate jdbcTemplate;

		private final EmbeddingModel embeddingModel;

		private String schemaName = PgVectorStore.DEFAULT_SCHEMA_NAME;

		private String vectorTableName;

		private boolean vectorTableValidationsEnabled = PgVectorStore.DEFAULT_SCHEMA_VALIDATION;

		private int dimensions = PgVectorStore.INVALID_EMBEDDING_DIMENSION;

		private PgDistanceType distanceType = PgDistanceType.COSINE_DISTANCE;

		private boolean removeExistingVectorStoreTable = false;

		private PgIndexType indexType = PgIndexType.HNSW;

		private boolean initializeSchema;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		@Nullable
		private VectorStoreObservationConvention searchObservationConvention;

		// Builder constructor with mandatory parameters
		public Builder(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
			if (jdbcTemplate == null || embeddingModel == null) {
				throw new IllegalArgumentException("JdbcTemplate and EmbeddingModel must not be null");
			}
			this.jdbcTemplate = jdbcTemplate;
			this.embeddingModel = embeddingModel;
		}

		public Builder withSchemaName(String schemaName) {
			this.schemaName = schemaName;
			return this;
		}

		public Builder withVectorTableName(String vectorTableName) {
			this.vectorTableName = vectorTableName;
			return this;
		}

		public Builder withVectorTableValidationsEnabled(boolean vectorTableValidationsEnabled) {
			this.vectorTableValidationsEnabled = vectorTableValidationsEnabled;
			return this;
		}

		public Builder withDimensions(int dimensions) {
			this.dimensions = dimensions;
			return this;
		}

		public Builder withDistanceType(PgDistanceType distanceType) {
			this.distanceType = distanceType;
			return this;
		}

		public Builder withRemoveExistingVectorStoreTable(boolean removeExistingVectorStoreTable) {
			this.removeExistingVectorStoreTable = removeExistingVectorStoreTable;
			return this;
		}

		public Builder withIndexType(PgIndexType indexType) {
			this.indexType = indexType;
			return this;
		}

		public Builder withInitializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		public Builder withObservationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public Builder withSearchObservationConvention(
				VectorStoreObservationConvention customSearchObservationConvention) {
			this.searchObservationConvention = customSearchObservationConvention;
			return this;
		}

		public PgVectorStore build() {
			return new PgVectorStore(schemaName, vectorTableName, vectorTableValidationsEnabled, jdbcTemplate,
					embeddingModel, dimensions, distanceType, removeExistingVectorStoreTable, indexType,
					initializeSchema, observationRegistry, searchObservationConvention);
		}

	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {

		return VectorStoreObservationContext.builder(VectorStoreProvider.PG_VECTOR.value(), operationName)
			.withDimensions(this.embeddingDimensions())
			.withCollectionName(this.vectorTableName)
			.withNamespace(this.schemaName)
			.withSimilarityMetric(getSimilarityMetric())
			.withIndexName(this.createIndexMethod.name());
	}

	private static Map<PgDistanceType, VectorStoreSimilarityMetric> SIMILARITY_TYPE_MAPPING = Map.of(
			PgDistanceType.COSINE_DISTANCE, VectorStoreSimilarityMetric.COSINE, PgDistanceType.EUCLIDEAN_DISTANCE,
			VectorStoreSimilarityMetric.EUCLIDEAN, PgDistanceType.NEGATIVE_INNER_PRODUCT,
			VectorStoreSimilarityMetric.DOT);

	private String getSimilarityMetric() {
		if (!SIMILARITY_TYPE_MAPPING.containsKey(this.getDistanceType())) {
			return this.getDistanceType().name();
		}
		return SIMILARITY_TYPE_MAPPING.get(this.distanceType).value();
	}

}