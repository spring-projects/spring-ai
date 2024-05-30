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

import oracle.jdbc.OracleType;
import oracle.sql.VECTOR;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonGenerator;
import oracle.sql.json.OracleJsonObject;
import oracle.sql.json.OracleJsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.StringUtils;

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

import static org.springframework.ai.vectorstore.OracleVectorStore.OracleAIVectorSearchDistanceType.DOT;
import static org.springframework.jdbc.core.StatementCreatorUtils.setParameterValue;

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
 * </p>
 *
 * @author Loïc Lefèvre
 */
public class OracleVectorStore implements VectorStore, InitializingBean {

	private static final Logger logger = LoggerFactory.getLogger(OracleVectorStore.class);

	public static final double SIMILARITY_THRESHOLD_EXACT_MATCH = 1.0d;

	public enum OracleAIVectorSearchIndexType {

		/**
		 * Performs exact nearest neighbor search.
		 */
		NONE,

		/**
		 * </p>
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
		 * </p>
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
		IVF;

	}

	public enum OracleAIVectorSearchDistanceType {

		/**
		 * Default metric. It calculates the cosine distane between two vectors.
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

	public static final String DEFAULT_TABLE_NAME = "SPRING_AI_VECTORS";

	public static final OracleAIVectorSearchIndexType DEFAULT_INDEX_TYPE = OracleAIVectorSearchIndexType.IVF;

	public static final OracleAIVectorSearchDistanceType DEFAULT_DISTANCE_TYPE = OracleAIVectorSearchDistanceType.COSINE;

	public static final int DEFAULT_DIMENSIONS = -1;

	public static final int DEFAULT_SEARCH_ACCURACY = -1;

	private final JdbcTemplate jdbcTemplate;

	private final EmbeddingModel embeddingModel;

	private final boolean initializeSchema;

	private final boolean removeExistingVectorStoreTable;

	public final FilterExpressionConverter filterExpressionConverter = new ISOSQLJSONPathFilterExpressionConverter();

	/**
	 * Table name where vectors will be stored.
	 */
	private final String tableName;

	/**
	 * Index type used to index the vectors. It can impact performance and database memory
	 * consumption.
	 */
	private final OracleAIVectorSearchIndexType indexType;

	/**
	 * Distance type to use for computing vector distances.
	 */
	private final OracleAIVectorSearchDistanceType distanceType;

	/**
	 * Expected number of dimensions for vectors. Enforcing vector dimensions is very
	 * useful to ensure future vector distance computations will be relevant.
	 */
	private final int dimensions;

	private final boolean forcedNormalization;

	private final int searchAccuracy;

	public OracleVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
		this(jdbcTemplate, embeddingModel, DEFAULT_TABLE_NAME, DEFAULT_INDEX_TYPE, DEFAULT_DISTANCE_TYPE,
				DEFAULT_DIMENSIONS, DEFAULT_SEARCH_ACCURACY, false, false, false);
	}

