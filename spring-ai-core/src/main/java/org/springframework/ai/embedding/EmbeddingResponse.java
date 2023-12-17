package org.springframework.ai.embedding;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Embedding response object.
 */
public class EmbeddingResponse {

	/**
	 * Embedding data.
	 */
	private List<Embedding> data;

	/**
	 * Embedding metadata.
	 */
	private Map<String, Object> metadata = new HashMap<>();

	/**
	 * Creates a new {@link EmbeddingResponse} instance with empty metadata.
	 * @param data the embedding data.
	 */
	public EmbeddingResponse(List<Embedding> data) {
		this(data, new HashMap<>());
	}

	/**
	 * Creates a new {@link EmbeddingResponse} instance.
	 * @param data the embedding data.
	 * @param metadata the embedding metadata.
	 */
	public EmbeddingResponse(List<Embedding> data, Map<String, Object> metadata) {
		this.data = data;
		this.metadata = metadata;
	}

	/**
	 * @return Get the embedding data.
	 */
	public List<Embedding> getData() {
		return data;
	}

	/**
	 * @return Get the embedding metadata.
	 */
	public Map<String, Object> getMetadata() {
		return metadata;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		EmbeddingResponse that = (EmbeddingResponse) o;
		return Objects.equals(data, that.data) && Objects.equals(metadata, that.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(data, metadata);
	}

	@Override
	public String toString() {
		return "EmbeddingResult{" + "data=" + data + ", metadata=" + metadata + '}';
	}

}
