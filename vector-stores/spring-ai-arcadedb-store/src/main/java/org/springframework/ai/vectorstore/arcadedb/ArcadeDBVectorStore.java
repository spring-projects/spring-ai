/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.arcadedb;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.database.Identifiable;
import com.arcadedb.graph.MutableVertex;
import com.arcadedb.graph.Vertex;
import com.arcadedb.index.vector.HnswVectorIndex;
import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultSet;
import com.arcadedb.schema.Schema;
import com.arcadedb.schema.Type;
import com.arcadedb.schema.VertexType;
import com.arcadedb.utility.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;

/**
 * ArcadeDB implementation of Spring AI
 * {@link org.springframework.ai.vectorstore.VectorStore} using an embedded
 * database with native HNSW vector indexing.
 *
 * <p>
 * Unlike most Spring AI vector store integrations that connect to external
 * servers, ArcadeDB runs <strong>embedded in the same JVM</strong> — zero
 * network overhead, zero serialization cost, with persistence, HNSW vector
 * indexing, and graph capabilities.
 *
 * <p>
 * Usage:
 * <pre>{@code
 * ArcadeDBVectorStore store = ArcadeDBVectorStore.builder(embeddingModel)
 *     .databasePath("/tmp/mydb")
 *     .embeddingDimension(1536)
 *     .build();
 * store.afterPropertiesSet();
 * }</pre>
 *
 * @author Luca Garulli
 * @since 2.0.0
 */
