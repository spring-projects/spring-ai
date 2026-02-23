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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.client.RestClient;

import org.springframework.ai.chat.memory.repository.elasticsearch.ElasticSearchChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.elasticsearch.ElasticSearchChatMemoryRepositoryConfig;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for
 * {@link ElasticSearchChatMemoryRepository}.
 *
 * @author Fu Jian
 * @since 1.1.0
 */
@AutoConfiguration(after = ElasticsearchRestClientAutoConfiguration.class, before = ChatMemoryAutoConfiguration.class)
@ConditionalOnClass({ ElasticSearchChatMemoryRepository.class, RestClient.class })
@EnableConfigurationProperties(ElasticSearchChatMemoryRepositoryProperties.class)
public class ElasticSearchChatMemoryRepositoryAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ElasticSearchChatMemoryRepositoryConfig elasticSearchChatMemoryRepositoryConfig(
			ElasticSearchChatMemoryRepositoryProperties properties, RestClient restClient) {
		ElasticsearchClient elasticsearchClient = new ElasticsearchClient(new RestClientTransport(restClient,
				new JacksonJsonpMapper(
						new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false))))
			.withTransportOptions(t -> t.addHeader("user-agent", "spring-ai-chat-memory elastic-java"));
		return ElasticSearchChatMemoryRepositoryConfig.builder()
			.withClient(elasticsearchClient)
			.withIndexName(properties.getIndexName())
			.build();
	}

	@Bean
	@ConditionalOnMissingBean
	public ElasticSearchChatMemoryRepository elasticSearchChatMemoryRepository(
			ElasticSearchChatMemoryRepositoryConfig config) {
		return ElasticSearchChatMemoryRepository.create(config);
	}

}
