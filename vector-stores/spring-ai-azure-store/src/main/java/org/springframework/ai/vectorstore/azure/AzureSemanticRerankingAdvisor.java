package org.springframework.ai.vectorstore.azure;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.RerankingAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;

/**
 * Reranking advisor for Azure AI Search's
 * {@link org.springframework.ai.vectorstore.SearchMode#HYBRID_RERANKED} mode, filtering
 * and sorting results based on reranker scores.
 */
public class AzureSemanticRerankingAdvisor implements RerankingAdvisor {

	/**
	 * Reranks search results by filtering documents based on the similarity threshold and
	 * sorting by score in descending order.
	 * @param results The initial list of documents.
	 * @param request The search request.
	 * @return The reranked list of documents.
	 */
	@Override
	public List<Document> rerank(List<Document> results, SearchRequest request) {
		return results.stream()
			.filter(doc -> doc.getScore() >= request.getScoreThreshold())
			.sorted(Comparator.comparingDouble(Document::getScore).reversed())
			.collect(Collectors.toList());
	}

}
