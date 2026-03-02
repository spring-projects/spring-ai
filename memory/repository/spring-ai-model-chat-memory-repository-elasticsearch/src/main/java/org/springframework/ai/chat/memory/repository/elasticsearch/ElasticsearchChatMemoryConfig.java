/*
 * Copyright 2026-2026 the original author or authors.
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

package org.springframework.ai.chat.memory.repository.elasticsearch;

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.springframework.util.Assert;

/**
 * Configuration class for ElasticsearchChatMemoryRepository.
 *
 * @author Laura Trotta
 */
public class ElasticsearchChatMemoryConfig {

	public static final String DEFAULT_INDEX_NAME = "spring-ai-chat-memory";

	/**
	 * Default maximum number of results to return (Elasticsearch defaults to 10, while
	 * the max is 10000).
	 */
	public static final int DEFAULT_MAX_RESULTS = 1000;

	/** The Elasticsearch index name */
	private final String indexName;

	/**
	 * Maximum number of results to return.
	 */
	private final int maxResults;

	/** Whether to automatically initialize the schema */
	private final boolean initializeSchema;

	/** Elasticsearch rest client instance */
	private final Rest5Client esRestClient;

	private ElasticsearchChatMemoryConfig(final Builder builder) {
		this.indexName = builder.indexName;
		this.initializeSchema = builder.initializeSchema;
		this.maxResults = builder.maxResults;
		this.esRestClient = builder.esRestClient;
	}

	public static Builder builder(Rest5Client esRestClient) {
		return new Builder(esRestClient);
	}

	public String getIndexName() {
		return indexName;
	}

	public boolean isInitializeSchema() {
		return initializeSchema;
	}

	public int getMaxResults() {
		return maxResults;
	}

	public Rest5Client getEsRestClient() {
		return esRestClient;
	}

	/**
	 * Builder for ElasticsearchChatMemoryConfig.
	 */
	public static class Builder {

		/** The index name */
		private String indexName = DEFAULT_INDEX_NAME;

		/** Whether to initialize the schema */
		private boolean initializeSchema = true;

		/** Maximum number of results to return. */
		private int maxResults = DEFAULT_MAX_RESULTS;

		/** Elasticsearch rest client instance */
		private final Rest5Client esRestClient;

		private Builder(final Rest5Client esRestClient) {
			Assert.notNull(esRestClient, "Rest5Client must not be null");
			this.esRestClient = esRestClient;
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
		 * Sets whether to initialize the schema.
		 * @param initialize true to initialize schema, false otherwise
		 * @return the builder instance
		 */
		public Builder initializeSchema(final boolean initialize) {
			this.initializeSchema = initialize;
			return this;
		}

		/**
		 * Sets the max number of results to return.
		 * @param maxResults the number of results
		 * @return the builder instance
		 */
		public Builder maxResults(final int maxResults) {
			this.maxResults = maxResults;
			return this;
		}

		/**
		 * Builds a new ElasticsearchChatMemoryConfig instance.
		 * @return the new configuration instance
		 */
		public ElasticsearchChatMemoryConfig build() {
			return new ElasticsearchChatMemoryConfig(this);
		}

	}

}
