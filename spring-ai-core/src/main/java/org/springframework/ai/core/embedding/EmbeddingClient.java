package org.springframework.ai.core.embedding;

import java.util.List;

public interface EmbeddingClient {

	List<Double> createEmbedding(String text);

	List<List<Double>> createEmbedding(List<String> texts);

	EmbeddingResponse createEmbeddingResult(List<String> texts);

}
