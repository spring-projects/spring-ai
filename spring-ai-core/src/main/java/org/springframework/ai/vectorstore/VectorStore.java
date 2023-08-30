package org.springframework.ai.vectorstore;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Optional;

public interface VectorStore {

	/**
	 * Adds Documents to the vector store.
	 * @param documents the list of documents to store Will throw an exception if the
	 * underlying provider checks for duplicate IDs on add
	 */
	void add(List<Document> documents);

	Optional<Boolean> delete(List<String> idList);

	List<Document> similaritySearch(String query);

	List<Document> similaritySearch(String query, int k);

	/**
	 * @param query The query to send, it will be converted to an embedding based on the
	 * configuration of the vector store.
	 * @param k the top 'k' similar results
	 * @param threshold the lower bound of the similarity score
	 * @return similar documents
	 */
	List<Document> similaritySearch(String query, int k, double threshold);

}
