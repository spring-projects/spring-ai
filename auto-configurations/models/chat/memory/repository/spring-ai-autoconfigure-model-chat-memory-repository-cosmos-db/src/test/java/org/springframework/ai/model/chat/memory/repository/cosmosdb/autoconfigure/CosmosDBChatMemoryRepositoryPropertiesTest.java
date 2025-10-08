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

package org.springframework.ai.model.chat.memory.repository.cosmosdb.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.memory.repository.cosmosdb.CosmosDBChatMemoryRepositoryConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CosmosDBChatMemoryRepositoryProperties}.
 *
 * @author Theo van Kraay
 * @since 1.1.0
 */
class CosmosDBChatMemoryRepositoryPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void defaultProperties() {
		this.contextRunner.run(context -> {
			CosmosDBChatMemoryRepositoryProperties properties = context
				.getBean(CosmosDBChatMemoryRepositoryProperties.class);
			assertThat(properties.getDatabaseName())
				.isEqualTo(CosmosDBChatMemoryRepositoryConfig.DEFAULT_DATABASE_NAME);
			assertThat(properties.getContainerName())
				.isEqualTo(CosmosDBChatMemoryRepositoryConfig.DEFAULT_CONTAINER_NAME);
			assertThat(properties.getPartitionKeyPath())
				.isEqualTo(CosmosDBChatMemoryRepositoryConfig.DEFAULT_PARTITION_KEY_PATH);
		});
	}

	@Test
	void customProperties() {
		this.contextRunner.withPropertyValues("spring.ai.chat.memory.repository.cosmosdb.database-name=custom-db")
			.withPropertyValues("spring.ai.chat.memory.repository.cosmosdb.container-name=custom-container")
			.withPropertyValues("spring.ai.chat.memory.repository.cosmosdb.partition-key-path=/custom-partition-key")
			.run(context -> {
				CosmosDBChatMemoryRepositoryProperties properties = context
					.getBean(CosmosDBChatMemoryRepositoryProperties.class);
				assertThat(properties.getDatabaseName()).isEqualTo("custom-db");
				assertThat(properties.getContainerName()).isEqualTo("custom-container");
				assertThat(properties.getPartitionKeyPath()).isEqualTo("/custom-partition-key");
			});
	}

	@Configuration
	@EnableConfigurationProperties(CosmosDBChatMemoryRepositoryProperties.class)
	static class TestConfiguration {

	}

}
