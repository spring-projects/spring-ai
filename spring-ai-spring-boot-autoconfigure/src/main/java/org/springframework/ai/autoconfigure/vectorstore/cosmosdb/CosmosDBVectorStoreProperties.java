package org.springframework.ai.autoconfigure.vectorstore.cosmosdb;

import org.springframework.ai.autoconfigure.vectorstore.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(CosmosDBVectorStoreProperties.CONFIG_PREFIX)
public class CosmosDBVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.cosmosdb";

	private String containerName;

	private String databaseName;

	private String metadataFields;

	private int vectorStoreThoughput = 400;

	private String partitionKeyPath;

	private String endpoint;

	private String key;

	public int getVectorStoreThoughput() {
		return vectorStoreThoughput;
	}

	public void setVectorStoreThoughput(int vectorStoreThoughput) {
		this.vectorStoreThoughput = vectorStoreThoughput;
	}

	public String getMetadataFields() {
		return metadataFields;
	}

	public void setMetadataFields(String metadataFields) {
		this.metadataFields = metadataFields;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getDatabaseName() {
		return databaseName;
	}

	public void setDatabaseName(String databaseName) {
		this.databaseName = databaseName;
	}

	public String getContainerName() {
		return containerName;
	}

	public void setContainerName(String containerName) {
		this.containerName = containerName;
	}

	public String getPartitionKeyPath() {
		return partitionKeyPath;
	}

	public void setPartitionKeyPath(String partitionKeyPath) {
		this.partitionKeyPath = partitionKeyPath;
	}

}
