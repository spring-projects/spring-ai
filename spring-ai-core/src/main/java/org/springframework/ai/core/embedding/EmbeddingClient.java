package org.springframework.ai.core.embedding;

import java.util.List;

public interface EmbeddingClient {

	List<Double> embed(String text);

	EmbeddingResult embed(List<String> texts);

}
