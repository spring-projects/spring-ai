/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.autoconfigure.chat.memory.repository.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for S3 chat memory repository.
 *
 * @author Yuriy Bezsonov
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "spring.ai.chat.memory.repository.s3")
public class S3ChatMemoryProperties {

	/**
	 * The name of the S3 bucket where conversation data will be stored.
	 */
	private String bucketName;

	/**
	 * The prefix to use for S3 object keys. Defaults to "chat-memory".
	 */
	private String keyPrefix = "chat-memory";

	/**
	 * The AWS region to use for S3 operations. Defaults to "us-east-1".
	 */
	private String region = "us-east-1";

	/**
	 * Whether to automatically create the S3 bucket if it doesn't exist. Defaults to
	 * false.
	 */
	private boolean initializeBucket = false;

	/**
	 * S3 storage class for conversation objects. Defaults to "STANDARD". Supported
	 * values: STANDARD, STANDARD_IA, ONEZONE_IA, REDUCED_REDUNDANCY.
	 */
	private String storageClass = "STANDARD";

	public String getBucketName() {
		return this.bucketName;
	}

	public void setBucketName(String bucketName) {
		this.bucketName = bucketName;
	}

	public String getKeyPrefix() {
		return this.keyPrefix;
	}

	public void setKeyPrefix(String keyPrefix) {
		this.keyPrefix = keyPrefix;
	}

	public String getRegion() {
		return this.region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public boolean isInitializeBucket() {
		return this.initializeBucket;
	}

	public void setInitializeBucket(boolean initializeBucket) {
		this.initializeBucket = initializeBucket;
	}

	public String getStorageClass() {
		return this.storageClass;
	}

	public void setStorageClass(String storageClass) {
		this.storageClass = storageClass;
	}

}
