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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.pgvector.PGbit;
import com.pgvector.PGhalfvec;
import com.pgvector.PGsparsevec;
import com.pgvector.PGvector;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.StatementCreatorUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * PostgreSQL-based vector store implementation using the pgvector extension.
 *
 * <p>
 * The store uses a database table to persist the vector embeddings along with their
 * associated document content and metadata. By default, it uses the "vector_store" table
 * in the "public" schema, but this can be configured.
 * </p>
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>Automatic schema initialization with configurable table and index creation</li>
 * <li>Multiple pgvector column types: {@code vector} (default), {@code halfvec},
 * {@code bit}, {@code sparsevec}</li>
 * <li>Support for six distance metrics: cosine, Euclidean, inner product, L1, Hamming,
 * Jaccard (the last two only for {@code bit})</li>
 * <li>Flexible indexing options: HNSW (default), IVFFlat, or exact search (no index)</li>
 * <li>Metadata filtering using JSON path expressions</li>
 * <li>Configurable similarity thresholds for search results</li>
 * <li>Batch processing support with configurable batch sizes</li>
 * </ul>
 *
 * <p>
 * Basic usage example:
 * </p>
 * <pre>{@code
 * PgVectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
 *     .dimensions(1536) // Optional: defaults to model dimensions or 1536
 *     .distanceType(PgDistanceType.COSINE_DISTANCE)
 *     .indexType(PgIndexType.HNSW)
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
 * PgVectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
 *     .schemaName("custom_schema")
 *     .vectorTableName("custom_vectors")
 *     .distanceType(PgDistanceType.NEGATIVE_INNER_PRODUCT)
 *     .removeExistingVectorStoreTable(true)
 *     .initializeSchema(true)
 *     .maxDocumentBatchSize(1000)
 *     .build();
 * }</pre>
 *
 * <p>
 * Extended vector types (pgvector 0.7+):
 * </p>
 * <pre>{@code
 * // halfvec: half the storage of vector at FP16 precision.
 * // Recommended for normalized embeddings (OpenAI text-embedding-3-*, Cohere, etc.)
 * // where the tiny precision loss is negligible.
 * PgVectorStore halfvecStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
 *     .vectorType(PgVectorType.HALFVEC)
 *     .dimensions(1536)
 *     .distanceType(PgDistanceType.COSINE_DISTANCE)
 *     .initializeSchema(true)
 *     .build();
 *
 * // bit: binary-quantized embeddings. Only HAMMING_DISTANCE or JACCARD_DISTANCE.
 * // Float embeddings are sign-bit quantized in Java (x > 0f -> 1) before bind.
 * // If your embedding model needs a different quantization strategy, pre-quantize
 * // in your own EmbeddingModel.
 * PgVectorStore bitStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
 *     .vectorType(PgVectorType.BIT)
 *     .dimensions(1536)
 *     .distanceType(PgDistanceType.HAMMING_DISTANCE)
 *     .initializeSchema(true)
 *     .build();
 *
 * // sparsevec: stores only non-zero elements, up to 16,000 non-zeros.
 * // Useful for high-dimensional sparse embeddings (e.g. SPLADE).
 * PgVectorStore sparseStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
 *     .vectorType(PgVectorType.SPARSEVEC)
 *     .dimensions(30000)
 *     .distanceType(PgDistanceType.COSINE_DISTANCE)
 *     .initializeSchema(true)
 *     .build();
 * }</pre>
 *
 * <p>
 * Database Requirements:
 * </p>
 * <ul>
 * <li>PostgreSQL with pgvector extension installed</li>
 * <li>Required extensions: vector, hstore, uuid-ossp</li>
 * <li>Table schema with id (uuid), content (text), metadata (json), and embedding column
 * (type depends on {@link PgVectorType})</li>
 * <li>pgvector 0.7+ for {@code halfvec} / {@code bit} / {@code sparsevec} and the
 * {@code L1_DISTANCE} metric</li>
 * </ul>
 *
 * <p>
 * Vector Types (see {@link PgVectorType}):
 * </p>
 * <ul>
 * <li>VECTOR: Default, 4 bytes per dimension, up to 16,000 dims</li>
 * <li>HALFVEC: 2 bytes per dimension (FP16), same metric set as VECTOR</li>
 * <li>BIT: 1 bit per dimension, only HAMMING/JACCARD distance</li>
 * <li>SPARSEVEC: Stores non-zero elements only, same metric set as VECTOR</li>
 * </ul>
 *
 * <p>
 * Distance Types (see {@link PgDistanceType}):
 * </p>
 * <ul>
 * <li>COSINE_DISTANCE: Default, suitable for most use cases</li>
 * <li>EUCLIDEAN_DISTANCE: L2 distance between vectors</li>
 * <li>NEGATIVE_INNER_PRODUCT: Best performance for normalized vectors (e.g., OpenAI
 * embeddings)</li>
 * <li>L1_DISTANCE: Taxicab distance (VECTOR/HALFVEC/SPARSEVEC only)</li>
 * <li>HAMMING_DISTANCE / JACCARD_DISTANCE: Bit-vector metrics (BIT only)</li>
 * </ul>
 *
 * <p>
 * Index Types:
 * </p>
 * <ul>
 * <li>HNSW: Default, better query performance but slower builds and more memory</li>
 * <li>IVFFLAT: Faster builds, less memory, but lower query performance</li>
 * <li>NONE: Exact search without indexing</li>
 * </ul>
 *
 * @author Christian Tzolov
 * @author Josh Long
 * @author Muthukumaran Navaneethakrishnan
 * @author Thomas Vitale
 * @author Soby Chacko
 * @author Sebastien Deleuze
 * @author Jihoon Kim
 * @author YeongMin Song
 * @author Jonghoon Park
 * @since 1.0.0
 */
