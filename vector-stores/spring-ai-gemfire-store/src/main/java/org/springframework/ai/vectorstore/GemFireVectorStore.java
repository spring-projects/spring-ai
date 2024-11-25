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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.DocumentMetadata;
import reactor.util.annotation.NonNull;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * A VectorStore implementation backed by GemFire. This store supports creating, updating,
 * deleting, and similarity searching of documents in a GemFire index.
 *
 * @author Geet Rawat
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Soby Chacko
 * @author Sebastien Deleuze
 */
public class GemFireVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	private static final Logger logger = LoggerFactory.getLogger(GemFireVectorStore.class);

	private static final String DEFAULT_URI = "http{ssl}://{host}:{port}/gemfire-vectordb/v1/indexes";

	private static final String EMBEDDINGS = "/embeddings";

	private final WebClient client;

	private final EmbeddingModel embeddingModel;

	private static final String DOCUMENT_FIELD = "document";

	private final boolean initializeSchema;

	private final BatchingStrategy batchingStrategy;

	private final ObjectMapper objectMapper;

	/**
	 * Configures and initializes a GemFireVectorStore instance based on the provided
	 * configuration.
	 * @param config the configuration for the GemFireVectorStore
	 * @param embeddingModel the embedding client used for generating embeddings
	 * @param initializeSchema whether to initialize the schema during initialization
	 */
	public GemFireVectorStore(GemFireVectorStoreConfig config, EmbeddingModel embeddingModel,
			boolean initializeSchema) {
		this(config, embeddingModel, initializeSchema, ObservationRegistry.NOOP, null,
				new TokenCountBatchingStrategy());
	}

	/**
	 * Configures and initializes a GemFireVectorStore instance based on the provided
	 * configuration.
	 * @param config the configuration for the GemFireVectorStore
	 * @param embeddingModel the embedding client used for generating embeddings
	 * @param initializeSchema whether to initialize the schema during initialization
	 * @param observationRegistry the observation registry to use for recording
	 * observations
	 * @param customObservationConvention the custom observation convention to use for
	 * observing operations
	 */
	public GemFireVectorStore(GemFireVectorStoreConfig config, EmbeddingModel embeddingModel, boolean initializeSchema,
			ObservationRegistry observationRegistry, VectorStoreObservationConvention customObservationConvention,
			BatchingStrategy batchingStrategy) {

		super(observationRegistry, customObservationConvention);

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
		this.batchingStrategy = batchingStrategy;
		this.objectMapper = JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build();
	}

	// Create Index Parameters

	private String indexName;

	public String getIndexName() {
		return this.indexName;
	}

	private int beamWidth;

	public int getBeamWidth() {
		return this.beamWidth;
	}

	private int maxConnections;

	public int getMaxConnections() {
		return this.maxConnections;
	}

	private int buckets;

	public int getBuckets() {
		return this.buckets;
	}

	private String vectorSimilarityFunction;

	public String getVectorSimilarityFunction() {
		return this.vectorSimilarityFunction;
	}

	private String[] fields;

	public String[] getFields() {
		return this.fields;
	}

	// Query Defaults
	private static final String QUERY = "/query";

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
		return this.client.get()
			.uri("/" + this.indexName)
			.retrieve()
			.bodyToMono(String.class)
			.onErrorReturn("")
			.block();
	}

	@Override
	public void doAdd(List<Document> documents) {
		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(),
				this.batchingStrategy);
		UploadRequest upload = new UploadRequest(documents.stream()
			.map(document -> new UploadRequest.Embedding(document.getId(), embeddings.get(documents.indexOf(document)),
					DOCUMENT_FIELD, document.getContent(), document.getMetadata()))
			.toList());

		String embeddingsJson = null;
		try {
			String embeddingString = this.objectMapper.writeValueAsString(upload);
			embeddingsJson = embeddingString.substring("{\"embeddings\":".length());
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(String.format("Embedding JSON parsing error: %s", e.getMessage()));
		}

		this.client.post()
			.uri("/" + this.indexName + EMBEDDINGS)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(embeddingsJson)
			.retrieve()
			.bodyToMono(Void.class)
			.onErrorMap(WebClientException.class, this::handleHttpClientException)
			.block();
	}

	@Override
	public Optional<Boolean> doDelete(List<String> idList) {
		try {
			this.client.method(HttpMethod.DELETE)
				.uri("/" + this.indexName + EMBEDDINGS)
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
	public List<Document> doSimilaritySearch(SearchRequest request) {
		if (request.hasFilterExpression()) {
			throw new UnsupportedOperationException("GemFire currently does not support metadata filter expressions.");
		}
		float[] floatVector = this.embeddingModel.embed(request.getQuery());
		return this.client.post()
			.uri("/" + this.indexName + QUERY)
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
				metadata.put(DocumentMetadata.DISTANCE.value(), 1 - r.score);
				String content = (String) metadata.remove(DOCUMENT_FIELD);
				return Document.builder().id(r.key).content(content).metadata(metadata).score((double) r.score).build();
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
		CreateRequest createRequest = new CreateRequest(this.indexName);
		createRequest.setBeamWidth(this.beamWidth);
		createRequest.setMaxConnections(this.maxConnections);
		createRequest.setBuckets(this.buckets);
		createRequest.setVectorSimilarityFunction(this.vectorSimilarityFunction);
		createRequest.setFields(this.fields);

		String index = this.objectMapper.writeValueAsString(createRequest);

		this.client.post()
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(index)
			.retrieve()
			.bodyToMono(Void.class)
			.onErrorMap(WebClientException.class, this::handleHttpClientException)
			.block();
	}

	public void deleteIndex() {
		DeleteRequest deleteRequest = new DeleteRequest();
		this.client.method(HttpMethod.DELETE)
			.uri("/" + this.indexName)
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

		if (clientException.getStatusCode().equals(org.springframework.http.HttpStatus.NOT_FOUND)) {
			throw new RuntimeException(String.format("Index %s not found: %s", this.indexName, ex));
		}
		else if (clientException.getStatusCode().equals(org.springframework.http.HttpStatus.BAD_REQUEST)) {
			throw new RuntimeException(String.format("Bad Request: %s", ex));
		}
		else {
			throw new RuntimeException(String.format("Got an unexpected HTTP error: %s", ex));
		}
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		return VectorStoreObservationContext.builder(VectorStoreProvider.GEMFIRE.value(), operationName)
			.withCollectionName(this.indexName)
			.withDimensions(this.embeddingModel.dimensions())
			.withFieldName(EMBEDDINGS);
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
			return this.indexName;
		}

		public void setIndexName(String indexName) {
			this.indexName = indexName;
		}

		public int getBeamWidth() {
			return this.beamWidth;
		}

		public void setBeamWidth(int beamWidth) {
			this.beamWidth = beamWidth;
		}

		public int getMaxConnections() {
			return this.maxConnections;
		}

		public void setMaxConnections(int maxConnections) {
			this.maxConnections = maxConnections;
		}

		public String getVectorSimilarityFunction() {
			return this.vectorSimilarityFunction;
		}

		public void setVectorSimilarityFunction(String vectorSimilarityFunction) {
			this.vectorSimilarityFunction = vectorSimilarityFunction;
		}

		public String[] getFields() {
			return this.fields;
		}

		public void setFields(String[] fields) {
			this.fields = fields;
		}

		public int getBuckets() {
			return this.buckets;
		}

		public void setBuckets(int buckets) {
			this.buckets = buckets;
		}

	}

	private static final class UploadRequest {

		private final List<Embedding> embeddings;

		public List<Embedding> getEmbeddings() {
			return this.embeddings;
		}

		@JsonCreator
		UploadRequest(@JsonProperty("embeddings") List<Embedding> embeddings) {
			this.embeddings = embeddings;
		}

		private static final class Embedding {

			private final String key;

			private float[] vector;

			@JsonInclude(JsonInclude.Include.NON_NULL)
			private Map<String, Object> metadata;

			Embedding(@JsonProperty("key") String key, @JsonProperty("vector") float[] vector, String contentName,
					String content, @JsonProperty("metadata") Map<String, Object> metadata) {
				this.key = key;
				this.vector = vector;
				this.metadata = new HashMap<>(metadata);
				this.metadata.put(contentName, content);
			}

			public String getKey() {
				return this.key;
			}

			public float[] getVector() {
				return this.vector;
			}

			public Map<String, Object> getMetadata() {
				return this.metadata;
			}

		}

	}

	private static final class QueryRequest {

		@JsonProperty("vector")
		@NonNull
		private final float[] vector;

		@JsonProperty("top-k")
		private final int k;

		@JsonProperty("k-per-bucket")
		private final int kPerBucket;

		@JsonProperty("include-metadata")
		private final boolean includeMetadata;

		QueryRequest(float[] vector, int k, int kPerBucket, boolean includeMetadata) {
			this.vector = vector;
			this.k = k;
			this.kPerBucket = kPerBucket;
			this.includeMetadata = includeMetadata;
		}

		public float[] getVector() {
			return this.vector;
		}

		public int getK() {
			return this.k;
		}

		public int getkPerBucket() {
			return this.kPerBucket;
		}

		public boolean isIncludeMetadata() {
			return this.includeMetadata;
		}

	}

	private static final class QueryResponse {

		private String key;

		private float score;

		private Map<String, Object> metadata;

		private String getContent(String field) {
			return (String) this.metadata.get(field);
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

		DeleteRequest() {
		}

		DeleteRequest(boolean deleteData) {
			this.deleteData = deleteData;
		}

		public boolean isDeleteData() {
			return this.deleteData;
		}

		public void setDeleteData(boolean deleteData) {
			this.deleteData = deleteData;
		}

	}

	public static final class GemFireVectorStoreConfig {

		// Create Index DEFAULT Values
		public static final String DEFAULT_HOST = "localhost";

		public static final int DEFAULT_PORT = 8080;

		public static final String DEFAULT_INDEX_NAME = "spring-ai-gemfire-index";

		public static final int UPPER_BOUND_BEAM_WIDTH = 3200;

		public static final int DEFAULT_BEAM_WIDTH = 100;

		private static final int UPPER_BOUND_MAX_CONNECTIONS = 512;

		public static final int DEFAULT_MAX_CONNECTIONS = 16;

		public static final String DEFAULT_SIMILARITY_FUNCTION = "COSINE";

		public static final String[] DEFAULT_FIELDS = new String[] {};

		public static final int DEFAULT_BUCKETS = 0;

		public static final boolean DEFAULT_SSL_ENABLED = false;

		String host;

		int port;

		String indexName;

		int beamWidth;

		int maxConnections;

		String vectorSimilarityFunction;

		String[] fields;

		int buckets;

		boolean sslEnabled;

		private GemFireVectorStoreConfig(Builder builder) {
			this.host = builder.host;
			this.port = builder.port;
			this.sslEnabled = builder.sslEnabled;
			this.indexName = builder.indexName;
			this.beamWidth = builder.beamWidth;
			this.maxConnections = builder.maxConnections;
			this.buckets = builder.buckets;
			this.vectorSimilarityFunction = builder.vectorSimilarityFunction;
			this.fields = builder.fields;
		}

		/**
		 * Start building a new configuration.
		 * @return The entry point for creating a new configuration.
		 */
		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			// Create Index DEFAULT Values
			String host = GemFireVectorStoreConfig.DEFAULT_HOST;

			int port = GemFireVectorStoreConfig.DEFAULT_PORT;

			String indexName = GemFireVectorStoreConfig.DEFAULT_INDEX_NAME;

			int beamWidth = GemFireVectorStoreConfig.DEFAULT_BEAM_WIDTH;

			int maxConnections = GemFireVectorStoreConfig.DEFAULT_MAX_CONNECTIONS;

			String vectorSimilarityFunction = GemFireVectorStoreConfig.DEFAULT_SIMILARITY_FUNCTION;

			String[] fields = GemFireVectorStoreConfig.DEFAULT_FIELDS;

			int buckets = GemFireVectorStoreConfig.DEFAULT_BUCKETS;

			boolean sslEnabled = GemFireVectorStoreConfig.DEFAULT_SSL_ENABLED;

			public Builder setHost(String host) {
				Assert.hasText(host, "host must have a value");
				this.host = host;
				return this;
			}

			public Builder setPort(int port) {
				Assert.isTrue(port > 0, "port must be positive");
				this.port = port;
				return this;
			}

			public Builder setSslEnabled(boolean sslEnabled) {
				this.sslEnabled = sslEnabled;
				return this;
			}

			public Builder setIndexName(String indexName) {
				Assert.hasText(indexName, "indexName must have a value");
				this.indexName = indexName;
				return this;
			}

			public Builder setBeamWidth(int beamWidth) {
				Assert.isTrue(beamWidth > 0, "beamWidth must be positive");
				Assert.isTrue(beamWidth <= GemFireVectorStoreConfig.UPPER_BOUND_BEAM_WIDTH,
						"beamWidth must be less than or equal to " + GemFireVectorStoreConfig.UPPER_BOUND_BEAM_WIDTH);
				this.beamWidth = beamWidth;
				return this;
			}

			public Builder setMaxConnections(int maxConnections) {
				Assert.isTrue(maxConnections > 0, "maxConnections must be positive");
				Assert.isTrue(maxConnections <= GemFireVectorStoreConfig.UPPER_BOUND_MAX_CONNECTIONS,
						"maxConnections must be less than or equal to "
								+ GemFireVectorStoreConfig.UPPER_BOUND_MAX_CONNECTIONS);
				this.maxConnections = maxConnections;
				return this;
			}

			public Builder setBuckets(int buckets) {
				Assert.isTrue(buckets >= 0, "bucket must be 1 or more");
				this.buckets = buckets;
				return this;
			}

			public Builder setVectorSimilarityFunction(String vectorSimilarityFunction) {
				Assert.hasText(vectorSimilarityFunction, "vectorSimilarityFunction must have a value");
				this.vectorSimilarityFunction = vectorSimilarityFunction;
				return this;
			}

			public Builder setFields(String[] fields) {
				this.fields = fields;
				return this;
			}

			public GemFireVectorStoreConfig build() {
				return new GemFireVectorStoreConfig(this);
			}

		}

	}

}
