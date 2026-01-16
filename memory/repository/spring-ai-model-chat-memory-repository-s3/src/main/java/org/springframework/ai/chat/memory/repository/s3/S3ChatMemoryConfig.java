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

import org.jspecify.annotations.Nullable;
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

	/** Default key prefix for S3 objects. */
	public static final String DEFAULT_KEY_PREFIX = "chat-memory";

	/** Default storage class for S3 objects. */
	public static final StorageClass DEFAULT_STORAGE_CLASS = StorageClass.STANDARD;

	/** The S3 client for operations. */
	private final S3Client s3Client;

	/** The S3 bucket name. */
	private final String bucketName;

	/** The key prefix for S3 objects. */
	private final String keyPrefix;

	/** Whether to initialize the bucket if it doesn't exist. */
	private final boolean initializeBucket;

	/** The storage class for S3 objects. */
	private final StorageClass storageClass;

	private S3ChatMemoryConfig(final Builder builder) {
		Assert.notNull(builder.s3Client, "s3Client cannot be null");
		Assert.hasText(builder.bucketName, "bucketName cannot be null or empty");

		this.s3Client = builder.s3Client;
		this.bucketName = builder.bucketName;
		this.keyPrefix = builder.keyPrefix != null ? builder.keyPrefix : DEFAULT_KEY_PREFIX;
		this.initializeBucket = builder.initializeBucket;

		this.storageClass = builder.storageClass != null ? builder.storageClass : DEFAULT_STORAGE_CLASS;
	}

	/**
	 * Gets the S3 client.
	 * @return the S3 client
	 */
	public S3Client getS3Client() {
		return this.s3Client;
	}

	/**
	 * Gets the bucket name.
	 * @return the bucket name
	 */
	public String getBucketName() {
		return this.bucketName;
	}

	/**
	 * Gets the key prefix.
	 * @return the key prefix
	 */
	public String getKeyPrefix() {
		return this.keyPrefix;
	}

	/**
	 * Checks if bucket initialization is enabled.
	 * @return true if bucket should be initialized
	 */
	public boolean isInitializeBucket() {
		return this.initializeBucket;
	}

	/**
	 * Gets the storage class.
	 * @return the storage class
	 */
	public StorageClass getStorageClass() {
		return this.storageClass;
	}

	/**
	 * Creates a new builder.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for S3ChatMemoryConfig.
	 */
	public static final class Builder {

		/** The S3 client. */
		private @Nullable S3Client s3Client;

		/** The bucket name. */
		private @Nullable String bucketName;

		/** The key prefix. */
		private @Nullable String keyPrefix;

		/** Whether to initialize bucket. */
		private boolean initializeBucket = false;

		/** The storage class. */
		private @Nullable StorageClass storageClass;

		/**
		 * Private constructor.
		 */
		private Builder() {
		}

		/**
		 * Sets the S3 client.
		 * @param client the S3 client
		 * @return this builder
		 */
		public Builder s3Client(final S3Client client) {
			this.s3Client = client;
			return this;
		}

		/**
		 * Sets the bucket name.
		 * @param name the bucket name
		 * @return this builder
		 */
		public Builder bucketName(final String name) {
			this.bucketName = name;
			return this;
		}

		/**
		 * Sets the key prefix.
		 * @param prefix the key prefix
		 * @return this builder
		 */
		public Builder keyPrefix(final String prefix) {
			this.keyPrefix = prefix;
			return this;
		}

		/**
		 * Sets whether to initialize bucket.
		 * @param initialize true to initialize bucket
		 * @return this builder
		 */
		public Builder initializeBucket(final boolean initialize) {
			this.initializeBucket = initialize;
			return this;
		}

		/**
		 * Sets the storage class.
		 * @param storage the storage class
		 * @return this builder
		 */
		public Builder storageClass(final StorageClass storage) {
			this.storageClass = storage;
			return this;
		}

		/**
		 * Builds the configuration.
		 * @return the S3ChatMemoryConfig instance
		 */
		public S3ChatMemoryConfig build() {
			return new S3ChatMemoryConfig(this);
		}

	}

}
