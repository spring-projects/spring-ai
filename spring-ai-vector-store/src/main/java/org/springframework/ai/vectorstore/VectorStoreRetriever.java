/*
 * Copyright 2023-2025 the original author or authors.
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

}
