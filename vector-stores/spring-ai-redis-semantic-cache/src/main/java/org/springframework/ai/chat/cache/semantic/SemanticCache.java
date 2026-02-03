/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.chat.cache.semantic;

import java.time.Duration;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Interface defining operations for a semantic cache implementation that stores and
 * retrieves chat responses based on semantic similarity of queries. This cache uses
 * vector embeddings to determine similarity between queries.
 *
 * <p>
 * The semantic cache provides functionality to:
 * <ul>
 * <li>Store chat responses with their associated queries</li>
 * <li>Retrieve responses for semantically similar queries</li>
 * <li>Support time-based expiration of cached entries</li>
 * <li>Support context-based isolation (e.g., different system prompts)</li>
 * <li>Clear the entire cache</li>
 * </ul>
 *
 * <p>
 * Implementations should ensure thread-safety and proper resource management.
 *
 * @author Brian Sam-Bodden
 * @author Soby Chacko
 */
public interface SemanticCache {

	/**
	 * Stores a query and its corresponding chat response in the cache. Implementations
	 * should handle vector embedding of the query and proper storage of both the query
	 * embedding and response.
	 * @param query The original query text to be cached
	 * @param response The chat response associated with the query
	 */
	void set(String query, ChatResponse response);

	/**
	 * Stores a query and its corresponding chat response in the cache with an optional
	 * context identifier for isolation. The context hash ensures that cached responses
	 * are only returned for queries with matching context (e.g., same system prompt).
	 * @param query The original query text to be cached
	 * @param response The chat response associated with the query
	 * @param contextHash Optional hash identifier for context isolation (e.g., system
	 * prompt hash). If null, behaves the same as {@link #set(String, ChatResponse)}.
	 */
	default void set(String query, ChatResponse response, @Nullable String contextHash) {
		set(query, response);
	}

	/**
	 * Stores a query and response in the cache with a specified time-to-live duration.
	 * After the TTL expires, the entry should be automatically removed from the cache.
	 * @param query The original query text to be cached
	 * @param response The chat response associated with the query
	 * @param ttl The duration after which the cache entry should expire
	 */
	void set(String query, ChatResponse response, Duration ttl);

	/**
	 * Retrieves a cached response for a semantically similar query. The implementation
	 * should:
	 * <ul>
	 * <li>Convert the input query to a vector embedding</li>
	 * <li>Search for similar query embeddings in the cache</li>
	 * <li>Return the response associated with the most similar query if it meets the
	 * similarity threshold</li>
	 * </ul>
	 * @param query The query to find similar responses for
	 * @return Optional containing the most similar cached response if found and meets
	 * similarity threshold, empty Optional otherwise
	 */
	Optional<ChatResponse> get(String query);

	/**
	 * Retrieves a cached response for a semantically similar query, filtered by context.
	 * Only returns responses that were stored with the same context hash, ensuring
	 * isolation between different contexts (e.g., different system prompts).
	 * @param query The query to find similar responses for
	 * @param contextHash Optional hash identifier for context filtering. If null, behaves
	 * the same as {@link #get(String)}.
	 * @return Optional containing the most similar cached response if found, matches
	 * context, and meets similarity threshold; empty Optional otherwise
	 */
	default Optional<ChatResponse> get(String query, @Nullable String contextHash) {
		return get(query);
	}

	/**
	 * Removes all entries from the cache. This operation should be atomic and
	 * thread-safe.
	 */
	void clear();

	/**
	 * Returns the underlying vector store used by this cache implementation. This allows
	 * access to lower-level vector operations if needed.
	 * @return The VectorStore instance used by this cache
	 */
	VectorStore getStore();

}
