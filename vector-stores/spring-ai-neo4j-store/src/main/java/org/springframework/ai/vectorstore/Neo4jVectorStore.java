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

import org.neo4j.cypherdsl.support.schema_name.SchemaNames;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.filter.Neo4jVectorFilterExpressionConverter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * @author Gerrit Meier
 * @author Michael Simons
 */
public class Neo4jVectorStore implements VectorStore, InitializingBean {

	/**
	 * An enum to configure the distance function used in the Neo4j vector index.
	 */
	public enum Neo4jDistanceType {

		COSINE("cosine"), EUCLIDEAN("euclidean");

		public final String name;

		Neo4jDistanceType(String name) {
			this.name = name;
		}

	}

	/**
	 * Configuration for the Neo4j vector store.
	 */
	public static final class Neo4jVectorStoreConfig {

		private final SessionConfig sessionConfig;

		private final int embeddingDimension;

		private final Neo4jDistanceType distanceType;

		private final String label;

		private final String embeddingProperty;

		private final String quotedLabel;

		private final String indexName;

		/**
		 * Start building a new configuration.
		 * @return The entry point for creating a new configuration.
		 */
		public static Builder builder() {

			return new Builder();
		}

		/**
		 * {@return the default config}
		 */
		public static Neo4jVectorStoreConfig defaultConfig() {

			return builder().build();
		}

		private Neo4jVectorStoreConfig(Builder builder) {

			this.sessionConfig = Optional.ofNullable(builder.databaseName)
				.filter(Predicate.not(String::isBlank))
				.map(SessionConfig::forDatabase)
				.orElseGet(SessionConfig::defaultConfig);
			this.embeddingDimension = builder.embeddingDimension;
			this.distanceType = builder.distanceType;
			this.label = builder.label;
			this.embeddingProperty = builder.embeddingProperty;
			this.quotedLabel = SchemaNames.sanitize(this.label).orElseThrow();
			this.indexName = builder.indexName;
		}

		public static class Builder {

			private String databaseName;

			private int embeddingDimension = DEFAULT_EMBEDDING_DIMENSION;

			private Neo4jDistanceType distanceType = Neo4jDistanceType.COSINE;

			private String label = DEFAULT_LABEL;

			private String embeddingProperty = DEFAULT_EMBEDDING_PROPERTY;

			private String indexName = DEFAULT_INDEX_NAME;

			private Builder() {
			}

			/**
			 * Configures the Neo4j database name to use. Leave {@literal null} or blank
			 * to use the default database.
			 * @param databaseName the database name to use
			 * @return this builder
			 */
			public Builder withDatabaseName(String databaseName) {
				this.databaseName = databaseName;
				return this;
			}

			/**
			 * Configures the size of the embedding. Defaults to {@literal 1536}, inline
			 * with OpenAIs embeddings.
			 * @param newEmbeddingDimension The dimension of the embedding
			 * @return this builder
			 */
			public Builder withEmbeddingDimension(int newEmbeddingDimension) {

				Assert.isTrue(newEmbeddingDimension >= 1 && newEmbeddingDimension <= 2048,
						"Dimension has to be withing the boundaries 1 and 2048 (inclusively)");

				this.embeddingDimension = newEmbeddingDimension;
				return this;
			}

			/**
			 * Configures the distance type to store in the index and to use in queries.
			 * @param newDistanceType The distance type, must not be {@literal null}
			 * @return this builder
			 */
			public Builder withDistanceType(Neo4jDistanceType newDistanceType) {

				Assert.notNull(newDistanceType, "Distance type may not be null");

				this.distanceType = newDistanceType;
				return this;
			}

			/**
			 * Configures the node label to use for storing documents. Defaults to
			 * {@literal Document}.
			 * @param newLabel The label used on the nodes representing the document
			 * @return this builder
			 */
			public Builder withLabel(String newLabel) {

				Assert.hasText(newLabel, "Node label may not be null or blank");

				this.label = newLabel;
				return this;
			}

			/**
			 * Configures the property of the node to use for storing embedding. Defaults
			 * to {@literal embedding}.
			 * @param newEmbeddingProperty The property of the nodes for storing the
			 * embedding
			 * @return this builder
			 */
			public Builder withEmbeddingProperty(String newEmbeddingProperty) {

				Assert.hasText(newEmbeddingProperty, "Embedding property may not be null or blank");

				this.embeddingProperty = newEmbeddingProperty;
				return this;
			}

			/**
			 * Configures the vector index to be used. Defaults to
			 * {@literal spring-ai-document-index}.
			 * @param newIndexName The name of the index to be used for storing and
			 * searching data.
			 * @return this builder
			 */
			public Builder withIndexName(String newIndexName) {

				Assert.hasText(newIndexName, "Index name may not be null or blank");

				this.indexName = newIndexName;
				return this;
			}

			/**
			 * {@return the immutable configuration}
			 */
			public Neo4jVectorStoreConfig build() {

				return new Neo4jVectorStoreConfig(this);
			}

		}

	}

	public static final int DEFAULT_EMBEDDING_DIMENSION = 1536;

	public static final String DEFAULT_LABEL = "Document";

	public static final String DEFAULT_INDEX_NAME = "spring-ai-document-index";

	public static final String DEFAULT_EMBEDDING_PROPERTY = "embedding";

