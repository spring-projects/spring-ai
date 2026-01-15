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

package org.springframework.ai.chat.memory.repository.cosmosdb;

import java.util.Objects;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Configuration for the CosmosDB Chat Memory store.
 *
 * @author Theo van Kraay
 * @since 1.1.0
 */
public final class CosmosDBChatMemoryRepositoryConfig {

	public static final String DEFAULT_DATABASE_NAME = "springai";

	public static final String DEFAULT_CONTAINER_NAME = "chat_memory";

	public static final String DEFAULT_PARTITION_KEY_PATH = "/conversationId";

	private final CosmosAsyncClient cosmosClient;

	private final String databaseName;

	private final String containerName;

	private final String partitionKeyPath;

	private CosmosAsyncContainer container;

	private CosmosDBChatMemoryRepositoryConfig(Builder builder) {
		this.cosmosClient = Objects.requireNonNull(builder.cosmosClient);
		this.databaseName = builder.databaseName;
		this.containerName = builder.containerName;
		this.partitionKeyPath = builder.partitionKeyPath;
		this.initializeContainer();
	}

	public static Builder builder() {
		return new Builder();
	}

	public CosmosAsyncContainer getContainer() {
		return this.container;
	}

	public String getDatabaseName() {
		return this.databaseName;
	}

	public String getContainerName() {
		return this.containerName;
	}

	public String getPartitionKeyPath() {
		return this.partitionKeyPath;
	}

	private void initializeContainer() {
		// Create database if it doesn't exist
		this.cosmosClient.createDatabaseIfNotExists(this.databaseName).block();
		CosmosAsyncDatabase database = this.cosmosClient.getDatabase(this.databaseName);

		// Create container if it doesn't exist
		database.createContainerIfNotExists(this.containerName, this.partitionKeyPath).block();
		this.container = database.getContainer(this.containerName);
	}

	public static final class Builder {

		private @Nullable CosmosAsyncClient cosmosClient;

		private String databaseName = DEFAULT_DATABASE_NAME;

		private String containerName = DEFAULT_CONTAINER_NAME;

		private String partitionKeyPath = DEFAULT_PARTITION_KEY_PATH;

		private Builder() {
		}

		public Builder withCosmosClient(CosmosAsyncClient cosmosClient) {
			this.cosmosClient = cosmosClient;
			return this;
		}

		public Builder withDatabaseName(String databaseName) {
			this.databaseName = databaseName;
			return this;
		}

		public Builder withContainerName(String containerName) {
			this.containerName = containerName;
			return this;
		}

		public Builder withPartitionKeyPath(String partitionKeyPath) {
			this.partitionKeyPath = partitionKeyPath;
			return this;
		}

		public CosmosDBChatMemoryRepositoryConfig build() {
			Assert.notNull(this.cosmosClient, "CosmosAsyncClient cannot be null");
			Assert.hasText(this.databaseName, "databaseName cannot be null or empty");
			Assert.hasText(this.containerName, "containerName cannot be null or empty");
			Assert.hasText(this.partitionKeyPath, "partitionKeyPath cannot be null or empty");

			return new CosmosDBChatMemoryRepositoryConfig(this);
		}

	}

}
