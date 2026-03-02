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

package org.springframework.ai.model.chat.memory.elasticsearch.autoconfigure;

import org.springframework.ai.chat.memory.repository.elasticsearch.ElasticsearchChatMemoryConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Elasticsearch-based chat memory.
 *
 * <p>
 * Connection properties (host, port, authentication) are managed by Spring Boot's
 * standard {@code spring.elasticsearch.*} properties, which provide the
 * {@code Rest5Client} bean.
 * </p>
 *
 * @author Laura
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "spring.ai.chat.memory.elasticsearch")
public class ElasticsearchChatMemoryProperties {

	/**
	 * Name of the Elasticsearch index for chat memory.
	 */
	private String indexName = ElasticsearchChatMemoryConfig.DEFAULT_INDEX_NAME;

	/**
	 * Whether to initialize the Elasticsearch index schema on startup. Default is true.
	 */
	private boolean initializeSchema = true;

	/**
	 * Maximum number of results to return (defaults to 1000).
	 */
	private int maxResults = ElasticsearchChatMemoryConfig.DEFAULT_MAX_RESULTS;

	public String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public boolean isInitializeSchema() {
		return this.initializeSchema;
	}

	public void setInitializeSchema(boolean initializeSchema) {
		this.initializeSchema = initializeSchema;
	}

	public int getMaxResults() {
		return this.maxResults;
	}

	public void setMaxResults(int maxResults) {
		this.maxResults = maxResults;
	}

}
