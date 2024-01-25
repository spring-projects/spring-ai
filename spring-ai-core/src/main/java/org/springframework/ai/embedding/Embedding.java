package org.springframework.ai.embedding;

import java.util.List;
import java.util.Objects;

import org.springframework.ai.model.ModelResult;

/**
 * Represents a single embedding vector.
 */
public class Embedding implements ModelResult<List<Double>> {

	private List<Double> embedding;

	private Integer index;

	private EmbeddingResultMetadata metadata;

	/**
	 * Creates a new {@link Embedding} instance.
	 * @param embedding the embedding vector values.
	 * @param index the embedding index in a list of embeddings.
	 */
	public Embedding(List<Double> embedding, Integer index) {
		this.embedding = embedding;
		this.index = index;
	}

	/**
	 * @return Get the embedding vector values.
	 */
	@Override
	public List<Double> getOutput() {
		return embedding;
	}

	/**
	 * @return Get the embedding index in a list of embeddings.
	 */
	public Integer getIndex() {
		return index;
	}

	/**
	 * @return Get the metadata associated with the embedding.
	 */
	public EmbeddingResultMetadata getMetadata() {
		return metadata;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Embedding embedding1 = (Embedding) o;
		return Objects.equals(embedding, embedding1.embedding) && Objects.equals(index, embedding1.index);
	}

	@Override
	public int hashCode() {
		return Objects.hash(embedding, index);
	}

	@Override
	public String toString() {
		String message = this.embedding.isEmpty() ? "<empty>" : "<has data>";
		return "Embedding{" + "embedding=" + message + ", index=" + index + '}';
	}

}
