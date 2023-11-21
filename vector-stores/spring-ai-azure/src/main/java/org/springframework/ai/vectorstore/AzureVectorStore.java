/*
 * Copyright 2023-2023 the original author or authors.
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

import java.util.Collections;
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
import com.azure.search.documents.models.SearchOptions;
import com.azure.search.documents.models.VectorSearchOptions;
import com.azure.search.documents.models.VectorizedQuery;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.beans.factory.InitializingBean;
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
 */
public class AzureVectorStore implements VectorStore, InitializingBean {

	public static final String DEFAULT_INDEX_NAME = "spring_ai_azure_vector_store";

	private static final String ID_FIELD_NAME = "id";

	private static final String CONTENT_FIELD_NAME = "content";

	private static final String EMBEDDING_FIELD_NAME = "embedding";

	private static final String METADATA_FIELD_NAME = "metadata";

	private static final String DISTANCE_METADATA_FIELD_NAME = "distance";

	private static final int DEFAULT_TOP_K = 4;

	private static final Double DEFAULT_SIMILARITY_THRESHOLD = 0.0;

	private final SearchIndexClient searchIndexClient;

	private final EmbeddingClient embeddingClient;

	private SearchClient searchClient;

	private int defaultTopK = DEFAULT_TOP_K;

	private Double defaultSimilarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;

	private String indexName = DEFAULT_INDEX_NAME;

	/**
	 * Constructs a new AzureCognitiveSearchVectorStore.
	 * @param searchIndexClient A pre-configured Azure {@link SearchIndexClient} that CRUD
	 * for Azure search indexes and factory for {@link SearchClient}.
	 * @param embeddingClient The client for embedding operations.
	 */
	public AzureVectorStore(SearchIndexClient searchIndexClient, EmbeddingClient embeddingClient) {
		Assert.notNull(embeddingClient, "The embedding client can not be null.");
		Assert.notNull(searchIndexClient, "The search index client can not be null.");
		this.searchIndexClient = searchIndexClient;
		this.embeddingClient = embeddingClient;
	}

	/**
	 * Change the Index Name.
	 * @param indexName The Azure VectorStore index name to use.
	 */
	public void setIndexName(String indexName) {
		Assert.hasText(indexName, "The index name can not be empty.");
		this.indexName = indexName;
	}

	/**
	 * Sets the a default maximum number of similar documents returned.
	 * @param topK The default maximum number of similar documents returned.
	 */
	public void setDefaultTopK(int topK) {
		Assert.isTrue(topK >= 0, "The topK should be positive value.");
		this.defaultTopK = topK;
	}

	/**
	 * Sets the a default similarity threshold for returned documents.
	 * @param similarityThreshold The a default similarity threshold for returned
	 * documents.
	 */
	public void setDefaultSimilarityThreshold(Double similarityThreshold) {
		Assert.isTrue(similarityThreshold >= 0.0 && similarityThreshold <= 1.0,
				"The similarity threshold must be in range [0.0:1.00].");
		this.defaultSimilarityThreshold = similarityThreshold;
	}

	@Override
	public void add(List<Document> documents) {

		Assert.notNull(documents, "The document list should not be null.");
		if (CollectionUtils.isEmpty(documents)) {
			return; // nothing to do;
		}

		final var searchDocuments = documents.stream().map(document -> {
			final var embeddings = this.embeddingClient.embed(document);
			SearchDocument searchDocument = new SearchDocument();
			searchDocument.put(ID_FIELD_NAME, document.getId());
			searchDocument.put(EMBEDDING_FIELD_NAME, embeddings);
			searchDocument.put(CONTENT_FIELD_NAME, document.getContent());
			// TODO: Consider alternate/native field type for metadata
			searchDocument.put(METADATA_FIELD_NAME, new JSONObject(document.getMetadata()).toJSONString());
			return searchDocument;
		}).toList();

		IndexDocumentsResult result = this.searchClient.uploadDocuments(searchDocuments);

		for (IndexingResult indexingResult : result.getResults()) {
			Assert.isTrue(indexingResult.isSucceeded(),
					String.format("Document with key %s upload is not successfully", indexingResult.getKey()));
		}
	}

