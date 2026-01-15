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

package org.springframework.ai.vectorstore.gemfire;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
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

	// Query Defaults
	private static final String QUERY = "/query";

	private static final String DOCUMENT_FIELD = "document";

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

	private final WebClient client;

	private final boolean initializeSchema;

	private final ObjectMapper objectMapper;

	private final String indexName;

	private final int beamWidth;

	private final int maxConnections;

	private final int buckets;

	private final String vectorSimilarityFunction;

	private final String[] fields;

	private final FilterExpressionConverter filterExpressionConverter;

	/**
	 * Protected constructor that accepts a builder instance. This is the preferred way to
	 * create new GemFireVectorStore instances.
	 * @param builder the configured builder instance
	 */
	protected GemFireVectorStore(Builder builder) {
		super(builder);

		this.initializeSchema = builder.initializeSchema;
		this.indexName = builder.indexName;
		this.beamWidth = builder.beamWidth;
		this.maxConnections = builder.maxConnections;
		this.buckets = builder.buckets;
		this.vectorSimilarityFunction = builder.vectorSimilarityFunction;
		this.fields = builder.fields;

		String base = UriComponentsBuilder.fromUriString(DEFAULT_URI)
			.build(builder.sslEnabled ? "s" : "", builder.host, builder.port)
			.toString();
		WebClient.Builder webClientBuilder = WebClient.builder().baseUrl(base);

		ExchangeFilterFunction authenticationFilterFunction = null;

		if (builder.isUsingTokenAuthentication()) {
			Assert.state(builder.token != null, "builder.token can't be null");
			authenticationFilterFunction = new BearerTokenAuthenticationFilterFunction(builder.token);
		}
		else if (builder.isUsingBasicAuthentication()) {
			Assert.state(builder.username != null && builder.password != null,
					"builder.username and password can't be null");
			authenticationFilterFunction = ExchangeFilterFunctions.basicAuthentication(builder.username,
					builder.password);
		}

		if (authenticationFilterFunction != null) {
			webClientBuilder.filter(authenticationFilterFunction);
		}

		this.client = webClientBuilder.build();
		this.filterExpressionConverter = new GemFireAiSearchFilterExpressionConverter();
		this.objectMapper = JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build();
	}

	public static Builder builder(EmbeddingModel embeddingModel) {
		return new Builder(embeddingModel);
	}

	public String getIndexName() {
		return this.indexName;
	}

	public int getBeamWidth() {
		return this.beamWidth;
	}

	public int getMaxConnections() {
		return this.maxConnections;
	}

	public int getBuckets() {
		return this.buckets;
	}

	public String getVectorSimilarityFunction() {
		return this.vectorSimilarityFunction;
	}

	public String[] getFields() {
		return this.fields;
	}

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
		return indexResponse != null && !indexResponse.isEmpty();
	}

	public @Nullable String getIndex() {
		return this.client.get()
			.uri("/" + this.indexName)
			.retrieve()
			.bodyToMono(String.class)
			.onErrorReturn("")
			.block();
	}

	@Override
	public void doAdd(List<Document> documents) {
		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
				this.batchingStrategy);
		UploadRequest upload = new UploadRequest(documents.stream()
			.map(document -> new UploadRequest.Embedding(document.getId(), embeddings.get(documents.indexOf(document)),
					DOCUMENT_FIELD, Objects.requireNonNullElse(document.getText(), ""), document.getMetadata()))
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
	public void doDelete(List<String> idList) {
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
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		String filterQuery = null;
		if (request.hasFilterExpression()) {
			Assert.notNull(request.getFilterExpression(), "filterExpression should not be null");
			filterQuery = this.filterExpressionConverter.convertExpression(request.getFilterExpression());
		}
		float[] floatVector = this.embeddingModel.embed(request.getQuery());
		List<Document> result = this.client.post()
			.uri("/" + this.indexName + QUERY)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(new QueryRequest(floatVector, request.getTopK(), request.getTopK(), // TopKPerBucket
					true, filterQuery))
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
				return Document.builder().id(r.key).text(content).metadata(metadata).score((double) r.score).build();
			})
			.collectList()
			.onErrorMap(WebClientException.class, this::handleHttpClientException)
			.block();
		return Objects.requireNonNullElse(result, List.of());
	}

	/**
	 * Creates a new index in the GemFireVectorStore using specified parameters. This
	 * method is invoked during initialization.
	 * @throws JsonProcessingException if an error occurs during JSON processing
	 */
	public void createIndex() throws JsonProcessingException {
		CreateRequest createRequest = new CreateRequest(this.indexName, this.beamWidth, this.maxConnections,
				this.vectorSimilarityFunction, this.fields, this.buckets);

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
			.collectionName(this.indexName)
			.dimensions(this.embeddingModel.dimensions())
			.fieldName(EMBEDDINGS);
	}

	public static class CreateRequest {

		@JsonProperty("name")
		private final String indexName;

		@JsonProperty("beam-width")
		private final int beamWidth;

		@JsonProperty("max-connections")
		private final int maxConnections;

		@JsonProperty("vector-similarity-function")
		private final String vectorSimilarityFunction;

		@JsonProperty("fields")
		private final String[] fields;

		@JsonProperty("buckets")
		private final int buckets;

		public CreateRequest(String indexName, int beamWidth, int maxConnections, String vectorSimilarityFunction,
				String[] fields, int buckets) {
			this.indexName = indexName;
			this.beamWidth = beamWidth;
			this.maxConnections = maxConnections;
			this.vectorSimilarityFunction = vectorSimilarityFunction;
			this.fields = fields;
			this.buckets = buckets;
		}

		public String getIndexName() {
			return this.indexName;
		}

		public int getBeamWidth() {
			return this.beamWidth;
		}

		public int getMaxConnections() {
			return this.maxConnections;
		}

		public String getVectorSimilarityFunction() {
			return this.vectorSimilarityFunction;
		}

		public String[] getFields() {
			return this.fields;
		}

		public int getBuckets() {
			return this.buckets;
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

			private final float[] vector;

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
		private final float[] vector;

		@JsonProperty("top-k")
		private final int k;

		@JsonProperty("k-per-bucket")
		private final int kPerBucket;

		@JsonProperty("include-metadata")
		private final boolean includeMetadata;

		@JsonProperty("filter-query")
		@JsonInclude(JsonInclude.Include.NON_NULL)
		private final @Nullable String filterQuery;

		QueryRequest(float[] vector, int k, int kPerBucket, boolean includeMetadata) {
			this(vector, k, kPerBucket, includeMetadata, null);
		}

		QueryRequest(float[] vector, int k, int kPerBucket, boolean includeMetadata, @Nullable String filterQuery) {
			this.vector = vector;
			this.k = k;
			this.kPerBucket = kPerBucket;
			this.includeMetadata = includeMetadata;
			this.filterQuery = filterQuery;
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

		public @Nullable String getFilterQuery() {
			return this.filterQuery;
		}

	}

	@SuppressWarnings("NullAway.Init") // fields late-initialized by deserialization from
										// an
										// http body
	private static final class QueryResponse {

		private String key;

		private float score;

		private Map<String, Object> metadata;

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

	/**
	 * Builder class for creating {@link GemFireVectorStore} instances.
	 * <p>
	 * Provides a fluent API for configuring all aspects of the GemFire vector store.
	 *
	 * @since 1.0.0
	 */
	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private String host = GemFireVectorStore.DEFAULT_HOST;

		private int port = GemFireVectorStore.DEFAULT_PORT;

		private boolean sslEnabled = GemFireVectorStore.DEFAULT_SSL_ENABLED;

		private String indexName = GemFireVectorStore.DEFAULT_INDEX_NAME;

		private int beamWidth = GemFireVectorStore.DEFAULT_BEAM_WIDTH;

		private int maxConnections = GemFireVectorStore.DEFAULT_MAX_CONNECTIONS;

		private int buckets = GemFireVectorStore.DEFAULT_BUCKETS;

		private String vectorSimilarityFunction = GemFireVectorStore.DEFAULT_SIMILARITY_FUNCTION;

		private String[] fields = GemFireVectorStore.DEFAULT_FIELDS;

		private boolean initializeSchema = false;

		private @Nullable String username;

		private @Nullable String password;

		private @Nullable String token;

		private Builder(EmbeddingModel embeddingModel) {
			super(embeddingModel);
		}

		/**
		 * Sets the host for the GemFire connection.
		 * @param host the host to connect to
		 * @return the builder instance
		 * @throws IllegalArgumentException if host is null or empty
		 */
		public Builder host(String host) {
			Assert.hasText(host, "host must have a value");
			this.host = host;
			return this;
		}

		/**
		 * Sets the port for the GemFire connection.
		 * @param port the port to connect to
		 * @return the builder instance
		 * @throws IllegalArgumentException if port is not positive
		 */
		public Builder port(int port) {
			Assert.isTrue(port > 0, "port must be positive");
			this.port = port;
			return this;
		}

		/**
		 * Sets whether SSL is enabled for the connection.
		 * @param sslEnabled true to enable SSL, false otherwise
		 * @return the builder instance
		 */
		public Builder sslEnabled(boolean sslEnabled) {
			this.sslEnabled = sslEnabled;
			return this;
		}

		/**
		 * Sets the index name.
		 * @param indexName the name of the index
		 * @return the builder instance
		 * @throws IllegalArgumentException if indexName is null or empty
		 */
		public Builder indexName(String indexName) {
			Assert.hasText(indexName, "indexName must have a value");
			this.indexName = indexName;
			return this;
		}

		/**
		 * Sets the beam width.
		 * @param beamWidth the beam width value
		 * @return the builder instance
		 * @throws IllegalArgumentException if beamWidth is not within valid range
		 */
		public Builder beamWidth(int beamWidth) {
			Assert.isTrue(beamWidth > 0, "beamWidth must be positive");
			Assert.isTrue(beamWidth <= GemFireVectorStore.UPPER_BOUND_BEAM_WIDTH,
					"beamWidth must be less than or equal to " + GemFireVectorStore.UPPER_BOUND_BEAM_WIDTH);
			this.beamWidth = beamWidth;
			return this;
		}

		/**
		 * Sets the maximum number of connections.
		 * @param maxConnections the maximum connections value
		 * @return the builder instance
		 * @throws IllegalArgumentException if maxConnections is not within valid range
		 */
		public Builder maxConnections(int maxConnections) {
			Assert.isTrue(maxConnections > 0, "maxConnections must be positive");
			Assert.isTrue(maxConnections <= GemFireVectorStore.UPPER_BOUND_MAX_CONNECTIONS,
					"maxConnections must be less than or equal to " + GemFireVectorStore.UPPER_BOUND_MAX_CONNECTIONS);
			this.maxConnections = maxConnections;
			return this;
		}

		/**
		 * Sets the number of buckets.
		 * @param buckets the number of buckets
		 * @return the builder instance
		 * @throws IllegalArgumentException if buckets is negative
		 */
		public Builder buckets(int buckets) {
			Assert.isTrue(buckets >= 0, "buckets must not be negative");
			this.buckets = buckets;
			return this;
		}

		/**
		 * Sets the vector similarity function.
		 * @param vectorSimilarityFunction the similarity function to use
		 * @return the builder instance
		 * @throws IllegalArgumentException if vectorSimilarityFunction is null or empty
		 */
		public Builder vectorSimilarityFunction(String vectorSimilarityFunction) {
			Assert.hasText(vectorSimilarityFunction, "vectorSimilarityFunction must have a value");
			this.vectorSimilarityFunction = vectorSimilarityFunction;
			return this;
		}

		/**
		 * Sets the fields array.
		 * @param fields the fields to use
		 * @return the builder instance
		 */
		public Builder fields(String[] fields) {
			this.fields = fields;
			return this;
		}

		/**
		 * Sets whether to initialize the schema.
		 * @param initializeSchema true to initialize schema, false otherwise
		 * @return the builder instance
		 */
		public Builder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		/**
		 * Sets the username to authenticate requests with
		 * @param username the username to authenticate or unauthenticated if not set
		 * @return the builder instance
		 */
		public Builder username(String username) {
			this.username = username;
			return this;
		}

		/**
		 * Sets the password to authenticate requests with
		 * @param password the password to authenticate if username is also provided
		 * @return the builder instance
		 */
		public Builder password(String password) {
			this.password = password;
			return this;
		}

		/**
		 * Sets the token to authenticate requests with
		 * @param token the token to use for authentication
		 * @return the builder instance
		 */
		public Builder token(String token) {
			this.token = token;
			return this;
		}

		/**
		 * @return true if a token has been provided
		 */
		public boolean isUsingTokenAuthentication() {
			return this.token != null;
		}

		/**
		 * @return true if a username and password have been provided
		 */
		public boolean isUsingBasicAuthentication() {
			return this.username != null && this.password != null;
		}

		@Override
		public GemFireVectorStore build() {
			return new GemFireVectorStore(this);
		}

	}

}
