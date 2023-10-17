package org.springframework.ai.embedding;

import org.springframework.ai.document.Document;

import java.util.List;

public interface EmbeddingClient {

	List<Double> embed(String text);

	List<Double> embed(Document document);

	List<List<Double>> embed(List<String> texts);

	EmbeddingResponse embedForResponse(List<String> texts);

	default int dimensions() {
		return embed("Test String").size();
	}

}
