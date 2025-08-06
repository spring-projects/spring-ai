package org.springframework.ai.vectorstore.s3.autoconfigure;


import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * @author Matej Nedic
 */
@ConfigurationProperties(prefix = S3VectorStoreProperties.CONFIG_PREFIX)
public class S3VectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.s3";

	private String indexName;

	private String vectorBucketName;

	public String getIndexName() {
		return indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public String getVectorBucketName() {
		return vectorBucketName;
	}

	public void setVectorBucketName(String vectorBucketName) {
		this.vectorBucketName = vectorBucketName;
	}
}
