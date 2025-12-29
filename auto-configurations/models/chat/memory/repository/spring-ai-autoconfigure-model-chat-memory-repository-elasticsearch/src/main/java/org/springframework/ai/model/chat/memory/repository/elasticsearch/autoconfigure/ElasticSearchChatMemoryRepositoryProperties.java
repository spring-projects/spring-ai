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

package org.springframework.ai.model.chat.memory.repository.elasticsearch.autoconfigure;

import org.springframework.ai.chat.memory.repository.elasticsearch.ElasticSearchChatMemoryRepositoryConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Elasticsearch chat memory.
 *
 * @author Fu Jian
 * @since 1.1.0
 */
@ConfigurationProperties(ElasticSearchChatMemoryRepositoryProperties.CONFIG_PREFIX)
public class ElasticSearchChatMemoryRepositoryProperties {

	public static final String CONFIG_PREFIX = "spring.ai.chat.memory.repository.elasticsearch";

	private String indexName = ElasticSearchChatMemoryRepositoryConfig.DEFAULT_INDEX_NAME;

	public String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

}
