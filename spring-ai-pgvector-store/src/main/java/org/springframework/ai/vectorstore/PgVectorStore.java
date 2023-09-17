/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.vectorstore;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import org.postgresql.util.PGobject;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * @author Christian Tzolov
 */
public class PgVectorStore implements VectorStore {

	public static final int OPENAI_EMBEDDING_DIMENSION_SIZE = 1536;

	private final JdbcTemplate jdbcTemplate;

	private final EmbeddingClient embeddingClient;

	private int dimensions;

	private PgDistanceType distanceType;

	private ObjectMapper objectMapper = new ObjectMapper();

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

	public enum PgDistanceType {

		EuclideanDistance("<->", "vector_l2_ops"),

		NegativeInnerProduct("<#>", "vector_ip_ops"),

		CosineDistance("<=>", "vector_cosine_ops");

		public final String operator;

		public final String index;

		PgDistanceType(String operator, String index) {
			this.operator = operator;
			this.index = index;
		}

	}

	private static class DocumentRowMapper implements RowMapper<Document> {

		private static final String COLUMN_EMBEDDING = "embedding";

		private static final String COLUMN_METADATA = "metadata";

		private static final String COLUMN_ID = "id";

		private static final String COLUMN_CONTENT = "content";

		private ObjectMapper objectMapper;

		public DocumentRowMapper(ObjectMapper objectMapper) {
			this.objectMapper = objectMapper;
		}

		@Override
		public Document mapRow(ResultSet rs, int rowNum) throws SQLException {
			String id = rs.getString(COLUMN_ID);
			String content = rs.getString(COLUMN_CONTENT);
			PGobject metadata = rs.getObject(COLUMN_METADATA, PGobject.class);
			PGobject embedding = rs.getObject(COLUMN_EMBEDDING, PGobject.class);

			Document document = new Document(id, content, toMap(metadata));
			document.setEmbedding(toDoubleList(embedding));

			return document;
		}

		private List<Double> toDoubleList(PGobject embedding) throws SQLException {
			float[] floatArray = new PGvector(embedding.getValue()).toArray();
			List<Double> doubleEmbedding = IntStream.range(0, floatArray.length)
				.mapToDouble(i -> floatArray[i])
				.boxed()
				.collect(Collectors.toList());
			return doubleEmbedding;

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

	public PgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingClient embeddingClient) {
		this(jdbcTemplate, embeddingClient, OPENAI_EMBEDDING_DIMENSION_SIZE,
				PgVectorStore.PgDistanceType.CosineDistance, false, PgIndexType.NONE);
	}

	public PgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingClient embeddingClient, int dimensions,
			PgDistanceType distanceType, boolean removeExistingVectorStoreTable, PgIndexType createIndexMethod) {
		this.jdbcTemplate = jdbcTemplate;
		this.embeddingClient = embeddingClient;
		this.dimensions = dimensions;
		this.distanceType = distanceType;

		// Add PGVector support.
		this.jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
		// Add JSONB support.
		this.jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS hstore");
		// Add UUID support.
		this.jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");

		// Remove existing VectorStoreTable
		if (removeExistingVectorStoreTable) {
			this.jdbcTemplate.execute("DROP TABLE IF EXISTS vector_store");
		}

		// TODO: we create id of type UUID, while the Document's id is String!!!
		// TODO: remove injection!
		this.jdbcTemplate
			.execute("CREATE TABLE IF NOT EXISTS vector_store ( " + "id uuid DEFAULT uuid_generate_v4 () PRIMARY KEY, "
					+ "content text, " + "metadata json, " + "embedding vector(" + this.dimensions + "))");

		if (createIndexMethod != PgIndexType.NONE) {
			this.jdbcTemplate.execute("CREATE INDEX ON vector_store USING " + createIndexMethod.name() + " (embedding "
					+ distanceType.index + ")");
		}

		this.jdbcTemplate
			.execute("CREATE TABLE IF NOT EXISTS vector_store ( " + "id uuid DEFAULT uuid_generate_v4 () PRIMARY KEY, "
					+ "content text, " + "metadata json, " + "embedding vector(" + this.dimensions + "))");
	}

	@Override
	public void add(List<Document> documents) {
		for (Document document : documents) {
			List<Double> embedding = this.embeddingClient.embed(document);
			document.setEmbedding(embedding);

			UUID id = UUID.fromString(document.getId());
			String content = document.getText(); // TODO: shall we use the text of text +
													// metadata?
			Map<String, Object> metadata = document.getMetadata();
			PGvector pgEmbedding = new PGvector(toFloatArray(embedding));

			jdbcTemplate.update(
					"INSERT INTO vector_store (id, content, metadata, embedding) VALUES (?, ?, ?::jsonb, ?) "
							+ "ON CONFLICT (id) DO " + "UPDATE SET content = ? , metadata = ?::jsonb , embedding = ? ",
					id, content, metadata, pgEmbedding, content, metadata, pgEmbedding);
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
			int count = jdbcTemplate.update("DELETE FROM vector_store WHERE id = ?", UUID.fromString(id));

			updateCount = updateCount + count;
		}

		return Optional.of(updateCount == idList.size());
	}

	@Override
	public List<Document> similaritySearch(String query) {
		return this.similaritySearch(query, 4);
	}

	@Override
	public List<Document> similaritySearch(String query, int k) {
		PGvector queryEmbedding = getQueryEmbedding(query);
		return this.jdbcTemplate.query(
				"SELECT * FROM vector_store ORDER BY embedding " + this.distanceType.operator + " ? LIMIT ?",
				new DocumentRowMapper(this.objectMapper), queryEmbedding, k);
	}

	@Override
	public List<Document> similaritySearch(String query, int k, double threshold) {
		PGvector queryEmbedding = getQueryEmbedding(query);
		return this.jdbcTemplate.query(
				"SELECT * FROM vector_store ORDER BY embedding " + this.distanceType.operator + " ? < ? LIMIT ? ",
				new DocumentRowMapper(this.objectMapper), queryEmbedding, threshold, k);
	}

	private PGvector getQueryEmbedding(String query) {
		List<Double> embedding = this.embeddingClient.embed(query);
		return new PGvector(toFloatArray(embedding));
	}

}