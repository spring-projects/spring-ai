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

package org.springframework.ai.vectorstore.opensearch;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.CreateIndexResponse;
import org.opensearch.client.transport.endpoints.BooleanResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

/**
 * OpenSearch-based vector store implementation using OpenSearch's vector search
 * capabilities.
 *
 * <p>
 * The store uses OpenSearch's k-NN functionality to persist and query vector embeddings
 * along with their associated document content and metadata. The implementation supports
 * various similarity functions and provides efficient vector search operations.
 * </p>
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>Automatic schema initialization with configurable index creation</li>
 * <li>Support for multiple similarity functions: Cosine, L1, L2, and Linf</li>
 * <li>Metadata filtering using OpenSearch query expressions</li>
 * <li>Configurable similarity thresholds for search results</li>
 * <li>Batch processing support with configurable strategies</li>
 * <li>Observation and metrics support through Micrometer</li>
 * </ul>
 *
 * <p>
 * Basic usage example:
 * </p>
 * <pre>{@code
 * OpenSearchVectorStore vectorStore = OpenSearchVectorStore.builder(openSearchClient, embeddingModel)
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
 * AWS OpenSearch Serverless usage example:
 * </p>
 * <pre>{@code
 * OpenSearchVectorStore vectorStore = OpenSearchVectorStore.builder(openSearchClient, embeddingModel)
 *     .initializeSchema(true)
 *     .manageDocumentIds(false)  // Required for AWS OpenSearch Serverless
 *     .build();
 * }</pre>
 *
 * <p>
 * Advanced configuration example:
 * </p>
 * <pre>{@code
 * OpenSearchVectorStore vectorStore = OpenSearchVectorStore.builder(openSearchClient, embeddingModel)
 *     .index("custom-index")
 *     .mappingJson(customMapping)
 *     .similarityFunction("l2")
 *     .initializeSchema(true)
 *     .batchingStrategy(new TokenCountBatchingStrategy())
 *     .filterExpressionConverter(new CustomFilterExpressionConverter())
 *     .build();
 * }</pre>
 *
 * <p>
 * Similarity Functions:
 * </p>
 * <ul>
 * <li>cosinesimil: Default, suitable for most use cases. Measures cosine similarity
 * between vectors.</li>
 * <li>l1: Manhattan distance between vectors.</li>
 * <li>l2: Euclidean distance between vectors.</li>
 * <li>linf: Chebyshev distance between vectors.</li>
 * </ul>
 *
 * <p>
 * For more information about available similarity functions, see: <a href=
 * "https://opensearch.org/docs/latest/search-plugins/knn/approximate-knn/#spaces">OpenSearch
 * KNN Spaces</a>
 * </p>
 *
 * @author Jemin Huh
 * @author Soby Chacko
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author inpink
 * @author Sanghun Lee
 * @author chabinhwang
 * @since 1.0.0
 */
