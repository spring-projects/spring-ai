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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micrometer.observation.ObservationRegistry;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * Uses the "vector_store" table to store the Spring AI vector data. The table and the
 * vector index will be auto-created if not available.
 *
 * @author Diego Dupin
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
public class MariaDBVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	public static final int OPENAI_EMBEDDING_DIMENSION_SIZE = 1536;

	public static final int INVALID_EMBEDDING_DIMENSION = -1;

	public static final boolean DEFAULT_SCHEMA_VALIDATION = false;

	public static final int MAX_DOCUMENT_BATCH_SIZE = 10_000;

	private static final Logger logger = LoggerFactory.getLogger(MariaDBVectorStore.class);

	public static final String DEFAULT_TABLE_NAME = "vector_store";

	public static final String DEFAULT_COLUMN_EMBEDDING = "embedding";

	public static final String DEFAULT_COLUMN_METADATA = "metadata";

	public static final String DEFAULT_COLUMN_ID = "id";

	public static final String DEFAULT_COLUMN_CONTENT = "content";

	private static Map<MariaDBDistanceType, VectorStoreSimilarityMetric> SIMILARITY_TYPE_MAPPING = Map.of(
			MariaDBDistanceType.COSINE, VectorStoreSimilarityMetric.COSINE, MariaDBDistanceType.EUCLIDEAN,
			VectorStoreSimilarityMetric.EUCLIDEAN);

	public final FilterExpressionConverter filterExpressionConverter;

	private final String vectorTableName;

	private final JdbcTemplate jdbcTemplate;

	private final EmbeddingModel embeddingModel;

	private final String schemaName;

	private final boolean schemaValidation;

	private final boolean initializeSchema;

	private final int dimensions;

	private final String contentFieldName;

	private final String embeddingFieldName;

	private final String idFieldName;

	private final String metadataFieldName;

	private final MariaDBDistanceType distanceType;

	private final ObjectMapper objectMapper;

	private final boolean removeExistingVectorStoreTable;

	private final MariaDBSchemaValidator schemaValidator;

	private final BatchingStrategy batchingStrategy;

	private final int maxDocumentBatchSize;

	public MariaDBVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
		this(jdbcTemplate, embeddingModel, INVALID_EMBEDDING_DIMENSION, MariaDBDistanceType.COSINE, false, false);
	}

	public MariaDBVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, int dimensions) {
		this(jdbcTemplate, embeddingModel, dimensions, MariaDBDistanceType.COSINE, false, false);
	}

	public MariaDBVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, int dimensions,
			MariaDBDistanceType distanceType, boolean removeExistingVectorStoreTable, boolean initializeSchema) {

		this(DEFAULT_TABLE_NAME, jdbcTemplate, embeddingModel, dimensions, distanceType, removeExistingVectorStoreTable,
				initializeSchema);
	}

	public MariaDBVectorStore(String vectorTableName, JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
			int dimensions, MariaDBDistanceType distanceType, boolean removeExistingVectorStoreTable,
			boolean initializeSchema) {

		this(null, vectorTableName, DEFAULT_SCHEMA_VALIDATION, jdbcTemplate, embeddingModel, dimensions, distanceType,
				removeExistingVectorStoreTable, initializeSchema);
	}

	private MariaDBVectorStore(String schemaName, String vectorTableName, boolean vectorTableValidationsEnabled,
			JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, int dimensions, MariaDBDistanceType distanceType,
			boolean removeExistingVectorStoreTable, boolean initializeSchema) {

		this(schemaName, vectorTableName, vectorTableValidationsEnabled, jdbcTemplate, embeddingModel, dimensions,
				distanceType, removeExistingVectorStoreTable, initializeSchema, ObservationRegistry.NOOP, null,
				new TokenCountBatchingStrategy(), MAX_DOCUMENT_BATCH_SIZE, DEFAULT_COLUMN_EMBEDDING,
				DEFAULT_COLUMN_METADATA, DEFAULT_COLUMN_ID, DEFAULT_COLUMN_CONTENT);
	}

	private MariaDBVectorStore(String schemaName, String vectorTableName, boolean vectorTableValidationsEnabled,
			JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, int dimensions, MariaDBDistanceType distanceType,
			boolean removeExistingVectorStoreTable, boolean initializeSchema, ObservationRegistry observationRegistry,
			VectorStoreObservationConvention customObservationConvention, BatchingStrategy batchingStrategy,
			int maxDocumentBatchSize, String contentFieldName, String embeddingFieldName, String idFieldName,
			String metadataFieldName) {

		super(observationRegistry, customObservationConvention);

		this.objectMapper = JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build();

		this.vectorTableName = (null == vectorTableName || vectorTableName.isEmpty()) ? DEFAULT_TABLE_NAME
				: MariaDBSchemaValidator.validateAndEnquoteIdentifier(vectorTableName.trim(), false);
		logger.info("Using the vector table name: {}. Is empty: {}", this.vectorTableName,
				(vectorTableName == null || vectorTableName.isEmpty()));

		this.schemaName = schemaName == null ? null
				: MariaDBSchemaValidator.validateAndEnquoteIdentifier(schemaName, false);
		this.schemaValidation = vectorTableValidationsEnabled;

		this.jdbcTemplate = jdbcTemplate;
		this.embeddingModel = embeddingModel;
		this.dimensions = dimensions;
		this.distanceType = distanceType;
		this.removeExistingVectorStoreTable = removeExistingVectorStoreTable;
		this.initializeSchema = initializeSchema;
		this.schemaValidator = new MariaDBSchemaValidator(jdbcTemplate);
		this.batchingStrategy = batchingStrategy;
		this.maxDocumentBatchSize = maxDocumentBatchSize;

		this.contentFieldName = MariaDBSchemaValidator.validateAndEnquoteIdentifier(contentFieldName, false);
		this.embeddingFieldName = MariaDBSchemaValidator.validateAndEnquoteIdentifier(embeddingFieldName, false);
		this.idFieldName = MariaDBSchemaValidator.validateAndEnquoteIdentifier(idFieldName, false);
		this.metadataFieldName = MariaDBSchemaValidator.validateAndEnquoteIdentifier(metadataFieldName, false);
		filterExpressionConverter = new MariaDBFilterExpressionConverter(this.metadataFieldName);
	}

	public MariaDBDistanceType getDistanceType() {
		return this.distanceType;
	}

	@Override
	public void doAdd(List<Document> documents) {
		// Batch the documents based on the batching strategy
		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(),
				this.batchingStrategy);

		List<List<MariaDBDocument>> batchedDocuments = batchDocuments(documents, embeddings);
		batchedDocuments.forEach(this::insertOrUpdateBatch);
	}

	private List<List<MariaDBDocument>> batchDocuments(List<Document> documents, List<float[]> embeddings) {
		List<List<MariaDBDocument>> batches = new ArrayList<>();
		List<MariaDBDocument> mariaDBDocuments = new ArrayList<>(documents.size());
		if (embeddings.size() == documents.size()) {
			for (Document document : documents) {
				mariaDBDocuments.add(new MariaDBDocument(document.getId(), document.getContent(),
						document.getMetadata(), embeddings.get(documents.indexOf(document))));
			}
		}
		else {
			for (Document document : documents) {
				mariaDBDocuments
					.add(new MariaDBDocument(document.getId(), document.getContent(), document.getMetadata(), null));
			}
		}

		for (int i = 0; i < mariaDBDocuments.size(); i += this.maxDocumentBatchSize) {
			batches.add(mariaDBDocuments.subList(i, Math.min(i + this.maxDocumentBatchSize, mariaDBDocuments.size())));
		}
		return batches;
	}

	private void insertOrUpdateBatch(List<MariaDBDocument> batch) {
		String sql = String.format(
				"INSERT INTO %s (%s, %s, %s, %s) VALUES (?, ?, ?, ?) "
						+ "ON DUPLICATE KEY UPDATE %s = VALUES(%s) , %s = VALUES(%s) , %s = VALUES(%s)",
				getFullyQualifiedTableName(), this.idFieldName, this.contentFieldName, this.metadataFieldName,
				this.embeddingFieldName, this.contentFieldName, this.contentFieldName, this.metadataFieldName,
				this.metadataFieldName, this.embeddingFieldName, this.embeddingFieldName);

		this.jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {

			@Override
			public void setValues(PreparedStatement ps, int i) throws SQLException {
				var document = batch.get(i);
				ps.setObject(1, document.id());
				ps.setString(2, document.content());
				ps.setString(3, toJson(document.metadata()));
				ps.setObject(4, document.embedding());
			}

			@Override
			public int getBatchSize() {
				return batch.size();
			}
		});
	}

	private String toJson(Map<String, Object> map) {
		try {
			return this.objectMapper.writeValueAsString(map);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Optional<Boolean> doDelete(List<String> idList) {
		int updateCount = 0;
		for (String id : idList) {
			int count = this.jdbcTemplate.update(
					String.format("DELETE FROM %s WHERE %s = ?", getFullyQualifiedTableName(), this.idFieldName), id);
			updateCount = updateCount + count;
		}

		return Optional.of(updateCount == idList.size());
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {

		String nativeFilterExpression = (request.getFilterExpression() != null)
				? this.filterExpressionConverter.convertExpression(request.getFilterExpression()) : "";
		float[] embedding = this.embeddingModel.embed(request.getQuery());
		String jsonPathFilter = "";

		if (StringUtils.hasText(nativeFilterExpression)) {
			jsonPathFilter = "and " + nativeFilterExpression + " ";
		}
		String distanceType = this.distanceType.name().toLowerCase(Locale.ROOT);

		double distance = 1 - request.getSimilarityThreshold();
		final String sql = String.format(
				"SELECT * FROM (select %s, %s, %s, %s, vec_distance_%s(%s, ?) as distance "
						+ "from %s) as t where distance < ? %sorder by distance asc LIMIT ?",
				this.idFieldName, this.contentFieldName, this.metadataFieldName, this.embeddingFieldName, distanceType,
				this.embeddingFieldName, getFullyQualifiedTableName(), jsonPathFilter);

		logger.debug("SQL query: " + sql);

		return this.jdbcTemplate.query(sql, new DocumentRowMapper(this.objectMapper), embedding, distance,
				request.getTopK());
	}

	// ---------------------------------------------------------------------------------
	// Initialize
	// ---------------------------------------------------------------------------------
	@Override
	public void afterPropertiesSet() {

		logger.info("Initializing MariaDBVectorStore schema for table: {} in schema: {}", this.vectorTableName,
				this.schemaName);

		logger.info("vectorTableValidationsEnabled {}", this.schemaValidation);

		if (this.schemaValidation) {
			this.schemaValidator.validateTableSchema(this.schemaName, this.vectorTableName, idFieldName,
					contentFieldName, metadataFieldName, embeddingFieldName, this.embeddingDimensions());
		}

		if (!this.initializeSchema) {
			logger.debug("Skipping the schema initialization for the table: {}", this.getFullyQualifiedTableName());
			return;
		}

		if (this.schemaName != null)
			this.jdbcTemplate.execute(String.format("CREATE SCHEMA IF NOT EXISTS %s", this.schemaName));

		// Remove existing VectorStoreTable
		if (this.removeExistingVectorStoreTable) {
			this.jdbcTemplate.execute(String.format("DROP TABLE IF EXISTS %s", this.getFullyQualifiedTableName()));
		}

		this.jdbcTemplate.execute(String.format("""
				CREATE TABLE IF NOT EXISTS %s (
					%s UUID NOT NULL DEFAULT uuid() PRIMARY KEY,
					%s TEXT,
					%s JSON,
					%s VECTOR(%d) NOT NULL,
					VECTOR INDEX %s_idx (%s)
				) ENGINE=InnoDB
				""", this.getFullyQualifiedTableName(), idFieldName, contentFieldName, metadataFieldName,
				embeddingFieldName, this.embeddingDimensions(),
				(vectorTableName + "_" + embeddingFieldName).replaceAll("[^\\n\\r\\t\\p{Print}]", ""),
				embeddingFieldName));
	}

	private String getFullyQualifiedTableName() {
		if (this.schemaName != null)
			return this.schemaName + "." + this.vectorTableName;
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
			logger.warn("Failed to obtain the embedding dimensions from the embedding model and fall backs to"
					+ " default:" + OPENAI_EMBEDDING_DIMENSION_SIZE, e);
		}
		return OPENAI_EMBEDDING_DIMENSION_SIZE;
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {

		return VectorStoreObservationContext.builder(VectorStoreProvider.MARIADB.value(), operationName)
			.withCollectionName(this.vectorTableName)
			.withDimensions(this.embeddingDimensions())
			.withNamespace(this.schemaName)
			.withSimilarityMetric(getSimilarityMetric());
	}

	private String getSimilarityMetric() {
		if (!SIMILARITY_TYPE_MAPPING.containsKey(this.getDistanceType())) {
			return this.getDistanceType().name();
		}
		return SIMILARITY_TYPE_MAPPING.get(this.distanceType).value();
	}

	public enum MariaDBDistanceType {

		EUCLIDEAN, COSINE;

	}

	private static class DocumentRowMapper implements RowMapper<Document> {

		private final ObjectMapper objectMapper;

		public DocumentRowMapper(ObjectMapper objectMapper) {
			this.objectMapper = objectMapper;
		}

		@Override
		public Document mapRow(ResultSet rs, int rowNum) throws SQLException {
			String id = rs.getString(1);
			String content = rs.getString(2);
			Map<String, Object> metadata = toMap(rs.getString(3));
			float[] embedding = rs.getObject(4, float[].class);
			float distance = rs.getFloat(5);

			metadata.put("distance", distance);

			Document document = new Document(id, content, metadata);
			document.setEmbedding(embedding);

			return document;
		}

		private Map<String, Object> toMap(String source) {
			try {
				return (Map<String, Object>) this.objectMapper.readValue(source, Map.class);
			}
			catch (JsonProcessingException e) {
				throw new RuntimeException(e);
			}
		}

	}

	public static class Builder {

		private String contentFieldName = DEFAULT_COLUMN_CONTENT;

		private String embeddingFieldName = DEFAULT_COLUMN_EMBEDDING;

		private String idFieldName = DEFAULT_COLUMN_ID;

		private String metadataFieldName = DEFAULT_COLUMN_METADATA;

		private final JdbcTemplate jdbcTemplate;

		private final EmbeddingModel embeddingModel;

		private String schemaName = null;

		private String vectorTableName;

		private boolean vectorTableValidationsEnabled = MariaDBVectorStore.DEFAULT_SCHEMA_VALIDATION;

		private int dimensions = MariaDBVectorStore.INVALID_EMBEDDING_DIMENSION;

		private MariaDBDistanceType distanceType = MariaDBDistanceType.COSINE;

		private boolean removeExistingVectorStoreTable = false;

		private boolean initializeSchema;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private BatchingStrategy batchingStrategy = new TokenCountBatchingStrategy();

		private int maxDocumentBatchSize = MAX_DOCUMENT_BATCH_SIZE;

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

		public Builder withDistanceType(MariaDBDistanceType distanceType) {
			this.distanceType = distanceType;
			return this;
		}

		public Builder withRemoveExistingVectorStoreTable(boolean removeExistingVectorStoreTable) {
			this.removeExistingVectorStoreTable = removeExistingVectorStoreTable;
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

		public Builder withSearchObservationConvention(VectorStoreObservationConvention customObservationConvention) {
			this.searchObservationConvention = customObservationConvention;
			return this;
		}

		public Builder withBatchingStrategy(BatchingStrategy batchingStrategy) {
			this.batchingStrategy = batchingStrategy;
			return this;
		}

		public Builder withMaxDocumentBatchSize(int maxDocumentBatchSize) {
			this.maxDocumentBatchSize = maxDocumentBatchSize;
			return this;
		}

		/**
		 * Configures the content field name to use.
		 * @param name the content field name to use
		 * @return this builder
		 */
		public Builder withContentFieldName(String name) {
			this.contentFieldName = name;
			return this;
		}

		/**
		 * Configures the embedding field name to use.
		 * @param name the embedding field name to use
		 * @return this builder
		 */
		public Builder withEmbeddingFieldName(String name) {
			this.embeddingFieldName = name;
			return this;
		}

		/**
		 * Configures the id field name to use.
		 * @param name the id field name to use
		 * @return this builder
		 */
		public Builder withIdFieldName(String name) {
			this.idFieldName = name;
			return this;
		}

		/**
		 * Configures the metadata field name to use.
		 * @param name the metadata field name to use
		 * @return this builder
		 */
		public Builder withMetadataFieldName(String name) {
			this.metadataFieldName = name;
			return this;
		}

		public MariaDBVectorStore build() {
			return new MariaDBVectorStore(this.schemaName, this.vectorTableName, this.vectorTableValidationsEnabled,
					this.jdbcTemplate, this.embeddingModel, this.dimensions, this.distanceType,
					this.removeExistingVectorStoreTable, this.initializeSchema, this.observationRegistry,
					this.searchObservationConvention, this.batchingStrategy, this.maxDocumentBatchSize,
					this.contentFieldName, this.embeddingFieldName, this.idFieldName, this.metadataFieldName);
		}

	}

	/**
	 * The representation of {@link Document} along with its embedding.
	 *
	 * @param id The id of the document
	 * @param content The content of the document
	 * @param metadata The metadata of the document
	 * @param embedding The vectors representing the content of the document
	 */
	public record MariaDBDocument(String id, String content, Map<String, Object> metadata, float[] embedding) {
	}

}
