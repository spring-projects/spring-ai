/*
 * Copyright 2023-2024 the original author or authors.
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

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.util.annotation.NonNull;

/**
 * A VectorStore implementation backed by GemFire. This store supports creating, updating,
 * deleting, and similarity searching of documents in a GemFire index.
 *
 * @author Geet Rawat
 */
public class GemFireVectorStore implements VectorStore, InitializingBean {

	private static final Logger logger = LoggerFactory.getLogger(GemFireVectorStore.class);

	private static final String DEFAULT_URI = "http{ssl}://{host}:{port}/gemfire-vectordb/v1/indexes";

	private static final String EMBEDDINGS = "/embeddings";

	private final WebClient client;

	private final EmbeddingModel embeddingModel;

	private static final String DOCUMENT_FIELD = "document";

	private final boolean initializeSchema;

	/**
	 * Configures and initializes a GemFireVectorStore instance based on the provided
	 * configuration.
	 * @param config the configuration for the GemFireVectorStore
	 * @param embeddingModel the embedding client used for generating embeddings
	 */

	public GemFireVectorStore(GemFireVectorStoreConfig config, EmbeddingModel embeddingModel,
			boolean initializeSchema) {
		Assert.notNull(config, "GemFireVectorStoreConfig must not be null");
		Assert.notNull(embeddingModel, "EmbeddingModel must not be null");
		this.initializeSchema = initializeSchema;
		this.indexName = config.indexName;
		this.embeddingModel = embeddingModel;
		this.beamWidth = config.beamWidth;
		this.maxConnections = config.maxConnections;
		this.buckets = config.buckets;
		this.vectorSimilarityFunction = config.vectorSimilarityFunction;
		this.fields = config.fields;

		String base = UriComponentsBuilder.fromUriString(DEFAULT_URI)
			.build(config.sslEnabled ? "s" : "", config.host, config.port)
			.toString();
		this.client = WebClient.create(base);
	}

	// Create Index Parameters

	private String indexName;

	public String getIndexName() {
		return indexName;
	}

	private int beamWidth;

	public int getBeamWidth() {
		return beamWidth;
	}

	private int maxConnections;

	public int getMaxConnections() {
		return maxConnections;
	}

	private int buckets;

	public int getBuckets() {
		return buckets;
	}

	private String vectorSimilarityFunction;

	public String getVectorSimilarityFunction() {
		return vectorSimilarityFunction;
	}

	private String[] fields;

	public String[] getFields() {
		return fields;
	}

	// Query Defaults
	private static final String QUERY = "/query";

	private static final String DISTANCE_METADATA_FIELD_NAME = "distance";

	/**
	 * Initializes the GemFireVectorStore after properties are set. This method is called
	 * after all bean properties have been set and allows the bean to perform any
	 * initialization it requires.
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		if (!this.initializeSchema) {
			return;
		}
		if (!indexExists()) {
			createIndex();
		}
	}

	/**
	 * Checks if the index exists in the GemFireVectorStore.
	 * @return {@code true} if the index exists, {@code false} otherwise
	 */
	public boolean indexExists() {
		String indexResponse = getIndex();
		return !indexResponse.isEmpty();
	}

	public String getIndex() {
		return client.get().uri("/" + indexName).retrieve().bodyToMono(String.class).onErrorReturn("").block();
	}

	public static class CreateRequest {

		@JsonProperty("name")
		private String indexName;

		@JsonProperty("beam-width")
		private int beamWidth;

		@JsonProperty("max-connections")
		private int maxConnections;

		@JsonProperty("vector-similarity-function")
		private String vectorSimilarityFunction;

		@JsonProperty("fields")
		private String[] fields;

		@JsonProperty("buckets")
		private int buckets;

		public CreateRequest() {
		}

		public CreateRequest(String indexName) {
			this.indexName = indexName;
		}

		public String getIndexName() {
			return indexName;
		}

		public void setIndexName(String indexName) {
			this.indexName = indexName;
		}

		public int getBeamWidth() {
			return beamWidth;
		}

		public void setBeamWidth(int beamWidth) {
			this.beamWidth = beamWidth;
		}

