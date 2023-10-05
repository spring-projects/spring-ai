package org.springframework.ai.retriever;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class VectorStoreRetriever implements DocumentRetriever {

	private VectorStore vectorStore;

	int k;

	Optional<Double> threshold = Optional.empty();

	public VectorStoreRetriever(VectorStore vectorStore) {
		this(vectorStore, 4);
	}

	public VectorStoreRetriever(VectorStore vectorStore, int k) {
		Objects.requireNonNull(vectorStore, "VectorStore must not be null");
		this.vectorStore = vectorStore;
		this.k = k;
	}

	public VectorStoreRetriever(VectorStore vectorStore, int k, double threshold) {
		Objects.requireNonNull(vectorStore, "VectorStore must not be null");
		this.vectorStore = vectorStore;
		this.k = k;
		this.threshold = Optional.of(threshold);
	}

	public VectorStore getVectorStore() {
		return vectorStore;
	}

	public int getK() {
		return k;
	}

	public Optional<Double> getThreshold() {
		return threshold;
	}

	@Override
	public List<Document> retrieve(String query) {
		if (threshold.isPresent()) {
			return this.vectorStore.similaritySearch(query, this.k, this.threshold.get());
		}
		else {
			return this.vectorStore.similaritySearch(query, this.k);
		}
	}

}
