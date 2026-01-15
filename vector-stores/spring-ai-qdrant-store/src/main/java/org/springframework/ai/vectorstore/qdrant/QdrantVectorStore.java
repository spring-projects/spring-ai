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

package org.springframework.ai.vectorstore.qdrant;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.PointId;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

/**
 * Qdrant vectorStore implementation. This store supports creating, updating, deleting,
 * and similarity searching of documents in a Qdrant collection.
 *
 * <p>
 * The store uses Qdrant's vector search functionality to persist and query vector
 * embeddings along with their associated document content and metadata. The
 * implementation leverages Qdrant's HNSW (Hierarchical Navigable Small World) algorithm
 * for efficient k-NN search operations.
 * </p>
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>Automatic schema initialization with configurable collection creation</li>
 * <li>Support for cosine similarity distance metric</li>
 * <li>Metadata filtering using Qdrant's filter expressions</li>
 * <li>Configurable similarity thresholds for search results</li>
 * <li>Batch processing support with configurable strategies</li>
 * <li>Observation and metrics support through Micrometer</li>
 * </ul>
 *
 * <p>
 * Basic usage example:
 * </p>
 * <pre>{@code
 * QdrantVectorStore vectorStore = QdrantVectorStore.builder(qdrantClient)
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
 * QdrantVectorStore vectorStore = QdrantVectorStore.builder(qdrantClient, embeddingModel)
 *     .collectionName("custom-collection")
 *     .initializeSchema(true)
 *     .batchingStrategy(new TokenCountBatchingStrategy())
 *     .observationRegistry(observationRegistry)
 *     .customObservationConvention(customConvention)
 *     .build();
 * }</pre>
 *
 * <p>
 * Requirements:
 * </p>
 * <ul>
 * <li>Running Qdrant instance accessible via gRPC</li>
 * <li>Collection with vector size matching the embedding model dimensions</li>
 * </ul>
 *
 * @author Anush Shetty
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Josh Long
 * @author Soby Chacko
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class QdrantVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	private static final Logger logger = LoggerFactory.getLogger(QdrantVectorStore.class);

	public static final String DEFAULT_COLLECTION_NAME = "vector_store";

	public static final String DEFAULT_CONTENT_FIELD_NAME = "doc_content";

	private final QdrantClient qdrantClient;

	private final String collectionName;

	private final String contentFieldName;

	private final QdrantFilterExpressionConverter filterExpressionConverter = new QdrantFilterExpressionConverter();

	private final boolean initializeSchema;

	/**
	 * Protected constructor for creating a QdrantVectorStore instance using the builder
	 * pattern.
	 * @param builder the {@link Builder} containing all configuration settings
	 * @throws IllegalArgumentException if qdrant client is missing
	 * @see Builder
	 * @since 1.0.0
	 */
	protected QdrantVectorStore(Builder builder) {
		super(builder);

		Assert.notNull(builder.qdrantClient, "QdrantClient must not be null");

		this.qdrantClient = builder.qdrantClient;
		this.collectionName = builder.collectionName;
		this.initializeSchema = builder.initializeSchema;
		this.contentFieldName = builder.contentFieldName;
	}

	/**
	 * Creates a new QdrantBuilder instance. This is the recommended way to instantiate a
	 * QdrantVectorStore.
	 * @param qdrantClient the client for interfacing with Qdrant
	 * @return a new QdrantBuilder instance
	 */
	public static Builder builder(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
		return new Builder(qdrantClient, embeddingModel);
	}

	/**
	 * Adds a list of documents to the vector store.
	 * @param documents The list of documents to be added.
	 */
	@Override
	public void doAdd(List<Document> documents) {
		try {

			// Compute and assign an embedding to the document.
			List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
					this.batchingStrategy);

			List<PointStruct> points = documents.stream()
				.map(document -> PointStruct.newBuilder()
					.setId(io.qdrant.client.PointIdFactory.id(UUID.fromString(document.getId())))
					.setVectors(io.qdrant.client.VectorsFactory.vectors(embeddings.get(documents.indexOf(document))))
					.putAllPayload(toPayload(document))
					.build())
				.toList();

			this.qdrantClient.upsertAsync(this.collectionName, points).get();
		}
		catch (InterruptedException | ExecutionException | IllegalArgumentException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Deletes a list of documents by their IDs.
	 * @param documentIds The list of document IDs to be deleted.
	 */
	@Override
	public void doDelete(List<String> documentIds) {
		try {
			List<PointId> ids = documentIds.stream()
				.map(id -> io.qdrant.client.PointIdFactory.id(UUID.fromString(id)))
				.toList();
			this.qdrantClient.deleteAsync(this.collectionName, ids).get();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void doDelete(org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression) {
		Assert.notNull(filterExpression, "Filter expression must not be null");

		try {
			Filter filter = this.filterExpressionConverter.convertExpression(filterExpression);

			io.qdrant.client.grpc.Points.UpdateResult response = this.qdrantClient
				.deleteAsync(this.collectionName, filter)
				.get();

			if (response.getStatus() != io.qdrant.client.grpc.Points.UpdateStatus.Completed) {
				throw new IllegalStateException("Failed to delete documents by filter: " + response.getStatus());
			}

			logger.debug("Deleted documents matching filter expression");
		}
		catch (Exception e) {
			logger.error("Failed to delete documents by filter: {}", e.getMessage(), e);
			throw new IllegalStateException("Failed to delete documents by filter", e);
		}
	}

	/**
	 * Performs a similarity search on the vector store.
	 * @param request The {@link SearchRequest} object containing the query and other
	 * search parameters.
	 * @return A list of documents that are similar to the query.
	 */
	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		try {
			Filter filter = (request.getFilterExpression() != null)
					? this.filterExpressionConverter.convertExpression(request.getFilterExpression())
					: Filter.getDefaultInstance();

			float[] queryEmbedding = this.embeddingModel.embed(request.getQuery());

			var searchPoints = SearchPoints.newBuilder()
				.setCollectionName(this.collectionName)
				.setLimit(request.getTopK())
				.setWithPayload(io.qdrant.client.WithPayloadSelectorFactory.enable(true))
				.addAllVector(EmbeddingUtils.toList(queryEmbedding))
				.setFilter(filter)
				.setScoreThreshold((float) request.getSimilarityThreshold())
				.build();

			var queryResponse = this.qdrantClient.searchAsync(searchPoints).get();

			return queryResponse.stream().map(this::toDocument).toList();

		}
		catch (InterruptedException | ExecutionException | IllegalArgumentException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns {@link Document} using the {@link ScoredPoint}
	 * @param point ScoredPoint containing the query response.
	 * @return the {@link Document} representing the response.
	 */
	private Document toDocument(ScoredPoint point) {
		try {
			var id = point.getId().getUuid();

			var metadata = QdrantObjectFactory.toObjectMap(point.getPayloadMap());
			metadata.put(DocumentMetadata.DISTANCE.value(), 1 - point.getScore());

			var content = (String) metadata.remove(this.contentFieldName);

			return Document.builder().id(id).text(content).metadata(metadata).score((double) point.getScore()).build();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Converts the document metadata to a Protobuf Struct.
	 * @param document The document containing metadata.
	 * @return The metadata as a Protobuf Struct.
	 */
	private Map<String, Value> toPayload(Document document) {
		try {
			var payload = QdrantValueFactory.toValueMap(document.getMetadata());
			payload.put(this.contentFieldName,
					io.qdrant.client.ValueFactory.value(Objects.requireNonNullElse(document.getText(), "")));
			return payload;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {

		if (!this.initializeSchema) {
			return;
		}

		// Create the collection if it does not exist.
		if (!isCollectionExists()) {
			var vectorParams = VectorParams.newBuilder()
				.setDistance(Distance.Cosine)
				.setSize(this.embeddingModel.dimensions())
				.build();
			this.qdrantClient.createCollectionAsync(this.collectionName, vectorParams).get();
		}
	}

	private boolean isCollectionExists() {
		try {
			return this.qdrantClient.listCollectionsAsync().get().stream().anyMatch(c -> c.equals(this.collectionName));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {

		return VectorStoreObservationContext.builder(VectorStoreProvider.QDRANT.value(), operationName)
			.dimensions(this.embeddingModel.dimensions())
			.collectionName(this.collectionName);

	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this.qdrantClient;
		return Optional.of(client);
	}

	/**
	 * Builder for creating instances of {@link QdrantVectorStore}. This builder provides
	 * a fluent API for configuring all aspects of the vector store.
	 *
	 * @since 1.0.0
	 */
	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private final QdrantClient qdrantClient;

		private String collectionName = DEFAULT_COLLECTION_NAME;

		private String contentFieldName = DEFAULT_CONTENT_FIELD_NAME;

		private boolean initializeSchema = false;

		/**
		 * Creates a new builder instance with the required QdrantClient and
		 * EmbeddingModel.
		 * @param qdrantClient the client for Qdrant operations
		 * @throws IllegalArgumentException if qdrantClient is null
		 */
		private Builder(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(qdrantClient, "QdrantClient must not be null");
			this.qdrantClient = qdrantClient;
		}

		/**
		 * Configures the Qdrant collection name.
		 * @param collectionName the name of the collection to use (defaults to
		 * {@value DEFAULT_COLLECTION_NAME})
		 * @return this builder instance
		 * @throws IllegalArgumentException if collectionName is null or empty
		 */
		public Builder collectionName(String collectionName) {
			Assert.hasText(collectionName, "collectionName must not be empty");
			this.collectionName = collectionName;
			return this;
		}

		/**
		 * Configures the Qdrant content field name.
		 * @param contentFieldName the name of the content field to use (defaults to
		 * {@value DEFAULT_CONTENT_FIELD_NAME})
		 * @return this builder instance
		 * @throws IllegalArgumentException if contentFieldName is null or empty
		 */
		public Builder contentFieldName(String contentFieldName) {
			Assert.hasText(contentFieldName, "contentFieldName must not be empty");
			this.contentFieldName = contentFieldName;
			return this;
		}

		/**
		 * Configures whether to initialize the collection schema.
		 * @param initializeSchema true to initialize schema automatically
		 * @return this builder instance
		 */
		public Builder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		/**
		 * Builds and returns a new QdrantVectorStore instance with the configured
		 * settings.
		 * @return a new QdrantVectorStore instance
		 * @throws IllegalStateException if the builder configuration is invalid
		 */
		@Override
		public QdrantVectorStore build() {
			return new QdrantVectorStore(this);
		}

	}

}
