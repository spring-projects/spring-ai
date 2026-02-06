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

import org.jspecify.annotations.Nullable;

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
	private @Nullable String bucketName;

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

	/**
	 * Gets the S3 bucket name.
	 * @return the bucket name
	 */
	public @Nullable String getBucketName() {
		return this.bucketName;
	}

	/**
	 * Sets the S3 bucket name.
	 * @param name the bucket name to set
	 */
	public void setBucketName(final String name) {
		this.bucketName = name;
	}

	/**
	 * Gets the S3 key prefix.
	 * @return the key prefix
	 */
	public String getKeyPrefix() {
		return this.keyPrefix;
	}

	/**
	 * Sets the S3 key prefix.
	 * @param prefix the key prefix to set
	 */
	public void setKeyPrefix(final String prefix) {
		this.keyPrefix = prefix;
	}

	/**
	 * Gets the AWS region.
	 * @return the region
	 */
	public String getRegion() {
		return this.region;
	}

	/**
	 * Sets the AWS region.
	 * @param awsRegion the region to set
	 */
	public void setRegion(final String awsRegion) {
		this.region = awsRegion;
	}

	/**
	 * Gets whether to initialize bucket.
	 * @return true if bucket should be initialized
	 */
	public boolean isInitializeBucket() {
		return this.initializeBucket;
	}

	/**
	 * Sets whether to initialize bucket.
	 * @param initialize true to initialize bucket
	 */
	public void setInitializeBucket(final boolean initialize) {
		this.initializeBucket = initialize;
	}

	/**
	 * Gets the storage class.
	 * @return the storage class
	 */
	public String getStorageClass() {
		return this.storageClass;
	}

	/**
	 * Sets the storage class.
	 * @param storage the storage class to set
	 */
	public void setStorageClass(final String storage) {
		this.storageClass = storage;
	}

}
