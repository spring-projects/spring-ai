/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.oracle;

import java.io.ByteArrayOutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import oracle.jdbc.OracleType;
import oracle.sql.VECTOR;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonGenerator;
import oracle.sql.json.OracleJsonObject;
import oracle.sql.json.OracleJsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * <p>
 * Integration of Oracle database 23ai as a Vector Store.
 * </p>
 * <p>
 * With the release 23ai (23.4), the Oracle database provides numerous features useful for
 * artificial intelligence such as Vectors, Similarity search, Vector indexes, ONNX
 * models...
 * </p>
 * <p>
 * This Spring AI Vector store supports the following features:
 * <ul>
 * <li>Vectors with unspecified or fixed dimensions</li>
 * <li>Distance type for similarity search (note that similarity threshold can be used
 * only with distance type COSINE and DOT when ingested vectors are normalized, see
 * forcedNormalization)</li>
 * <li>Vector indexes (use IVF as of 23.4)</li>
 * <li>Exact and Approximate similarity search</li>
 * <li>Filter expression as SQL/JSON Path expression evaluation</li>
 * </ul>
 *
 * @author Loïc Lefèvre
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Thomas Vitale
 * @author chabinhwang
 */
public class OracleVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	public static final double SIMILARITY_THRESHOLD_EXACT_MATCH = 1.0d;

	public static final String DEFAULT_TABLE_NAME = "SPRING_AI_VECTORS";

	public static final OracleVectorStoreIndexType DEFAULT_INDEX_TYPE = OracleVectorStoreIndexType.IVF;

	public static final OracleVectorStoreDistanceType DEFAULT_DISTANCE_TYPE = OracleVectorStoreDistanceType.COSINE;

	public static final int DEFAULT_DIMENSIONS = -1;

	public static final int DEFAULT_SEARCH_ACCURACY = -1;

	private static final Logger logger = LoggerFactory.getLogger(OracleVectorStore.class);

	private static final Map<OracleVectorStoreDistanceType, VectorStoreSimilarityMetric> SIMILARITY_TYPE_MAPPING = Map
		.of(OracleVectorStoreDistanceType.COSINE, VectorStoreSimilarityMetric.COSINE,
				OracleVectorStoreDistanceType.EUCLIDEAN, VectorStoreSimilarityMetric.EUCLIDEAN,
				OracleVectorStoreDistanceType.DOT, VectorStoreSimilarityMetric.DOT);

	public final FilterExpressionConverter filterExpressionConverter = new SqlJsonPathFilterExpressionConverter();

	private final JdbcTemplate jdbcTemplate;

	private final boolean initializeSchema;

	private final boolean removeExistingVectorStoreTable;

	/**
	 * Table name where vectors will be stored.
	 */
	private final String tableName;

	/**
	 * Index type used to index the vectors. It can impact performance and database memory
	 * consumption.
	 */
	private final OracleVectorStoreIndexType indexType;

	/**
	 * Distance type to use for computing vector distances.
	 */
	private final OracleVectorStoreDistanceType distanceType;

	/**
	 * Expected number of dimensions for vectors. Enforcing vector dimensions is very
	 * useful to ensure future vector distance computations will be relevant.
	 */
	private final int dimensions;

	private final boolean forcedNormalization;

	private final int searchAccuracy;

	private final OracleJsonFactory osonFactory = new OracleJsonFactory();

	private final ByteArrayOutputStream out = new ByteArrayOutputStream();

	/**
	 * Protected constructor that accepts a builder instance. This is the preferred way to
	 * create new OracleVectorStore instances.
	 * @param builder the configured builder instance
	 */
	protected OracleVectorStore(Builder builder) {
		super(builder);

		Assert.notNull(builder.jdbcTemplate, "JdbcTemplate must not be null");

		this.jdbcTemplate = builder.jdbcTemplate;
		this.tableName = builder.tableName;
		this.indexType = builder.indexType;
		this.distanceType = builder.distanceType;
		this.dimensions = builder.dimensions;
		this.searchAccuracy = builder.searchAccuracy;
		this.initializeSchema = builder.initializeSchema;
		this.removeExistingVectorStoreTable = builder.removeExistingVectorStoreTable;
		this.forcedNormalization = builder.forcedNormalization;
	}

	public static Builder builder(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
		return new Builder(jdbcTemplate, embeddingModel);
	}

	@Override
	public void doAdd(final List<Document> documents) {
		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
				this.batchingStrategy);
		this.jdbcTemplate.batchUpdate(getIngestStatement(), new BatchPreparedStatementSetter() {

			@Override
			public void setValues(PreparedStatement ps, int i) throws SQLException {
				final Document document = documents.get(i);
				final String content = document.getText();
				final byte[] json = toJson(document.getMetadata());
				final VECTOR embeddingVector = toVECTOR(embeddings.get(i));

				org.springframework.jdbc.core.StatementCreatorUtils.setParameterValue(ps, 1, Types.VARCHAR,
						document.getId());
				org.springframework.jdbc.core.StatementCreatorUtils.setParameterValue(ps, 2, Types.VARCHAR, content);
				org.springframework.jdbc.core.StatementCreatorUtils.setParameterValue(ps, 3,
						OracleType.JSON.getVendorTypeNumber(), json);
				org.springframework.jdbc.core.StatementCreatorUtils.setParameterValue(ps, 4,
						OracleType.VECTOR.getVendorTypeNumber(), embeddingVector);
			}

			@Override
			public int getBatchSize() {
				return documents.size();
			}
		});
	}

	private String getIngestStatement() {
		return String
			.format("""
					merge into %s target using (values(?, ?, ?, ?)) source (id, content, metadata, embedding) on (target.id = source.id)
					when matched then update set target.content = source.content, target.metadata = source.metadata, target.embedding = source.embedding
					when not matched then insert (target.id, target.content, target.metadata, target.embedding) values (source.id, source.content, source.metadata, source.embedding)""",
					this.tableName);
	}

	/**
	 * Bind binary JSON from the client.
	 * @param m map of metadata
	 * @return the binary JSON ready to be inserted
	 */
	private byte[] toJson(final Map<String, Object> m) {
		this.out.reset();
		try (OracleJsonGenerator gen = this.osonFactory.createJsonBinaryGenerator(this.out)) {
			gen.writeStartObject();
			for (String key : m.keySet()) {
				final Object o = m.get(key);
				if (o instanceof String) {
					gen.write(key, (String) o);
				}
				else if (o instanceof Integer) {
					gen.write(key, (Integer) o);
				}
				else if (o instanceof Float) {
					gen.write(key, (Float) o);
				}
				else if (o instanceof Double) {
					gen.write(key, (Double) o);
				}
				else if (o instanceof Boolean) {
					gen.write(key, (Boolean) o);
				}
			}
			gen.writeEnd();
		}

		return this.out.toByteArray();
	}

	/**
	 * Converts a list of Double values into an Oracle VECTOR object ready to be inserted.
	 * Optionally normalize the vector beforehand (see forcedNormalization).
	 * @param floatList
	 * @return
	 * @throws SQLException
	 */
	private VECTOR toVECTOR(final float[] floatList) throws SQLException {
		final double[] doubles = new double[floatList.length];
		int i = 0;
		for (double d : floatList) {
			doubles[i++] = d;
		}

		if (this.forcedNormalization) {
			return VECTOR.ofFloat64Values(normalize(doubles));
		}

		return VECTOR.ofFloat64Values(doubles);
	}

	/**
	 * Normalize a vector if requested.
	 * @param v vector to normalize
	 * @return the vector normalized
	 */
	private double[] normalize(final double[] v) {
		double squaredSum = 0d;

		for (double e : v) {
			squaredSum += e * e;
		}

		final double magnitude = Math.sqrt(squaredSum);

		if (magnitude > 0) {
			final double multiplier = 1d / magnitude;
			final int length = v.length;
			for (int i = 0; i < length; i++) {
				v[i] *= multiplier;
			}
		}

		return v;
	}

	@Override
	public void doDelete(final List<String> idList) {
		final String sql = String.format("delete from %s where id=?", this.tableName);
		final int[] argTypes = { Types.VARCHAR };

		final List<Object[]> batchArgs = new ArrayList<>();
		for (String id : idList) {
			batchArgs.add(new Object[] { id });
		}

		final int[] deleteCounts = this.jdbcTemplate.batchUpdate(sql, batchArgs, argTypes);

		for (int detailedResult : deleteCounts) {
			switch (detailedResult) {
				case Statement.EXECUTE_FAILED:
					break;
				case 1:
				case Statement.SUCCESS_NO_INFO:
					break;
			}
		}
	}

	@Override
	protected void doDelete(Filter.Expression filterExpression) {
		Assert.notNull(filterExpression, "Filter expression must not be null");

		try {
			String jsonPath = this.filterExpressionConverter.convertExpression(filterExpression);
			String sql = String.format("DELETE FROM %s WHERE JSON_EXISTS(metadata, '%s')", this.tableName, jsonPath);

			logger.debug("Executing delete with filter: {}", sql);

			int deletedCount = this.jdbcTemplate.update(sql);
			logger.debug("Deleted {} documents matching filter expression", deletedCount);
		}
		catch (Exception e) {
			logger.error("Failed to delete documents by filter: {}", e.getMessage(), e);
			throw new IllegalStateException("Failed to delete documents by filter", e);
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		try {
			// From the provided query, generate a vector using the embedding model
			final VECTOR embeddingVector = toVECTOR(this.embeddingModel.embed(request.getQuery()));

			if (logger.isDebugEnabled()) {
				this.jdbcTemplate.batchUpdate("insert into debug(embedding) values(?)",
						new BatchPreparedStatementSetter() {

							@Override
							public void setValues(PreparedStatement ps, int i) throws SQLException {
								org.springframework.jdbc.core.StatementCreatorUtils.setParameterValue(ps, 1,
										OracleType.VECTOR.getVendorTypeNumber(), embeddingVector);
							}

							@Override
							public int getBatchSize() {
								return 1;
							}
						});
			}

			final String nativeFilterExpression = (request.getFilterExpression() != null)
					? this.filterExpressionConverter.convertExpression(request.getFilterExpression()) : "";

			String jsonPathFilter = "";

			if (request.getSimilarityThreshold() == SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL) {
				if (StringUtils.hasText(nativeFilterExpression)) {
					jsonPathFilter = String.format("where JSON_EXISTS( metadata, '%s' )\n", nativeFilterExpression);
				}

				final String sql = this.searchAccuracy == DEFAULT_SEARCH_ACCURACY ? String.format("""
						select id, content, metadata, embedding, %sVECTOR_DISTANCE(embedding, ?, %s)%s as distance
						from %s
						%sorder by distance
						fetch first %d rows only""",
						this.distanceType == OracleVectorStore.OracleVectorStoreDistanceType.DOT ? "(1+" : "",
						this.distanceType.name(),
						this.distanceType == OracleVectorStore.OracleVectorStoreDistanceType.DOT ? ")/2" : "",
						this.tableName, jsonPathFilter, request.getTopK())
						: String.format(
								"""
										select id, content, metadata, embedding, %sVECTOR_DISTANCE(embedding, ?, %s)%s as distance
										from %s
										%sorder by distance
										fetch APPROXIMATE first %d rows only WITH TARGET ACCURACY %d""",
								this.distanceType == OracleVectorStore.OracleVectorStoreDistanceType.DOT ? "(1+" : "",
								this.distanceType.name(),
								this.distanceType == OracleVectorStore.OracleVectorStoreDistanceType.DOT ? ")/2" : "",
								this.tableName, jsonPathFilter, request.getTopK(), this.searchAccuracy);

				logger.debug("SQL query: {}", sql);

				return this.jdbcTemplate.query(sql, new DocumentRowMapper(), embeddingVector);
			}
			else if (request.getSimilarityThreshold() == SIMILARITY_THRESHOLD_EXACT_MATCH) {
				if (StringUtils.hasText(nativeFilterExpression)) {
					jsonPathFilter = String.format("where JSON_EXISTS( metadata, '%s' )\n", nativeFilterExpression);
				}

				final String sql = String.format("""
						select id, content, metadata, embedding, %sVECTOR_DISTANCE(embedding, ?, %s)%s as distance
						from %s
						%sorder by distance
						fetch EXACT first %d rows only""",
						this.distanceType == OracleVectorStore.OracleVectorStoreDistanceType.DOT ? "(1+" : "",
						this.distanceType.name(),
						this.distanceType == OracleVectorStore.OracleVectorStoreDistanceType.DOT ? ")/2" : "",
						this.tableName, jsonPathFilter, request.getTopK());

				logger.debug("SQL query: {}", sql);

				return this.jdbcTemplate.query(sql, new DocumentRowMapper(), embeddingVector);
			}
			else {
				if (!this.forcedNormalization || (this.distanceType != OracleVectorStoreDistanceType.COSINE
						&& this.distanceType != OracleVectorStore.OracleVectorStoreDistanceType.DOT)) {
					throw new RuntimeException(
							"Similarity threshold filtering requires all vectors to be normalized, see the forcedNormalization parameter for this Vector store. Also only COSINE and DOT distance types are supported.");
				}

				final double distance = this.distanceType == OracleVectorStore.OracleVectorStoreDistanceType.DOT
						? (1d - request.getSimilarityThreshold()) * 2d - 1d : 1d - request.getSimilarityThreshold();

				if (StringUtils.hasText(nativeFilterExpression)) {
					jsonPathFilter = String.format(" and JSON_EXISTS( metadata, '%s' )", nativeFilterExpression);
				}

				final String sql = this.distanceType == OracleVectorStore.OracleVectorStoreDistanceType.DOT
						? (this.searchAccuracy == DEFAULT_SEARCH_ACCURACY
								? String.format(
										"""
												select id, content, metadata, embedding, (1+VECTOR_DISTANCE(embedding, ?, DOT))/2 as distance
												from %s
												where VECTOR_DISTANCE(embedding, ?, DOT) <= ?%s
												order by distance
												fetch first %d rows only""",
										this.tableName, jsonPathFilter, request.getTopK())
								: String.format(
										"""
												select id, content, metadata, embedding, (1+VECTOR_DISTANCE(embedding, ?, DOT))/2 as distance
												from %s
												where VECTOR_DISTANCE(embedding, ?, DOT) <= ?%s
												order by distance
												fetch APPROXIMATE first %d rows only WITH TARGET ACCURACY %d""",
										this.tableName, jsonPathFilter, request.getTopK(), this.searchAccuracy)

						)
						: (this.searchAccuracy == DEFAULT_SEARCH_ACCURACY
								? String.format(
										"""
												select id, content, metadata, embedding, VECTOR_DISTANCE(embedding, ?, COSINE) as distance
												from %s
												where VECTOR_DISTANCE(embedding, ?, COSINE) <= ?%s
												order by distance
												fetch first %d rows only""",
										this.tableName, jsonPathFilter, request.getTopK())
								: String.format(
										"""
												select id, content, metadata, embedding, VECTOR_DISTANCE(embedding, ?, COSINE) as distance
												from %s
												where VECTOR_DISTANCE(embedding, ?, COSINE) <= ?%s
												order by distance
												fetch APPROXIMATE first %d rows only WITH TARGET ACCURACY %d""",
										this.tableName, jsonPathFilter, request.getTopK(), this.searchAccuracy));

				logger.debug("SQL query: {}", sql);

				return this.jdbcTemplate.query(sql, new DocumentRowMapper(), embeddingVector, embeddingVector,
						distance);
			}
		}
		catch (SQLException sqle) {
			throw new RuntimeException(sqle);
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (this.initializeSchema) {
			// Remove existing VectorStoreTable
			if (this.removeExistingVectorStoreTable) {
				this.jdbcTemplate.execute(String.format("drop table if exists %s purge", this.tableName));
			}

			this.jdbcTemplate.execute(String.format("""
					create table if not exists %s (
						id        varchar2(36) default sys_guid() primary key,
						content   clob not null,
						metadata  json not null,
						embedding vector(%s,FLOAT64) annotations(Distance '%s', IndexType '%s')
					)""", this.tableName, this.dimensions == DEFAULT_DIMENSIONS ? "*" : String.valueOf(this.dimensions),
					this.distanceType.name(), this.indexType.name()));

			if (logger.isDebugEnabled()) {
				this.jdbcTemplate.execute(String.format("""
						create table if not exists debug (
						id varchar2(36) default sys_guid() primary key,
						embedding vector(%s,FLOAT64) annotations(Distance '%s')
						)""", this.dimensions == DEFAULT_DIMENSIONS ? "*" : String.valueOf(this.dimensions),
						this.distanceType.name()));
			}

			switch (this.indexType) {
				case IVF:
					this.jdbcTemplate.execute(String.format("""
							create vector index if not exists vector_index_%s on %s (embedding)
							organization neighbor partitions
									distance %s
									with target accuracy %d
									parameters (type IVF, neighbor partitions 10)""", this.tableName, this.tableName,
							this.distanceType.name(),
							this.searchAccuracy == DEFAULT_SEARCH_ACCURACY ? 95 : this.searchAccuracy));
					break;

				/*
				 * TODO: Enable for 23.5 case HNSW:
				 * this.jdbcTemplate.execute(String.format(""" create vector index if not
				 * exists vector_index_%s on %s (embedding) organization inmemory neighbor
				 * graph distance %s with target accuracy %d parameters (type HNSW,
				 * neighbors 40, efconstruction 500)""", tableName, tableName,
				 * distanceType.name(), searchAccuracy == DEFAULT_SEARCH_ACCURACY ? 95 :
				 * searchAccuracy)); break;
				 */
			}
		}
	}

	public String getTableName() {
		return this.tableName;
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		return VectorStoreObservationContext.builder(VectorStoreProvider.ORACLE.value(), operationName)
			.dimensions(this.embeddingModel.dimensions())
			.collectionName(this.getTableName())
			.similarityMetric(getSimilarityMetric());
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this.jdbcTemplate;
		return Optional.of(client);
	}

	private String getSimilarityMetric() {
		if (!SIMILARITY_TYPE_MAPPING.containsKey(this.distanceType)) {
			return this.distanceType.name();
		}
		return SIMILARITY_TYPE_MAPPING.get(this.distanceType).value();
	}

	public enum OracleVectorStoreIndexType {

		/**
		 * Performs exact nearest neighbor search.
		 */
		NONE,

		/**
		 * <p>
		 * The default type of index created for an In-Memory Neighbor Graph vector index
		 * is Hierarchical Navigable Small World (HNSW).
		 * </p>
		 *
		 * <p>
		 * With Navigable Small World (NSW), the idea is to build a proximity graph where
		 * each vector in the graph connects to several others based on three
		 * characteristics:
		 * <ul>
		 * <li>The distance between vectors</li>
		 * <li>The maximum number of closest vector candidates considered at each step of
		 * the search during insertion (EFCONSTRUCTION)</li>
		 * <li>Within the maximum number of connections (NEIGHBORS) permitted per
		 * vector</li>
		 * </ul>
		 *
		 * @see <a href=
		 * "https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/understand-hierarchical-navigable-small-world-indexes.html">Oracle
		 * Database documentation</a>
		 */
		HNSW,

		/**
		 * <p>
		 * The default type of index created for a Neighbor Partition vector index is
		 * Inverted File Flat (IVF) vector index. The IVF index is a technique designed to
		 * enhance search efficiency by narrowing the search area through the use of
		 * neighbor partitions or clusters.
		 * </p>
		 *
		 * * @see <a href=
		 * "https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/understand-inverted-file-flat-vector-indexes.html">Oracle
		 * Database documentation</a>
		 */
		IVF

	}

	public enum OracleVectorStoreDistanceType {

		/**
		 * Default metric. It calculates the cosine distance between two vectors.
		 */
		COSINE,

		/**
		 * Also called the inner product, calculates the negated dot product of two
		 * vectors.
		 */
		DOT,

		/**
		 * Also called L2_DISTANCE, calculates the Euclidean distance between two vectors.
		 */
		EUCLIDEAN,

		/**
		 * Also called L2_SQUARED is the Euclidean distance without taking the square
		 * root.
		 */
		EUCLIDEAN_SQUARED,

		/*
		 * Calculates the hamming distance between two vectors. Requires INT8 element
		 * type.
		 */
		// TODO: add HAMMING support,

		/**
		 * Also called L1_DISTANCE or taxicab distance, calculates the Manhattan distance.
		 */
		MANHATTAN

	}

	private final static class DocumentRowMapper implements RowMapper<Document> {

		@Override
		public Document mapRow(ResultSet rs, int rowNum) throws SQLException {
			final Map<String, Object> metadata = getMap(rs.getObject(3, OracleJsonValue.class));
			metadata.put(DocumentMetadata.DISTANCE.value(), rs.getDouble(5));

			return Document.builder()
				.id(rs.getString(1))
				.text(rs.getString(2))
				.metadata(metadata)
				.score(1 - rs.getDouble(5))
				.build();
		}

		private Map<String, Object> getMap(OracleJsonValue value) {
			final Map<String, Object> result = new HashMap<>();

			if (value != null) {
				final OracleJsonObject json = value.asJsonObject();
				for (String key : json.keySet()) {
					result.put(key, json.get(key));
				}
			}

			return result;
		}

		private List<Float> toFloatList(final float[] embeddings) {
			final List<Float> result = new ArrayList<>(embeddings.length);
			for (float v : embeddings) {
				result.add(v);
			}
			return result;
		}

	}

	/**
	 * Builder class for creating {@link OracleVectorStore} instances.
	 * <p>
	 * Provides a fluent API for configuring all aspects of the Oracle vector store,
	 * including database connection, schema initialization, vector dimensions, and search
	 * parameters.
	 *
	 * @since 1.0.0
	 */
	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private final JdbcTemplate jdbcTemplate;

		private String tableName = DEFAULT_TABLE_NAME;

		private OracleVectorStoreIndexType indexType = DEFAULT_INDEX_TYPE;

		private OracleVectorStoreDistanceType distanceType = DEFAULT_DISTANCE_TYPE;

		private int dimensions = DEFAULT_DIMENSIONS;

		private int searchAccuracy = DEFAULT_SEARCH_ACCURACY;

		private boolean initializeSchema = false;

		private boolean removeExistingVectorStoreTable = false;

		private boolean forcedNormalization = false;

		/**
		 * Sets the JdbcTemplate to be used for database operations.
		 * @param jdbcTemplate the JdbcTemplate instance
		 * @param embeddingModel the Embedding Model to be used
		 */
		public Builder(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(jdbcTemplate, "JdbcTemplate must not be null");
			this.jdbcTemplate = jdbcTemplate;
		}

		/**
		 * Sets the table name for vector storage.
		 * @param tableName the name of the table to use
		 * @return the builder instance
		 */
		public Builder tableName(String tableName) {
			if (StringUtils.hasText(tableName)) {
				this.tableName = tableName;
			}
			return this;
		}

		/**
		 * Sets the vector index type.
		 * @param indexType the index type to use
		 * @return the builder instance
		 * @throws IllegalArgumentException if indexType is null
		 */
		public Builder indexType(OracleVectorStoreIndexType indexType) {
			Assert.notNull(indexType, "Index type must not be null");
			this.indexType = indexType;
			return this;
		}

		/**
		 * Sets the distance type for vector similarity calculations.
		 * @param distanceType the distance type to use
		 * @return the builder instance
		 * @throws IllegalArgumentException if distanceType is null
		 */
		public Builder distanceType(OracleVectorStoreDistanceType distanceType) {
			Assert.notNull(distanceType, "Distance type must not be null");
			this.distanceType = distanceType;
			return this;
		}

		/**
		 * Sets the number of dimensions for the vector space.
		 * @param dimensions the number of dimensions (must be between 1 and 65535)
		 * @return the builder instance
		 * @throws IllegalArgumentException if dimensions is not within valid range
		 */
		public Builder dimensions(int dimensions) {
			if (dimensions != DEFAULT_DIMENSIONS) {
				Assert.isTrue(dimensions > 0 && dimensions <= 65535,
						"Number of dimensions must be between 1 and 65535");
			}
			this.dimensions = dimensions;
			return this;
		}

		/**
		 * Sets the search accuracy parameter.
		 * @param searchAccuracy the search accuracy value (must be between 1 and 100)
		 * @return the builder instance
		 * @throws IllegalArgumentException if searchAccuracy is not within valid range
		 */
		public Builder searchAccuracy(int searchAccuracy) {
			if (searchAccuracy != DEFAULT_SEARCH_ACCURACY) {
				Assert.isTrue(searchAccuracy >= 1 && searchAccuracy <= 100,
						"Search accuracy must be between 1 and 100");
			}
			this.searchAccuracy = searchAccuracy;
			return this;
		}

		/**
		 * Sets whether to initialize the database schema.
		 * @param initializeSchema true to initialize schema, false otherwise
		 * @return the builder instance
		 */
		public Builder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		/**
		 * Sets whether to remove existing vector store table before initialization.
		 * @param removeExistingVectorStoreTable true to remove existing table, false
		 * otherwise
		 * @return the builder instance
		 */
		public Builder removeExistingVectorStoreTable(boolean removeExistingVectorStoreTable) {
			this.removeExistingVectorStoreTable = removeExistingVectorStoreTable;
			return this;
		}

		/**
		 * Sets whether to force vector normalization.
		 * @param forcedNormalization true to force normalization, false otherwise
		 * @return the builder instance
		 */
		public Builder forcedNormalization(boolean forcedNormalization) {
			this.forcedNormalization = forcedNormalization;
			return this;
		}

		@Override
		public OracleVectorStore build() {
			return new OracleVectorStore(this);
		}

	}

}
