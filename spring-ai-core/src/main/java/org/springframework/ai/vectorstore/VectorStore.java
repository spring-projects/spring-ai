package org.springframework.ai.vectorstore;

import java.util.List;
import java.util.Optional;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentWriter;

public interface VectorStore extends DocumentWriter {

	/**
	 * Adds Documents to the vector store.
	 * @param documents the list of documents to store Will throw an exception if the
	 * underlying provider checks for duplicate IDs on add
	 */
	void add(List<Document> documents);

	default void accept(List<Document> documents) {
		add(documents);
	}

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

	/**
	 * @param query The query to send, it will be converted to an embedding based on the
	 * configuration of the vector store.
	 * @param k the top 'k' similar results
	 * @param threshold the lower bound of the similarity score
	 * @param filters vector store specific search push-down filter predicates. Usually a
	 * json expression.
	 * @return similar documents that match the requested threshold and filter.
	 */
	default List<Document> similaritySearch(String query, int k, double threshold, String filters) {
		throw new UnsupportedOperationException("This vector store doesn't support search filtering");
	}

}
