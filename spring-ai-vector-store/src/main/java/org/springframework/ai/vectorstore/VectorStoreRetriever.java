/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.vectorstore;

import java.util.List;

import org.springframework.ai.document.Document;

/**
 * A functional interface that provides read-only access to vector store retrieval
 * operations. This interface extracts only the document retrieval functionality from
 * {@link VectorStore}, ensuring that mutation operations (add, delete) are not exposed.
 *
 * <p>
 * This is useful when you want to provide retrieval-only access to a vector store,
 * following the principle of least privilege by not exposing write operations.
 *
 * @author Mark Pollack
 * @since 1.0.0
 */
@FunctionalInterface
public interface VectorStoreRetriever {

	/**
	 * Retrieves documents by query embedding similarity and metadata filters to retrieve
	 * exactly the number of nearest-neighbor results that match the request criteria.
	 * @param request Search request for set search parameters, such as the query text,
	 * topK, similarity threshold and metadata filter expressions.
	 * @return Returns documents that match the query request conditions.
	 */
	List<Document> similaritySearch(SearchRequest request);

	/**
	 * Retrieves documents by query embedding similarity using the default
	 * {@link SearchRequest}'s search criteria.
	 * @param query Text to use for embedding similarity comparison.
	 * @return Returns a list of documents that have embeddings similar to the query text
	 * embedding.
	 */
	default List<Document> similaritySearch(String query) {
		return this.similaritySearch(SearchRequest.builder().query(query).build());
	}

	/**
	 * Retrieves documents by similarity to a query embedding the caller has already
	 * computed, instead of embedding query text with the store's own embedding model.
	 * <p>
	 * This is the search-side counterpart of {@link VectorStore#upsert(List)}: an
	 * application that produces its own embeddings can embed the query the same way and
	 * search with the result, so the store's embedding model is never involved. The query
	 * embedding must come from the same model, with the same settings, as the stored
	 * embeddings; otherwise the results are meaningless even though no error is raised.
	 * <p>
	 * The request supplies the rest of the search: {@code topK}, the similarity threshold
	 * and the filter expression. Its query text, if any, is ignored.
	 * <p>
	 * The default implementation throws {@link UnsupportedOperationException}; each store
	 * opts in independently.
	 * @param queryEmbedding the query embedding; must not be null or empty, and must
	 * contain only finite values
	 * @param request the search parameters; its query text is ignored
	 * @return the documents most similar to the query embedding that match the request
	 * conditions
	 * @throws UnsupportedOperationException if the store does not support searching with
	 * a query embedding
	 * @since 2.1.0
	 */
	default List<Document> similaritySearch(float[] queryEmbedding, SearchRequest request) {
		throw new UnsupportedOperationException(
				getClass().getSimpleName() + " does not support similarity search with a query embedding");
	}

}
