/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vectorstore;

import java.util.List;
import java.util.Optional;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentWriter;

/**
 * The {@code VectorStore} interface defines the operations for managing and querying
 * documents in a vector database. It extends {@link DocumentWriter} to support document
 * writing operations. Vector databases are specialized for AI applications, performing
 * similarity searches based on vector representations of data rather than exact matches.
 * This interface allows for adding, deleting, and searching documents based on their
 * similarity to a given query.
 */
public interface VectorStore extends DocumentWriter {

	default String getName() {
		return this.getClass().getSimpleName();
	}

	/**
	 * Adds list of {@link Document}s to the vector store.
	 * @param documents the list of documents to store. Throws an exception if the
	 * underlying provider checks for duplicate IDs.
	 */
	void add(List<Document> documents);

	@Override
	default void accept(List<Document> documents) {
		add(documents);
	}

	/**
	 * Deletes documents from the vector store.
	 * @param idList list of document ids for which documents will be removed.
	 * @return
	 */
	Optional<Boolean> delete(List<String> idList);

	/**
	 * Retrieves documents by query embedding similarity and metadata filters to retrieve
	 * exactly the number of nearest-neighbor results that match the request criteria.
	 * @param request Search request for set search parameters, such as the query text,
	 * topK, similarity threshold and metadata filter expressions.
	 * @return Returns documents th match the query request conditions.
	 */
	List<Document> similaritySearch(SearchRequest request);

	/**
	 * Retrieves documents by query embedding similarity using the default
	 * {@link SearchRequest}'s' search criteria.
	 * @param query Text to use for embedding similarity comparison.
	 * @return Returns a list of documents that have embeddings similar to the query text
	 * embedding.
	 */
	default List<Document> similaritySearch(String query) {
		return this.similaritySearch(SearchRequest.query(query));
	}

	/**
	 * Retrieves documents by query full text content and metadata filters to retrieve
	 * exactly the number of nearest-neighbor results that match the request criteria.
	 * @param request Search request for set search parameters, such as the query text,
	 * topK, similarity threshold and metadata filter expressions.
	 * @return a list of {@link Document} objects representing the retrieved documents
	 * that match the search criteria.
	 * @throws UnsupportedOperationException if the method is not supported by the current
	 * implementation. Subclasses should override this method to provide a specific
	 * implementation.
	 */
	default List<Document> fullTextSearch(SearchRequest request) {
		throw new UnsupportedOperationException("The [" + this.getClass() + "] doesn't support full text search!");
	}

	/**
	 * Retrieves documents by query full text content using the default
	 * {@link SearchRequest}'s' search criteria.
	 * @param query Text to use for full text search.
	 * @return a list of {@link Document} objects representing the retrieved documents
	 * that match the search criteria.
	 */
	default List<Document> fullTextSearch(String query) {
		return this.fullTextSearch(SearchRequest.query(query));
	}

	/**
	 * Performs a hybrid search by combining semantic and keyword-based search techniques
	 * to retrieve a list of relevant documents based on the provided
	 * {@link SearchRequest}.
	 * <p>
	 * This method is intended to retrieve documents that match the query both
	 * semantically (using vector embeddings) and via keyword matching. The hybrid
	 * approach aims to enhance retrieval accuracy by leveraging the strengths of both
	 * search methods.
	 * </p>
	 * @param request the {@link SearchRequest} object containing the query and search
	 * parameters.
	 * @return a list of {@link Document} objects representing the retrieved documents
	 * that match the search criteria.
	 * @throws UnsupportedOperationException if the method is not supported by the current
	 * implementation. Subclasses should override this method to provide a specific
	 * implementation.
	 */
	default List<Document> hybridSearch(SearchRequest request) {
		throw new UnsupportedOperationException(
				"The [" + this.getClass() + "] doesn't support hybrid (vector + text) search!");
	}

	/**
	 * Performs a hybrid search by combining semantic and keyword-based search techniques
	 * to retrieve a list of relevant documents based on the provided
	 * {@link SearchRequest}.
	 * <p>
	 * This method is intended to retrieve documents that match the query both
	 * semantically (using vector embeddings) and via keyword matching. The hybrid
	 * approach aims to enhance retrieval accuracy by leveraging the strengths of both
	 * search methods.
	 * </p>
	 * @param query Text to use for embedding similarity comparison.
	 * @return a list of {@link Document} objects representing the retrieved documents
	 * that match the search criteria.
	 */
	default List<Document> hybridSearch(String query) {
		return this.hybridSearch(SearchRequest.query(query));
	}

}
