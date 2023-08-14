package org.springframework.ai.core.retriever;

import org.springframework.ai.core.document.Document;

import java.util.List;

public interface Retriever {

	/**
	 * Retrieves relevant documents however the implementation sees fit.
	 * @param query query string
	 * @return relevant documents
	 */
	List<Document> retrieve(String query);

}
