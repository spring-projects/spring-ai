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

package org.springframework.ai.vectorstore.cosmosdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.implementation.guava25.collect.ImmutableList;
import com.azure.cosmos.models.CosmosBulkOperations;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosItemOperation;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.CosmosVectorDataType;
import com.azure.cosmos.models.CosmosVectorDistanceFunction;
import com.azure.cosmos.models.CosmosVectorEmbedding;
import com.azure.cosmos.models.CosmosVectorEmbeddingPolicy;
import com.azure.cosmos.models.CosmosVectorIndexSpec;
import com.azure.cosmos.models.CosmosVectorIndexType;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.PartitionKeyDefinition;
import com.azure.cosmos.models.PartitionKind;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.util.CosmosPagedFlux;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Cosmos DB implementation.
 *
 * @author Theo van Kraay
 * @author Soby Chacko
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class CosmosDBVectorStore extends AbstractObservationVectorStore implements AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(CosmosDBVectorStore.class);

	private final CosmosAsyncClient cosmosClient;

	private final String containerName;

	private final String databaseName;

	private final String partitionKeyPath;

	private final int vectorStoreThroughput;

	private final long vectorDimensions;

	private final List<String> metadataFieldsList;

	private final BatchingStrategy batchingStrategy;

	private CosmosAsyncContainer container;

	/**
	 * Creates a new CosmosDBVectorStore with basic configuration.
	 * @param observationRegistry the observation registry
	 * @param customObservationConvention the custom observation convention
	 * @param cosmosClient the Cosmos DB client
	 * @param properties the configuration properties
	 * @param embeddingModel the embedding model
	 * @deprecated Since 1.0.0-M5, use {@link #builder(CosmosAsyncClient, EmbeddingModel)}
	 * ()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public CosmosDBVectorStore(ObservationRegistry observationRegistry,
			VectorStoreObservationConvention customObservationConvention, CosmosAsyncClient cosmosClient,
			CosmosDBVectorStoreConfig properties, EmbeddingModel embeddingModel) {
		this(observationRegistry, customObservationConvention, cosmosClient, properties, embeddingModel,
				new TokenCountBatchingStrategy());
	}

	/**
	 * Creates a new CosmosDBVectorStore with full configuration.
	 * @param observationRegistry the observation registry
	 * @param customObservationConvention the custom observation convention
	 * @param cosmosClient the Cosmos DB client
	 * @param properties the configuration properties
	 * @param embeddingModel the embedding model
	 * @param batchingStrategy the batching strategy
	 * @deprecated Since 1.0.0-M5, use {@link #builder(CosmosAsyncClient, EmbeddingModel)}
	 * ()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public CosmosDBVectorStore(ObservationRegistry observationRegistry,
			VectorStoreObservationConvention customObservationConvention, CosmosAsyncClient cosmosClient,
			CosmosDBVectorStoreConfig properties, EmbeddingModel embeddingModel, BatchingStrategy batchingStrategy) {
		this(builder(cosmosClient, embeddingModel).containerName(properties.getContainerName())
			.databaseName(properties.getDatabaseName())
			.partitionKeyPath(properties.getPartitionKeyPath())
			.vectorStoreThroughput(properties.getVectorStoreThroughput())
			.vectorDimensions(properties.getVectorDimensions())
			.metadataFields(properties.getMetadataFieldsList())
			.observationRegistry(observationRegistry)
			.customObservationConvention(customObservationConvention)
			.batchingStrategy(batchingStrategy));
	}

	/**
	 * Protected constructor that accepts a builder instance. This is the preferred way to
	 * create new CosmosDBVectorStore instances.
	 * @param builder the configured builder instance
	 */
	protected CosmosDBVectorStore(Builder builder) {
		super(builder);

		Assert.notNull(builder.cosmosClient, "CosmosClient must not be null");
		Assert.hasText(builder.containerName, "Container name must not be empty");
		Assert.hasText(builder.databaseName, "Database name must not be empty");
		Assert.hasText(builder.partitionKeyPath, "Partition key path must not be empty");

		this.cosmosClient = builder.cosmosClient;
		this.containerName = builder.containerName;
		this.databaseName = builder.databaseName;
		this.partitionKeyPath = builder.partitionKeyPath;
		this.vectorStoreThroughput = builder.vectorStoreThroughput;
		this.vectorDimensions = builder.vectorDimensions;
		this.metadataFieldsList = builder.metadataFieldsList;
		this.batchingStrategy = builder.batchingStrategy;

		cosmosClient.createDatabaseIfNotExists(databaseName).block();
		initializeContainer(containerName, databaseName, vectorStoreThroughput, vectorDimensions, partitionKeyPath);
	}

	public static Builder builder(CosmosAsyncClient cosmosClient, EmbeddingModel embeddingModel) {
		return new Builder(cosmosClient, embeddingModel);
	}

	private void initializeContainer(String containerName, String databaseName, int vectorStoreThroughput,
			long vectorDimensions, String partitionKeyPath) {

		// Set defaults if not provided
		if (vectorStoreThroughput == 0) {
			vectorStoreThroughput = 400;
		}
		if (partitionKeyPath == null) {
			partitionKeyPath = "/id";
		}

		// handle hierarchical partition key
		PartitionKeyDefinition subPartitionKeyDefinition = new PartitionKeyDefinition();
		List<String> pathsFromCommaSeparatedList = new ArrayList<String>();
		String[] subPartitionKeyPaths = partitionKeyPath.split(",");
		Collections.addAll(pathsFromCommaSeparatedList, subPartitionKeyPaths);
		if (subPartitionKeyPaths.length > 1) {
			subPartitionKeyDefinition.setPaths(pathsFromCommaSeparatedList);
			subPartitionKeyDefinition.setKind(PartitionKind.MULTI_HASH);
		}
		else {
			subPartitionKeyDefinition.setPaths(Collections.singletonList(partitionKeyPath));
			subPartitionKeyDefinition.setKind(PartitionKind.HASH);
		}
		CosmosContainerProperties collectionDefinition = new CosmosContainerProperties(containerName,
				subPartitionKeyDefinition);
		// Set vector embedding policy
		CosmosVectorEmbeddingPolicy embeddingPolicy = new CosmosVectorEmbeddingPolicy();
		CosmosVectorEmbedding embedding = new CosmosVectorEmbedding();
		embedding.setPath("/embedding");
		embedding.setDataType(CosmosVectorDataType.FLOAT32);
		embedding.setDimensions(vectorDimensions);
		embedding.setDistanceFunction(CosmosVectorDistanceFunction.COSINE);
		embeddingPolicy.setCosmosVectorEmbeddings(Collections.singletonList(embedding));
		collectionDefinition.setVectorEmbeddingPolicy(embeddingPolicy);

		// set vector indexing policy
		IndexingPolicy indexingPolicy = new IndexingPolicy();
		indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
		ExcludedPath excludedPath = new ExcludedPath("/*");
		indexingPolicy.setExcludedPaths(Collections.singletonList(excludedPath));
		IncludedPath includedPath1 = new IncludedPath("/metadata/?");
		IncludedPath includedPath2 = new IncludedPath("/content/?");
		indexingPolicy.setIncludedPaths(ImmutableList.of(includedPath1, includedPath2));
		CosmosVectorIndexSpec cosmosVectorIndexSpec = new CosmosVectorIndexSpec();
		cosmosVectorIndexSpec.setPath("/embedding");
		cosmosVectorIndexSpec.setType(CosmosVectorIndexType.DISK_ANN.toString());
		indexingPolicy.setVectorIndexes(List.of(cosmosVectorIndexSpec));
		collectionDefinition.setIndexingPolicy(indexingPolicy);

		ThroughputProperties throughputProperties = ThroughputProperties.createManualThroughput(vectorStoreThroughput);
		CosmosAsyncDatabase cosmosAsyncDatabase = this.cosmosClient.getDatabase(databaseName);
		cosmosAsyncDatabase.createContainerIfNotExists(collectionDefinition, throughputProperties).block();
		this.container = cosmosAsyncDatabase.getContainer(containerName);
	}

	@Override
	public void close() {
		if (this.cosmosClient != null) {
			this.cosmosClient.close();
			logger.info("Cosmos DB client closed successfully.");
		}
	}

	private JsonNode mapCosmosDocument(Document document, float[] queryEmbedding) {
		ObjectMapper objectMapper = new ObjectMapper();

		String id = document.getId();
		String content = document.getText();

		// Convert metadata and embedding directly to JsonNode
		JsonNode metadataNode = objectMapper.valueToTree(document.getMetadata());
		JsonNode embeddingNode = objectMapper.valueToTree(queryEmbedding);

		// Create an ObjectNode specifically
		ObjectNode objectNode = objectMapper.createObjectNode();

		// Use put for simple values and set for JsonNode values
		objectNode.put("id", id);
		objectNode.put("content", content);
		objectNode.set("metadata", metadataNode); // Use set to add JsonNode directly
		objectNode.set("embedding", embeddingNode); // Use set to add JsonNode directly

		return objectNode;
	}

	@Override
	public void doAdd(List<Document> documents) {

		// Batch the documents based on the batching strategy
		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(),
				this.batchingStrategy);

		// Create a list to hold both the CosmosItemOperation and the corresponding
		// document ID
		List<ImmutablePair<String, CosmosItemOperation>> itemOperationsWithIds = documents.stream().map(doc -> {
			CosmosItemOperation operation = CosmosBulkOperations.getCreateItemOperation(
					mapCosmosDocument(doc, embeddings.get(documents.indexOf(doc))), new PartitionKey(doc.getId()));
			return new ImmutablePair<>(doc.getId(), operation); // Pair the document ID
			// with the operation
		}).toList();

		try {
			// Extract just the CosmosItemOperations from the pairs
			List<CosmosItemOperation> itemOperations = itemOperationsWithIds.stream()
				.map(ImmutablePair::getValue)
				.collect(Collectors.toList());

			this.container.executeBulkOperations(Flux.fromIterable(itemOperations)).doOnNext(response -> {
				if (response != null && response.getResponse() != null) {
					int statusCode = response.getResponse().getStatusCode();
					if (statusCode == 409) {
						// Retrieve the ID associated with the failed operation
						String documentId = itemOperationsWithIds.stream()
							.filter(pair -> pair.getValue().equals(response.getOperation()))
							.findFirst()
							.map(ImmutablePair::getKey)
							.orElse("Unknown ID"); // Fallback if the ID can't be found

						String errorMessage = String.format("Duplicate document id: %s", documentId);
						logger.error(errorMessage);
						throw new RuntimeException(errorMessage); // Throw an exception
						// for status code 409
					}
					else {
						logger.info("Document added with status: {}", statusCode);
					}
				}
				else {
					logger.warn("Received a null response or null status code for a document operation.");
				}
			})
				.doOnError(error -> logger.error("Error adding document: {}", error.getMessage()))
				.doOnComplete(() -> logger.info("Bulk operation completed successfully."))
				.blockLast(); // Block until the last item of the Flux is processed
		}
		catch (Exception e) {
			logger.error("Exception occurred during bulk add operation: {}", e.getMessage(), e);
			throw e; // Rethrow the exception after logging
		}
	}

	@Override
	public Optional<Boolean> doDelete(List<String> idList) {
		try {
			// Convert the list of IDs into bulk delete operations
			List<CosmosItemOperation> itemOperations = idList.stream()
				.map(id -> CosmosBulkOperations.getDeleteItemOperation(id, new PartitionKey(id)))
				.collect(Collectors.toList());

			// Execute bulk delete operations synchronously by using blockLast() on the
			// Flux
			this.container.executeBulkOperations(Flux.fromIterable(itemOperations))
				.doOnNext(response -> logger.info("Document deleted with status: {}",
						response.getResponse().getStatusCode()))
				.doOnError(error -> logger.error("Error deleting document: {}", error.getMessage()))
				.blockLast(); // This will block until all operations have finished

			return Optional.of(true);
		}
		catch (Exception e) {
			logger.error("Exception while deleting documents: {}", e.getMessage());
			return Optional.of(false);
		}
	}

	@Override
	public List<Document> similaritySearch(String query) {
		return similaritySearch(SearchRequest.builder().query(query).build());
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		// Ensure topK is within acceptable limits
		if (request.getTopK() > 1000) {
			throw new IllegalArgumentException("Top K must be 1000 or less.");
		}

		// Convert query into vector embedding
		float[] embedding = this.embeddingModel.embed(request.getQuery());

		logger.info("similarity threshold: {}", request.getSimilarityThreshold());

		List<Float> embeddingList = IntStream.range(0, embedding.length)
			.mapToObj(i -> embedding[i])
			.collect(Collectors.toList());

		// Start building query for similarity search
		StringBuilder queryBuilder = new StringBuilder("SELECT TOP @topK * FROM c WHERE ");
		queryBuilder.append("VectorDistance(c.embedding, @embedding) > @similarityThreshold");

		// Handle filter expression if it's set
		Filter.Expression filterExpression = request.getFilterExpression();
		if (filterExpression != null) {
			CosmosDBFilterExpressionConverter filterExpressionConverter = new CosmosDBFilterExpressionConverter(
					this.metadataFieldsList); // Use the expression
			// directly as
			// it handles the
			// "metadata"
			// fields internally
			String filterQuery = filterExpressionConverter.convertExpression(filterExpression);
			queryBuilder.append(" AND ").append(filterQuery);
		}

		queryBuilder.append(" ORDER BY VectorDistance(c.embedding, @embedding)");

		String query = queryBuilder.toString();
		List<SqlParameter> parameters = new ArrayList<>();
		parameters.add(new SqlParameter("@embedding", embeddingList));
		parameters.add(new SqlParameter("@topK", request.getTopK()));
		parameters.add(new SqlParameter("@similarityThreshold", request.getSimilarityThreshold()));

		SqlQuerySpec sqlQuerySpec = new SqlQuerySpec(query, parameters);
		CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

		CosmosPagedFlux<JsonNode> pagedFlux = this.container.queryItems(sqlQuerySpec, options, JsonNode.class);

		logger.info("Executing similarity search query: {}", query);
		try {
			// Collect documents from the paged flux
			List<JsonNode> documents = pagedFlux.byPage()
				.flatMap(page -> Flux.fromIterable(page.getResults()))
				.collectList()
				.block();
			// Convert JsonNode to Document
			List<Document> docs = documents.stream()
				.map(doc -> Document.builder().id(doc.get("id").asText()).text(doc.get("content").asText()).build())
				.collect(Collectors.toList());

			return docs != null ? docs : List.of();
		}
		catch (Exception e) {
			logger.error("Error during similarity search: {}", e.getMessage());
			return List.of();
		}
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		return VectorStoreObservationContext.builder(VectorStoreProvider.COSMOSDB.value(), operationName)
			.collectionName(this.container.getId())
			.dimensions(this.embeddingModel.dimensions())
			.namespace(this.container.getDatabase().getId())
			.similarityMetric("cosine");
	}

	/**
	 * Builder class for creating {@link CosmosDBVectorStore} instances.
	 * <p>
	 * Provides a fluent API for configuring all aspects of the Cosmos DB vector store.
	 *
	 * @since 1.0.0
	 */
	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private final CosmosAsyncClient cosmosClient;

		@Nullable
		private String containerName;

		@Nullable
		private String databaseName;

		@Nullable
		private String partitionKeyPath;

		private int vectorStoreThroughput = 400;

		private long vectorDimensions = 1536;

		private List<String> metadataFieldsList = new ArrayList<>();

		private BatchingStrategy batchingStrategy = new TokenCountBatchingStrategy();

		private Builder(CosmosAsyncClient cosmosClient, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(cosmosClient, "CosmosClient must not be null");
			this.cosmosClient = cosmosClient;
		}

		/**
		 * Sets the container name.
		 * @param containerName the name of the container
		 * @return the builder instance
		 * @throws IllegalArgumentException if containerName is null or empty
		 */
		public Builder containerName(String containerName) {
			Assert.hasText(containerName, "Container name must not be empty");
			this.containerName = containerName;
			return this;
		}

		/**
		 * Sets the database name.
		 * @param databaseName the name of the database
		 * @return the builder instance
		 * @throws IllegalArgumentException if databaseName is null or empty
		 */
		public Builder databaseName(String databaseName) {
			Assert.hasText(databaseName, "Database name must not be empty");
			this.databaseName = databaseName;
			return this;
		}

		/**
		 * Sets the partition key path.
		 * @param partitionKeyPath the partition key path
		 * @return the builder instance
		 * @throws IllegalArgumentException if partitionKeyPath is null or empty
		 */
		public Builder partitionKeyPath(String partitionKeyPath) {
			Assert.hasText(partitionKeyPath, "Partition key path must not be empty");
			this.partitionKeyPath = partitionKeyPath;
			return this;
		}

		/**
		 * Sets the vector store throughput.
		 * @param vectorStoreThroughput the throughput value
		 * @return the builder instance
		 * @throws IllegalArgumentException if vectorStoreThroughput is not positive
		 */
		public Builder vectorStoreThroughput(int vectorStoreThroughput) {
			Assert.isTrue(vectorStoreThroughput > 0, "Vector store throughput must be positive");
			this.vectorStoreThroughput = vectorStoreThroughput;
			return this;
		}

		/**
		 * Sets the vector dimensions.
		 * @param vectorDimensions the number of dimensions
		 * @return the builder instance
		 * @throws IllegalArgumentException if vectorDimensions is not positive
		 */
		public Builder vectorDimensions(long vectorDimensions) {
			Assert.isTrue(vectorDimensions > 0, "Vector dimensions must be positive");
			this.vectorDimensions = vectorDimensions;
			return this;
		}

		/**
		 * Sets the metadata fields list.
		 * @param metadataFieldsList the list of metadata fields
		 * @return the builder instance
		 */
		public Builder metadataFields(List<String> metadataFieldsList) {
			this.metadataFieldsList = metadataFieldsList != null ? new ArrayList<>(metadataFieldsList)
					: new ArrayList<>();
			return this;
		}

		/**
		 * Sets the batching strategy.
		 * @param batchingStrategy the strategy to use
		 * @return the builder instance
		 * @throws IllegalArgumentException if batchingStrategy is null
		 */
		public Builder batchingStrategy(BatchingStrategy batchingStrategy) {
			Assert.notNull(batchingStrategy, "BatchingStrategy must not be null");
			this.batchingStrategy = batchingStrategy;
			return this;
		}

		@Override
		public CosmosDBVectorStore build() {
			return new CosmosDBVectorStore(this);
		}

	}

}
