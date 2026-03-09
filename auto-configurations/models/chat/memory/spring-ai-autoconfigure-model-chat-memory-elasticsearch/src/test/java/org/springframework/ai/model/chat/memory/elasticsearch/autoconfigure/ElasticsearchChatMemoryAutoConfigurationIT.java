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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.elasticsearch.ElasticsearchChatMemoryRepository;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ElasticsearchChatMemoryAutoConfigurationIT {

	private static final Logger logger = LoggerFactory.getLogger(ElasticsearchChatMemoryAutoConfigurationIT.class);

	@Container
	static ElasticsearchContainer elasticsearch = new ElasticsearchContainer("elasticsearch:9.3.0")
		.withEnv("xpack.security.enabled", "false");

	@BeforeAll
	static void setup() {
		logger.info("Elasticsearch container running at: {}", elasticsearch.getHttpHostAddress());
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ElasticsearchChatMemoryAutoConfiguration.class,
				ElasticsearchRestClientAutoConfiguration.class))
		.withPropertyValues("spring.elasticsearch.uris=" + elasticsearch.getHttpHostAddress());

	@Test
	void autoConfigurationRegistersExpectedBeans() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(ElasticsearchChatMemoryRepository.class);
			assertThat(context).hasSingleBean(ChatMemoryRepository.class);
		});
	}

	@Test
	void customPropertiesAreApplied() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.memory.elasticsearch.index-name=custom-index",
					"spring.ai.chat.memory.elasticsearch.initialize-schema=false",
					"spring.ai.chat.memory.elasticsearch.max-results=500")
			.run(context -> {
				ElasticsearchChatMemoryProperties properties = context
					.getBean(ElasticsearchChatMemoryProperties.class);
				assertThat(properties.getIndexName()).isEqualTo("custom-index");
				assertThat(properties.isInitializeSchema()).isFalse();
				assertThat(properties.getMaxResults()).isEqualTo(500);
			});
	}

	@Test
	void chatMemoryRepositoryIsProvidedByElasticsearchChatMemory() {
		this.contextRunner.run(context -> {
			ElasticsearchChatMemoryRepository elasticsearchChatMemory = context
				.getBean(ElasticsearchChatMemoryRepository.class);
			ChatMemoryRepository repository = context.getBean(ChatMemoryRepository.class);

			assertThat(repository).isSameAs(elasticsearchChatMemory);
		});
	}

}
