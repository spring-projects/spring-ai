package org.springframework.ai.vertexai.embedding;

import org.springframework.ai.chat.metadata.Usage;

public class VertexAiEmbeddingUsage implements Usage {

	private final Integer totalTokens;

	public VertexAiEmbeddingUsage(Integer totalTokens) {
		this.totalTokens = totalTokens;
	}

	@Override
	public Long getPromptTokens() {
		return 0L;
	}

	@Override
	public Long getGenerationTokens() {
		return 0L;
	}

	@Override
	public Long getTotalTokens() {
		return Long.valueOf(this.totalTokens);
	}

}
