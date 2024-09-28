package org.springframework.ai.vectorstore;

public  class CosmosDBVectorStoreConfig implements AutoCloseable {
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

	public String getContainerName() {
		return containerName;
	}

	public void setContainerName(String containerName) {
		this.containerName = containerName;
	}

	public String getDatabaseName() {
		return databaseName;
	}

	public void setDatabaseName(String databaseName) {
		this.databaseName = databaseName;
	}

	public String getPartitionKeyPath() {
		return partitionKeyPath;
	}

	public void setPartitionKeyPath(String partitionKeyPath) {
		this.partitionKeyPath = partitionKeyPath;
	}

	@Override
	public void close() throws Exception {

	}
}
