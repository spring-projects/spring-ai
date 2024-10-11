package org.springframework.ai.vectorstore;

import java.util.List;

public class CosmosDBVectorStoreConfig implements AutoCloseable {

	private String containerName;

	private String databaseName;

	private String partitionKeyPath;

	private String endpoint;

	private String key;

	private String metadataFields;

	private int vectorStoreThoughput = 400;

	private long vectorDimensions = 1536;

	private List<String> metadataFieldsList;

	public int getVectorStoreThoughput() {
		return vectorStoreThoughput;
	}

	public void setVectorStoreThoughput(int vectorStoreThoughput) {
		this.vectorStoreThoughput = vectorStoreThoughput;
	}

	public void setMetadataFields(String metadataFields) {
		this.metadataFields = metadataFields;
		this.metadataFieldsList = List.of(metadataFields.split(","));
	}

	public String getMetadataFields() {
		return metadataFields;
	}

	public List<String> getMetadataFieldsList() {
		return metadataFieldsList;
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

	public long getVectorDimensions() {
		return vectorDimensions;
	}

	public void setVectorDimensions(long vectorDimensions) {
		this.vectorDimensions = vectorDimensions;
	}
}
