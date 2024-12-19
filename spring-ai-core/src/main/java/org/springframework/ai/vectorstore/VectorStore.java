/*
 * Copyright 2023-2024 the original author or authors.
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
import java.util.Optional;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.ai.vectorstore.observation.DefaultVectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.lang.Nullable;

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
	 * @return Returns true if the documents were successfully deleted.
	 */
	@Nullable
	Optional<Boolean> delete(List<String> idList);

	/**
	 * Retrieves documents by query embedding similarity and metadata filters to retrieve
	 * exactly the number of nearest-neighbor results that match the request criteria.
	 * @param request Search request for set search parameters, such as the query text,
	 * topK, similarity threshold and metadata filter expressions.
	 * @return Returns documents th match the query request conditions.
	 */
	@Nullable
	List<Document> similaritySearch(SearchRequest request);

	/**
	 * Retrieves documents by query embedding similarity using the default
	 * {@link SearchRequest}'s' search criteria.
	 * @param query Text to use for embedding similarity comparison.
	 * @return Returns a list of documents that have embeddings similar to the query text
	 * embedding.
	 */
	@Nullable
	default List<Document> similaritySearch(String query) {
		return this.similaritySearch(SearchRequest.query(query));
	}

	/**
	 * Builder interface for creating VectorStore instances. Implements a fluent builder
	 * pattern for configuring observation-related settings.
	 *
	 * @param <T> the concrete builder type, enabling method chaining with the correct
	 * return type
	 */
	interface Builder<T extends Builder<T>> {

		/**
		 * Sets the registry for collecting observations and metrics. Defaults to
		 * {@link ObservationRegistry#NOOP} if not specified.
		 * @param observationRegistry the registry to use for observations
		 * @return the builder instance for method chaining
		 */
		T observationRegistry(ObservationRegistry observationRegistry);

		/**
		 * Sets a custom convention for creating observations. If not specified,
		 * {@link DefaultVectorStoreObservationConvention} will be used.
		 * @param convention the custom observation convention to use
		 * @return the builder instance for method chaining
		 */
		T customObservationConvention(VectorStoreObservationConvention convention);

		/**
		 * Builds and returns a new VectorStore instance with the configured settings.
		 * @return a new VectorStore instance
		 */
		VectorStore build();

	}

}
