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

package org.springframework.ai.autoconfigure.vectorstore.cosmosdb;

import org.springframework.ai.autoconfigure.vectorstore.CommonVectorStoreProperties;
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

	private String containerName;

	private String databaseName;

	private String metadataFields;

	private int vectorStoreThroughput = 400;

	private long vectorDimensions = 1536;

	private String partitionKeyPath;

	private String endpoint;

	private String key;

	public int getVectorStoreThroughput() {
		return this.vectorStoreThroughput;
	}

	public void setVectorStoreThroughput(int vectorStoreThroughput) {
		this.vectorStoreThroughput = vectorStoreThroughput;
	}

	public String getMetadataFields() {
		return this.metadataFields;
	}

	public void setMetadataFields(String metadataFields) {
		this.metadataFields = metadataFields;
	}

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

	public long getVectorDimensions() {
		return this.vectorDimensions;
	}

	public void setVectorDimensions(long vectorDimensions) {
		this.vectorDimensions = vectorDimensions;
	}

}
