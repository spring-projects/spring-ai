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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;

/**
 * @author Theo van Kraay
 * @author Soby Chacko
 * @since 1.0.0
 */
public class CosmosDBVectorStore extends AbstractObservationVectorStore implements AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(CosmosDBVectorStore.class);

	private final CosmosAsyncClient cosmosClient;

	private final EmbeddingModel embeddingModel;

	private final CosmosDBVectorStoreConfig properties;

	private final BatchingStrategy batchingStrategy;

	private CosmosAsyncContainer container;

	public CosmosDBVectorStore(ObservationRegistry observationRegistry,
			VectorStoreObservationConvention customObservationConvention, CosmosAsyncClient cosmosClient,
			CosmosDBVectorStoreConfig properties, EmbeddingModel embeddingModel) {
		this(observationRegistry, customObservationConvention, cosmosClient, properties, embeddingModel,
				new TokenCountBatchingStrategy());
	}

	public CosmosDBVectorStore(ObservationRegistry observationRegistry,
			VectorStoreObservationConvention customObservationConvention, CosmosAsyncClient cosmosClient,
			CosmosDBVectorStoreConfig properties, EmbeddingModel embeddingModel, BatchingStrategy batchingStrategy) {
		super(observationRegistry, customObservationConvention);
		this.cosmosClient = cosmosClient;
		this.properties = properties;
		this.batchingStrategy = batchingStrategy;
		cosmosClient.createDatabaseIfNotExists(properties.getDatabaseName()).block();

		initializeContainer(properties.getContainerName(), properties.getDatabaseName(),
				properties.getVectorStoreThroughput(), properties.getVectorDimensions(),
				properties.getPartitionKeyPath());

		this.embeddingModel = embeddingModel;
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
		String content = document.getContent();

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
		this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(), this.batchingStrategy);

		// Create a list to hold both the CosmosItemOperation and the corresponding
		// document ID
		List<ImmutablePair<String, CosmosItemOperation>> itemOperationsWithIds = documents.stream().map(doc -> {
			CosmosItemOperation operation = CosmosBulkOperations
				.getCreateItemOperation(mapCosmosDocument(doc, doc.getEmbedding()), new PartitionKey(doc.getId()));
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
		return similaritySearch(SearchRequest.query(query));
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
					this.properties.getMetadataFieldsList()); // Use the expression
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
				.map(doc -> new Document(doc.get("id").asText(), doc.get("content").asText(), new HashMap<>()))
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
			.withCollectionName(this.container.getId())
			.withDimensions(this.embeddingModel.dimensions())
			.withNamespace(this.container.getDatabase().getId())
			.withSimilarityMetric("cosine");
	}

}
