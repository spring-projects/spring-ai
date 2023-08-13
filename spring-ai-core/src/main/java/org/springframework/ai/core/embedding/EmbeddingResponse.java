package org.springframework.ai.core.embedding;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class EmbeddingResponse {

	private List<Embedding> data;

	private Map<String, Object> metadata = new HashMap<>();

	public EmbeddingResponse(List<Embedding> data, Map<String, Object> metadata) {
		this.data = data;
		this.metadata = metadata;
	}

	public List<Embedding> getData() {
		return data;
	}

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
