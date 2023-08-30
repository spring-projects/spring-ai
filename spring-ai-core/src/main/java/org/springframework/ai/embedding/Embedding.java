package org.springframework.ai.embedding;

import java.util.List;
import java.util.Objects;

public class Embedding {

	private List<Double> embedding;

	private Integer index;

	public Embedding(List<Double> embedding, Integer index) {
		this.embedding = embedding;
		this.index = index;
	}

	public List<Double> getEmbedding() {
		return embedding;
	}

	public Integer getIndex() {
		return index;
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
		String message = this.embedding.size() == 0 ? "<empty>" : "<has data>";
		return "Embedding{" + "embedding=" + message + ", index=" + index + '}';
	}

}
