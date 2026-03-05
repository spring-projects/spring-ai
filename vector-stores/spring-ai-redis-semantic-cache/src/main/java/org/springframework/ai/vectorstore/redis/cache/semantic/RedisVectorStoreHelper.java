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

package org.springframework.ai.vectorstore.redis.cache.semantic;

import redis.clients.jedis.JedisPooled;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;

/**
 * Helper utility for creating and configuring Redis-based vector stores for semantic
 * caching.
 *
 * @author Brian Sam-Bodden
 */
public final class RedisVectorStoreHelper {

	private static final String DEFAULT_INDEX_NAME = "semantic-cache-idx";

	private static final String DEFAULT_PREFIX = "semantic-cache:";

	private RedisVectorStoreHelper() {
		// Utility class - prevent instantiation
	}

	/**
	 * Creates a pre-configured RedisVectorStore suitable for semantic caching.
	 * @param jedis The Redis client to use
	 * @param embeddingModel The embedding model to use for vectorization
	 * @return A configured RedisVectorStore instance
	 */
	public static RedisVectorStore createVectorStore(JedisPooled jedis, EmbeddingModel embeddingModel) {
		return createVectorStore(jedis, embeddingModel, DEFAULT_INDEX_NAME, DEFAULT_PREFIX);
	}

	/**
	 * Creates a pre-configured RedisVectorStore with custom index name and prefix.
	 * @param jedis The Redis client to use
	 * @param embeddingModel The embedding model to use for vectorization
	 * @param indexName The name of the search index to create
	 * @param prefix The key prefix to use for Redis documents
	 * @return A configured RedisVectorStore instance
	 */
	public static RedisVectorStore createVectorStore(JedisPooled jedis, EmbeddingModel embeddingModel, String indexName,
			String prefix) {
		RedisVectorStore vectorStore = RedisVectorStore.builder(jedis, embeddingModel)
			.indexName(indexName)
			.prefix(prefix)
			.metadataFields(MetadataField.text("response"), MetadataField.text("response_text"),
					MetadataField.numeric("ttl"))
			.initializeSchema(true)
			.build();

		vectorStore.afterPropertiesSet();
		return vectorStore;
	}

}
