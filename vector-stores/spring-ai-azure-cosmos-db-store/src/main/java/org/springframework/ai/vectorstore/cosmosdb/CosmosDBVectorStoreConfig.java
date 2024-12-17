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

package org.springframework.ai.vectorstore.cosmosdb;

import java.util.List;

/**
 * Configuration properties for a CosmosDB vector store.
 *
 * @author Theo van Kraay
 * @since 1.0.0
 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
 */
@Deprecated(since = "1.0.0-M5", forRemoval = true)
public class CosmosDBVectorStoreConfig implements AutoCloseable {

	private String containerName;

	private String databaseName;

	private String partitionKeyPath;

	private String endpoint;

	private String key;

	private String metadataFields;

	private int vectorStoreThroughput = 400;

	private long vectorDimensions = 1536;

	private List<String> metadataFieldsList;

	/**
	 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public int getVectorStoreThroughput() {
		return this.vectorStoreThroughput;
	}

	/**
	 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public void setVectorStoreThroughput(int vectorStoreThroughput) {
		this.vectorStoreThroughput = vectorStoreThroughput;
	}

	/**
	 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public String getMetadataFields() {
		return this.metadataFields;
	}

	/**
	 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public void setMetadataFields(String metadataFields) {
		this.metadataFields = metadataFields;
		this.metadataFieldsList = List.of(metadataFields.split(","));
	}

	/**
	 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public List<String> getMetadataFieldsList() {
		return this.metadataFieldsList;
	}

	/**
	 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public String getEndpoint() {
		return this.endpoint;
	}

	/**
	 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	/**
	 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public String getKey() {
		return this.key;
	}

	/**
	 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public void setKey(String key) {
		this.key = key;
	}

	/**
	 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public String getContainerName() {
		return this.containerName;
	}

	/**
	 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public void setContainerName(String containerName) {
		this.containerName = containerName;
	}

	/**
	 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public String getDatabaseName() {
		return this.databaseName;
	}

	/**
	 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public void setDatabaseName(String databaseName) {
		this.databaseName = databaseName;
	}

	/**
	 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public String getPartitionKeyPath() {
		return this.partitionKeyPath;
	}

	/**
	 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public void setPartitionKeyPath(String partitionKeyPath) {
		this.partitionKeyPath = partitionKeyPath;
	}

	@Override
	public void close() throws Exception {

	}

	/**
	 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public long getVectorDimensions() {
		return this.vectorDimensions;
	}

	/**
	 * @deprecated Since 1.0.0-M5, use {@link CosmosDBVectorStore#builder()} instead
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public void setVectorDimensions(long vectorDimensions) {
		this.vectorDimensions = vectorDimensions;
	}

}
