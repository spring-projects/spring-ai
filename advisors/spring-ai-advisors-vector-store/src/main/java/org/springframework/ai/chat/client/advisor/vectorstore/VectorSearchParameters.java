package org.springframework.ai.chat.client.advisor.vectorstore;

import org.springframework.ai.vectorstore.SearchRequest;

/**
 * Parameters for vector store similarity search with default values.
 */
public record VectorSearchParameters(int topK, String filter) {

	public static final int DEFAULT_TOP_K = 100;

	public VectorSearchParameters() {
		this(VectorStoreChatMemoryAdvisor.DEFAULT_CHAT_MEMORY_RESPONSE_SIZE, null);
	}

	public static VectorSearchParameters of(int topK) {
		return new VectorSearchParameters(topK, null);
	}

	public static VectorSearchParameters forConversation(String conversationId) {
		return new VectorSearchParameters(DEFAULT_TOP_K, "conversationId=='" + conversationId + "'");
	}

	public VectorSearchParameters withTopK(int topK) {
		return new VectorSearchParameters(topK, this.filter);
	}

	/**
	 * Create a new instance with a different filter.
	 * @param filter the new filter expression
	 * @return a new VectorSearchParameters instance with the updated filter
	 */
	public VectorSearchParameters withFilter(String filter) {
		return new VectorSearchParameters(this.topK, filter);
	}

}