		public int getMaxConnections() {
			return maxConnections;
		}

		public void setMaxConnections(int maxConnections) {
			this.maxConnections = maxConnections;
		}

		public String getVectorSimilarityFunction() {
			return vectorSimilarityFunction;
		}

		public void setVectorSimilarityFunction(String vectorSimilarityFunction) {
			this.vectorSimilarityFunction = vectorSimilarityFunction;
		}

		public String[] getFields() {
			return fields;
		}

		public void setFields(String[] fields) {
			this.fields = fields;
		}

		public int getBuckets() {
			return buckets;
		}

		public void setBuckets(int buckets) {
			this.buckets = buckets;
		}

	}

	private static final class UploadRequest {

		private final List<Embedding> embeddings;

		public List<Embedding> getEmbeddings() {
			return embeddings;
		}

		@JsonCreator
		public UploadRequest(@JsonProperty("embeddings") List<Embedding> embeddings) {
			this.embeddings = embeddings;
		}

		private static final class Embedding {

			private final String key;

			private List<Float> vector;

			@JsonInclude(JsonInclude.Include.NON_NULL)
			private Map<String, Object> metadata;

			public Embedding(@JsonProperty("key") String key, @JsonProperty("vector") List<Float> vector,
					String contentName, String content, @JsonProperty("metadata") Map<String, Object> metadata) {
				this.key = key;
				this.vector = vector;
				this.metadata = new HashMap<>(metadata);
				this.metadata.put(contentName, content);
			}

			public String getKey() {
				return key;
			}

			public List<Float> getVector() {
				return vector;
			}

			public Map<String, Object> getMetadata() {
				return metadata;
			}

		}

	}

	private static final class QueryRequest {

		@JsonProperty("vector")
		@NonNull
		private final List<Float> vector;

		@JsonProperty("top-k")
		private final int k;

		@JsonProperty("k-per-bucket")
		private final int kPerBucket;

		@JsonProperty("include-metadata")
		private final boolean includeMetadata;

		public QueryRequest(List<Float> vector, int k, int kPerBucket, boolean includeMetadata) {
			this.vector = vector;
			this.k = k;
			this.kPerBucket = kPerBucket;
			this.includeMetadata = includeMetadata;
		}

		public List<Float> getVector() {
			return vector;
		}

		public int getK() {
			return k;
		}

		public int getkPerBucket() {
			return kPerBucket;
		}

		public boolean isIncludeMetadata() {
			return includeMetadata;
		}

	}

	private static final class QueryResponse {

		private String key;

		private float score;

		private Map<String, Object> metadata;

		private String getContent(String field) {
			return (String) metadata.get(field);
		}

		public void setKey(String key) {
			this.key = key;
		}

		public void setScore(float score) {
			this.score = score;
		}

		public void setMetadata(Map<String, Object> metadata) {
			this.metadata = metadata;
		}

	}

	private static class DeleteRequest {

		@JsonProperty("delete-data")
		private boolean deleteData = true;

		public DeleteRequest() {
		}

		public DeleteRequest(boolean deleteData) {
			this.deleteData = deleteData;
		}

		public boolean isDeleteData() {
			return deleteData;
		}

		public void setDeleteData(boolean deleteData) {
			this.deleteData = deleteData;
		}

	}

	@Override
	public void add(List<Document> documents) {
		UploadRequest upload = new UploadRequest(documents.stream().map(document -> {
			// Compute and assign an embedding to the document.
			document.setEmbedding(this.embeddingModel.embed(document));
			List<Float> floatVector = document.getEmbedding().stream().map(Double::floatValue).toList();
			return new UploadRequest.Embedding(document.getId(), floatVector, DOCUMENT_FIELD, document.getContent(),
					document.getMetadata());
		}).toList());

		ObjectMapper objectMapper = new ObjectMapper();
		String embeddingsJson = null;
		try {
			String embeddingString = objectMapper.writeValueAsString(upload);
			embeddingsJson = embeddingString.substring("{\"embeddings\":".length());
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(String.format("Embedding JSON parsing error: %s", e.getMessage()));
		}

		client.post()
			.uri("/" + indexName + EMBEDDINGS)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(embeddingsJson)
			.retrieve()
			.bodyToMono(Void.class)
			.onErrorMap(WebClientException.class, this::handleHttpClientException)
			.block();
	}

