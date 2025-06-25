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

package org.springframework.ai.vectorstore.azure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.azure.core.util.Context;
import com.azure.search.documents.SearchClient;
import com.azure.search.documents.SearchDocument;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.models.HnswAlgorithmConfiguration;
import com.azure.search.documents.indexes.models.HnswParameters;
import com.azure.search.documents.indexes.models.SearchField;
import com.azure.search.documents.indexes.models.SearchFieldDataType;
import com.azure.search.documents.indexes.models.SearchIndex;
import com.azure.search.documents.indexes.models.VectorSearch;
import com.azure.search.documents.indexes.models.VectorSearchAlgorithmMetric;
import com.azure.search.documents.indexes.models.VectorSearchProfile;
import com.azure.search.documents.models.IndexDocumentsResult;
import com.azure.search.documents.models.IndexingResult;
import com.azure.search.documents.models.QueryType;
import com.azure.search.documents.models.SearchOptions;
import com.azure.search.documents.models.SemanticSearchOptions;
import com.azure.search.documents.models.VectorSearchOptions;
import com.azure.search.documents.models.VectorizedQuery;
import com.azure.search.documents.util.SearchPagedIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.RerankingAdvisor;
import org.springframework.ai.vectorstore.SearchMode;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Uses Azure Cognitive Search as a backing vector store. Documents can be preloaded into
 * a Cognitive Search index and managed via Azure tools or added and managed through this
 * VectorStore. The underlying index is configured in the provided Azure
 * SearchIndexClient.
 *
 * @author Greg Meyer
 * @author Xiangyang Yu
 * @author Christian Tzolov
 * @author Josh Long
 * @author Thomas Vitale
 * @author Soby Chacko
 */
