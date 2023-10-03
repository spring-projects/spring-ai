package org.springframework.ai.document;

import java.util.List;
import java.util.function.Function;

public interface DocumentRetriever extends Function<String, List<Document>> {

	/**
	 * Retrieves relevant documents however the implementation sees fit.
	 * @param query query string
	 * @return relevant documents
	 */
	List<Document> retrieve(String query);

	default List<Document> apply(String query) {
		return retrieve(query);
	}

}
