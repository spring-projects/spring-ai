package org.springframework.ai.rag.postretrieval.rerank;

/**
 * Represents the API key holder for Cohere API authentication.
 *
 * @author KoreaNirsa
 */
public class CohereApi {
	private String apiKey;

	public static Builder builder() {
		return new Builder();
	}

	public String getApiKey() {
		return apiKey;
	}

	public static class Builder {
		private final CohereApi instance = new CohereApi();

		public Builder apiKey(String key) {
			instance.apiKey = key;
			return this;
		}

		public CohereApi build() {
			if (instance.apiKey == null || instance.apiKey.isBlank()) {
				throw new IllegalArgumentException("API key must be provided.");
			}
			return instance;
		}
	}
}
