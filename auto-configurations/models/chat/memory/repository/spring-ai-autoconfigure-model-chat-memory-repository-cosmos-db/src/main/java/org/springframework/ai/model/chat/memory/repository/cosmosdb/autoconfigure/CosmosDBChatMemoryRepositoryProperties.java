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

import org.springframework.ai.chat.memory.repository.cosmosdb.CosmosDBChatMemoryRepositoryConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for CosmosDB Chat Memory Repository.
 *
 * @author Theo van Kraay
 * @since 1.1.0
 */
@ConfigurationProperties(CosmosDBChatMemoryRepositoryProperties.CONFIG_PREFIX)
public class CosmosDBChatMemoryRepositoryProperties {

	public static final String CONFIG_PREFIX = "spring.ai.chat.memory.repository.cosmosdb";

	/**
	 * Azure Cosmos DB endpoint URI. Required for auto-configuration.
	 */
	private String endpoint;

	/**
	 * Azure Cosmos DB primary or secondary key. If not provided, Azure Identity
	 * authentication will be used.
	 */
	private String key;

	/**
	 * Connection mode for Cosmos DB client (direct or gateway).
	 */
	private String connectionMode = "gateway";

	/**
	 * Name of the Cosmos DB database.
	 */
	private String databaseName = CosmosDBChatMemoryRepositoryConfig.DEFAULT_DATABASE_NAME;

	/**
	 * Name of the Cosmos DB container.
	 */
	private String containerName = CosmosDBChatMemoryRepositoryConfig.DEFAULT_CONTAINER_NAME;

	/**
	 * Partition key path for the container.
	 */
	private String partitionKeyPath = CosmosDBChatMemoryRepositoryConfig.DEFAULT_PARTITION_KEY_PATH;

	public String getEndpoint() {
		return this.endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getKey() {
		return this.key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getConnectionMode() {
		return this.connectionMode;
	}

	public void setConnectionMode(String connectionMode) {
		this.connectionMode = connectionMode;
	}

	public String getDatabaseName() {
		return this.databaseName;
	}

	public void setDatabaseName(String databaseName) {
		this.databaseName = databaseName;
	}

	public String getContainerName() {
		return this.containerName;
	}

	public void setContainerName(String containerName) {
		this.containerName = containerName;
	}

	public String getPartitionKeyPath() {
		return this.partitionKeyPath;
	}

	public void setPartitionKeyPath(String partitionKeyPath) {
		this.partitionKeyPath = partitionKeyPath;
	}

}