	@Override
	public Optional<Boolean> delete(List<String> documentIds) {

		Assert.notNull(documentIds, "The document ID list should not be null.");
		if (CollectionUtils.isEmpty(documentIds)) {
			return Optional.of(true); // nothing to do;
		}

		final var searchDocumentIds = documentIds.stream().map(documentId -> {
			SearchDocument searchDocument = new SearchDocument();
			searchDocument.put(ID_FIELD_NAME, documentId);
			return searchDocument;
		}).toList();

		var results = this.searchClient.deleteDocuments(searchDocumentIds);

		boolean resSuccess = true;

		for (IndexingResult result : results.getResults()) {
			if (!result.isSucceeded()) {
				resSuccess = false;
				break;
			}
		}

		return Optional.of(resSuccess);
	}

	@Override
	public List<Document> similaritySearch(String query) {
		return this.similaritySearch(SearchRequest.query(query)
			.withTopK(this.defaultTopK)
			.withSimilarityThreshold(this.defaultSimilarityThreshold));
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {

		Assert.notNull(request, "The search request must not be null.");

		if (request.getFilterExpression() != null) {
			throw new UnsupportedOperationException(
					"The [" + this.getClass() + "] doesn't support metadata filtering!");
		}

		var searchEmbedding = toFloatList(embeddingClient.embed(request.getQuery()));

		final var vectorQuery = new VectorizedQuery(searchEmbedding).setKNearestNeighborsCount(request.getTopK())
			// Set the fields to compare the vector against. This is a comma-delimited
			// list of field names.
			.setFields(EMBEDDING_FIELD_NAME);

		final var searchResults = searchClient.search(null,
				new SearchOptions().setVectorSearchOptions(new VectorSearchOptions().setQueries(vectorQuery)),
				Context.NONE);

		return searchResults.stream()
			.filter(result -> result.getScore() >= request.getSimilarityThreshold())
			.map(result -> {

				final AzureSearchDocument entry = result.getDocument(AzureSearchDocument.class);

				Map<String, Object> metadata = (StringUtils.hasText(entry.metadata()))
						? JSONObject.parseObject(entry.metadata(), new TypeReference<Map<String, Object>>() {
						}) : Map.of();

				metadata.put(DISTANCE_METADATA_FIELD_NAME, 1 - (float) result.getScore());

				final Document doc = new Document(entry.id(), entry.content(), metadata);
				doc.setEmbedding(entry.embedding());

				return doc;

			})
			.collect(Collectors.toList());
	}

	private List<Float> toFloatList(List<Double> doubleList) {
		return doubleList.stream().map(Double::floatValue).toList();
	}

	/**
	 * Internal data structure for retrieving and and storing documents.
	 */
	private record AzureSearchDocument(String id, String content, List<Double> embedding, String metadata) {
	}

	@Override
	public void afterPropertiesSet() throws Exception {

		int dimensions = this.embeddingClient.dimensions();

		SearchIndex searchIndex = new SearchIndex(this.indexName).setFields(
				new SearchField(ID_FIELD_NAME, SearchFieldDataType.STRING).setKey(true)
					.setFilterable(true)
					.setSortable(true),
				new SearchField(EMBEDDING_FIELD_NAME, SearchFieldDataType.collection(SearchFieldDataType.SINGLE))
					.setSearchable(true)
					.setVectorSearchDimensions(dimensions)
					// This must match a vector search configuration name.
					.setVectorSearchProfileName("my-vector-profile"),
				new SearchField(CONTENT_FIELD_NAME, SearchFieldDataType.STRING).setSearchable(true).setFilterable(true),
				new SearchField(METADATA_FIELD_NAME, SearchFieldDataType.STRING).setSearchable(true)
					.setFilterable(true))
			// VectorSearch configuration is required for a vector field. The name used
			// for the vector search
			// algorithm configuration must match the configuration used by the search
			// field used for vector search.
			.setVectorSearch(new VectorSearch()
				.setProfiles(
						Collections.singletonList(new VectorSearchProfile("my-vector-profile", "my-vector-config")))
				.setAlgorithms(Collections.singletonList(
						new HnswAlgorithmConfiguration("my-vector-config").setParameters(new HnswParameters().setM(4)
							.setEfConstruction(400)
							.setEfSearch(1000)
							.setMetric(VectorSearchAlgorithmMetric.COSINE)))));

		var index = this.searchIndexClient.createOrUpdateIndex(searchIndex);

		this.searchClient = this.searchIndexClient.getSearchClient(this.indexName);
	}

}
