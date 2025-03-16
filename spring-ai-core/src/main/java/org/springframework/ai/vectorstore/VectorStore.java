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
import java.util.Optional;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.observation.DefaultVectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

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
	 */
	void delete(List<String> idList);

	/**
	 * Deletes documents from the vector store based on filter criteria.
	 * @param filterExpression Filter expression to identify documents to delete
	 * @throws IllegalStateException if the underlying delete causes an exception
	 */
	void delete(Filter.Expression filterExpression);

	/**
	 * Deletes documents from the vector store using a string filter expression. Converts
	 * the string filter to an Expression object and delegates to
	 * {@link #delete(Filter.Expression)}.
	 * @param filterExpression String representation of the filter criteria
	 * @throws IllegalArgumentException if the filter expression is null
	 * @throws IllegalStateException if the underlying delete causes an exception
	 */
	default void delete(String filterExpression) {
		SearchRequest searchRequest = SearchRequest.builder().filterExpression(filterExpression).build();
		Filter.Expression textExpression = searchRequest.getFilterExpression();
		Assert.notNull(textExpression, "Filter expression must not be null");
		this.delete(textExpression);
	}

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
		return this.similaritySearch(SearchRequest.builder().query(query).build());
	}

	/**
	 * Returns the native client if available in this vector store implementation.
	 *
	 * Note on usage: 1. Returns empty Optional when no native client is available 2. Due
	 * to Java type erasure, runtime type checking is not possible
	 *
	 * Example usage: When working with implementation with known native client:
	 * Optional<NativeClientType> client = vectorStore.getNativeClient();
	 *
	 * Note: Using Optional<?> will return the native client if one exists, rather than an
	 * empty Optional. For type safety, prefer using the specific client type.
	 * @return Optional containing native client if available, empty Optional otherwise
	 * @param <T> The type of the native client
	 */
	default <T> Optional<T> getNativeClient() {
		return Optional.empty();
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
		 * Sets the batching strategy.
		 * @param batchingStrategy the strategy to use
		 * @return the builder instance for method chaining
		 */
		T batchingStrategy(BatchingStrategy batchingStrategy);

		/**
		 * Builds and returns a new VectorStore instance with the configured settings.
		 * @return a new VectorStore instance
		 */
		VectorStore build();

	}

}
