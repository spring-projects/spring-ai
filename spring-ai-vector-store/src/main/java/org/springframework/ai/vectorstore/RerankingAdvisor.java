package org.springframework.ai.vectorstore;

import java.util.List;

import org.springframework.ai.document.Document;

/**
 * Defines a pluggable advisor for reranking search results in
 * {@link SearchMode#HYBRID_RERANKED} mode. Implementations refine the order and selection
 * of documents based on the search request.
 */
public interface RerankingAdvisor {

	/**
	 * Reranks the provided search results according to the search request.
	 * @param results The initial list of documents to rerank.
	 * @param request The search request containing query and mode information.
	 * @return A reranked list of documents.
	 */
	List<Document> rerank(List<Document> results, SearchRequest request);

}
