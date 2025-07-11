package org.springframework.ai.rag.postretrieval.rerank;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * A Reranker implementation that integrates with Cohere's Rerank API.
 * This component reorders retrieved documents based on semantic relevance to the input query.
 *
 * @author KoreaNirsa
 * @see <a href="https://docs.cohere.com/reference/rerank">Cohere Rerank API Documentation</a>
 */
public class CohereReranker {
	private static final String COHERE_RERANK_ENDPOINT = "https://api.cohere.ai/v1/rerank";

	private static final Logger logger = LoggerFactory.getLogger(CohereReranker.class);
	
	private static final int MAX_DOCUMENTS = 1000;

	private final WebClient webClient;

	/**
	 * Constructs a CohereReranker that communicates with the Cohere Rerank API.
	 * Initializes the internal WebClient with the provided API key for authorization.
	 *
	 * @param cohereApi the API configuration object containing the required API key (must not be null)
	 * @throws IllegalArgumentException if cohereApi is null
	 */
    CohereReranker(CohereApi cohereApi) {
        if (cohereApi == null) {
            throw new IllegalArgumentException("CohereApi must not be null");
        }

        this.webClient = WebClient.builder()
                .baseUrl(COHERE_RERANK_ENDPOINT)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cohereApi.getApiKey())
                .build();
    }

    /**
     * Reranks a list of documents based on the provided query using the Cohere API.
     *
     * @param query The user input query.
     * @param documents The list of documents to rerank.
     * @param topN The number of top results to return (at most).
     * @return A reranked list of documents. If the API fails, returns the original list.
     */
    public List<Document> rerank(String query, List<Document> documents, int topN) {
        if (topN < 1) {
            throw new IllegalArgumentException("topN must be ≥ 1. Provided: " + topN);
        }

        if (documents == null || documents.isEmpty()) {
            logger.warn("Empty document list provided. Skipping rerank.");
            return Collections.emptyList();
        }

        if (documents.size() > MAX_DOCUMENTS) {
            logger.warn("Cohere recommends ≤ {} documents per rerank request. Larger sizes may cause errors.", MAX_DOCUMENTS);
            return documents;
        }

        int adjustedTopN = Math.min(topN, documents.size());

        Map<String, Object> payload = Map.of(
            "query", query,
            "documents", documents.stream().map(Document::getText).toList(),
            "top_n", adjustedTopN
        );

        // Call the API and process the result
        return sendRerankRequest(payload)
                .map(results -> results.stream()
                    .sorted(Comparator.comparingDouble(RerankResponse.Result::getRelevanceScore).reversed())
                    .map(r -> {
                        Document original = documents.get(r.getIndex());
                        Map<String, Object> metadata = new HashMap<>(original.getMetadata());
                        metadata.put("score", String.format("%.4f", r.getRelevanceScore()));
                        return new Document(original.getText(), metadata);
                    })
                    .toList())
                .orElseGet(() -> {
                    logger.warn("Cohere response is null or invalid");
                    return documents;
                });
    }

    /**
     * Sends a rerank request to the Cohere API and returns the result list.
     *
     * @param payload The request body including query, documents, and top_n.
     * @return An Optional list of reranked results, or empty if failed.
     */
    private Optional<List<RerankResponse.Result>> sendRerankRequest(Map<String, Object> payload) {
        try {
            RerankResponse response = webClient.post()
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(RerankResponse.class)
                    .block();

            return Optional.ofNullable(response)
                           .map(RerankResponse::getResults);
        } catch (Exception e) {
            logger.error("Cohere rerank failed, fallback to original order: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
}
