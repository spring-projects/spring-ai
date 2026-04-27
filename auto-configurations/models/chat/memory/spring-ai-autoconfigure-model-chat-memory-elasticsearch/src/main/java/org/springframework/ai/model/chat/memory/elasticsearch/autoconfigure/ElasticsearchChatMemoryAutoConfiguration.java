/*
 * Copyright 2023-present the original author or authors.
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

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.elasticsearch.ElasticsearchChatMemoryRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchRestClientAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Elasticsearch-based chat memory implementation.
 *
 * <p>
 * The {@link Rest5Client} bean is provided by Spring Boot's
 * {@link ElasticsearchRestClientAutoConfiguration}, configured via standard
 * {@code spring.elasticsearch.*} properties.
 * </p>
 *
 * @author Laura
 * @since 2.0.0
 */
@AutoConfiguration(after = ElasticsearchRestClientAutoConfiguration.class)
@ConditionalOnClass({ ElasticsearchChatMemoryRepository.class, Rest5Client.class })
@EnableConfigurationProperties(ElasticsearchChatMemoryProperties.class)
public class ElasticsearchChatMemoryAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean({ ElasticsearchChatMemoryRepository.class, ChatMemory.class, ChatMemoryRepository.class })
	public ElasticsearchChatMemoryRepository elasticsearchChatMemoryRepository(Rest5Client restClient,
			ElasticsearchChatMemoryProperties properties) {
		ElasticsearchChatMemoryRepository.Builder builder = ElasticsearchChatMemoryRepository.builder(restClient)
			.indexName(properties.getIndexName())
			.initializeSchema(properties.isInitializeSchema())
			.maxResults(properties.getMaxResults());

		return builder.build();
	}

}
