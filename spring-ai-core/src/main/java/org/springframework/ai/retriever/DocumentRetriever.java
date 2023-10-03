package org.springframework.ai.retriever;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.function.Function;

public interface DocumentRetriever extends Function<String, List<Document>> {

	/**
	 * Retrieves relevant documents however the implementation sees fit.
	 * @param query query string
	 * @return relevant documents
	 */
	List<Document> retrieve(String query);

}
