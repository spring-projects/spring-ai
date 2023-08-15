package org.springframework.ai.embedding;

import org.springframework.ai.document.Document;

import java.util.List;

public interface EmbeddingClient {

	List<Double> createEmbedding(String text);

	List<Double> createEmbedding(Document document);

	List<List<Double>> createEmbedding(List<String> texts);

	EmbeddingResponse createEmbeddingResult(List<String> texts);

}
