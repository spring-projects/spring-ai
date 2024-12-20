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
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
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
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * MariaDB-based vector store implementation using MariaDB's vector search capabilities.
 *
 * <p>
 * The store uses MariaDB's vector search functionality to persist and query vector
 * embeddings along with their associated document content and metadata. The
 * implementation leverages MariaDB's vector index for efficient k-NN search operations.
 * </p>
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>Automatic schema initialization with configurable index creation</li>
 * <li>Support for multiple distance functions: Cosine and Euclidean</li>
 * <li>Metadata filtering using JSON path expressions</li>
 * <li>Configurable similarity thresholds for search results</li>
 * <li>Batch processing support with configurable strategies</li>
 * <li>Observation and metrics support through Micrometer</li>
 * </ul>
 *
 * <p>
 * Basic usage example:
 * </p>
 * <pre>{@code
 * MariaDBVectorStore vectorStore = MariaDBVectorStore.builder(jdbcTemplate)
 *     .embeddingModel(embeddingModel)
 *     .initializeSchema(true)
 *     .build();
 *
 * // Add documents
 * vectorStore.add(List.of(
 *     new Document("content1", Map.of("key1", "value1")),
 *     new Document("content2", Map.of("key2", "value2"))
 * ));
 *
 * // Search with filters
 * List<Document> results = vectorStore.similaritySearch(
 *     SearchRequest.query("search text")
 *         .withTopK(5)
 *         .withSimilarityThreshold(0.7)
 *         .withFilterExpression("key1 == 'value1'")
 * );
 * }</pre>
 *
 * <p>
 * Advanced configuration example:
 * </p>
 * <pre>{@code
 * MariaDBVectorStore vectorStore = MariaDBVectorStore.builder(jdbcTemplate)
 *     .embeddingModel(embeddingModel)
 *     .schemaName("mydb")
 *     .distanceType(MariaDBDistanceType.COSINE)
 *     .dimensions(1536)
 *     .vectorTableName("custom_vectors")
 *     .contentFieldName("text")
 *     .embeddingFieldName("embedding")
 *     .idFieldName("doc_id")
 *     .metadataFieldName("meta")
 *     .initializeSchema(true)
 *     .batchingStrategy(new TokenCountBatchingStrategy())
 *     .build();
 * }</pre>
 *
 * <p>
 * Requirements:
 * </p>
 * <ul>
 * <li>MariaDB 11.3.0 or later</li>
 * <li>Table schema with id (UUID), text (TEXT), metadata (JSON), and embedding (VECTOR)
 * properties</li>
 * </ul>
 *
 * <p>
 * Distance Functions:
 * </p>
 * <ul>
 * <li>cosine: Default, suitable for most use cases. Measures cosine similarity between
 * vectors.</li>
 * <li>euclidean: Euclidean distance between vectors. Lower values indicate higher
 * similarity.</li>
 * </ul>
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

	private static final Map<MariaDBDistanceType, VectorStoreSimilarityMetric> SIMILARITY_TYPE_MAPPING = Map.of(
			MariaDBDistanceType.COSINE, VectorStoreSimilarityMetric.COSINE, MariaDBDistanceType.EUCLIDEAN,
			VectorStoreSimilarityMetric.EUCLIDEAN);

	public final FilterExpressionConverter filterExpressionConverter;

	private final String vectorTableName;

	private final JdbcTemplate jdbcTemplate;

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

	/**
	 * @deprecated Use {@link #builder(JdbcTemplate, EmbeddingModel)} (JdbcTemplate)}
	 * instead
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public MariaDBVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
		this(jdbcTemplate, embeddingModel, INVALID_EMBEDDING_DIMENSION, MariaDBDistanceType.COSINE, false, false);
	}

	/**
	 * @deprecated Use {@link #builder(JdbcTemplate, EmbeddingModel)} (JdbcTemplate)}
	 * instead
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public MariaDBVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, int dimensions) {
		this(jdbcTemplate, embeddingModel, dimensions, MariaDBDistanceType.COSINE, false, false);
	}

	/**
	 * @deprecated Use {@link #builder(JdbcTemplate, EmbeddingModel)} (JdbcTemplate)}
	 * instead
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public MariaDBVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, int dimensions,
			MariaDBDistanceType distanceType, boolean removeExistingVectorStoreTable, boolean initializeSchema) {
		this(DEFAULT_TABLE_NAME, jdbcTemplate, embeddingModel, dimensions, distanceType, removeExistingVectorStoreTable,
				initializeSchema);
	}

	/**
	 * @deprecated Use {@link #builder(JdbcTemplate, EmbeddingModel)} (JdbcTemplate)}
	 * instead
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	public MariaDBVectorStore(String vectorTableName, JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
			int dimensions, MariaDBDistanceType distanceType, boolean removeExistingVectorStoreTable,
			boolean initializeSchema) {
		this(null, vectorTableName, DEFAULT_SCHEMA_VALIDATION, jdbcTemplate, embeddingModel, dimensions, distanceType,
				removeExistingVectorStoreTable, initializeSchema);
	}

	/**
	 * @deprecated Use {@link #builder(JdbcTemplate, EmbeddingModel)} (JdbcTemplate)}
	 * instead
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	private MariaDBVectorStore(String schemaName, String vectorTableName, boolean vectorTableValidationsEnabled,
			JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, int dimensions, MariaDBDistanceType distanceType,
			boolean removeExistingVectorStoreTable, boolean initializeSchema) {
		this(schemaName, vectorTableName, vectorTableValidationsEnabled, jdbcTemplate, embeddingModel, dimensions,
				distanceType, removeExistingVectorStoreTable, initializeSchema, ObservationRegistry.NOOP, null,
				new TokenCountBatchingStrategy(), MAX_DOCUMENT_BATCH_SIZE, DEFAULT_COLUMN_EMBEDDING,
				DEFAULT_COLUMN_METADATA, DEFAULT_COLUMN_ID, DEFAULT_COLUMN_CONTENT);
	}

	/**
	 * @deprecated Use {@link #builder(JdbcTemplate, EmbeddingModel)} (JdbcTemplate)}
	 * instead
	 */
	@Deprecated(forRemoval = true, since = "1.0.0-M5")
	private MariaDBVectorStore(String schemaName, String vectorTableName, boolean vectorTableValidationsEnabled,
			JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, int dimensions, MariaDBDistanceType distanceType,
			boolean removeExistingVectorStoreTable, boolean initializeSchema, ObservationRegistry observationRegistry,
			VectorStoreObservationConvention customObservationConvention, BatchingStrategy batchingStrategy,
			int maxDocumentBatchSize, String contentFieldName, String embeddingFieldName, String idFieldName,
			String metadataFieldName) {

		this(builder(jdbcTemplate, embeddingModel).vectorTableName(vectorTableName)
			.dimensions(dimensions)
			.distanceType(distanceType)
			.removeExistingVectorStoreTable(removeExistingVectorStoreTable)
			.initializeSchema(initializeSchema)
			.observationRegistry(observationRegistry)
			.customObservationConvention(customObservationConvention)
			.batchingStrategy(batchingStrategy)
			.maxDocumentBatchSize(maxDocumentBatchSize)
			.contentFieldName(contentFieldName)
			.embeddingFieldName(embeddingFieldName)
			.idFieldName(idFieldName)
			.metadataFieldName(metadataFieldName));
	}

	/**
	 * Protected constructor for creating a MariaDBVectorStore instance using the builder
	 * pattern.
	 * @param builder the {@link MariaDBBuilder} containing all configuration settings
	 * @throws IllegalArgumentException if required parameters are missing or invalid
	 * @see MariaDBBuilder
	 * @since 1.0.0
	 */
	protected MariaDBVectorStore(MariaDBBuilder builder) {
		super(builder);

		Assert.notNull(builder.jdbcTemplate, "JdbcTemplate must not be null");

		this.objectMapper = JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build();

		this.vectorTableName = builder.vectorTableName.isEmpty() ? DEFAULT_TABLE_NAME
				: MariaDBSchemaValidator.validateAndEnquoteIdentifier(builder.vectorTableName.trim(), false);

		logger.info("Using the vector table name: {}. Is empty: {}", this.vectorTableName, vectorTableName.isEmpty());

		this.schemaName = builder.schemaName == null ? null
				: MariaDBSchemaValidator.validateAndEnquoteIdentifier(builder.schemaName, false);
		this.schemaValidation = builder.schemaValidation;
		this.jdbcTemplate = builder.jdbcTemplate;
		this.dimensions = builder.dimensions;
		this.distanceType = builder.distanceType;
		this.removeExistingVectorStoreTable = builder.removeExistingVectorStoreTable;
		this.initializeSchema = builder.initializeSchema;
		this.schemaValidator = new MariaDBSchemaValidator(jdbcTemplate);
		this.batchingStrategy = builder.batchingStrategy;
		this.maxDocumentBatchSize = builder.maxDocumentBatchSize;

		this.contentFieldName = MariaDBSchemaValidator.validateAndEnquoteIdentifier(builder.contentFieldName, false);
		this.embeddingFieldName = MariaDBSchemaValidator.validateAndEnquoteIdentifier(builder.embeddingFieldName,
				false);
		this.idFieldName = MariaDBSchemaValidator.validateAndEnquoteIdentifier(builder.idFieldName, false);
		this.metadataFieldName = MariaDBSchemaValidator.validateAndEnquoteIdentifier(builder.metadataFieldName, false);
		filterExpressionConverter = new MariaDBFilterExpressionConverter(this.metadataFieldName);
	}

	/**
	 * Creates a new MariaDBBuilder instance. This is the recommended way to instantiate a
	 * MariaDBVectorStore.
	 * @return a new MariaDBBuilder instance
	 */
	public static MariaDBBuilder builder(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
		return new MariaDBBuilder(jdbcTemplate, embeddingModel);
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
				mariaDBDocuments.add(new MariaDBDocument(document.getId(), document.getText(), document.getMetadata(),
						embeddings.get(documents.indexOf(document))));
			}
		}
		else {
			for (Document document : documents) {
				mariaDBDocuments
					.add(new MariaDBDocument(document.getId(), document.getText(), document.getMetadata(), null));
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
				"SELECT * FROM (select %s, %s, %s, vec_distance_%s(%s, ?) as distance "
						+ "from %s) as t where distance < ? %sorder by distance asc LIMIT ?",
				this.idFieldName, this.contentFieldName, this.metadataFieldName, distanceType, this.embeddingFieldName,
				getFullyQualifiedTableName(), jsonPathFilter);

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
			.collectionName(this.vectorTableName)
			.dimensions(this.embeddingDimensions())
			.namespace(this.schemaName)
			.similarityMetric(getSimilarityMetric());
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
			float distance = rs.getFloat(4);

			metadata.put("distance", distance);

			return new Document(id, content, metadata);
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

	/**
	 * Builder for creating instances of {@link MariaDBVectorStore}. This builder provides
	 * a fluent API for configuring all aspects of the vector store.
	 *
	 * @since 1.0.0
	 */
	public static final class MariaDBBuilder extends AbstractVectorStoreBuilder<MariaDBBuilder> {

		private String contentFieldName = DEFAULT_COLUMN_CONTENT;

		private String embeddingFieldName = DEFAULT_COLUMN_EMBEDDING;

		private String idFieldName = DEFAULT_COLUMN_ID;

		private String metadataFieldName = DEFAULT_COLUMN_METADATA;

		private final JdbcTemplate jdbcTemplate;

		@Nullable
		private String schemaName;

		private String vectorTableName = DEFAULT_TABLE_NAME;

		private boolean schemaValidation = DEFAULT_SCHEMA_VALIDATION;

		private int dimensions = INVALID_EMBEDDING_DIMENSION;

		private MariaDBDistanceType distanceType = MariaDBDistanceType.COSINE;

		private boolean removeExistingVectorStoreTable = false;

		private boolean initializeSchema = false;

		private BatchingStrategy batchingStrategy = new TokenCountBatchingStrategy();

		private int maxDocumentBatchSize = MAX_DOCUMENT_BATCH_SIZE;

		/**
		 * Creates a new builder instance with the required JDBC template.
		 * @param jdbcTemplate the JDBC template for database operations
		 * @throws IllegalArgumentException if jdbcTemplate is null
		 */
		private MariaDBBuilder(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(jdbcTemplate, "JdbcTemplate must not be null");
			this.jdbcTemplate = jdbcTemplate;
		}

		/**
		 * Configures the schema name for the vector store table.
		 * @param schemaName the database schema name (can be null for default schema)
		 * @return this builder instance
		 */
		public MariaDBBuilder schemaName(String schemaName) {
			this.schemaName = schemaName;
			return this;
		}

		/**
		 * Configures the vector store table name.
		 * @param vectorTableName the name for the vector store table (defaults to
		 * {@value DEFAULT_TABLE_NAME})
		 * @return this builder instance
		 */
		public MariaDBBuilder vectorTableName(String vectorTableName) {
			this.vectorTableName = vectorTableName;
			return this;
		}

		/**
		 * Configures whether schema validation should be performed.
		 * @param schemaValidation true to enable schema validation, false to disable
		 * @return this builder instance
		 */
		public MariaDBBuilder schemaValidation(boolean schemaValidation) {
			this.schemaValidation = schemaValidation;
			return this;
		}

		/**
		 * Configures the dimension size of the embedding vectors.
		 * @param dimensions the dimension of the embeddings
		 * @return this builder instance
		 */
		public MariaDBBuilder dimensions(int dimensions) {
			this.dimensions = dimensions;
			return this;
		}

		/**
		 * Configures the distance type used for similarity calculations.
		 * @param distanceType the distance type to use
		 * @return this builder instance
		 * @throws IllegalArgumentException if distanceType is null
		 */
		public MariaDBBuilder distanceType(MariaDBDistanceType distanceType) {
			Assert.notNull(distanceType, "DistanceType must not be null");
			this.distanceType = distanceType;
			return this;
		}

		/**
		 * Configures whether to remove any existing vector store table.
		 * @param removeExistingVectorStoreTable true to remove existing table, false to
		 * keep it
		 * @return this builder instance
		 */
		public MariaDBBuilder removeExistingVectorStoreTable(boolean removeExistingVectorStoreTable) {
			this.removeExistingVectorStoreTable = removeExistingVectorStoreTable;
			return this;
		}

		/**
		 * Configures whether to initialize the database schema.
		 * @param initializeSchema true to initialize schema, false otherwise
		 * @return this builder instance
		 */
		public MariaDBBuilder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		/**
		 * Configures the strategy for batching operations.
		 * @param batchingStrategy the batching strategy to use
		 * @return this builder instance
		 * @throws IllegalArgumentException if batchingStrategy is null
		 */
		public MariaDBBuilder batchingStrategy(BatchingStrategy batchingStrategy) {
			Assert.notNull(batchingStrategy, "BatchingStrategy must not be null");
			this.batchingStrategy = batchingStrategy;
			return this;
		}

		/**
		 * Configures the maximum batch size for document operations.
		 * @param maxDocumentBatchSize the maximum number of documents to process in a
		 * batch
		 * @return this builder instance
		 */
		public MariaDBBuilder maxDocumentBatchSize(int maxDocumentBatchSize) {
			Assert.isTrue(maxDocumentBatchSize > 0, "MaxDocumentBatchSize must be positive");
			this.maxDocumentBatchSize = maxDocumentBatchSize;
			return this;
		}

		/**
		 * Configures the name of the content field in the database.
		 * @param name the field name for document content (defaults to
		 * {@value DEFAULT_COLUMN_CONTENT})
		 * @return this builder instance
		 * @throws IllegalArgumentException if name is null or empty
		 */
		public MariaDBBuilder contentFieldName(String name) {
			Assert.hasText(name, "ContentFieldName must not be empty");
			this.contentFieldName = name;
			return this;
		}

		/**
		 * Configures the name of the embedding field in the database.
		 * @param name the field name for embeddings (defaults to
		 * {@value DEFAULT_COLUMN_EMBEDDING})
		 * @return this builder instance
		 * @throws IllegalArgumentException if name is null or empty
		 */
		public MariaDBBuilder embeddingFieldName(String name) {
			Assert.hasText(name, "EmbeddingFieldName must not be empty");
			this.embeddingFieldName = name;
			return this;
		}

		/**
		 * Configures the name of the ID field in the database.
		 * @param name the field name for document IDs (defaults to
		 * {@value DEFAULT_COLUMN_ID})
		 * @return this builder instance
		 * @throws IllegalArgumentException if name is null or empty
		 */
		public MariaDBBuilder idFieldName(String name) {
			Assert.hasText(name, "IdFieldName must not be empty");
			this.idFieldName = name;
			return this;
		}

		/**
		 * Configures the name of the metadata field in the database.
		 * @param name the field name for document metadata (defaults to
		 * {@value DEFAULT_COLUMN_METADATA})
		 * @return this builder instance
		 * @throws IllegalArgumentException if name is null or empty
		 */
		public MariaDBBuilder metadataFieldName(String name) {
			Assert.hasText(name, "MetadataFieldName must not be empty");
			this.metadataFieldName = name;
			return this;
		}

		/**
		 * Builds and returns a new MariaDBVectorStore instance with the configured
		 * settings.
		 * @return a new MariaDBVectorStore instance
		 * @throws IllegalStateException if the builder configuration is invalid
		 */
		@Override
		public MariaDBVectorStore build() {
			return new MariaDBVectorStore(this);
		}

	}

	@Deprecated(forRemoval = true, since = "1.0.0-M5")
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
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
			if (jdbcTemplate == null || embeddingModel == null) {
				throw new IllegalArgumentException("JdbcTemplate and EmbeddingModel must not be null");
			}
			this.jdbcTemplate = jdbcTemplate;
			this.embeddingModel = embeddingModel;
		}

		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withSchemaName(String schemaName) {
			this.schemaName = schemaName;
			return this;
		}

		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withVectorTableName(String vectorTableName) {
			this.vectorTableName = vectorTableName;
			return this;
		}

		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withVectorTableValidationsEnabled(boolean vectorTableValidationsEnabled) {
			this.vectorTableValidationsEnabled = vectorTableValidationsEnabled;
			return this;
		}

		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withDimensions(int dimensions) {
			this.dimensions = dimensions;
			return this;
		}

		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withDistanceType(MariaDBDistanceType distanceType) {
			this.distanceType = distanceType;
			return this;
		}

		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withRemoveExistingVectorStoreTable(boolean removeExistingVectorStoreTable) {
			this.removeExistingVectorStoreTable = removeExistingVectorStoreTable;
			return this;
		}

		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withInitializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withObservationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withSearchObservationConvention(VectorStoreObservationConvention customObservationConvention) {
			this.searchObservationConvention = customObservationConvention;
			return this;
		}

		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withBatchingStrategy(BatchingStrategy batchingStrategy) {
			this.batchingStrategy = batchingStrategy;
			return this;
		}

		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withMaxDocumentBatchSize(int maxDocumentBatchSize) {
			this.maxDocumentBatchSize = maxDocumentBatchSize;
			return this;
		}

		/**
		 * Configures the content field name to use.
		 * @param name the content field name to use
		 * @return this builder
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withContentFieldName(String name) {
			this.contentFieldName = name;
			return this;
		}

		/**
		 * Configures the embedding field name to use.
		 * @param name the embedding field name to use
		 * @return this builder
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withEmbeddingFieldName(String name) {
			this.embeddingFieldName = name;
			return this;
		}

		/**
		 * Configures the id field name to use.
		 * @param name the id field name to use
		 * @return this builder
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withIdFieldName(String name) {
			this.idFieldName = name;
			return this;
		}

		/**
		 * Configures the metadata field name to use.
		 * @param name the metadata field name to use
		 * @return this builder
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withMetadataFieldName(String name) {
			this.metadataFieldName = name;
			return this;
		}

		@Deprecated(forRemoval = true, since = "1.0.0-M5")
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
	public record MariaDBDocument(String id, @Nullable String content, Map<String, Object> metadata,
			@Nullable float[] embedding) {
	}

}
