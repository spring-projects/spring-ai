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

package org.springframework.ai.chat.memory.repository.redis;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import redis.clients.jedis.JedisPooled;

import org.springframework.util.Assert;

/**
 * Configuration class for RedisChatMemoryRepository.
 *
 * @author Brian Sam-Bodden
 */
public class RedisChatMemoryConfig {

	public static final String DEFAULT_INDEX_NAME = "chat-memory-idx";

	public static final String DEFAULT_KEY_PREFIX = "chat-memory:";

	/**
	 * Default maximum number of results to return (1000 is Redis's default cursor read
	 * size).
	 */
	public static final int DEFAULT_MAX_RESULTS = 1000;

	/** The Redis client */
	private final JedisPooled jedisClient;

	/** The index name for Redis Search */
	private final String indexName;

	/** The key prefix for stored messages */
	private final String keyPrefix;

	/** The time-to-live in seconds for stored messages */
	private final Integer timeToLiveSeconds;

	/** Whether to automatically initialize the schema */
	private final boolean initializeSchema;

	/**
	 * Maximum number of conversation IDs to return.
	 */
	private final int maxConversationIds;

	/**
	 * Maximum number of messages to return per conversation.
	 */
	private final int maxMessagesPerConversation;

	/**
	 * Optional metadata field definitions for proper indexing. Format compatible with
	 * RedisVL schema format.
	 */
	private final List<Map<String, String>> metadataFields;

	private RedisChatMemoryConfig(final Builder builder) {
		Assert.notNull(builder.jedisClient, "JedisPooled client must not be null");
		Assert.hasText(builder.indexName, "Index name must not be empty");
		Assert.hasText(builder.keyPrefix, "Key prefix must not be empty");

		this.jedisClient = builder.jedisClient;
		this.indexName = builder.indexName;
		this.keyPrefix = builder.keyPrefix;
		this.timeToLiveSeconds = builder.timeToLiveSeconds;
		this.initializeSchema = builder.initializeSchema;
		this.maxConversationIds = builder.maxConversationIds;
		this.maxMessagesPerConversation = builder.maxMessagesPerConversation;
		this.metadataFields = Collections.unmodifiableList(builder.metadataFields);
	}

	public static Builder builder() {
		return new Builder();
	}

	public JedisPooled getJedisClient() {
		return jedisClient;
	}

	public String getIndexName() {
		return indexName;
	}

	public String getKeyPrefix() {
		return keyPrefix;
	}

	public Integer getTimeToLiveSeconds() {
		return timeToLiveSeconds;
	}

	public boolean isInitializeSchema() {
		return initializeSchema;
	}

	/**
	 * Gets the maximum number of conversation IDs to return.
	 * @return maximum number of conversation IDs
	 */
	public int getMaxConversationIds() {
		return maxConversationIds;
	}

	/**
	 * Gets the maximum number of messages to return per conversation.
	 * @return maximum number of messages per conversation
	 */
	public int getMaxMessagesPerConversation() {
		return maxMessagesPerConversation;
	}

	/**
	 * Gets the metadata field definitions.
	 * @return list of metadata field definitions in RedisVL-compatible format
	 */
	public List<Map<String, String>> getMetadataFields() {
		return metadataFields;
	}

	/**
	 * Builder for RedisChatMemoryConfig.
	 */
	public static class Builder {

		/** The Redis client */
		private @Nullable JedisPooled jedisClient;

		/** The index name */
		private String indexName = DEFAULT_INDEX_NAME;

		/** The key prefix */
		private String keyPrefix = DEFAULT_KEY_PREFIX;

		/** The time-to-live in seconds */
		private Integer timeToLiveSeconds = -1;

		/** Whether to initialize the schema */
		private boolean initializeSchema = true;

		/** Maximum number of conversation IDs to return */
		private int maxConversationIds = DEFAULT_MAX_RESULTS;

		/** Maximum number of messages per conversation */
		private int maxMessagesPerConversation = DEFAULT_MAX_RESULTS;

		/** Optional metadata field definitions for indexing */
		private List<Map<String, String>> metadataFields = Collections.emptyList();

		/**
		 * Sets the Redis client.
		 * @param jedisClient the Redis client to use
		 * @return the builder instance
		 */
		public Builder jedisClient(final JedisPooled jedisClient) {
			this.jedisClient = jedisClient;
			return this;
		}

		/**
		 * Sets the index name.
		 * @param indexName the index name to use
		 * @return the builder instance
		 */
		public Builder indexName(final String indexName) {
			this.indexName = indexName;
			return this;
		}

		/**
		 * Sets the key prefix.
		 * @param keyPrefix the key prefix to use
		 * @return the builder instance
		 */
		public Builder keyPrefix(final String keyPrefix) {
			this.keyPrefix = keyPrefix;
			return this;
		}

		/**
		 * Sets the time-to-live duration.
		 * @param ttl the time-to-live duration
		 * @return the builder instance
		 */
		public Builder timeToLive(final Duration ttl) {
			if (ttl != null) {
				this.timeToLiveSeconds = (int) ttl.toSeconds();
			}
			return this;
		}

		/**
		 * Sets whether to initialize the schema.
		 * @param initialize true to initialize schema, false otherwise
		 * @return the builder instance
		 */
		public Builder initializeSchema(final boolean initialize) {
			this.initializeSchema = initialize;
			return this;
		}

		/**
		 * Sets the maximum number of conversation IDs to return. Default is 1000, which
		 * is Redis's default cursor read size.
		 * @param maxConversationIds maximum number of conversation IDs
		 * @return the builder instance
		 */
		public Builder maxConversationIds(final int maxConversationIds) {
			this.maxConversationIds = maxConversationIds;
			return this;
		}

		/**
		 * Sets the maximum number of messages to return per conversation. Default is
		 * 1000, which is Redis's default cursor read size.
		 * @param maxMessagesPerConversation maximum number of messages
		 * @return the builder instance
		 */
		public Builder maxMessagesPerConversation(final int maxMessagesPerConversation) {
			this.maxMessagesPerConversation = maxMessagesPerConversation;
			return this;
		}

		/**
		 * Sets the metadata field definitions for proper indexing. Format is compatible
		 * with RedisVL schema format. Each map should contain "name" and "type" keys.
		 *
		 * Example: <pre>
		 * List.of(
		 *     Map.of("name", "priority", "type", "tag"),
		 *     Map.of("name", "score", "type", "numeric"),
		 *     Map.of("name", "category", "type", "tag")
		 * )
		 * </pre>
		 * @param metadataFields list of field definitions
		 * @return the builder instance
		 */
		public Builder metadataFields(List<Map<String, String>> metadataFields) {
			this.metadataFields = metadataFields;
			return this;
		}

		/**
		 * Builds a new RedisChatMemoryConfig instance.
		 * @return the new configuration instance
		 */
		public RedisChatMemoryConfig build() {
			return new RedisChatMemoryConfig(this);
		}

	}

}
