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

package org.springframework.ai.chat.memory.repository.s3;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.StorageClass;

import org.springframework.util.Assert;

/**
 * Configuration class for S3ChatMemoryRepository.
 *
 * @author Yuriy Bezsonov
 * @since 2.0.0
 */
public final class S3ChatMemoryConfig {

	public static final String DEFAULT_KEY_PREFIX = "chat-memory";

	public static final StorageClass DEFAULT_STORAGE_CLASS = StorageClass.STANDARD;

	private final S3Client s3Client;

	private final String bucketName;

	private final String keyPrefix;

	private final boolean initializeBucket;

	private final StorageClass storageClass;

	private S3ChatMemoryConfig(Builder builder) {
		Assert.notNull(builder.s3Client, "s3Client cannot be null");
		Assert.hasText(builder.bucketName, "bucketName cannot be null or empty");

		this.s3Client = builder.s3Client;
		this.bucketName = builder.bucketName;
		this.keyPrefix = builder.keyPrefix != null ? builder.keyPrefix : DEFAULT_KEY_PREFIX;
		this.initializeBucket = builder.initializeBucket;

		this.storageClass = builder.storageClass != null ? builder.storageClass : DEFAULT_STORAGE_CLASS;
	}

	public S3Client getS3Client() {
		return this.s3Client;
	}

	public String getBucketName() {
		return this.bucketName;
	}

	public String getKeyPrefix() {
		return this.keyPrefix;
	}

	public boolean isInitializeBucket() {
		return this.initializeBucket;
	}

	public StorageClass getStorageClass() {
		return this.storageClass;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private S3Client s3Client;

		private String bucketName;

		private String keyPrefix;

		private boolean initializeBucket = false;

		private StorageClass storageClass;

		private Builder() {
		}

		public Builder s3Client(S3Client s3Client) {
			this.s3Client = s3Client;
			return this;
		}

		public Builder bucketName(String bucketName) {
			this.bucketName = bucketName;
			return this;
		}

		public Builder keyPrefix(String keyPrefix) {
			this.keyPrefix = keyPrefix;
			return this;
		}

		public Builder initializeBucket(boolean initializeBucket) {
			this.initializeBucket = initializeBucket;
			return this;
		}

		public Builder storageClass(StorageClass storageClass) {
			this.storageClass = storageClass;
			return this;
		}

		public S3ChatMemoryConfig build() {
			return new S3ChatMemoryConfig(this);
		}

	}

}
