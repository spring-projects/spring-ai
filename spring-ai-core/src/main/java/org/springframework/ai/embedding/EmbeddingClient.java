package org.springframework.ai.embedding;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * EmbeddingClient is a generic interface for embedding clients.
 */
public interface EmbeddingClient {

	/**
	 * Embeds the given text into a vector.
	 * @param text the text to embed.
	 * @return the embedded vector.
	 */
	List<Double> embed(String text);

	/**
	 * Embeds the given document's content into a vector.
	 * @param document the document to embed.
	 * @return the embedded vector.
	 */
	List<Double> embed(Document document);

	/**
	 * Embeds a batch of texts into vectors.
	 * @param texts list of texts to embed.
	 * @return list of list of embedded vectors.
	 */
	List<List<Double>> embed(List<String> texts);

	/**
	 * Embeds a batch of texts into vectors and returns the {@link EmbeddingResponse}.
	 * @param texts list of texts to embed.
	 * @return the embedding response.
	 */
	EmbeddingResponse embedForResponse(List<String> texts);

	/**
	 * @return the number of dimensions of the embedded vectors. It is model specific.
	 */
	default int dimensions() {
		return embed("Test String").size();
	}

}