	private final Neo4jVectorFilterExpressionConverter filterExpressionConverter = new Neo4jVectorFilterExpressionConverter();

	private final Driver driver;

	private final EmbeddingClient embeddingClient;

	private final Neo4jVectorStoreConfig config;

	public Neo4jVectorStore(Driver driver, EmbeddingClient embeddingClient, Neo4jVectorStoreConfig config) {

		Assert.notNull(driver, "Neo4j driver must not be null");
		Assert.notNull(embeddingClient, "Embedding client must not be null");

		this.driver = driver;
		this.embeddingClient = embeddingClient;

		this.config = config;
	}

	@Override
	public void add(List<Document> documents) {

		var rows = documents.stream().map(this::documentToRecord).toList();

		try (var session = this.driver.session()) {
			var statement = """
						UNWIND $rows AS row
						MERGE (u:%s {id: row.id})
						ON CREATE
							SET u += row.properties
						ON MATCH
							SET u = {}
							SET u.id = row.id,
								u += row.properties
						WITH row, u
						CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.embedding)
					""".formatted(this.config.quotedLabel);
			session.run(statement, Map.of("rows", rows, "embeddingProperty", this.config.embeddingProperty)).consume();
		}
	}

	@Override
	public Optional<Boolean> delete(List<String> idList) {

		try (var session = this.driver.session(this.config.sessionConfig)) {

			var summary = session.run("""
					MATCH (n:%s) WHERE n.id IN $ids
					CALL { WITH n DETACH DELETE n } IN TRANSACTIONS OF $transactionSize ROWS
					 """.formatted(this.config.quotedLabel), Map.of("ids", idList, "transactionSize", 10_000))
				.consume();
			return Optional.of(idList.size() == summary.counters().nodesDeleted());
		}
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {
		Assert.isTrue(request.getTopK() > 0, "The number of documents to returned must be greater than zero");
		Assert.isTrue(request.getSimilarityThreshold() >= 0 && request.getSimilarityThreshold() <= 1,
				"The similarity score is bounded between 0 and 1; least to most similar respectively.");

		var embedding = Values.value(toFloatArray(this.embeddingClient.embed(request.getQuery())));
		try (var session = this.driver.session(this.config.sessionConfig)) {
			StringBuilder condition = new StringBuilder("score >= $threshold");
			if (request.hasFilterExpression()) {
				condition.append(" AND ")
					.append(this.filterExpressionConverter.convertExpression(request.getFilterExpression()));
			}
			String query = """
					CALL db.index.vector.queryNodes($indexName, $numberOfNearestNeighbours, $embeddingValue)
					YIELD node, score
					WHERE %s
					RETURN node, score""".formatted(condition);

			return session
				.run(query,
						Map.of("indexName", this.config.indexName, "numberOfNearestNeighbours", request.getTopK(),
								"embeddingValue", embedding, "threshold", request.getSimilarityThreshold()))
				.list(Neo4jVectorStore::recordToDocument);
		}
	}

	@Override
	public void afterPropertiesSet() {

		try (var session = this.driver.session(this.config.sessionConfig)) {

			session
				.run("CREATE CONSTRAINT %s IF NOT EXISTS FOR (n:%s) REQUIRE n.id IS UNIQUE".formatted(
						SchemaNames.sanitize(this.config.label + "_unique_idx").orElseThrow(), this.config.quotedLabel))
				.consume();

			var statement = """
					CREATE VECTOR INDEX %s IF NOT EXISTS FOR (n:%s) ON (n.%s)
							OPTIONS {indexConfig: {
							 `vector.dimensions`: %d,
							 `vector.similarity_function`: '%s'
							}}
					""".formatted(SchemaNames.sanitize(this.config.indexName, true).orElseThrow(),
					this.config.quotedLabel, this.config.embeddingProperty, this.config.embeddingDimension,
					this.config.distanceType.name);
			session.run(statement).consume();
			session.run("CALL db.awaitIndexes()").consume();
		}
	}

	private Map<String, Object> documentToRecord(Document document) {
		var embedding = this.embeddingClient.embed(document);
		document.setEmbedding(embedding);

		var row = new HashMap<String, Object>();

		row.put("id", document.getId());

		var properties = new HashMap<String, Object>();
		properties.put("text", document.getContent());

		document.getMetadata().forEach((k, v) -> properties.put("metadata." + k, Values.value(v)));
		row.put("properties", properties);

		row.put(this.config.embeddingProperty, Values.value(toFloatArray(embedding)));
		return row;
	}

	private static float[] toFloatArray(List<Double> embeddingDouble) {
		float[] embeddingFloat = new float[embeddingDouble.size()];
		int i = 0;
		for (Double d : embeddingDouble) {
			embeddingFloat[i++] = d.floatValue();
		}
		return embeddingFloat;
	}

	private static Document recordToDocument(org.neo4j.driver.Record neoRecord) {
		var node = neoRecord.get("node").asNode();
		var score = neoRecord.get("score").asFloat();
		var metaData = new HashMap<String, Object>();
		metaData.put("distance", 1 - score);
		node.keys().forEach(key -> {
			if (key.startsWith("metadata.")) {
				metaData.put(key.substring(key.indexOf(".") + 1), node.get(key).asObject());
			}
		});

		return new Document(node.get("id").asString(), node.get("text").asString(), Map.copyOf(metaData));
	}

}
