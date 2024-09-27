package org.springframework.ai.autoconfigure.vectorstore.cosmosdb;

import org.springframework.ai.autoconfigure.vectorstore.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(CosmosDBVectorStoreProperties.CONFIG_PREFIX)
public class CosmosDBVectorStoreProperties extends CommonVectorStoreProperties {

    public static final String CONFIG_PREFIX = "spring.ai.vectorstore.cosmosdb";

    private String containerName;
	private String databaseName;
    private String partitionKeyPath;
	private String endpoint;
	private String key;

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
		return containerName;
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
