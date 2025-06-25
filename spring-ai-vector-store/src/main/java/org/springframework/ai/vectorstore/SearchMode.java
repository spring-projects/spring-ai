package org.springframework.ai.vectorstore;

/**
 * Enum defining the search modes supported by the {@link VectorStore} interface. Each
 * mode specifies a different type of search operation for querying documents.
 */
public enum SearchMode {

	/**
	 * Vector-based similarity search using embeddings (e.g., cosine similarity).
	 */
	VECTOR,
	/**
	 * Keyword-based full-text search (e.g., TF-IDF or BM25).
	 */
	FULL_TEXT,
	/**
	 * Hybrid search combining vector and full-text search (e.g., using rank fusion).
	 */
	HYBRID,
	/**
	 * Hybrid search with additional reranking for enhanced relevance.
	 */
	HYBRID_RERANKED

}