public class PgVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	public static final int OPENAI_EMBEDDING_DIMENSION_SIZE = 1536;

	public static final int INVALID_EMBEDDING_DIMENSION = -1;

	public static final String DEFAULT_TABLE_NAME = "vector_store";

	public static final PgIdType DEFAULT_ID_TYPE = PgIdType.UUID;

	public static final String DEFAULT_VECTOR_INDEX_NAME = "spring_ai_vector_index";

	public static final String DEFAULT_SCHEMA_NAME = "public";

	public static final boolean DEFAULT_SCHEMA_VALIDATION = false;

	public static final int MAX_DOCUMENT_BATCH_SIZE = 10_000;

	private static final Logger logger = LoggerFactory.getLogger(PgVectorStore.class);

	private static final Map<PgDistanceType, VectorStoreSimilarityMetric> SIMILARITY_TYPE_MAPPING = Map.of(
			PgDistanceType.COSINE_DISTANCE, VectorStoreSimilarityMetric.COSINE, PgDistanceType.EUCLIDEAN_DISTANCE,
			VectorStoreSimilarityMetric.EUCLIDEAN, PgDistanceType.NEGATIVE_INNER_PRODUCT,
			VectorStoreSimilarityMetric.DOT);

	public final FilterExpressionConverter filterExpressionConverter = new PgVectorFilterExpressionConverter();

	private final String vectorTableName;

	private final String vectorIndexName;

	private final JdbcTemplate jdbcTemplate;

	private final String schemaName;

	private final PgIdType idType;

	private final boolean schemaValidation;

	private final boolean initializeSchema;

	private final int dimensions;

	private final PgDistanceType distanceType;

	private final JsonMapper jsonMapper;

	private final DocumentRowMapper documentRowMapper;

	private final boolean removeExistingVectorStoreTable;

	private final PgIndexType createIndexMethod;

	private final PgVectorType vectorType;

	private final PgVectorSchemaValidator schemaValidator;

	private final int maxDocumentBatchSize;

	/**
	 * @param builder {@link VectorStore.Builder} for pg vector store
	 */
	protected PgVectorStore(PgVectorStoreBuilder builder) {
		super(builder);

		Assert.notNull(builder.jdbcTemplate, "JdbcTemplate must not be null");

		this.jsonMapper = JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build();
		this.documentRowMapper = new DocumentRowMapper(this.jsonMapper);

		String vectorTable = builder.vectorTableName;
		this.vectorTableName = vectorTable.isEmpty() ? DEFAULT_TABLE_NAME : vectorTable.trim();
		logger.info("Using the vector table name: {}. Is empty: {}", this.vectorTableName,
				this.vectorTableName.isEmpty());

		this.vectorIndexName = this.vectorTableName.equals(DEFAULT_TABLE_NAME) ? DEFAULT_VECTOR_INDEX_NAME
				: this.vectorTableName + "_index";

		this.schemaName = builder.schemaName;
		this.idType = builder.idType;
		this.schemaValidation = builder.vectorTableValidationsEnabled;

		this.jdbcTemplate = builder.jdbcTemplate;
		this.dimensions = builder.dimensions;
		this.distanceType = builder.distanceType;
		this.removeExistingVectorStoreTable = builder.removeExistingVectorStoreTable;
		this.createIndexMethod = builder.indexType;
		this.initializeSchema = builder.initializeSchema;
		this.vectorType = builder.vectorType;
		this.schemaValidator = new PgVectorSchemaValidator(this.jdbcTemplate);
		this.maxDocumentBatchSize = builder.maxDocumentBatchSize;

		if (!this.vectorType.supports(this.distanceType)) {
			throw new IllegalArgumentException("Distance type " + this.distanceType
					+ " is not supported by vector type " + this.vectorType
					+ ". Valid combinations: vector/halfvec/sparsevec → COSINE/EUCLIDEAN/NEGATIVE_INNER_PRODUCT/L1; bit → HAMMING/JACCARD.");
		}
	}

	public PgDistanceType getDistanceType() {
		return this.distanceType;
	}

	public static PgVectorStoreBuilder builder(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
		return new PgVectorStoreBuilder(jdbcTemplate, embeddingModel);
	}

	@Override
	public void doAdd(List<Document> documents) {
		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
				this.batchingStrategy);

		List<List<Document>> batchedDocuments = batchDocuments(documents);
		batchedDocuments.forEach(batchDocument -> insertOrUpdateBatch(batchDocument, documents, embeddings));
	}

	private List<List<Document>> batchDocuments(List<Document> documents) {
		List<List<Document>> batches = new ArrayList<>();
		for (int i = 0; i < documents.size(); i += this.maxDocumentBatchSize) {
			batches.add(documents.subList(i, Math.min(i + this.maxDocumentBatchSize, documents.size())));
		}
		return batches;
	}

	private void insertOrUpdateBatch(List<Document> batch, List<Document> documents, List<float[]> embeddings) {
		String sql = "INSERT INTO " + getFullyQualifiedTableName()
				+ " (id, content, metadata, embedding) VALUES (?, ?, ?::jsonb, ?) " + "ON CONFLICT (id) DO "
				+ "UPDATE SET content = ? , metadata = ?::jsonb , embedding = ? ";

		this.jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {

			@Override
			public void setValues(PreparedStatement ps, int i) throws SQLException {

				var document = batch.get(i);
				var id = convertIdToPgType(document.getId());
				var content = document.getText();
				var json = toJson(document.getMetadata());
				var embedding = embeddings.get(documents.indexOf(document));
				var pgEmbedding = PgVectorStore.this.vectorType.toPgObject(embedding);

				StatementCreatorUtils.setParameterValue(ps, 1, SqlTypeValue.TYPE_UNKNOWN, id);
				StatementCreatorUtils.setParameterValue(ps, 2, SqlTypeValue.TYPE_UNKNOWN, content);
				StatementCreatorUtils.setParameterValue(ps, 3, SqlTypeValue.TYPE_UNKNOWN, json);
				StatementCreatorUtils.setParameterValue(ps, 4, SqlTypeValue.TYPE_UNKNOWN, pgEmbedding);
				StatementCreatorUtils.setParameterValue(ps, 5, SqlTypeValue.TYPE_UNKNOWN, content);
				StatementCreatorUtils.setParameterValue(ps, 6, SqlTypeValue.TYPE_UNKNOWN, json);
				StatementCreatorUtils.setParameterValue(ps, 7, SqlTypeValue.TYPE_UNKNOWN, pgEmbedding);
			}

			@Override
			public int getBatchSize() {
				return batch.size();
			}
		});
	}

	private String toJson(Map<String, Object> map) {
		return this.jsonMapper.writeValueAsString(map);
	}

	private Object convertIdToPgType(String id) {
		return switch (getIdType()) {
			case UUID -> UUID.fromString(id);
			case TEXT -> id;
			case INTEGER, SERIAL -> Integer.valueOf(id);
			case BIGSERIAL -> Long.valueOf(id);
		};
	}

	@Override
	public void doDelete(List<String> idList) {
		String sql = "DELETE FROM " + getFullyQualifiedTableName() + " WHERE id = ?";

		this.jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {

			@Override
			public void setValues(PreparedStatement ps, int i) throws SQLException {
				var id = idList.get(i);
				StatementCreatorUtils.setParameterValue(ps, 1, SqlTypeValue.TYPE_UNKNOWN, convertIdToPgType(id));
			}

			@Override
			public int getBatchSize() {
				return idList.size();
			}
		});
	}

	@Override
	protected void doDelete(Filter.Expression filterExpression) {
		String nativeFilterExpression = this.filterExpressionConverter.convertExpression(filterExpression);

		String sql = "DELETE FROM " + getFullyQualifiedTableName() + " WHERE metadata::jsonb @@ '"
				+ nativeFilterExpression + "'::jsonpath";

		// Execute the delete
		try {
			this.jdbcTemplate.update(sql);
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to delete documents by filter", e);
		}
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

		PGobject queryEmbedding = getQueryEmbedding(request.getQuery());

		return this.jdbcTemplate.query(
				String.format(this.getDistanceType().similaritySearchSqlTemplate, getFullyQualifiedTableName(),
						jsonPathFilter),
				this.documentRowMapper, queryEmbedding, queryEmbedding, distance, request.getTopK());
	}

	public List<Double> embeddingDistance(String query) {
		return this.jdbcTemplate.query(
				"SELECT embedding " + this.comparisonOperator() + " ? AS distance FROM " + getFullyQualifiedTableName(),
				new RowMapper<>() {

					@Override
					public Double mapRow(ResultSet rs, int rowNum) throws SQLException {
						return rs.getDouble(DocumentRowMapper.COLUMN_DISTANCE);
					}

				}, getQueryEmbedding(query));
	}

	private PGobject getQueryEmbedding(String query) {
		float[] embedding = this.embeddingModel.embed(query);
		return this.vectorType.toPgObject(embedding);
	}

	private String comparisonOperator() {
		return this.getDistanceType().operator;
	}

	// ---------------------------------------------------------------------------------
	// Initialize
	// ---------------------------------------------------------------------------------
	@Override
	public void afterPropertiesSet() {

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

		if (this.idType == PgIdType.UUID) {
			this.jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
		}

		this.jdbcTemplate.execute(String.format("CREATE SCHEMA IF NOT EXISTS %s", this.getSchemaName()));

		// Remove existing VectorStoreTable
		if (this.removeExistingVectorStoreTable) {
			this.jdbcTemplate.execute(String.format("DROP TABLE IF EXISTS %s", this.getFullyQualifiedTableName()));
		}

		this.jdbcTemplate.execute(String.format("""
				CREATE TABLE IF NOT EXISTS %s (
					id %s PRIMARY KEY,
					content text,
					metadata json,
					embedding %s
				)
				""", this.getFullyQualifiedTableName(), this.getColumnTypeName(),
				this.vectorType.columnDefinition(this.embeddingDimensions())));

		if (this.createIndexMethod != PgIndexType.NONE) {
			this.jdbcTemplate.execute(String.format("""
					CREATE INDEX IF NOT EXISTS %s ON %s USING %s (embedding %s)
					""", this.getVectorIndexName(), this.getFullyQualifiedTableName(), this.createIndexMethod,
					this.vectorType.opclass(this.distanceType)));
		}
	}

	private String getFullyQualifiedTableName() {
		return this.schemaName + "." + this.vectorTableName;
	}

	private PgIdType getIdType() {
		return this.idType;
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

	private String getColumnTypeName() {
		return switch (getIdType()) {
			case UUID -> "uuid DEFAULT uuid_generate_v4()";
			case TEXT -> "text";
			case INTEGER -> "integer";
			case SERIAL -> "serial";
			case BIGSERIAL -> "bigserial";
		};
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

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {

		return VectorStoreObservationContext.builder(VectorStoreProvider.PG_VECTOR.value(), operationName)
			.collectionName(this.vectorTableName)
			.dimensions(this.embeddingDimensions())
			.namespace(this.schemaName)
			.similarityMetric(getSimilarityMetric());
	}

	private String getSimilarityMetric() {
		VectorStoreSimilarityMetric metric = SIMILARITY_TYPE_MAPPING.get(this.distanceType);
		return metric != null ? metric.value() : this.distanceType.name();
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this.jdbcTemplate;
		return Optional.of(client);
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
		HNSW

	}

	/**
	 * The ID type for the Pg vector store schema. Defaults to UUID.
	 */
	public enum PgIdType {

		UUID, TEXT, INTEGER, SERIAL, BIGSERIAL

	}

	/**
	 * The pgvector column type used to store embeddings. Different types have different
	 * trade-offs in storage size, precision and supported distance metrics:
	 * <ul>
	 * <li>{@link #VECTOR}: default 4-byte floats, any of
	 * {@link PgDistanceType#COSINE_DISTANCE}, {@link PgDistanceType#EUCLIDEAN_DISTANCE},
	 * {@link PgDistanceType#NEGATIVE_INNER_PRODUCT}, {@link PgDistanceType#L1_DISTANCE}.
	 * Up to 16,000 dimensions.</li>
	 * <li>{@link #HALFVEC}: half-precision 2-byte floats, same metrics as VECTOR. Up to
	 * 16,000 dimensions. Halves storage at a small precision cost. Requires pgvector
	 * 0.7+.</li>
	 * <li>{@link #BIT}: binary (1 bit per element), only supports
	 * {@link PgDistanceType#HAMMING_DISTANCE} and
	 * {@link PgDistanceType#JACCARD_DISTANCE}. Up to 64,000 dimensions. The
	 * {@code float[]} embedding is sign-bit quantized in Java ({@code x > 0f → 1}) before
	 * being bound as a {@code PGbit}. This is intentionally non-configurable; callers
	 * needing a different quantization strategy should pre-quantize in their
	 * {@code EmbeddingModel}. Requires pgvector 0.7+.</li>
	 * <li>{@link #SPARSEVEC}: sparse vectors that only store non-zero elements, same
	 * metrics as VECTOR. Up to 16,000 non-zero elements. Requires pgvector 0.7+.</li>
	 * </ul>
	 */
	public enum PgVectorType {

		VECTOR("vector"), HALFVEC("halfvec"), BIT("bit"), SPARSEVEC("sparsevec");

		private final String sqlType;

		PgVectorType(String sqlType) {
			this.sqlType = sqlType;
		}

		/**
		 * @return DDL column type, e.g. {@code "halfvec(1536)"}.
		 */
		public String columnDefinition(int dimensions) {
			return this.sqlType + "(" + dimensions + ")";
		}

		/**
		 * @return the full opclass name to be used in CREATE INDEX, e.g.
		 * {@code "halfvec_cosine_ops"}. Throws if the distance type is not supported by
		 * this vector type.
		 */
		public String opclass(PgDistanceType distanceType) {
			if (!supports(distanceType)) {
				throw new IllegalArgumentException(
						"Distance type " + distanceType + " is not supported by vector type " + this);
			}
			return this.sqlType + "_" + distanceType.opclassSuffix;
		}

		/**
		 * @return whether the given distance type is compatible with this vector type.
		 */
		public boolean supports(PgDistanceType distanceType) {
			return switch (this) {
				case VECTOR, HALFVEC, SPARSEVEC ->
					distanceType == PgDistanceType.COSINE_DISTANCE || distanceType == PgDistanceType.EUCLIDEAN_DISTANCE
							|| distanceType == PgDistanceType.NEGATIVE_INNER_PRODUCT
							|| distanceType == PgDistanceType.L1_DISTANCE;
				case BIT ->
					distanceType == PgDistanceType.HAMMING_DISTANCE || distanceType == PgDistanceType.JACCARD_DISTANCE;
			};
		}

		/**
		 * Convert a dense float embedding produced by the {@code EmbeddingModel} into the
		 * appropriate {@link PGobject} for binding to a prepared statement. For
		 * {@link #BIT} the embedding is collapsed into bits by a fixed sign-bit rule
		 * ({@code x > 0f → 1}); this is the same semantic as pgvector's native
		 * {@code binary_quantize()} function and is intentionally non-configurable to
		 * keep the API surface small. Callers whose embedding model needs a different
		 * quantization strategy should pre-quantize in their {@code EmbeddingModel} or
		 * customize at the JDBC layer.
		 */
		public PGobject toPgObject(float[] embedding) {
			return switch (this) {
				case VECTOR -> new PGvector(embedding);
				case HALFVEC -> new PGhalfvec(embedding);
				case SPARSEVEC -> new PGsparsevec(embedding);
				case BIT -> {
					boolean[] bits = new boolean[embedding.length];
					for (int i = 0; i < embedding.length; i++) {
						bits[i] = embedding[i] > 0f;
					}
					yield new PGbit(bits);
				}
			};
		}

	}

	/**
	 * Defaults to CosineDistance. But if vectors are normalized to length 1 (like OpenAI
	 * embeddings), use inner product (NegativeInnerProduct) for best performance.
	 */
	public enum PgDistanceType {

		// NOTE: works only if vectors are normalized to length 1 (like OpenAI
		// embeddings), use inner product for best performance.
		// The Sentence transformers are NOT normalized:
		// https://github.com/UKPLab/sentence-transformers/issues/233
		EUCLIDEAN_DISTANCE("<->", "vector_l2_ops", "l2_ops",
				"SELECT *, embedding <-> ? AS distance FROM %s WHERE embedding <-> ? < ? %s ORDER BY distance LIMIT ? "),

		// NOTE: works only if vectors are normalized to length 1 (like OpenAI
		// embeddings), use inner product for best performance.
		// The Sentence transformers are NOT normalized:
		// https://github.com/UKPLab/sentence-transformers/issues/233
		NEGATIVE_INNER_PRODUCT("<#>", "vector_ip_ops", "ip_ops",
				"SELECT *, (1 + (embedding <#> ?)) AS distance FROM %s WHERE (1 + (embedding <#> ?)) < ? %s ORDER BY distance LIMIT ? "),

		COSINE_DISTANCE("<=>", "vector_cosine_ops", "cosine_ops",
				"SELECT *, embedding <=> ? AS distance FROM %s WHERE embedding <=> ? < ? %s ORDER BY distance LIMIT ? "),

		/**
		 * Taxicab (L1) distance. Requires pgvector 0.7+. Supported by vector, halfvec and
		 * sparsevec column types.
		 */
		L1_DISTANCE("<+>", "vector_l1_ops", "l1_ops",
				"SELECT *, embedding <+> ? AS distance FROM %s WHERE embedding <+> ? < ? %s ORDER BY distance LIMIT ? "),

		/**
		 * Hamming distance between bit vectors. Only supported by the bit column type.
		 */
		HAMMING_DISTANCE("<~>", "bit_hamming_ops", "hamming_ops",
				"SELECT *, embedding <~> ? AS distance FROM %s WHERE embedding <~> ? < ? %s ORDER BY distance LIMIT ? "),

		/**
		 * Jaccard distance between bit vectors. Only supported by the bit column type.
		 */
		JACCARD_DISTANCE("<%>", "bit_jaccard_ops", "jaccard_ops",
				"SELECT *, embedding <%%> ? AS distance FROM %s WHERE embedding <%%> ? < ? %s ORDER BY distance LIMIT ? ");

		public final String operator;

		public final String index;

		/**
		 * Opclass suffix used to compose the full opclass name together with a
		 * {@link PgVectorType} prefix (e.g. {@code "cosine_ops"} →
		 * {@code "halfvec_cosine_ops"}).
		 */
		public final String opclassSuffix;

		public final String similaritySearchSqlTemplate;

		PgDistanceType(String operator, String index, String opclassSuffix, String sqlTemplate) {
			this.operator = operator;
			this.index = index;
			this.opclassSuffix = opclassSuffix;
			this.similaritySearchSqlTemplate = sqlTemplate;
		}

	}

	private static class DocumentRowMapper implements RowMapper<Document> {

		private static final String COLUMN_METADATA = "metadata";

		private static final String COLUMN_ID = "id";

		private static final String COLUMN_CONTENT = "content";

		private static final String COLUMN_DISTANCE = "distance";

		private final JsonMapper jsonMapper;

		DocumentRowMapper(JsonMapper jsonMapper) {
			this.jsonMapper = jsonMapper;
		}

		@Override
		public Document mapRow(ResultSet rs, int rowNum) throws SQLException {
			String id = rs.getString(COLUMN_ID);
			String content = rs.getString(COLUMN_CONTENT);
			PGobject pgMetadata = rs.getObject(COLUMN_METADATA, PGobject.class);
			Float distance = rs.getFloat(COLUMN_DISTANCE);

			Map<String, Object> metadata = toMap(pgMetadata);
			metadata.put(DocumentMetadata.DISTANCE.value(), distance);

			// @formatter:off
			return Document.builder()
				.id(id)
				.text(content)
				.metadata(metadata)
				.score(1.0 - distance)
				.build(); // @formatter:on
		}

		private Map<String, Object> toMap(PGobject pgObject) {

			String source = pgObject.getValue();
			return (Map<String, Object>) this.jsonMapper.readValue(source, Map.class);
		}

	}

	public static final class PgVectorStoreBuilder extends AbstractVectorStoreBuilder<PgVectorStoreBuilder> {

		private final JdbcTemplate jdbcTemplate;

		private String schemaName = PgVectorStore.DEFAULT_SCHEMA_NAME;

		private String vectorTableName = PgVectorStore.DEFAULT_TABLE_NAME;

		private PgIdType idType = PgVectorStore.DEFAULT_ID_TYPE;

		private boolean vectorTableValidationsEnabled = PgVectorStore.DEFAULT_SCHEMA_VALIDATION;

		private int dimensions = PgVectorStore.INVALID_EMBEDDING_DIMENSION;

		private PgDistanceType distanceType = PgDistanceType.COSINE_DISTANCE;

		private boolean removeExistingVectorStoreTable = false;

		private PgIndexType indexType = PgIndexType.HNSW;

		private boolean initializeSchema;

		private PgVectorType vectorType = PgVectorType.VECTOR;

		private int maxDocumentBatchSize = MAX_DOCUMENT_BATCH_SIZE;

		private PgVectorStoreBuilder(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(jdbcTemplate, "JdbcTemplate must not be null");
			this.jdbcTemplate = jdbcTemplate;
		}

		public PgVectorStoreBuilder schemaName(String schemaName) {
			this.schemaName = schemaName;
			return this;
		}

		public PgVectorStoreBuilder vectorTableName(String vectorTableName) {
			this.vectorTableName = vectorTableName;
			return this;
		}

		public PgVectorStoreBuilder idType(PgIdType idType) {
			this.idType = idType;
			return this;
		}

		public PgVectorStoreBuilder vectorTableValidationsEnabled(boolean vectorTableValidationsEnabled) {
			this.vectorTableValidationsEnabled = vectorTableValidationsEnabled;
			return this;
		}

		public PgVectorStoreBuilder dimensions(int dimensions) {
			this.dimensions = dimensions;
			return this;
		}

		public PgVectorStoreBuilder distanceType(PgDistanceType distanceType) {
			this.distanceType = distanceType;
			return this;
		}

		public PgVectorStoreBuilder removeExistingVectorStoreTable(boolean removeExistingVectorStoreTable) {
			this.removeExistingVectorStoreTable = removeExistingVectorStoreTable;
			return this;
		}

		public PgVectorStoreBuilder indexType(PgIndexType indexType) {
			this.indexType = indexType;
			return this;
		}

		public PgVectorStoreBuilder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		public PgVectorStoreBuilder maxDocumentBatchSize(int maxDocumentBatchSize) {
			this.maxDocumentBatchSize = maxDocumentBatchSize;
			return this;
		}

		/**
		 * Selects the pgvector column type. Defaults to {@link PgVectorType#VECTOR}. Pick
		 * {@link PgVectorType#HALFVEC} for half the storage on dense float embeddings,
		 * {@link PgVectorType#SPARSEVEC} for sparse embeddings, or
		 * {@link PgVectorType#BIT} for binary-quantized embeddings (must be paired with
		 * {@link PgDistanceType#HAMMING_DISTANCE} or
		 * {@link PgDistanceType#JACCARD_DISTANCE}).
		 */
		public PgVectorStoreBuilder vectorType(PgVectorType vectorType) {
			Assert.notNull(vectorType, "vectorType must not be null");
			this.vectorType = vectorType;
			return this;
		}

		public PgVectorStore build() {
			return new PgVectorStore(this);
		}

	}

}