public class AzureVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	public static final String DEFAULT_INDEX_NAME = "spring_ai_azure_vector_store";

	private static final Logger logger = LoggerFactory.getLogger(AzureVectorStore.class);

	private static final String SPRING_AI_VECTOR_CONFIG = "spring-ai-vector-config";

	private static final String SPRING_AI_VECTOR_PROFILE = "spring-ai-vector-profile";

	private static final String ID_FIELD_NAME = "id";

	private static final String CONTENT_FIELD_NAME = "content";

	private static final String EMBEDDING_FIELD_NAME = "embedding";

	private static final String METADATA_FIELD_NAME = "metadata";

	private static final int DEFAULT_TOP_K = 4;

	private static final Double DEFAULT_SIMILARITY_THRESHOLD = 0.0;

	private static final String METADATA_FIELD_PREFIX = "meta_";

	private final SearchIndexClient searchIndexClient;

	private final FilterExpressionConverter filterExpressionConverter;

	private final boolean initializeSchema;

	/**
	 * List of metadata fields (as field name and type) that can be used in similarity
	 * search query filter expressions. The {@link Document#getMetadata()} can contain
	 * arbitrary number of metadata entries, but only the fields listed here can be used
	 * in the search filter expressions.
	 * <p>
	 * If new entries are added ot the filterMetadataFields the affected documents must be
	 * (re)updated.
	 */
	private final List<MetadataField> filterMetadataFields;

	@Nullable
	private SearchClient searchClient;

	private int defaultTopK;

	private Double defaultSimilarityThreshold;

	private String indexName;

	private RerankingAdvisor rerankingAdvisor;

	/**
	 * Protected constructor that accepts a builder instance. This is the preferred way to
	 * create new AzureVectorStore instances.
	 * @param builder the configured builder instance
	 */
	protected AzureVectorStore(Builder builder) {
		super(builder);

		Assert.notNull(builder.searchIndexClient, "The search index client cannot be null");
		Assert.notNull(builder.filterMetadataFields, "The filterMetadataFields cannot be null");

		this.searchIndexClient = builder.searchIndexClient;
		this.initializeSchema = builder.initializeSchema;
		this.filterMetadataFields = builder.filterMetadataFields;
		this.defaultTopK = builder.defaultTopK;
		this.defaultSimilarityThreshold = builder.defaultSimilarityThreshold;
		this.indexName = builder.indexName;
		this.filterExpressionConverter = new AzureAiSearchFilterExpressionConverter(this.filterMetadataFields);
	}

	public static Builder builder(SearchIndexClient searchIndexClient, EmbeddingModel embeddingModel) {
		return new Builder(searchIndexClient, embeddingModel);
	}

	@Override
	public void doAdd(List<Document> documents) {

		Assert.notNull(documents, "The document list should not be null.");
		if (CollectionUtils.isEmpty(documents)) {
			return; // nothing to do;
		}

		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptionsBuilder.builder().build(),
				this.batchingStrategy);

		final var searchDocuments = documents.stream().map(document -> {
			SearchDocument searchDocument = new SearchDocument();
			searchDocument.put(ID_FIELD_NAME, document.getId());
			searchDocument.put(EMBEDDING_FIELD_NAME, embeddings.get(documents.indexOf(document)));
			searchDocument.put(CONTENT_FIELD_NAME, document.getText());
			searchDocument.put(METADATA_FIELD_NAME, new JSONObject(document.getMetadata()).toJSONString());

			// Add the filterable metadata fields as top level fields, allowing filler
			// expressions on them.
			for (MetadataField mf : this.filterMetadataFields) {
				if (document.getMetadata().containsKey(mf.name())) {
					searchDocument.put(METADATA_FIELD_PREFIX + mf.name(), document.getMetadata().get(mf.name()));
				}
			}

			return searchDocument;
		}).toList();

		IndexDocumentsResult result = this.searchClient.uploadDocuments(searchDocuments);

		for (IndexingResult indexingResult : result.getResults()) {
			Assert.isTrue(indexingResult.isSucceeded(),
					String.format("Document with key %s did not upload successfully", indexingResult.getKey()));
		}
	}

	@Override
	public void doDelete(List<String> documentIds) {

		Assert.notNull(documentIds, "The document ID list should not be null.");

		final var searchDocumentIds = documentIds.stream().map(documentId -> {
			SearchDocument searchDocument = new SearchDocument();
			searchDocument.put(ID_FIELD_NAME, documentId);
			return searchDocument;
		}).toList();

		this.searchClient.deleteDocuments(searchDocumentIds);
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		Assert.notNull(request, "The search request must not be null.");
		return search(SearchRequest.builder()
			.query(request.getQuery())
			.topK(request.getTopK())
			.similarityThreshold(request.getSimilarityThreshold())
			.filterExpression(request.getFilterExpression())
			.searchMode(SearchMode.VECTOR)
			.build());
	}

	@Override
	public void afterPropertiesSet() throws Exception {

		if (!this.initializeSchema) {
			this.searchClient = this.searchIndexClient.getSearchClient(this.indexName);
			return;
		}

		int dimensions = this.embeddingModel.dimensions();

		List<SearchField> fields = new ArrayList<>();

		fields.add(new SearchField(ID_FIELD_NAME, SearchFieldDataType.STRING).setKey(true)
			.setFilterable(true)
			.setSortable(true));
		fields.add(new SearchField(EMBEDDING_FIELD_NAME, SearchFieldDataType.collection(SearchFieldDataType.SINGLE))
			.setSearchable(true)
			.setHidden(false)
			.setVectorSearchDimensions(dimensions)
			// This must match a vector search configuration name.
			.setVectorSearchProfileName(SPRING_AI_VECTOR_PROFILE));
		fields.add(new SearchField(CONTENT_FIELD_NAME, SearchFieldDataType.STRING).setSearchable(true)
			.setFilterable(true));
		fields.add(new SearchField(METADATA_FIELD_NAME, SearchFieldDataType.STRING).setSearchable(true)
			.setFilterable(true));

		for (MetadataField filterableMetadataField : this.filterMetadataFields) {
			fields.add(new SearchField(METADATA_FIELD_PREFIX + filterableMetadataField.name(),
					filterableMetadataField.fieldType())
				.setSearchable(false)
				.setFacetable(true));
		}

		SearchIndex searchIndex = new SearchIndex(this.indexName).setFields(fields)
			// VectorSearch configuration is required for a vector field. The name used
			// for the vector search algorithm configuration must match the configuration
			// used by the search field used for vector search.
			.setVectorSearch(new VectorSearch()
				.setProfiles(Collections
					.singletonList(new VectorSearchProfile(SPRING_AI_VECTOR_PROFILE, SPRING_AI_VECTOR_CONFIG)))
				.setAlgorithms(Collections.singletonList(new HnswAlgorithmConfiguration(SPRING_AI_VECTOR_CONFIG)
					.setParameters(new HnswParameters().setM(4)
						.setEfConstruction(400)
						.setEfSearch(1000)
						.setMetric(VectorSearchAlgorithmMetric.COSINE)))));

		SearchIndex index = this.searchIndexClient.createOrUpdateIndex(searchIndex);

		logger.info("Created search index: " + index.getName());

		this.searchClient = this.searchIndexClient.getSearchClient(this.indexName);
	}

	@Override
	public List<Document> search(SearchRequest request) {
		Assert.notNull(request, "The search request must not be null.");

		SearchOptions options = new SearchOptions();
		int limit = request.getResultLimit();

		switch (request.getSearchMode()) {
			case VECTOR:
				var vectorQuery = new VectorizedQuery(EmbeddingUtils.toList(embeddingModel.embed(request.getQuery())))
					.setKNearestNeighborsCount(request.getTopK())
					.setFields(EMBEDDING_FIELD_NAME);
				options.setVectorSearchOptions(new VectorSearchOptions().setQueries(vectorQuery));
				options.setTop(request.getTopK());
				break;
			case FULL_TEXT:
				options.setQueryType(QueryType.FULL);
				options.setTop(request.getTopK());
				break;
			case HYBRID:
				var hybridVectorQuery = new VectorizedQuery(
						EmbeddingUtils.toList(embeddingModel.embed(request.getQuery())))
					.setKNearestNeighborsCount(request.getTopK())
					.setFields(EMBEDDING_FIELD_NAME);
				options.setVectorSearchOptions(new VectorSearchOptions().setQueries(hybridVectorQuery));
				options.setQueryType(QueryType.FULL);
				options.setTop(request.getTopK());
				break;
			case HYBRID_RERANKED:
				var rerankedVectorQuery = new VectorizedQuery(
						EmbeddingUtils.toList(embeddingModel.embed(request.getQuery())))
					.setKNearestNeighborsCount(request.getTopK())
					.setFields(EMBEDDING_FIELD_NAME);
				options.setVectorSearchOptions(new VectorSearchOptions().setQueries(rerankedVectorQuery));
				options.setQueryType(QueryType.SEMANTIC);
				SemanticSearchOptions semanticSearchOptions = new SemanticSearchOptions();
				semanticSearchOptions.setSemanticConfigurationName("semanticConfiguration"); // TODO:
																								// make
																								// configurable
				options.setSemanticSearchOptions(semanticSearchOptions);
				options.setTop(limit);
				break;
			default:
				throw new IllegalArgumentException("Unsupported search mode: " + request.getSearchMode());
		}

		if (request.hasFilterExpression()) {
			options.setFilter(filterExpressionConverter.convertExpression(request.getFilterExpression()));
		}

		SearchPagedIterable results = searchClient.search(request.getQuery(), options,
				com.azure.core.util.Context.NONE);
		List<Document> documents = results.stream().filter(result -> {
			double score = request.getSearchMode() == SearchMode.HYBRID_RERANKED && result.getSemanticSearch() != null
					? result.getSemanticSearch().getRerankerScore() : result.getScore();
			return score >= request.getScoreThreshold();
		}).map(result -> {
			final AzureSearchDocument entry = result.getDocument(AzureSearchDocument.class);
			Map<String, Object> metadata = StringUtils.hasText(entry.metadata())
					? JSONObject.parseObject(entry.metadata(), new TypeReference<Map<String, Object>>() {
					}) : new HashMap<>();
			metadata.put(DocumentMetadata.DISTANCE.value(), 1.0 - result.getScore());
			double score = request.getSearchMode() == SearchMode.HYBRID_RERANKED && result.getSemanticSearch() != null
					? result.getSemanticSearch().getRerankerScore() : result.getScore();
			if (request.getSearchMode() == SearchMode.HYBRID_RERANKED && result.getSemanticSearch() != null) {
				metadata.put("re-rank_score", result.getSemanticSearch().getRerankerScore());
			}
			return Document.builder().id(entry.id()).text(entry.content()).metadata(metadata).score(score).build();
		}).collect(Collectors.toList());

		if (request.getRerankingAdvisor() != null && request.getSearchMode() == SearchMode.HYBRID_RERANKED) {
			documents = request.getRerankingAdvisor().rerank(documents, request);
		}

		return documents;
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {

		return VectorStoreObservationContext.builder(VectorStoreProvider.AZURE.value(), operationName)
			.collectionName(this.indexName)
			.dimensions(this.embeddingModel.dimensions())
			.similarityMetric(this.initializeSchema ? VectorStoreSimilarityMetric.COSINE.value() : null);
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this.searchClient;
		return Optional.of(client);
	}

	public record MetadataField(String name, SearchFieldDataType fieldType) {

		public static MetadataField text(String name) {
			return new MetadataField(name, SearchFieldDataType.STRING);
		}

		public static MetadataField int32(String name) {
			return new MetadataField(name, SearchFieldDataType.INT32);
		}

		public static MetadataField int64(String name) {
			return new MetadataField(name, SearchFieldDataType.INT64);
		}

		public static MetadataField decimal(String name) {
			return new MetadataField(name, SearchFieldDataType.DOUBLE);
		}

		public static MetadataField bool(String name) {
			return new MetadataField(name, SearchFieldDataType.BOOLEAN);
		}

		public static MetadataField date(String name) {
			return new MetadataField(name, SearchFieldDataType.DATE_TIME_OFFSET);
		}

	}

	/**
	 * Internal data structure for retrieving and storing documents.
	 */
	private record AzureSearchDocument(String id, String content, List<Float> embedding, String metadata) {

	}

	/**
	 * Builder class for creating {@link AzureVectorStore} instances.
	 * <p>
	 * Provides a fluent API for configuring all aspects of the Azure vector store.
	 *
	 * @since 1.0.0
	 */
	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private final SearchIndexClient searchIndexClient;

		private boolean initializeSchema = false;

		private List<MetadataField> filterMetadataFields = List.of();

		private int defaultTopK = DEFAULT_TOP_K;

		private Double defaultSimilarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;

		private String indexName = DEFAULT_INDEX_NAME;

		private Builder(SearchIndexClient searchIndexClient, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(searchIndexClient, "SearchIndexClient must not be null");
			this.searchIndexClient = searchIndexClient;
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
		 * Sets the metadata fields for filtering.
		 * @param filterMetadataFields the list of metadata fields
		 * @return the builder instance
		 */
		public Builder filterMetadataFields(List<MetadataField> filterMetadataFields) {
			this.filterMetadataFields = filterMetadataFields != null ? filterMetadataFields : List.of();
			return this;
		}

		/**
		 * Sets the index name for the Azure Vector Store.
		 * @param indexName the name of the index to use
		 * @return the builder instance
		 * @throws IllegalArgumentException if indexName is null or empty
		 */
		public Builder indexName(String indexName) {
			Assert.hasText(indexName, "The index name can not be empty.");
			this.indexName = indexName;
			return this;
		}

		/**
		 * Sets the default maximum number of similar documents to return.
		 * @param defaultTopK the maximum number of documents
		 * @return the builder instance
		 * @throws IllegalArgumentException if defaultTopK is negative
		 */
		public Builder defaultTopK(int defaultTopK) {
			Assert.isTrue(defaultTopK >= 0, "The topK should be positive value.");
			this.defaultTopK = defaultTopK;
			return this;
		}

		/**
		 * Sets the default similarity threshold for returned documents.
		 * @param defaultSimilarityThreshold the similarity threshold (must be between 0.0
		 * and 1.0)
		 * @return the builder instance
		 * @throws IllegalArgumentException if defaultSimilarityThreshold is not between
		 * 0.0 and 1.0
		 */
		public Builder defaultSimilarityThreshold(Double defaultSimilarityThreshold) {
			Assert.isTrue(defaultSimilarityThreshold >= 0.0 && defaultSimilarityThreshold <= 1.0,
					"The similarity threshold must be in range [0.0:1.00].");
			this.defaultSimilarityThreshold = defaultSimilarityThreshold;
			return this;
		}

		@Override
		public AzureVectorStore build() {
			return new AzureVectorStore(this);
		}

	}

}
