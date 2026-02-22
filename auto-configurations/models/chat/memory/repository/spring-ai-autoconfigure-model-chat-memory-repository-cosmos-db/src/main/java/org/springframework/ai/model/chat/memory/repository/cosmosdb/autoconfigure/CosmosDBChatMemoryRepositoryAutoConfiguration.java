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

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;

import org.springframework.ai.chat.memory.repository.cosmosdb.CosmosDBChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.cosmosdb.CosmosDBChatMemoryRepositoryConfig;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for {@link CosmosDBChatMemoryRepository}.
 *
 * @author Theo van Kraay
 * @since 1.1.0
 */
// Ordering is to make sure ChatMemoryRepository bean is cosmos one
@AutoConfiguration(before = ChatMemoryAutoConfiguration.class)
@ConditionalOnClass({ CosmosDBChatMemoryRepository.class, CosmosAsyncClient.class })
@EnableConfigurationProperties(CosmosDBChatMemoryRepositoryProperties.class)
public class CosmosDBChatMemoryRepositoryAutoConfiguration {

	private final String agentSuffix = "SpringAI-CDBNoSQL-ChatMemoryRepository";

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = "spring.ai.chat.memory.repository.cosmosdb", name = "endpoint")
	public CosmosAsyncClient cosmosClient(CosmosDBChatMemoryRepositoryProperties properties) {
		if (properties.getEndpoint() == null || properties.getEndpoint().isEmpty()) {
			throw new IllegalArgumentException(
					"Cosmos DB endpoint must be provided via spring.ai.chat.memory.repository.cosmosdb.endpoint property");
		}

		String mode = properties.getConnectionMode();
		if (mode == null) {
			properties.setConnectionMode("gateway");
		}
		else if (!mode.equals("direct") && !mode.equals("gateway")) {
			throw new IllegalArgumentException("Connection mode must be either 'direct' or 'gateway'");
		}

		CosmosClientBuilder builder = new CosmosClientBuilder().endpoint(properties.getEndpoint())
			.userAgentSuffix(this.agentSuffix);

		if (properties.getKey() == null || properties.getKey().isEmpty()) {
			builder.credential(new DefaultAzureCredentialBuilder().build());
		}
		else {
			builder.key(properties.getKey());
		}

		return ("direct".equals(properties.getConnectionMode()) ? builder.directMode() : builder.gatewayMode())
			.buildAsyncClient();
	}

	@Bean
	@ConditionalOnMissingBean
	public CosmosDBChatMemoryRepositoryConfig cosmosDBChatMemoryRepositoryConfig(
			CosmosDBChatMemoryRepositoryProperties properties, CosmosAsyncClient cosmosAsyncClient) {

		return CosmosDBChatMemoryRepositoryConfig.builder()
			.withCosmosClient(cosmosAsyncClient)
			.withDatabaseName(properties.getDatabaseName())
			.withContainerName(properties.getContainerName())
			.withPartitionKeyPath(properties.getPartitionKeyPath())
			.build();
	}

	@Bean
	@ConditionalOnMissingBean
	public CosmosDBChatMemoryRepository cosmosDBChatMemoryRepository(CosmosDBChatMemoryRepositoryConfig config) {
		return CosmosDBChatMemoryRepository.create(config);
	}

}
