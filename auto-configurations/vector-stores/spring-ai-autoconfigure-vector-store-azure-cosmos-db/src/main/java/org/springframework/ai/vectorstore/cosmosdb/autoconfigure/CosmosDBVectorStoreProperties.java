/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.vectorstore.cosmosdb.autoconfigure;

import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for CosmosDB Vector Store.
 *
 * @author Theo van Kraay
 * @since 1.0.0
 */
@ConfigurationProperties(CosmosDBVectorStoreProperties.CONFIG_PREFIX)
public class CosmosDBVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.cosmosdb";

	private @Nullable String containerName;

	private @Nullable String databaseName;

	private @Nullable String metadataFields;

	private int vectorStoreThroughput = 400;

	private long vectorDimensions = 1536;

	private @Nullable String partitionKeyPath;

	private @Nullable String endpoint;

	private @Nullable String key;

	private @Nullable String connectionMode;

	public int getVectorStoreThroughput() {
		return this.vectorStoreThroughput;
	}

	public void setVectorStoreThroughput(int vectorStoreThroughput) {
		this.vectorStoreThroughput = vectorStoreThroughput;
	}

	public @Nullable String getMetadataFields() {
		return this.metadataFields;
	}

	public void setMetadataFields(@Nullable String metadataFields) {
		this.metadataFields = metadataFields;
	}

	public List<String> getMetadataFieldList() {
		return this.metadataFields != null
				? Arrays.stream(this.metadataFields.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
				: List.of();
	}

	public @Nullable String getEndpoint() {
		return this.endpoint;
	}

	public void setEndpoint(@Nullable String endpoint) {
		this.endpoint = endpoint;
	}

	public @Nullable String getKey() {
		return this.key;
	}

	public void setKey(@Nullable String key) {
		this.key = key;
	}

	public void setConnectionMode(@Nullable String connectionMode) {
		this.connectionMode = connectionMode;
	}

	public @Nullable String getConnectionMode() {
		return this.connectionMode;
	}

	public @Nullable String getDatabaseName() {
		return this.databaseName;
	}

	public void setDatabaseName(@Nullable String databaseName) {
		this.databaseName = databaseName;
	}

	public @Nullable String getContainerName() {
		return this.containerName;
	}

	public void setContainerName(@Nullable String containerName) {
		this.containerName = containerName;
	}

	public @Nullable String getPartitionKeyPath() {
		return this.partitionKeyPath;
	}

	public void setPartitionKeyPath(@Nullable String partitionKeyPath) {
		this.partitionKeyPath = partitionKeyPath;
	}

	public long getVectorDimensions() {
		return this.vectorDimensions;
	}

	public void setVectorDimensions(long vectorDimensions) {
		this.vectorDimensions = vectorDimensions;
	}

}