	public OracleVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, boolean initializeSchema) {
		this(jdbcTemplate, embeddingModel, DEFAULT_TABLE_NAME, DEFAULT_INDEX_TYPE, DEFAULT_DISTANCE_TYPE,
				DEFAULT_DIMENSIONS, DEFAULT_SEARCH_ACCURACY, initializeSchema, false, false);
	}

	public OracleVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, String tableName,
			OracleAIVectorSearchIndexType indexType, OracleAIVectorSearchDistanceType distanceType, int dimensions,
			int searchAccuracy, boolean initializeSchema, boolean removeExistingVectorStoreTable,
			boolean forcedNormalization) {
		if (dimensions != DEFAULT_DIMENSIONS) {
			if (dimensions <= 0) {
				throw new RuntimeException("Number of dimensions must be strictly positive");
			}
			if (dimensions > 65535) {
				throw new RuntimeException("Number of dimensions must be at most 65535");
			}
		}

		if (searchAccuracy != DEFAULT_SEARCH_ACCURACY) {
			if (searchAccuracy < 1) {
				throw new RuntimeException("Search accuracy must be greater or equals to 1");
			}
			if (searchAccuracy > 100) {
				throw new RuntimeException("Search accuracy must be lower or equals to 100");
			}
		}

		this.jdbcTemplate = jdbcTemplate;
		this.embeddingModel = embeddingModel;
		this.tableName = tableName;
		this.indexType = indexType;
		this.distanceType = distanceType;
		this.dimensions = dimensions;
		this.searchAccuracy = searchAccuracy;
		this.initializeSchema = initializeSchema;
		this.removeExistingVectorStoreTable = removeExistingVectorStoreTable;
		this.forcedNormalization = forcedNormalization;
	}

	@Override
	public void add(final List<Document> documents) {
		this.jdbcTemplate.batchUpdate(getIngestStatement(), new BatchPreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps, int i) throws SQLException {
				final Document document = documents.get(i);
				final String content = document.getContent();
				final byte[] json = toJson(document.getMetadata());
				final VECTOR embeddingVector = toVECTOR(embeddingModel.embed(document));

				setParameterValue(ps, 1, Types.VARCHAR, document.getId());
				setParameterValue(ps, 2, Types.VARCHAR, content);
				setParameterValue(ps, 3, OracleType.JSON.getVendorTypeNumber(), json);
				setParameterValue(ps, 4, OracleType.VECTOR.getVendorTypeNumber(), embeddingVector);
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
					tableName);
	}

	private final OracleJsonFactory osonFactory = new OracleJsonFactory();

	private final ByteArrayOutputStream out = new ByteArrayOutputStream();

	/**
	 * Bind binary JSON from the client.
	 * @param m map of metadata
	 * @return the binary JSON ready to be inserted
	 */
	private byte[] toJson(final Map<String, Object> m) {
		out.reset();
		try (OracleJsonGenerator gen = osonFactory.createJsonBinaryGenerator(out)) {
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

		return out.toByteArray();
	}

	/**
	 * Converts a list of Double values into an Oracle VECTOR object ready to be inserted.
	 * Optionally normalize the vector beforehand (see forcedNormalization).
	 * @param doubleList
	 * @return
	 * @throws SQLException
	 */
	private VECTOR toVECTOR(final List<Double> doubleList) throws SQLException {
		final double[] doubles = new double[doubleList.size()];
		int i = 0;
		for (double d : doubleList) {
			doubles[i++] = d;
		}

		if (forcedNormalization) {
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
	public Optional<Boolean> delete(final List<String> idList) {
		final String sql = String.format("delete from %s where id=?", tableName);
		final int[] argTypes = { Types.VARCHAR };

		final List<Object[]> batchArgs = new ArrayList<>();
		for (String id : idList) {
			batchArgs.add(new Object[] { id });
		}

		final int[] deleteCounts = jdbcTemplate.batchUpdate(sql, batchArgs, argTypes);

		int deleteCount = 0;
		for (int detailedResult : deleteCounts) {
			switch (detailedResult) {
				case Statement.EXECUTE_FAILED:
					break;
				case 1:
				case Statement.SUCCESS_NO_INFO:
					deleteCount++;
					break;
			}
		}

		return Optional.of(deleteCount == idList.size());
	}

	private static class DocumentRowMapper implements RowMapper<Document> {

		@Override
		public Document mapRow(ResultSet rs, int rowNum) throws SQLException {
			final Map<String, Object> metadata = getMap(rs.getObject(3, OracleJsonValue.class));
			metadata.put("distance", rs.getDouble(5));

			final Document document = new Document(rs.getString(1), rs.getString(2), metadata);
			final double[] embedding = rs.getObject(4, double[].class);
			document.setEmbedding(toDoubleList(embedding));
			return document;
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

		private List<Double> toDoubleList(final double[] embeddings) {
			final List<Double> result = new ArrayList<>(embeddings.length);
			for (double v : embeddings) {
				result.add(v);
			}
			return result;
		}

	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {
		try {
			// From the provided query, generate a vector using the embedding model
			final VECTOR embeddingVector = toVECTOR(embeddingModel.embed(request.getQuery()));

			if (logger.isDebugEnabled()) {
				this.jdbcTemplate.batchUpdate("insert into debug(embedding) values(?)",
						new BatchPreparedStatementSetter() {
							@Override
							public void setValues(PreparedStatement ps, int i) throws SQLException {
								setParameterValue(ps, 1, OracleType.VECTOR.getVendorTypeNumber(), embeddingVector);
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

				final String sql = searchAccuracy == DEFAULT_SEARCH_ACCURACY ? String.format("""
						select id, content, metadata, embedding, %sVECTOR_DISTANCE(embedding, ?, %s)%s as distance
						from %s
						%sorder by distance
						fetch first %d rows only""", distanceType == DOT ? "(1+" : "", distanceType.name(),
						distanceType == DOT ? ")/2" : "", tableName, jsonPathFilter, request.getTopK())
						: String.format(
								"""
										select id, content, metadata, embedding, %sVECTOR_DISTANCE(embedding, ?, %s)%s as distance
										from %s
										%sorder by distance
										fetch APPROXIMATE first %d rows only WITH TARGET ACCURACY %d""",
								distanceType == DOT ? "(1+" : "", distanceType.name(), distanceType == DOT ? ")/2" : "",
								tableName, jsonPathFilter, request.getTopK(), searchAccuracy);

				logger.debug("SQL query: " + sql);

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
						fetch EXACT first %d rows only""", distanceType == DOT ? "(1+" : "", distanceType.name(),
						distanceType == DOT ? ")/2" : "", tableName, jsonPathFilter, request.getTopK());

				logger.debug("SQL query: " + sql);

				return this.jdbcTemplate.query(sql, new DocumentRowMapper(), embeddingVector);
			}
			else {
				if (!forcedNormalization
						|| (distanceType != OracleAIVectorSearchDistanceType.COSINE && distanceType != DOT)) {
					throw new RuntimeException(
							"Similarity threshold filtering requires all vectors to be normalized, see the forcedNormalization parameter for this Vector store. Also only COSINE and DOT distance types are supported.");
				}

				final double distance = distanceType == DOT ? (1d - request.getSimilarityThreshold()) * 2d - 1d
						: 1d - request.getSimilarityThreshold();

				if (StringUtils.hasText(nativeFilterExpression)) {
					jsonPathFilter = String.format(" and JSON_EXISTS( metadata, '%s' )", nativeFilterExpression);
				}

				final String sql = distanceType == DOT ? (searchAccuracy == DEFAULT_SEARCH_ACCURACY ? String.format("""
						select id, content, metadata, embedding, (1+VECTOR_DISTANCE(embedding, ?, DOT))/2 as distance
						from %s
						where VECTOR_DISTANCE(embedding, ?, DOT) <= ?%s
						order by distance
						fetch first %d rows only""", tableName, jsonPathFilter, request.getTopK()) : String.format("""
						select id, content, metadata, embedding, (1+VECTOR_DISTANCE(embedding, ?, DOT))/2 as distance
						from %s
						where VECTOR_DISTANCE(embedding, ?, DOT) <= ?%s
						order by distance
						fetch APPROXIMATE first %d rows only WITH TARGET ACCURACY %d""", tableName, jsonPathFilter,
						request.getTopK(), searchAccuracy)

				) : (searchAccuracy == DEFAULT_SEARCH_ACCURACY ? String.format("""
						select id, content, metadata, embedding, VECTOR_DISTANCE(embedding, ?, COSINE) as distance
						from %s
						where VECTOR_DISTANCE(embedding, ?, COSINE) <= ?%s
						order by distance
						fetch first %d rows only""", tableName, jsonPathFilter, request.getTopK()) : String.format("""
						select id, content, metadata, embedding, VECTOR_DISTANCE(embedding, ?, COSINE) as distance
						from %s
						where VECTOR_DISTANCE(embedding, ?, COSINE) <= ?%s
						order by distance
						fetch APPROXIMATE first %d rows only WITH TARGET ACCURACY %d""", tableName, jsonPathFilter,
						request.getTopK(), searchAccuracy));

				logger.debug("SQL query: " + sql);

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
				this.jdbcTemplate.execute(String.format("drop table if exists %s purge", tableName));
			}

			this.jdbcTemplate.execute(String.format("""
					create table if not exists %s (
						id        varchar2(36) default sys_guid() primary key,
						content   clob not null,
						metadata  json not null,
						embedding vector(%s,FLOAT64) annotations(Distance '%s', IndexType '%s')
					)""", tableName, dimensions == DEFAULT_DIMENSIONS ? "*" : String.valueOf(dimensions),
					distanceType.name(), indexType.name()));

			if (logger.isDebugEnabled()) {
				this.jdbcTemplate.execute(String.format("""
						create table if not exists debug (
						id varchar2(36) default sys_guid() primary key,
						embedding vector(%s,FLOAT64) annotations(Distance '%s')
						)""", dimensions == DEFAULT_DIMENSIONS ? "*" : String.valueOf(dimensions),
						distanceType.name()));
			}

			switch (indexType) {
				case IVF:
					this.jdbcTemplate.execute(String.format("""
							create vector index if not exists vector_index_%s on %s (embedding)
							organization neighbor partitions
							            distance %s
							            with target accuracy %d
							            parameters (type IVF, neighbor partitions 10)""", tableName, tableName,
							distanceType.name(), searchAccuracy == DEFAULT_SEARCH_ACCURACY ? 95 : searchAccuracy));
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
		return tableName;
	}

}