	@Override
	public Optional<Boolean> delete(List<String> idList) {
		try {
			client.method(HttpMethod.DELETE)
				.uri("/" + indexName + EMBEDDINGS)
				.body(BodyInserters.fromValue(idList))
				.retrieve()
				.bodyToMono(Void.class)
				.block();
		}
		catch (Exception e) {
			logger.warn("Error removing embedding: {}", e.getMessage(), e);
			return Optional.of(false);
		}
		return Optional.of(true);
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {
		if (request.hasFilterExpression()) {
			throw new UnsupportedOperationException("GemFire currently does not support metadata filter expressions.");
		}
		List<Double> vector = this.embeddingModel.embed(request.getQuery());
		List<Float> floatVector = vector.stream().map(Double::floatValue).toList();
		return client.post()
			.uri("/" + indexName + QUERY)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(new QueryRequest(floatVector, request.getTopK(), request.getTopK(), // TopKPerBucket
					true))
			.retrieve()
			.bodyToFlux(QueryResponse.class)
			.filter(r -> r.score >= request.getSimilarityThreshold())
			.map(r -> {
				Map<String, Object> metadata = r.metadata;
				if (r.metadata == null) {
					metadata = new HashMap<>();
					metadata.put(DOCUMENT_FIELD, "--Deleted--");
				}
				metadata.put(DISTANCE_METADATA_FIELD_NAME, 1 - r.score);
				String content = (String) metadata.remove(DOCUMENT_FIELD);
				return new Document(r.key, content, metadata);
			})
			.collectList()
			.onErrorMap(WebClientException.class, this::handleHttpClientException)
			.block();
	}

	/**
	 * Creates a new index in the GemFireVectorStore using specified parameters. This
	 * method is invoked during initialization.
	 * @throws JsonProcessingException if an error occurs during JSON processing
	 */
	public void createIndex() throws JsonProcessingException {
		CreateRequest createRequest = new CreateRequest(indexName);
		createRequest.setBeamWidth(beamWidth);
		createRequest.setMaxConnections(maxConnections);
		createRequest.setBuckets(buckets);
		createRequest.setVectorSimilarityFunction(vectorSimilarityFunction);
		createRequest.setFields(fields);

		ObjectMapper objectMapper = new ObjectMapper();
		String index = objectMapper.writeValueAsString(createRequest);

		client.post()
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(index)
			.retrieve()
			.bodyToMono(Void.class)
			.onErrorMap(WebClientException.class, this::handleHttpClientException)
			.block();
	}

	public void deleteIndex() {
		DeleteRequest deleteRequest = new DeleteRequest();
		client.method(HttpMethod.DELETE)
			.uri("/" + indexName)
			.body(BodyInserters.fromValue(deleteRequest))
			.retrieve()
			.bodyToMono(Void.class)
			.onErrorMap(WebClientException.class, this::handleHttpClientException)
			.block();
	}

	/**
	 * Handles exceptions that occur during HTTP client operations and maps them to
	 * appropriate runtime exceptions.
	 * @param ex the exception that occurred during HTTP client operation
	 * @return a mapped runtime exception corresponding to the HTTP client exception
	 */
	private Throwable handleHttpClientException(Throwable ex) {
		if (!(ex instanceof WebClientResponseException clientException)) {
			throw new RuntimeException(String.format("Got an unexpected error: %s", ex));
		}

		if (clientException.getStatusCode().equals(NOT_FOUND)) {
			throw new RuntimeException(String.format("Index %s not found: %s", indexName, ex));
		}
		else if (clientException.getStatusCode().equals(BAD_REQUEST)) {
			throw new RuntimeException(String.format("Bad Request: %s", ex));
		}
		else {
			throw new RuntimeException(String.format("Got an unexpected HTTP error: %s", ex));
		}
	}

}
