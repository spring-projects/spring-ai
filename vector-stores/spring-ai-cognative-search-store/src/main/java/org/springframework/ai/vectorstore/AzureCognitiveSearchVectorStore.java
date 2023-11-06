package org.springframework.ai.vectorstore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.util.StringUtils;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.azure.core.util.Context;
import com.azure.search.documents.SearchClient;
import com.azure.search.documents.models.IndexingResult;
import com.azure.search.documents.models.RawVectorQuery;
import com.azure.search.documents.models.SearchOptions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

/**
 * Uses Azure Cognitive Search as a backing vector store. Documents can be preloaded into
 * a Cognitive Search index and managed via Azure tools or added and managed through this
 * VectorStore. The underlying index is configured in the provided Azure SearchClient.
 *
 * @author Greg Meyer
 * @author Xiangyang Yu
 *
 */
public class AzureCognitiveSearchVectorStore implements VectorStore {

	private static final String DISTANCE_METADATA_FIELD_NAME = "distance";

	private static final int DEFAULT_TOP_K = 4;

	private static final Double DEFAULT_SIMILARITY_THRESHOLD = 0.0;

	private final SearchClient searchClient;

	private final EmbeddingClient embeddingClient;

	private int topK;

	private Double similarityThreshold;

	private String contentField;

	/**
	 * Constructs a new AzureCognitiveSearchVectorStore.
	 * @param searchClient A preconfigured Azure searchClient that references an index in
	 * an existing Azure Cognitive Search instance.
	 * @param embeddingClient The client for embedding operations.
	 */
	public AzureCognitiveSearchVectorStore(SearchClient searchClient, EmbeddingClient embeddingClient) {
		this.searchClient = searchClient;
		this.embeddingClient = embeddingClient;

		setTopK(DEFAULT_TOP_K);
		setSimilarityThreshold(DEFAULT_SIMILARITY_THRESHOLD);
		setContentField(AzureSearchClientProperties.DEFAULT_CONTENT_FIELD);
	}

	/**
	 * Sets the a default maximum number of similar documents returned.
	 * @param topK The default maximum number of similar documents returned.
	 */
	public void setTopK(int topK) {
		this.topK = topK;
	}

	/**
	 * Sets the a default similarity threshold for returned documents.
	 * @param similarityThreshold The a default similarity threshold for returned
	 * documents.
	 */
	public void setSimilarityThreshold(Double similarityThreshold) {
		this.similarityThreshold = similarityThreshold;
	}

	/**
	 * Sets the default content field name used for search documents.
	 * @param contentField The content field name used for search documents.
	 */
	public void setContentField(String contentField) {
		this.contentField = contentField;
	}

	/**
	 * Adds a list of documents to the vector store.
	 * @param documents The list of documents to be added.
	 */
	@Override
	public void add(List<Document> documents) {
		final var docs = documents.stream().map(document -> {

			final var embeddings = embeddingClient.embed(document);

			/*
			 * TODO: Consider alternate/native field type for metadata
			 */
			final var metadata = new JSONObject(document.getMetadata()).toJSONString();

			return new DocEntry(document.getId(), document.getContent(), embeddings, metadata);

		}).toList();

		searchClient.uploadDocuments(docs);

	}

	/**
	 * Deletes a list of documents by their IDs.
	 * @param idList The list of document IDs to be deleted.
	 * @return An optional boolean indicating the deletion status. All deletions much be
	 * successful to return True.
	 */
	@Override
	public Optional<Boolean> delete(List<String> idList) {
		final List<DocEntry> docIds = idList.stream().map(id -> DocEntry.builder().id(id).build()).toList();

		var results = searchClient.deleteDocuments(docIds);

		boolean resSuccess = true;

		for (IndexingResult result : results.getResults())
			if (!result.isSucceeded()) {
				resSuccess = false;
				break;
			}

		return Optional.of(resSuccess);
	}

	/**
	 * Searches for documents similar to the given query. Uses the configured topK value
	 * (uses a default value is not set).
	 * @param query The query string.
	 * @return A list of similar documents.
	 */
	@Override
	public List<Document> similaritySearch(String query) {
		return similaritySearch(query, topK);
	}

	/**
	 * Searches for documents similar to the given query. Uses the configured
	 * similarityThreshold value (uses a default value is not set).
	 * @param query The query string.
	 * @param k The maximum number of results to return.
	 * @return A list of similar documents.
	 */
	@Override
	public List<Document> similaritySearch(String query, int k) {
		return similaritySearch(query, k, similarityThreshold);
	}

	/**
	 * Searches for documents similar to the given query.
	 * @param query The query string.
	 * @param k The maximum number of results to return.
	 * @param threshold The similarity threshold for results.
	 * @return A list of similar documents.
	 */
	@Override
	public List<Document> similaritySearch(String query, int k, double threshold) {
		final var searchQueryVector = new RawVectorQuery().setVector(toFloatList(embeddingClient.embed(query)))
			.setKNearestNeighborsCount(k)
			.setFields(contentField);

		final var searchResults = searchClient.search(null, new SearchOptions().setVectorQueries(searchQueryVector),
				Context.NONE);

		return searchResults.stream().filter(r -> r.getScore() >= threshold).map(r -> {

			final DocEntry entry = r.getDocument(DocEntry.class);

			Map<String, Object> metadata = null;

			if (StringUtils.hasText(entry.getMetadata())) {
				metadata = JSONObject.parseObject(entry.getMetadata(), new TypeReference<Map<String, Object>>() {
				});
			}
			else
				metadata = new HashMap<>();

			metadata.put(DISTANCE_METADATA_FIELD_NAME, 1 - (float) r.getScore());

			final Document doc = new Document(entry.getId(), entry.getContent(), metadata);
			doc.setEmbedding(entry.getContentVector());

			return doc;
		}).collect(Collectors.toList());
	}

	/*
	 * Converts a Double list to Float List
	 */
	private List<Float> toFloatList(List<Double> doubleList) {
		return doubleList.stream().map(Double::floatValue).toList();
	}

	/**
	 * Internal data structure for retrieving and and storing documents.
	 */
	@Data
	@Builder
	@Jacksonized
	@AllArgsConstructor
	@NoArgsConstructor
	private static class DocEntry {

		private String id;

		private String content;

		private List<Double> contentVector;

		private String metadata;

	}

}
