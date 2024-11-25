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

package org.springframework.ai.vectorstore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import io.micrometer.observation.ObservationRegistry;
import org.neo4j.cypherdsl.support.schema_name.SchemaNames;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.filter.Neo4jVectorFilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

/**
 * A vector store implementation that stores and retrieves vectors in a Neo4j database.
 *
 * @author Gerrit Meier
 * @author Michael Simons
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Soby Chacko
 * @author Jihoon Kim
 */
public class Neo4jVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	public static final int DEFAULT_EMBEDDING_DIMENSION = 1536;

	public static final int DEFAULT_TRANSACTION_SIZE = 10_000;

	public static final String DEFAULT_LABEL = "Document";

	public static final String DEFAULT_INDEX_NAME = "spring-ai-document-index";

	public static final String DEFAULT_EMBEDDING_PROPERTY = "embedding";

	public static final String DEFAULT_ID_PROPERTY = "id";

	public static final String DEFAULT_CONSTRAINT_NAME = DEFAULT_LABEL + "_unique_idx";

	private static Map<Neo4jDistanceType, VectorStoreSimilarityMetric> SIMILARITY_TYPE_MAPPING = Map.of(
			Neo4jDistanceType.COSINE, VectorStoreSimilarityMetric.COSINE, Neo4jDistanceType.EUCLIDEAN,
			VectorStoreSimilarityMetric.EUCLIDEAN);

	private final Neo4jVectorFilterExpressionConverter filterExpressionConverter = new Neo4jVectorFilterExpressionConverter();

	private final Driver driver;

	private final EmbeddingModel embeddingModel;

	private final Neo4jVectorStoreConfig config;

	private final boolean initializeSchema;

	private final BatchingStrategy batchingStrategy;

	public Neo4jVectorStore(Driver driver, EmbeddingModel embeddingModel, Neo4jVectorStoreConfig config,
			boolean initializeSchema) {
		this(driver, embeddingModel, config, initializeSchema, ObservationRegistry.NOOP, null,
				new TokenCountBatchingStrategy());
	}

	public Neo4jVectorStore(Driver driver, EmbeddingModel embeddingModel, Neo4jVectorStoreConfig config,
			boolean initializeSchema, ObservationRegistry observationRegistry,
			VectorStoreObservationConvention customObservationConvention, BatchingStrategy batchingStrategy) {
		super(observationRegistry, customObservationConvention);

		this.initializeSchema = initializeSchema;
		Assert.notNull(driver, "Neo4j driver must not be null");
		Assert.notNull(embeddingModel, "Embedding model must not be null");
		this.driver = driver;
		this.embeddingModel = embeddingModel;
		this.config = config;
		this.batchingStrategy = batchingStrategy;
	}

	@Override
	public void doAdd(List<Document> documents) {

		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(),
				this.batchingStrategy);

		var rows = documents.stream()
			.map(document -> documentToRecord(document, embeddings.get(documents.indexOf(document))))
			.toList();

		try (var session = this.driver.session()) {
			var statement = """
						UNWIND $rows AS row
						MERGE (u:%s {%2$s: row.id})
						ON CREATE
							SET u += row.properties
						ON MATCH
							SET u = {}
							SET u.%2$s = row.id,
								u += row.properties
						WITH row, u
						CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row[$embeddingProperty])
					""".formatted(this.config.label, this.config.idProperty);
			session.executeWrite(
					tx -> tx.run(statement, Map.of("rows", rows, "embeddingProperty", this.config.embeddingProperty))
						.consume());
		}
	}

	@Override
	public Optional<Boolean> doDelete(List<String> idList) {

		try (var session = this.driver.session(this.config.sessionConfig)) {

			// Those queries with internal, cypher based transaction management cannot be
			// run with executeWrite
			var summary = session
				.run("""
						MATCH (n:%s) WHERE n.%s IN $ids
						CALL { WITH n DETACH DELETE n } IN TRANSACTIONS OF $transactionSize ROWS
						""".formatted(this.config.label, this.config.idProperty),
						Map.of("ids", idList, "transactionSize", DEFAULT_TRANSACTION_SIZE))
				.consume();
			return Optional.of(idList.size() == summary.counters().nodesDeleted());
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		Assert.isTrue(request.getTopK() > 0, "The number of documents to returned must be greater than zero");
		Assert.isTrue(request.getSimilarityThreshold() >= 0 && request.getSimilarityThreshold() <= 1,
				"The similarity score is bounded between 0 and 1; least to most similar respectively.");

		var embedding = Values.value(this.embeddingModel.embed(request.getQuery()));
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

			return session.executeRead(tx -> tx
				.run(query, Map.of("indexName", this.config.indexNameNotSanitized, "numberOfNearestNeighbours",
						request.getTopK(), "embeddingValue", embedding, "threshold", request.getSimilarityThreshold()))
				.list(this::recordToDocument));
		}
	}

	@Override
	public void afterPropertiesSet() {

		if (!this.initializeSchema) {
			return;
		}

		try (var session = this.driver.session(this.config.sessionConfig)) {

			session.executeWriteWithoutResult(tx -> {
				tx.run("CREATE CONSTRAINT %s IF NOT EXISTS FOR (n:%s) REQUIRE n.%s IS UNIQUE"
					.formatted(this.config.constraintName, this.config.label, this.config.idProperty)).consume();

				var statement = """
						CREATE VECTOR INDEX %s IF NOT EXISTS FOR (n:%s) ON (n.%s)
								OPTIONS {indexConfig: {
								`vector.dimensions`: %d,
								`vector.similarity_function`: '%s'
								}}
						""".formatted(this.config.indexName, this.config.label, this.config.embeddingProperty,
						this.config.embeddingDimension, this.config.distanceType.name);
				tx.run(statement).consume();
			});

			// Bad idea to retry this...
			session.run("CALL db.awaitIndexes()").consume();
		}
	}

	private Map<String, Object> documentToRecord(Document document, float[] embedding) {

		var row = new HashMap<String, Object>();

		row.put("id", document.getId());

		var properties = new HashMap<String, Object>();
		properties.put("text", document.getContent());

		document.getMetadata().forEach((k, v) -> properties.put("metadata." + k, Values.value(v)));
		row.put("properties", properties);

		row.put(this.config.embeddingProperty, Values.value(embedding));
		return row;
	}

	private Document recordToDocument(org.neo4j.driver.Record neoRecord) {
		var node = neoRecord.get("node").asNode();
		var score = neoRecord.get("score").asFloat();
		var metaData = new HashMap<String, Object>();
		metaData.put(DocumentMetadata.DISTANCE.value(), 1 - score);
		node.keys().forEach(key -> {
			if (key.startsWith("metadata.")) {
				metaData.put(key.substring(key.indexOf(".") + 1), node.get(key).asObject());
			}
		});

		return Document.builder()
			.id(node.get(this.config.idProperty).asString())
			.content(node.get("text").asString())
			.metadata(Map.copyOf(metaData))
			.score((double) score)
			.build();
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {

		return VectorStoreObservationContext.builder(VectorStoreProvider.NEO4J.value(), operationName)
			.withCollectionName(this.config.indexName)
			.withDimensions(this.embeddingModel.dimensions())
			.withSimilarityMetric(getSimilarityMetric());
	}

	private String getSimilarityMetric() {
		if (!SIMILARITY_TYPE_MAPPING.containsKey(this.config.distanceType)) {
			return this.config.distanceType.name();
		}
		return SIMILARITY_TYPE_MAPPING.get(this.config.distanceType).value();
	}

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

		private final String embeddingProperty;

		private final String label;

		private final String indexName;

		// needed for similarity search call
		private final String indexNameNotSanitized;

		private final String idProperty;

		private final String constraintName;

		private Neo4jVectorStoreConfig(Builder builder) {

			this.sessionConfig = Optional.ofNullable(builder.databaseName)
				.filter(Predicate.not(String::isBlank))
				.map(SessionConfig::forDatabase)
				.orElseGet(SessionConfig::defaultConfig);
			this.embeddingDimension = builder.embeddingDimension;
			this.distanceType = builder.distanceType;
			this.embeddingProperty = SchemaNames.sanitize(builder.embeddingProperty).orElseThrow();
			this.label = SchemaNames.sanitize(builder.label).orElseThrow();
			this.indexNameNotSanitized = builder.indexName;
			this.indexName = SchemaNames.sanitize(builder.indexName, true).orElseThrow();
			this.constraintName = SchemaNames.sanitize(builder.constraintName).orElseThrow();
			this.idProperty = SchemaNames.sanitize(builder.idProperty).orElseThrow();
		}

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

		public static final class Builder {

			private String databaseName;

			private int embeddingDimension = DEFAULT_EMBEDDING_DIMENSION;

			private Neo4jDistanceType distanceType = Neo4jDistanceType.COSINE;

			private String label = DEFAULT_LABEL;

			private String embeddingProperty = DEFAULT_EMBEDDING_PROPERTY;

			private String indexName = DEFAULT_INDEX_NAME;

			private String idProperty = DEFAULT_ID_PROPERTY;

			private String constraintName = DEFAULT_CONSTRAINT_NAME;

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

				Assert.isTrue(newEmbeddingDimension >= 1, "Dimension has to be positive.");

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

				Assert.hasText(newLabel, "Content label may not be null or blank");

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
			 * Configures the id property to be used. Defaults to {@literal id}.
			 * @param newIdProperty The name of the id property of the {@link Document}
			 * entity
			 * @return this builder
			 */
			public Builder withIdProperty(String newIdProperty) {

				Assert.hasText(newIdProperty, "Id property may not be null or blank");

				this.idProperty = newIdProperty;
				return this;
			}

			/**
			 * Configures the constraint name to be used. Defaults to
			 * {@literal Document_unique_idx}.
			 * @param newConstraintName The name of the unique constraint for the id
			 * property.
			 * @return this builder
			 */
			public Builder withConstraintName(String newConstraintName) {

				Assert.hasText(newConstraintName, "Constraint name may not be null or blank");

				this.constraintName = newConstraintName;
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

}
