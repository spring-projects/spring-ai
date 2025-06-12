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
package org.springframework.ai.chat.memory.redis;

import java.time.Duration;

import redis.clients.jedis.JedisPooled;

import org.springframework.util.Assert;

/**
 * Configuration class for RedisChatMemory.
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

	private final JedisPooled jedisClient;

	private final String indexName;

	private final String keyPrefix;

	private final Integer timeToLiveSeconds;

	private final boolean initializeSchema;

	/**
	 * Maximum number of conversation IDs to return.
	 */
	private final int maxConversationIds;

	/**
	 * Maximum number of messages to return per conversation.
	 */
	private final int maxMessagesPerConversation;

	private RedisChatMemoryConfig(Builder builder) {
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
	 * Builder for RedisChatMemoryConfig.
	 */
	public static class Builder {

		private JedisPooled jedisClient;

		private String indexName = DEFAULT_INDEX_NAME;

		private String keyPrefix = DEFAULT_KEY_PREFIX;

		private Integer timeToLiveSeconds = -1;

		private boolean initializeSchema = true;

		private int maxConversationIds = DEFAULT_MAX_RESULTS;

		private int maxMessagesPerConversation = DEFAULT_MAX_RESULTS;

		/**
		 * Sets the Redis client.
		 * @param jedisClient the Redis client to use
		 * @return the builder instance
		 */
		public Builder jedisClient(JedisPooled jedisClient) {
			this.jedisClient = jedisClient;
			return this;
		}

		/**
		 * Sets the index name.
		 * @param indexName the index name to use
		 * @return the builder instance
		 */
		public Builder indexName(String indexName) {
			this.indexName = indexName;
			return this;
		}

		/**
		 * Sets the key prefix.
		 * @param keyPrefix the key prefix to use
		 * @return the builder instance
		 */
		public Builder keyPrefix(String keyPrefix) {
			this.keyPrefix = keyPrefix;
			return this;
		}

		/**
		 * Sets the time-to-live duration.
		 * @param ttl the time-to-live duration
		 * @return the builder instance
		 */
		public Builder timeToLive(Duration ttl) {
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
		public Builder initializeSchema(boolean initialize) {
			this.initializeSchema = initialize;
			return this;
		}

		/**
		 * Sets the maximum number of conversation IDs to return. Default is 1000, which
		 * is Redis's default cursor read size.
		 * @param maxConversationIds maximum number of conversation IDs
		 * @return the builder instance
		 */
		public Builder maxConversationIds(int maxConversationIds) {
			this.maxConversationIds = maxConversationIds;
			return this;
		}

		/**
		 * Sets the maximum number of messages to return per conversation. Default is
		 * 1000, which is Redis's default cursor read size.
		 * @param maxMessagesPerConversation maximum number of messages
		 * @return the builder instance
		 */
		public Builder maxMessagesPerConversation(int maxMessagesPerConversation) {
			this.maxMessagesPerConversation = maxMessagesPerConversation;
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
