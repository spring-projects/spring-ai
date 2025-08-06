/*
 * Copyright 2025-2026 the original author or authors.
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
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public String getVectorBucketName() {
		return this.vectorBucketName;
	}

	public void setVectorBucketName(String vectorBucketName) {
		this.vectorBucketName = vectorBucketName;
	}

}