public class ArcadeDBVectorStore extends AbstractObservationVectorStore
		implements InitializingBean, AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(ArcadeDBVectorStore.class);

	static final String PROPERTY_ID = "id";

	static final String PROPERTY_EMBEDDING = "embedding";

	static final String PROPERTY_CONTENT = "content";

	static final String PROPERTY_DELETED = "deleted";

	private static final Set<String> RESERVED_PROPERTIES = Set.of(
			PROPERTY_ID, PROPERTY_EMBEDDING, PROPERTY_CONTENT, PROPERTY_DELETED,
			"vectorMaxLevel" // added by HNSW index
	);

	private static final String DEFAULT_TYPE_NAME = "Document";

	private static final String DEFAULT_EDGE_TYPE = "HnswEdge";

	private static final String DEFAULT_METADATA_PREFIX = "meta_";

	private static final int DEFAULT_EMBEDDING_DIMENSION = 1536;

	private final Database database;

	private final boolean ownsDatabase;

	private final String typeName;

	private final String quotedTypeName;

	private final String edgeType;

	private final String metadataPrefix;

	private final int embeddingDimension;

	private final ArcadeDBDistanceType distanceType;

	private final int m;

	private final int ef;

	private final int efConstruction;

	private final boolean initializeSchema;

	private HnswVectorIndex<Object, float[], Float> vectorIndex;

	protected ArcadeDBVectorStore(Builder builder) {
		super(builder);
		this.typeName = builder.typeName;
		this.quotedTypeName = "`" + builder.typeName + "`";
		this.edgeType = builder.edgeType;
		this.metadataPrefix = builder.metadataPrefix;
		this.embeddingDimension = builder.embeddingDimension;
		this.distanceType = builder.distanceType;
		this.m = builder.m;
		this.ef = builder.ef;
		this.efConstruction = builder.efConstruction;
		this.initializeSchema = builder.initializeSchema;

		if (builder.database != null) {
			this.database = builder.database;
			this.ownsDatabase = false;
		}
		else {
			if (builder.databasePath == null || builder.databasePath.isBlank()) {
				throw new IllegalArgumentException(
						"Either database or databasePath must be provided");
			}
			DatabaseFactory factory = new DatabaseFactory(builder.databasePath);
			this.database = factory.exists() ? factory.open() : factory.create();
			this.ownsDatabase = true;
		}
	}

	/**
	 * Returns the underlying ArcadeDB {@link Database} instance.
	 * @return an Optional containing the Database
	 * @param <T> the expected client type
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <T> Optional<T> getNativeClient() {
		return (Optional<T>) Optional.of(this.database);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void afterPropertiesSet() {
		if (!this.initializeSchema) {
			String indexName = typeName + "[" + PROPERTY_ID + ","
					+ PROPERTY_EMBEDDING + "]";
			try {
				var existingIndex = database.getSchema().getIndexByName(indexName);
				if (existingIndex instanceof HnswVectorIndex) {
					vectorIndex = (HnswVectorIndex<Object, float[], Float>) existingIndex;
				}
			}
			catch (Exception ex) {
				throw new IllegalStateException(
						"Schema not initialized and index not found: " + indexName,
						ex);
			}
			return;
		}

		database.transaction(() -> {
			Schema schema = database.getSchema();

			VertexType vertexType = schema.existsType(typeName)
					? (VertexType) schema.getType(typeName)
					: schema.createVertexType(typeName, 1);

			if (!vertexType.existsProperty(PROPERTY_ID)) {
				vertexType.createProperty(PROPERTY_ID, Type.STRING);
			}
			if (!vertexType.existsProperty(PROPERTY_EMBEDDING)) {
				vertexType.createProperty(PROPERTY_EMBEDDING, Type.ARRAY_OF_FLOATS);
			}
			if (!vertexType.existsProperty(PROPERTY_CONTENT)) {
				vertexType.createProperty(PROPERTY_CONTENT, Type.STRING);
			}
			if (!vertexType.existsProperty(PROPERTY_DELETED)) {
				vertexType.createProperty(PROPERTY_DELETED, Type.BOOLEAN);
			}

			if (vertexType.getPolymorphicIndexByProperties(PROPERTY_ID) == null) {
				schema.createTypeIndex(Schema.INDEX_TYPE.LSM_TREE, true, typeName,
						PROPERTY_ID);
			}
		});

		// Check if vector index already exists
		try {
			String indexName = typeName + "[" + PROPERTY_ID + ","
					+ PROPERTY_EMBEDDING + "]";
			var existingIndex = database.getSchema().getIndexByName(indexName);
			if (existingIndex instanceof HnswVectorIndex) {
				vectorIndex = (HnswVectorIndex<Object, float[], Float>) existingIndex;
				return;
			}
		}
		catch (Exception ex) {
			// Index doesn't exist yet, create it
		}

		database.transaction(() -> {
			var indexBuilder = database.getSchema().buildVectorIndex()
					.withVertexType(typeName)
					.withEdgeType(edgeType)
					.withVectorProperty(PROPERTY_EMBEDDING, Type.ARRAY_OF_FLOATS)
					.withIdProperty(PROPERTY_ID)
					.withDeletedProperty(PROPERTY_DELETED)
					.withDimensions(embeddingDimension)
					.withDistanceFunction(distanceType.getDistanceFunction())
					.withDistanceComparator((Comparator<Float>) Float::compareTo)
					.withM(m)
					.withEf(ef)
					.withEfConstruction(efConstruction)
					.withType(Schema.INDEX_TYPE.HNSW);
			vectorIndex = (HnswVectorIndex<Object, float[], Float>) indexBuilder
					.create();
		});
	}

	@Override
	public void doAdd(List<Document> documents) {
		if (documents == null || documents.isEmpty()) {
			return;
		}

		EmbeddingModel embeddingModel = this.embeddingModel;
		List<float[]> embeddings = new ArrayList<>(documents.size());
		for (Document doc : documents) {
			embeddings.add(embeddingModel.embed(doc));
		}

		List<Vertex> savedVertices = new ArrayList<>();

		for (int i = 0; i < documents.size(); i++) {
			final int idx = i;
			Document doc = documents.get(i);
			String id = doc.getId();
			if (id == null || id.isBlank()) {
				id = UUID.randomUUID().toString();
			}
			final String docId = id;

			database.transaction(() -> {
				hardDeleteById(docId);

				MutableVertex vertex = database.newVertex(typeName);
				vertex.set(PROPERTY_ID, docId);
				vertex.set(PROPERTY_EMBEDDING, embeddings.get(idx));
				vertex.set(PROPERTY_CONTENT, doc.getText());

				Map<String, Object> metadata = doc.getMetadata();
				if (metadata != null) {
					for (Map.Entry<String, Object> entry : metadata.entrySet()) {
						String propName = metadataPrefix + entry.getKey();
						Object value = entry.getValue();
						if (value instanceof UUID) {
							value = value.toString();
						}
						vertex.set(propName, value);
					}
				}

				vertex.save();
				savedVertices.add(vertex.asVertex());
			});
		}

		for (Vertex savedVertex : savedVertices) {
			database.transaction(() -> {
				vectorIndex.add(savedVertex);
			});
		}
	}

	@Override
	public void doDelete(List<String> idList) {
		if (idList == null || idList.isEmpty()) {
			return;
		}
		database.transaction(() -> {
			for (String id : idList) {
				softDeleteById(id);
			}
		});
	}

	@Override
	protected void doDelete(Filter.Expression filterExpression) {
		if (filterExpression == null) {
			return;
		}
		database.transaction(() -> {
			try (ResultSet rs = database.query("sql",
					"SELECT FROM " + quotedTypeName + " WHERE ("
							+ PROPERTY_DELETED + " IS NULL OR "
							+ PROPERTY_DELETED + " != true)")) {
				while (rs.hasNext()) {
					Result result = rs.next();
					result.getVertex().ifPresent(v -> {
						Map<String, Object> metadata = extractMetadata(v);
						if (ArcadeDBFilterExpressionConverter.matches(
								filterExpression, metadata)) {
							v.modify().set(PROPERTY_DELETED, true).save();
							String id = v.getString(PROPERTY_ID);
							if (id != null) {
								vectorIndex.remove(new Object[] { id });
							}
						}
					});
				}
			}
		});
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		float[] queryEmbedding = this.embeddingModel.embed(request.getQuery());
		int maxResults = request.getTopK();
		double similarityThreshold = request.getSimilarityThreshold();
		Filter.Expression filterExpression = request.getFilterExpression();

		int fetchSize = Math.max(maxResults * 4, maxResults + 100);
		List<Pair<Identifiable, ? extends Number>> neighbors = vectorIndex
				.findNeighborsFromVector(queryEmbedding, fetchSize);

		List<Document> results = new ArrayList<>();
		for (Pair<Identifiable, ? extends Number> neighbor : neighbors) {
			if (results.size() >= maxResults) {
				break;
			}

			double distance = neighbor.getSecond().doubleValue();
			double similarity = distanceType.toSimilarity(distance);

			if (similarity < similarityThreshold) {
				continue;
			}

			try {
				Vertex vertex = neighbor.getFirst().getRecord().asVertex();
				if (isDeleted(vertex)) {
					continue;
				}

				Map<String, Object> metadata = extractMetadata(vertex);
				if (filterExpression != null
						&& !ArcadeDBFilterExpressionConverter.matches(
								filterExpression, metadata)) {
					continue;
				}

				String id = vertex.getString(PROPERTY_ID);
				String content = vertex.has(PROPERTY_CONTENT)
						? vertex.getString(PROPERTY_CONTENT) : "";
				metadata.put("distance", 1.0 - similarity);

				Document doc = Document.builder()
						.id(id)
						.text(content)
						.metadata(metadata)
						.score(similarity)
						.build();
				results.add(doc);
			}
			catch (Exception ex) {
				logger.warn("Failed to load vertex {}: {}",
						neighbor.getFirst(), ex.getMessage());
			}
		}

		return results;
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(
			String operationName) {
		return VectorStoreObservationContext.builder(
				VectorStoreProvider.NEO4J.value(), operationName)
				.dimensions(this.embeddingDimension);
	}

	@Override
	public void close() {
		if (ownsDatabase && database != null && database.isOpen()) {
			database.close();
		}
	}

	private boolean isDeleted(Vertex vertex) {
		Object deleted = vertex.get(PROPERTY_DELETED);
		return Boolean.TRUE.equals(deleted);
	}

	private void softDeleteById(String id) {
		try (ResultSet rs = database.query("sql",
				"SELECT FROM " + quotedTypeName + " WHERE " + PROPERTY_ID
						+ " = ?", id)) {
			while (rs.hasNext()) {
				Result result = rs.next();
				result.getVertex().ifPresent(v -> {
					v.modify().set(PROPERTY_DELETED, true).save();
					vectorIndex.remove(new Object[] { id });
				});
			}
		}
	}

	private void hardDeleteById(String id) {
		try (ResultSet rs = database.query("sql",
				"SELECT FROM " + quotedTypeName + " WHERE " + PROPERTY_ID
						+ " = ?", id)) {
			while (rs.hasNext()) {
				Result result = rs.next();
				result.getVertex().ifPresent(v -> {
					vectorIndex.remove(new Object[] { id });
					v.delete();
				});
			}
		}
	}

	Map<String, Object> extractMetadata(com.arcadedb.database.Document doc) {
		Map<String, Object> metadata = new HashMap<>();
		for (String prop : doc.getPropertyNames()) {
			if (RESERVED_PROPERTIES.contains(prop)) {
				continue;
			}
			String key;
			if (!metadataPrefix.isEmpty() && prop.startsWith(metadataPrefix)) {
				key = prop.substring(metadataPrefix.length());
			}
			else if (metadataPrefix.isEmpty()) {
				key = prop;
			}
			else {
				continue;
			}
			metadata.put(key, doc.get(prop));
		}
		return metadata;
	}

	/**
	 * Create a new {@link Builder} instance.
	 * @param embeddingModel the embedding model to use
	 * @return a new Builder
	 */
	public static Builder builder(EmbeddingModel embeddingModel) {
		return new Builder(embeddingModel);
	}

	/**
	 * Builder for {@link ArcadeDBVectorStore}.
	 *
	 * @since 2.0.0
	 */
	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private String databasePath;

		private Database database;

		private String typeName = DEFAULT_TYPE_NAME;

		private String edgeType = DEFAULT_EDGE_TYPE;

		private String metadataPrefix = DEFAULT_METADATA_PREFIX;

		private int embeddingDimension = DEFAULT_EMBEDDING_DIMENSION;

		private ArcadeDBDistanceType distanceType = ArcadeDBDistanceType.COSINE;

		private int m = 16;

		private int ef = 10;

		private int efConstruction = 200;

		private boolean initializeSchema = true;

		private Builder(EmbeddingModel embeddingModel) {
			super(embeddingModel);
		}

		/**
		 * Set the path for the embedded ArcadeDB database directory.
		 * @param databasePath the database path
		 * @return this builder
		 */
		public Builder databasePath(String databasePath) {
			this.databasePath = databasePath;
			return this;
		}

		/**
		 * Use an existing ArcadeDB {@link Database} instance.
		 * @param database the database instance
		 * @return this builder
		 */
		public Builder database(Database database) {
			this.database = database;
			return this;
		}

		/**
		 * Set the vertex type name for stored documents.
		 * @param typeName the type name (default: "Document")
		 * @return this builder
		 */
		public Builder typeName(String typeName) {
			this.typeName = typeName;
			return this;
		}

		/**
		 * Set the edge type name for HNSW graph connections.
		 * @param edgeType the edge type (default: "HnswEdge")
		 * @return this builder
		 */
		public Builder edgeType(String edgeType) {
			this.edgeType = edgeType;
			return this;
		}

		/**
		 * Set the prefix for metadata properties on the vertex.
		 * @param metadataPrefix the prefix (default: "meta_")
		 * @return this builder
		 */
		public Builder metadataPrefix(String metadataPrefix) {
			this.metadataPrefix = metadataPrefix;
			return this;
		}

		/**
		 * Set the dimension of embedding vectors.
		 * @param embeddingDimension the dimension (default: 1536)
		 * @return this builder
		 */
		public Builder embeddingDimension(int embeddingDimension) {
			this.embeddingDimension = embeddingDimension;
			return this;
		}

		/**
		 * Set the distance metric type.
		 * @param distanceType COSINE or EUCLIDEAN (default: COSINE)
		 * @return this builder
		 */
		public Builder distanceType(ArcadeDBDistanceType distanceType) {
			this.distanceType = distanceType;
			return this;
		}

		/**
		 * Set the HNSW M parameter (max connections per node).
		 * @param m the M value (default: 16)
		 * @return this builder
		 */
		public Builder m(int m) {
			this.m = m;
			return this;
		}

		/**
		 * Set the HNSW ef parameter (search beam width).
		 * @param ef the ef value (default: 10)
		 * @return this builder
		 */
		public Builder ef(int ef) {
			this.ef = ef;
			return this;
		}

		/**
		 * Set the HNSW efConstruction parameter (construction beam width).
		 * @param efConstruction the value (default: 200)
		 * @return this builder
		 */
		public Builder efConstruction(int efConstruction) {
			this.efConstruction = efConstruction;
			return this;
		}

		/**
		 * Set whether to auto-create the schema on startup.
		 * @param initializeSchema true to initialize (default: true)
		 * @return this builder
		 */
		public Builder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		@Override
		public ArcadeDBVectorStore build() {
			return new ArcadeDBVectorStore(this);
		}

	}

}
