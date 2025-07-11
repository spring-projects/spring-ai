package org.springframework.ai.rag.postretrieval.rerank;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the response returned from Cohere's Rerank API.
 * The response includes a list of result objects that specify document indices
 * and their semantic relevance scores.
 *
 * @author KoreaNirsa
 */
public class RerankResponse {
    private List<Result> results;

    public List<Result> getResults() {
        return results;
    }

    public void setResults(List<Result> results) {
        this.results = results;
    }

    /**
     * Represents a single reranked document result returned by the Cohere API.
     * Contains the original index and the computed relevance score.
     */
    public static class Result {
        private int index;

        @JsonProperty("relevance_score")
        private double relevanceScore;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public double getRelevanceScore() {
            return relevanceScore;
        }

        public void setRelevanceScore(double relevanceScore) {
            this.relevanceScore = relevanceScore;
        }
    }
}