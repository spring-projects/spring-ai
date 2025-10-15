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

package org.springframework.ai.chat.memory.repository.elasticsearch;

import java.io.IOException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

import org.springframework.util.Assert;

/**
 * Configuration for the Elasticsearch Chat Memory store.
 *
 * @author Fu Jian
 * @since 1.1.0
 */
public final class ElasticSearchChatMemoryRepositoryConfig {

	public static final String DEFAULT_INDEX_NAME = "ai_chat_memory";

	private final ElasticsearchClient client;

	private final String indexName;

	private ElasticSearchChatMemoryRepositoryConfig(Builder builder) {
		this.client = builder.client;
		this.indexName = builder.indexName;
		this.initializeIndex();
	}

	public static Builder builder() {
		return new Builder();
	}

	public ElasticsearchClient getClient() {
		return this.client;
	}

	public String getIndexName() {
		return this.indexName;
	}

	private void initializeIndex() {
		try {
			// Check if index exists
			boolean exists = this.client.indices().exists(ex -> ex.index(this.indexName)).value();

			if (!exists) {
				// Create index with mapping
				this.client.indices()
					.create(cr -> cr.index(this.indexName)
						.mappings(m -> m.properties("conversationId", p -> p.keyword(k -> k))
							.properties("messageType", p -> p.keyword(k -> k))
							.properties("content", p -> p.text(t -> t))
							.properties("sequenceNumber", p -> p.integer(i -> i))
							.properties("messageTimestamp", p -> p.long_(l -> l))
							.properties("metadata", p -> p.object(o -> o.enabled(true)))));
			}
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to initialize Elasticsearch index", e);
		}
	}

	public static final class Builder {

		private ElasticsearchClient client;

		private String indexName = DEFAULT_INDEX_NAME;

		private Builder() {
		}

		public Builder withClient(ElasticsearchClient client) {
			this.client = client;
			return this;
		}

		public Builder withIndexName(String indexName) {
			this.indexName = indexName;
			return this;
		}

		public ElasticSearchChatMemoryRepositoryConfig build() {
			Assert.notNull(this.client, "ElasticsearchClient cannot be null");
			Assert.hasText(this.indexName, "indexName cannot be null or empty");

			return new ElasticSearchChatMemoryRepositoryConfig(this);
		}

	}

}