public class OpenSearchVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	private static final Logger logger = LoggerFactory.getLogger(OpenSearchVectorStore.class);

	public static final String COSINE_SIMILARITY_FUNCTION = "cosinesimil";

	public static final String DEFAULT_INDEX_NAME = "spring-ai-document-index";

	public static final String DEFAULT_MAPPING_EMBEDDING_TYPE_KNN_VECTOR_DIMENSION = """
			{
				"properties":{
					"embedding":{
						"type":"knn_vector",
						"dimension":%s
					}
				}
			}
			""";

	private final OpenSearchClient openSearchClient;

	private final String index;

	private final FilterExpressionConverter filterExpressionConverter;

	private final String mappingJson;

	private final boolean initializeSchema;

	private String similarityFunction;

	private final boolean useApproximateKnn;

	private final int dimensions;

	private final boolean manageDocumentIds;

	/**
	 * Creates a new OpenSearchVectorStore using the builder pattern.
	 * @param builder The configured builder instance
	 */
	protected OpenSearchVectorStore(Builder builder) {
		super(builder);

		Assert.notNull(builder.openSearchClient, "OpenSearchClient must not be null");

		this.openSearchClient = builder.openSearchClient;
		this.index = builder.index;
		this.mappingJson = builder.mappingJson;
		this.filterExpressionConverter = builder.filterExpressionConverter;
		// the potential functions for vector fields at
		// https://opensearch.org/docs/latest/search-plugins/knn/approximate-knn/#spaces
		this.similarityFunction = builder.similarityFunction;
		this.initializeSchema = builder.initializeSchema;
		this.useApproximateKnn = builder.useApproximateKnn;
		this.dimensions = builder.dimensions;
		this.manageDocumentIds = builder.manageDocumentIds;
	}

	/**
	 * Creates a new builder instance for configuring an OpenSearchVectorStore.
	 * @return A new OpenSearchBuilder instance
	 */
	public static Builder builder(OpenSearchClient openSearchClient, EmbeddingModel embeddingModel) {
		return new Builder(openSearchClient, embeddingModel);
	}

	public OpenSearchVectorStore withSimilarityFunction(String similarityFunction) {
		this.similarityFunction = similarityFunction;
		return this;
	}

	@Override
	public void doAdd(List<Document> documents) {
		List<float[]> embedding = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
				this.batchingStrategy);
		BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
		for (int i = 0; i < documents.size(); i++) {
			Document document = documents.get(i);
			OpenSearchDocument openSearchDocument = new OpenSearchDocument(document.getId(),
					Objects.requireNonNullElse(document.getText(), ""), document.getMetadata(), embedding.get(i));

			// Conditionally set document ID based on manageDocumentIds flag
			if (this.manageDocumentIds) {
				bulkRequestBuilder.operations(op -> op
					.index(idx -> idx.index(this.index).id(openSearchDocument.id()).document(openSearchDocument)));
			}
			else {
				bulkRequestBuilder
					.operations(op -> op.index(idx -> idx.index(this.index).document(openSearchDocument)));
			}
		}
		bulkRequest(bulkRequestBuilder.build());
	}

	@Override
	public void doDelete(List<String> idList) {
		if (!this.manageDocumentIds) {
			logger.warn("Document ID management is disabled. Delete operations may not work as expected "
					+ "since document IDs are auto-generated by OpenSearch. Consider using filter-based deletion instead.");
		}

		BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
		for (String id : idList) {
			bulkRequestBuilder.operations(op -> op.delete(idx -> idx.index(this.index).id(id)));
		}
		if (bulkRequest(bulkRequestBuilder.build()).errors()) {
			throw new IllegalStateException("Delete operation failed");
		}
	}

	private BulkResponse bulkRequest(BulkRequest bulkRequest) {
		try {
			return this.openSearchClient.bulk(bulkRequest);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void doDelete(Filter.Expression filterExpression) {
		Assert.notNull(filterExpression, "Filter expression must not be null");

		try {
			String filterStr = this.filterExpressionConverter.convertExpression(filterExpression);

			// Create delete by query request
			DeleteByQueryRequest request = new DeleteByQueryRequest.Builder().index(this.index)
				.query(q -> q.queryString(qs -> qs.query(filterStr)))
				.build();

			DeleteByQueryResponse response = this.openSearchClient.deleteByQuery(request);
			logger.debug("Deleted {} documents matching filter expression", response.deleted());

			if (!response.failures().isEmpty()) {
				throw new IllegalStateException("Failed to delete some documents: " + response.failures());
			}
		}
		catch (Exception e) {
			logger.error("Failed to delete documents by filter: {}", e.getMessage());
			throw new IllegalStateException("Failed to delete documents by filter", e);
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest searchRequest) {
		Assert.notNull(searchRequest, "The search request must not be null.");
		return similaritySearch(this.embeddingModel.embed(searchRequest.getQuery()), searchRequest.getTopK(),
				searchRequest.getSimilarityThreshold(), searchRequest.getFilterExpression());
	}

	public List<Document> similaritySearch(float[] embedding, int topK, double similarityThreshold,
			Filter.@Nullable Expression filterExpression) {
		return similaritySearch(
				this.useApproximateKnn ? buildApproximateQuery(embedding, topK, similarityThreshold, filterExpression)
						: buildExactQuery(embedding, topK, similarityThreshold, filterExpression));
	}

	private org.opensearch.client.opensearch.core.SearchRequest buildApproximateQuery(float[] embedding, int topK,
			double similarityThreshold, Filter.@Nullable Expression filterExpression) {
		return new org.opensearch.client.opensearch.core.SearchRequest.Builder().index(this.index)
			.query(Query.of(builder -> builder.knn(knnQueryBuilder -> knnQueryBuilder
				.filter(Query
					.of(queryBuilder -> queryBuilder.queryString(queryStringQuerybuilder -> queryStringQuerybuilder
						.query(getOpenSearchQueryString(filterExpression)))))
				.field("embedding")
				.k(topK)
				.vector(embedding))))
			.minScore(similarityThreshold)
			.build();
	}

	private org.opensearch.client.opensearch.core.SearchRequest buildExactQuery(float[] embedding, int topK,
			double similarityThreshold, Filter.@Nullable Expression filterExpression) {
		return new org.opensearch.client.opensearch.core.SearchRequest.Builder()
			.query(buildExactQuery(embedding, filterExpression))
			.index(this.index)
			.sort(sortOptionsBuilder -> sortOptionsBuilder
				.score(scoreSortBuilder -> scoreSortBuilder.order(SortOrder.Desc)))
			.size(topK)
			.minScore(similarityThreshold)
			.build();
	}

	private Query buildExactQuery(float[] embedding, Filter.@Nullable Expression filterExpression) {
		return Query.of(queryBuilder -> queryBuilder.scriptScore(scriptScoreQueryBuilder -> {
			scriptScoreQueryBuilder
				.query(queryBuilder2 -> queryBuilder2.queryString(queryStringQuerybuilder -> queryStringQuerybuilder
					.query(getOpenSearchQueryString(filterExpression))))
				.script(scriptBuilder -> scriptBuilder
					.inline(inlineScriptBuilder -> inlineScriptBuilder.source("knn_score")
						.lang("knn")
						.params("field", JsonData.of("embedding"))
						.params("query_value", JsonData.of(embedding))
						.params("space_type", JsonData.of(this.similarityFunction))));
			// https://opensearch.org/docs/latest/search-plugins/knn/knn-score-script
			// k-NN ensures non-negative scores by adding 1 to cosine similarity,
			// extending OpenSearch scores to 0-2.
			// A 0.5 boost normalizes to 0-1.
			return this.similarityFunction.equals(COSINE_SIMILARITY_FUNCTION) ? scriptScoreQueryBuilder.boost(0.5f)
					: scriptScoreQueryBuilder;
		}));
	}

	private String getOpenSearchQueryString(Filter.@Nullable Expression filterExpression) {
		return Objects.isNull(filterExpression) ? "*"
				: this.filterExpressionConverter.convertExpression(filterExpression);

	}

	private List<Document> similaritySearch(org.opensearch.client.opensearch.core.SearchRequest searchRequest) {
		try {
			return this.openSearchClient.search(searchRequest, Document.class)
				.hits()
				.hits()
				.stream()
				.map(this::toDocument)
				.collect(Collectors.toList());
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Document toDocument(Hit<Document> hit) {
		Document document = hit.source();
		Assert.notNull(document, "Document must not be null");
		Document.Builder documentBuilder = document.mutate();
		if (hit.score() != null) {
			documentBuilder.metadata(DocumentMetadata.DISTANCE.value(), 1 - hit.score().floatValue());
			documentBuilder.score(hit.score());
		}
		return documentBuilder.build();
	}

	public boolean exists(String targetIndex) {
		try {
			BooleanResponse response = this.openSearchClient.indices()
				.exists(existRequestBuilder -> existRequestBuilder.index(targetIndex));
			return response.value();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private CreateIndexResponse createIndexMapping(String index, String mappingJson) {
		JsonpMapper jsonpMapper = this.openSearchClient._transport().jsonpMapper();
		try {
			return this.openSearchClient.indices()
				.create(new CreateIndexRequest.Builder().index(index)
					.settings(settingsBuilder -> settingsBuilder.knn(true))
					.mappings(TypeMapping._DESERIALIZER.deserialize(
							jsonpMapper.jsonProvider().createParser(new StringReader(mappingJson)), jsonpMapper))
					.build());
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void afterPropertiesSet() {
		/**
		 * Generates a JSON string for the k-NN vector mapping configuration. The
		 * knn_vector field allows k-NN vectors ingestion into OpenSearch and supports
		 * various k-NN searches.
		 * @see <a href=
		 * "https://opensearch.org/docs/latest/search-plugins/knn/knn-index#method-definitions">OpenSearch
		 * k-NN Method Definitions</a>
		 */
		if (this.initializeSchema && !exists(this.index)) {
			String finalMappingJson;
			if (this.useApproximateKnn
					&& this.mappingJson.equals(DEFAULT_MAPPING_EMBEDDING_TYPE_KNN_VECTOR_DIMENSION)) {
				// Generate approximate k-NN mapping with HNSW method
				finalMappingJson = """
						{
							"properties": {
								"embedding": {
									"type": "knn_vector",
									"dimension": %d,
									"method": {
										"name": "hnsw",
										"engine": "lucene",
										"space_type": "%s"
									}
								}
							}
						}
						""".formatted(this.dimensions > 0 ? this.dimensions : this.embeddingModel.dimensions(),
						this.similarityFunction);
			}
			else {
				// Use provided mapping or default exact k-NN mapping
				finalMappingJson = String.format(this.mappingJson, this.embeddingModel.dimensions());
			}
			createIndexMapping(this.index, finalMappingJson);
		}
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		return VectorStoreObservationContext.builder(VectorStoreProvider.OPENSEARCH.value(), operationName)
			.collectionName(this.index)
			.dimensions(this.embeddingModel.dimensions())
			.similarityMetric(getSimilarityFunction());
	}

	private String getSimilarityFunction() {
		if ("cosinesimil".equalsIgnoreCase(this.similarityFunction)) {
			return VectorStoreSimilarityMetric.COSINE.value();
		}
		else if ("l2".equalsIgnoreCase(this.similarityFunction)) {
			return VectorStoreSimilarityMetric.EUCLIDEAN.value();
		}

		return this.similarityFunction;
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this.openSearchClient;
		return Optional.of(client);
	}

	/**
	 * The representation of {@link Document} along with its embedding.
	 *
	 * @param id The id of the document
	 * @param content The content of the document
	 * @param metadata The metadata of the document
	 * @param embedding The vectors representing the content of the document
	 */
	public record OpenSearchDocument(String id, String content, Map<String, Object> metadata, float[] embedding) {
	}

	/**
	 * Builder class for creating OpenSearchVectorStore instances.
	 */
	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private final OpenSearchClient openSearchClient;

		private String index = DEFAULT_INDEX_NAME;

		private String mappingJson = DEFAULT_MAPPING_EMBEDDING_TYPE_KNN_VECTOR_DIMENSION;

		private boolean initializeSchema = false;

		private FilterExpressionConverter filterExpressionConverter = new OpenSearchAiSearchFilterExpressionConverter();

		private String similarityFunction = COSINE_SIMILARITY_FUNCTION;

		private boolean useApproximateKnn = false;

		private int dimensions = 1536;

		private boolean manageDocumentIds = true;

		/**
		 * Sets the OpenSearch client.
		 * @param openSearchClient The OpenSearch client to use
		 * @throws IllegalArgumentException if openSearchClient is null
		 */
		private Builder(OpenSearchClient openSearchClient, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(openSearchClient, "OpenSearchClient must not be null");
			this.openSearchClient = openSearchClient;
		}

		/**
		 * Sets the index name.
		 * @param index The name of the index to use
		 * @return The builder instance
		 * @throws IllegalArgumentException if index is null or empty
		 */
		public Builder index(String index) {
			Assert.hasText(index, "index must not be null or empty");
			this.index = index;
			return this;
		}

		/**
		 * Sets the JSON mapping for the index.
		 * @param mappingJson The JSON mapping to use
		 * @return The builder instance
		 * @throws IllegalArgumentException if mappingJson is null or empty
		 */
		public Builder mappingJson(String mappingJson) {
			Assert.hasText(mappingJson, "mappingJson must not be null or empty");
			this.mappingJson = mappingJson;
			return this;
		}

		/**
		 * Sets whether to initialize the schema.
		 * @param initializeSchema true to initialize schema, false otherwise
		 * @return The builder instance
		 */
		public Builder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		/**
		 * Sets the filter expression converter.
		 * @param converter The filter expression converter to use
		 * @return The builder instance
		 * @throws IllegalArgumentException if converter is null
		 */
		public Builder filterExpressionConverter(FilterExpressionConverter converter) {
			Assert.notNull(converter, "filterExpressionConverter must not be null");
			this.filterExpressionConverter = converter;
			return this;
		}

		/**
		 * Sets the similarity function for vector comparison. See
		 * https://opensearch.org/docs/latest/search-plugins/knn/approximate-knn/#spaces
		 * for available functions.
		 * @param similarityFunction The similarity function to use
		 * @return The builder instance
		 * @throws IllegalArgumentException if similarityFunction is null or empty
		 */
		public Builder similarityFunction(String similarityFunction) {
			Assert.hasText(similarityFunction, "similarityFunction must not be null or empty");
			this.similarityFunction = similarityFunction;
			return this;
		}

		/**
		 * Sets whether to use approximate k-NN search. If true, the approximate k-NN
		 * method is used for faster searches and maintains good performance even at large
		 * scales. If false, the exact brute-force k-NN method is used for precise and
		 * highly accurate searches.
		 * @param useApproximateKnn true to use approximate k-NN, false for exact k-NN
		 * @return The builder instance
		 * @see <a href=
		 * "https://opensearch.org/docs/latest/search-plugins/knn/approximate-knn/">Approximate
		 * k-NN</a>
		 * @see <a href=
		 * "https://opensearch.org/docs/latest/search-plugins/knn/knn-score-script/">Exact
		 * k-NN with scoring script</a>
		 */
		public Builder useApproximateKnn(boolean useApproximateKnn) {
			this.useApproximateKnn = useApproximateKnn;
			return this;
		}

		/**
		 * Sets the number of dimensions for the vector embeddings. This is used when
		 * creating the index mapping for approximate k-NN. If not set, defaults to 1536
		 * or uses the embedding model's dimensions.
		 * @param dimensions The number of dimensions
		 * @return The builder instance
		 * @throws IllegalArgumentException if dimensions is less than or equal to 0
		 */
		public Builder dimensions(int dimensions) {
			Assert.isTrue(dimensions > 0, "dimensions must be greater than 0");
			this.dimensions = dimensions;
			return this;
		}

		/**
		 * Sets whether to manage document IDs during indexing operations.
		 * <p>
		 * When set to {@code true} (default), document IDs will be explicitly set during
		 * indexing operations. When set to {@code false}, OpenSearch will auto-generate
		 * document IDs, which is required for AWS OpenSearch Serverless vector search
		 * collections.
		 * </p>
		 * <p>
		 * Note: When document ID management is disabled, the {@link #doDelete(List)}
		 * method may not work as expected since document IDs are auto-generated by
		 * OpenSearch.
		 * </p>
		 * @param manageDocumentIds true to manage document IDs (default), false to let
		 * OpenSearch auto-generate IDs
		 * @return The builder instance
		 */
		public Builder manageDocumentIds(boolean manageDocumentIds) {
			this.manageDocumentIds = manageDocumentIds;
			return this;
		}

		/**
		 * Builds a new OpenSearchVectorStore instance with the configured properties.
		 * @return A new OpenSearchVectorStore instance
		 * @throws IllegalStateException if the builder is in an invalid state
		 */
		@Override
		public OpenSearchVectorStore build() {
			return new OpenSearchVectorStore(this);
		}

	}

}
