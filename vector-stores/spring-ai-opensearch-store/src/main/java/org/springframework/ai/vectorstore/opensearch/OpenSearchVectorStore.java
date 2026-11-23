/*
 * Copyright 2023-present the original author or authors.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.CreateIndexResponse;
import org.opensearch.client.opensearch.indices.GetMappingResponse;
import org.opensearch.client.opensearch.indices.get_mapping.IndexMappingRecord;
import org.opensearch.client.transport.endpoints.BooleanResponse;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.EmbeddedDocument;
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

	private static final Log logger = LogFactory.getLog(OpenSearchVectorStore.class);

	public static final String COSINE_SIMILARITY_FUNCTION = "cosinesimil";

	public static final String DEFAULT_INDEX_NAME = "spring-ai-document-index";

	private static final int DEFAULT_DIMENSIONS = 1536;

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

	// Cache for vectorDimensions(); only a known size is stored.
	private volatile int resolvedVectorDimensions = -1;

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
	protected boolean supportsUpsert() {
		return true;
	}

	@Override
	protected void doUpsert(List<EmbeddedDocument> entries) {
		// Replace-by-id needs the document id to be the OpenSearch _id, which is not the
		// case when OpenSearch generates the ids itself.
		if (!this.manageDocumentIds) {
			throw new UnsupportedOperationException(
					"upsert replaces documents by id, which requires manageDocumentIds(true)");
		}

		// Whole-batch dimension pre-check before any write, so a mismatched vector fails
		// fast and cannot partially write. Non-empty and finiteness are already enforced
		// by the EmbeddedDocument constructor.
		int expected = vectorDimensions();
		for (int i = 0; i < entries.size(); i++) {
			checkDimensions("Embedding at index " + i, entries.get(i).embedding().length, expected);
		}

		BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
		for (EmbeddedDocument entry : entries) {
			// Pair positionally: the document and its embedding come from the same entry.
			Document document = entry.document();
			OpenSearchDocument openSearchDocument = new OpenSearchDocument(document.getId(),
					Objects.requireNonNullElse(document.getText(), ""), document.getMetadata(), entry.embedding());
			bulkRequestBuilder.operations(op -> op
				.index(idx -> idx.index(this.index).id(openSearchDocument.id()).document(openSearchDocument)));
		}
		BulkResponse response = bulkRequest(bulkRequestBuilder.build());
		if (response.errors()) {
			for (BulkResponseItem item : response.items()) {
				if (item.error() != null) {
					throw new IllegalStateException(item.error().reason());
				}
			}
		}
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
			if (logger.isDebugEnabled()) {
				logger.debug("Deleted " + response.deleted() + " documents matching filter expression");
			}

			if (!response.failures().isEmpty()) {
				throw new IllegalStateException("Failed to delete some documents: " + response.failures());
			}
		}
		catch (Exception e) {
			if (logger.isErrorEnabled()) {
				logger.error("Failed to delete documents by filter: " + e.getMessage());
			}
			throw new IllegalStateException("Failed to delete documents by filter", e);
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest searchRequest) {
		Assert.notNull(searchRequest, "The search request must not be null.");
		return similaritySearch(this.embeddingModel.embed(searchRequest.getQuery()), searchRequest.getTopK(),
				searchRequest.getSimilarityThreshold(), searchRequest.getFilterExpression());
	}

	@Override
	protected boolean supportsQueryEmbeddingSearch() {
		return true;
	}

	@Override
	protected List<Document> doSimilaritySearch(float[] queryEmbedding, SearchRequest searchRequest) {
		checkDimensions("Query embedding", queryEmbedding.length, vectorDimensions());
		return similaritySearch(queryEmbedding, searchRequest.getTopK(), searchRequest.getSimilarityThreshold(),
				searchRequest.getFilterExpression());
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
				.vector(toFloatList(embedding)))))
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
						.lang(langBuilder -> langBuilder.custom("knn"))
						.params("field", JsonData.of("embedding"))
						.params("query_value", JsonData.of(toFloatList(embedding)))
						.params("space_type", JsonData.of(this.similarityFunction))));
			// https://opensearch.org/docs/latest/search-plugins/knn/knn-score-script
			// k-NN ensures non-negative scores by adding 1 to cosine similarity,
			// extending OpenSearch scores to 0-2.
			// A 0.5 boost normalizes to 0-1.
			return this.similarityFunction.equals(COSINE_SIMILARITY_FUNCTION) ? scriptScoreQueryBuilder.boost(0.5f)
					: scriptScoreQueryBuilder;
		}));
	}

	private List<Float> toFloatList(float[] array) {
		List<Float> list = new java.util.ArrayList<>(array.length);
		for (float value : array) {
			list.add(value);
		}
		return list;
	}

	private String getOpenSearchQueryString(Filter.@Nullable Expression filterExpression) {
		return Objects.isNull(filterExpression) ? "*"
				: this.filterExpressionConverter.convertExpression(filterExpression);

	}

	private List<Document> similaritySearch(org.opensearch.client.opensearch.core.SearchRequest searchRequest) {
		try {
			return this.openSearchClient.search(searchRequest, OpenSearchDocument.class)
				.hits()
				.hits()
				.stream()
				.map(this::toDocument)
				.toList();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Document toDocument(Hit<OpenSearchDocument> hit) {
		OpenSearchDocument openSearchDocument = hit.source();
		Assert.notNull(openSearchDocument, "OpenSearchDocument must not be null");
		Map<String, Object> metadata = openSearchDocument.metadata() != null
				? new HashMap<>(openSearchDocument.metadata()) : new HashMap<>();
		Document.Builder documentBuilder = Document.builder()
			.id(openSearchDocument.id())
			.text(openSearchDocument.content())
			.metadata(metadata);
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
			boolean defaultMapping = this.mappingJson.equals(DEFAULT_MAPPING_EMBEDDING_TYPE_KNN_VECTOR_DIMENSION);
			int createdDimensions;
			String finalMappingJson;
			if (this.useApproximateKnn && defaultMapping) {
				createdDimensions = (this.dimensions > 0) ? this.dimensions : DEFAULT_DIMENSIONS;
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
						""".formatted(createdDimensions, this.similarityFunction);
			}
			else {
				// Use provided mapping or default exact k-NN mapping. The embedding model
				// is
				// asked only when no size is configured, because the index is being
				// created.
				createdDimensions = (this.dimensions > 0) ? this.dimensions : this.embeddingModel.dimensions();
				finalMappingJson = String.format(this.mappingJson, createdDimensions);
			}
			createIndexMapping(this.index, finalMappingJson);
			// A custom mapping need not use the size it was formatted with, so its size
			// is
			// read back from the index when it is first needed.
			if (defaultMapping) {
				this.resolvedVectorDimensions = createdDimensions;
			}
		}
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		VectorStoreObservationContext.Builder builder = VectorStoreObservationContext
			.builder(VectorStoreProvider.OPENSEARCH.value(), operationName)
			.collectionName(this.index)
			.similarityMetric(getSimilarityFunction());
		int vectorDimensions = vectorDimensions();
		if (vectorDimensions > 0) {
			builder.dimensions(vectorDimensions);
		}
		return builder;
	}

	/**
	 * The vector size the index accepts, or -1 when it is not known.
	 * <p>
	 * A size set with {@link Builder#dimensions(int)} wins, and so does the size of an
	 * index this store created with the built-in mapping. Otherwise the size is read from
	 * the {@code dimension} of the embedding field in the existing index mapping. The
	 * embedding model is never asked, because its width says nothing about the width of
	 * an index that already exists. Reading it from the mapping means a store that only
	 * upserts into an existing index never has to contact the embedding model. When the
	 * size cannot be read it stays unknown and reading it is tried again on the next
	 * call. Only a known size is cached, so a store has to be rebuilt after the index is
	 * recreated with a different size.
	 * @return the known dimension, or -1
	 */
	private int vectorDimensions() {
		if (this.dimensions > 0) {
			return this.dimensions;
		}
		int cached = this.resolvedVectorDimensions;
		if (cached > 0) {
			return cached;
		}
		int resolved = resolveVectorDimensions();
		if (resolved > 0) {
			this.resolvedVectorDimensions = resolved;
		}
		return resolved;
	}

	private int resolveVectorDimensions() {
		try {
			int mappingDimensions = mappingVectorDimensions();
			if (mappingDimensions > 0) {
				return mappingDimensions;
			}
		}
		catch (Exception ex) {
			logger.debug("Could not read the vector size of index " + this.index, ex);
		}
		return -1;
	}

	private int mappingVectorDimensions() throws IOException {
		GetMappingResponse response = this.openSearchClient.indices().getMapping(m -> m.index(this.index));
		// The response is keyed by concrete index name, which differs from the configured
		// name when that name is an alias.
		IndexMappingRecord record = response.get(this.index);
		if (record == null && response.result().size() == 1) {
			record = response.result().values().iterator().next();
		}
		if (record == null || record.mappings() == null) {
			return -1;
		}
		Property property = record.mappings().properties().get("embedding");
		return (property != null && property.isKnnVector()) ? property.knnVector().dimension() : -1;
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

		private int dimensions = 0;

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
		 * Sets the number of dimensions for the vector embeddings. It is the size used
		 * when this store creates the index, and the size that {@code upsert} and vector
		 * search check vectors against. If not set, a new index gets 1536 for approximate
		 * k-NN and the embedding model's size otherwise, and the size of an existing
		 * index is read from its mapping.
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
