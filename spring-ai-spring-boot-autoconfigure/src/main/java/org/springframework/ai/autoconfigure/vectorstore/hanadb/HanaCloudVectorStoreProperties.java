package org.springframework.ai.autoconfigure.vectorstore.hanadb;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Rahul Mittal
 * @since 1.0.0
 */
@ConfigurationProperties(HanaCloudVectorStoreProperties.CONFIG_PREFIX)
public class HanaCloudVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.hanadb";

	private String tableName;

	private int topK;

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public int getTopK() {
		return topK;
	}

	public void setTopK(int topK) {
		this.topK = topK;
	}

}
