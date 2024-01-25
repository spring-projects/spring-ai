package org.springframework.ai.embedding;

import org.springframework.ai.document.Document;
import org.springframework.ai.model.ModelClient;
import org.springframework.util.Assert;

import java.util.List;

/**
 * EmbeddingClient is a generic interface for embedding clients.
 */
public interface EmbeddingClient extends ModelClient<EmbeddingRequest, EmbeddingResponse> {

	/**
	 * Embeds the given text into a vector.
	 * @param text the text to embed.
	 * @return the embedded vector.
	 */
	default List<Double> embed(String text) {
		Assert.notNull(text, "Text must not be null");
		return this.embed(List.of(text)).iterator().next();
	}

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
	default List<List<Double>> embed(List<String> texts) {
		Assert.notNull(texts, "Texts must not be null");
		return this.call(new EmbeddingRequest(texts, new EmbeddingOptions()))
			.getResults()
			.stream()
			.map(Embedding::getOutput)
			.toList();
	}

	/**
	 * Embeds a batch of texts into vectors and returns the {@link EmbeddingResponse}.
	 * @param texts list of texts to embed.
	 * @return the embedding response.
	 */
	default EmbeddingResponse embedForResponse(List<String> texts) {
		Assert.notNull(texts, "Texts must not be null");
		return this.call(new EmbeddingRequest(texts, new EmbeddingOptions()));
	}

	/**
	 * @return the number of dimensions of the embedded vectors. It is generative
	 * specific.
	 */
	default int dimensions() {
		return embed("Test String").size();
	}

}
