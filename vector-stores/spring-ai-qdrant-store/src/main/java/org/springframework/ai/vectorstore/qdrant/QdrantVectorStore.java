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

package org.springframework.ai.vectorstore.qdrant;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import io.micrometer.observation.ObservationRegistry;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.PointId;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.UpdateStatus;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

/**
 * Qdrant vectorStore implementation. This store supports creating, updating, deleting,
 * and similarity searching of documents in a Qdrant collection.
 *
 * @author Anush Shetty
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Josh Long
 * @author Soby Chacko
 * @author Thomas Vitale
 * @since 0.8.1
 */
public class QdrantVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	public static final String DEFAULT_COLLECTION_NAME = "vector_store";

	private static final String CONTENT_FIELD_NAME = "doc_content";

	private final EmbeddingModel embeddingModel;

	private final QdrantClient qdrantClient;

	private final String collectionName;

	private final QdrantFilterExpressionConverter filterExpressionConverter = new QdrantFilterExpressionConverter();

	private final boolean initializeSchema;

	private final BatchingStrategy batchingStrategy;

	/**
	 * Constructs a new QdrantVectorStore.
	 * @param qdrantClient A {@link QdrantClient} instance for interfacing with Qdrant.
	 * @param collectionName The name of the collection to use in Qdrant.
	 * @param embeddingModel The client for embedding operations.
	 * @param initializeSchema A boolean indicating whether to initialize the schema.
	 */
	public QdrantVectorStore(QdrantClient qdrantClient, String collectionName, EmbeddingModel embeddingModel,
			boolean initializeSchema) {
		this(qdrantClient, collectionName, embeddingModel, initializeSchema, ObservationRegistry.NOOP, null,
				new TokenCountBatchingStrategy());
	}

	/**
	 * Constructs a new QdrantVectorStore.
	 * @param qdrantClient A {@link QdrantClient} instance for interfacing with Qdrant.
	 * @param collectionName The name of the collection to use in Qdrant.
	 * @param embeddingModel The client for embedding operations.
	 * @param initializeSchema A boolean indicating whether to initialize the schema.
	 * @param observationRegistry The observation registry to use.
	 * @param customObservationConvention The custom search observation convention to use.
	 */
	public QdrantVectorStore(QdrantClient qdrantClient, String collectionName, EmbeddingModel embeddingModel,
			boolean initializeSchema, ObservationRegistry observationRegistry,
			VectorStoreObservationConvention customObservationConvention, BatchingStrategy batchingStrategy) {

		super(observationRegistry, customObservationConvention);

		Assert.notNull(qdrantClient, "QdrantClient must not be null");
		Assert.notNull(collectionName, "collectionName must not be null");
		Assert.notNull(embeddingModel, "EmbeddingModel must not be null");

		this.initializeSchema = initializeSchema;
		this.embeddingModel = embeddingModel;
		this.collectionName = collectionName;
		this.qdrantClient = qdrantClient;
		this.batchingStrategy = batchingStrategy;
	}

	/**
	 * Adds a list of documents to the vector store.
	 * @param documents The list of documents to be added.
	 */
	@Override
	public void doAdd(List<Document> documents) {
		try {

			// Compute and assign an embedding to the document.
			List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(),
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
	 * @return An optional boolean indicating the deletion status.
	 */
	@Override
	public Optional<Boolean> doDelete(List<String> documentIds) {
		try {
			List<PointId> ids = documentIds.stream()
				.map(id -> io.qdrant.client.PointIdFactory.id(UUID.fromString(id)))
				.toList();
			var result = this.qdrantClient.deleteAsync(this.collectionName, ids)
				.get()
				.getStatus() == UpdateStatus.Completed;
			return Optional.of(result);
		}
		catch (InterruptedException | ExecutionException | IllegalArgumentException e) {
			throw new RuntimeException(e);
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

			var content = (String) metadata.remove(CONTENT_FIELD_NAME);

			return Document.builder()
				.id(id)
				.content(content)
				.metadata(metadata)
				.score((double) point.getScore())
				.build();
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
			payload.put(CONTENT_FIELD_NAME, io.qdrant.client.ValueFactory.value(document.getContent()));
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
			.withDimensions(this.embeddingModel.dimensions())
			.withCollectionName(this.collectionName);

	}

}
