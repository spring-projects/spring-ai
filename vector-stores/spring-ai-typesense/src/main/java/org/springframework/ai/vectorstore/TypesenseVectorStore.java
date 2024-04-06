package org.springframework.ai.vectorstore;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Optional;

public class TypesenseVectorStore implements VectorStore {

	@Override
	public void add(List<Document> documents) {

	}

	@Override
	public Optional<Boolean> delete(List<String> idList) {
		return Optional.empty();
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {
		return null;
	}

}
