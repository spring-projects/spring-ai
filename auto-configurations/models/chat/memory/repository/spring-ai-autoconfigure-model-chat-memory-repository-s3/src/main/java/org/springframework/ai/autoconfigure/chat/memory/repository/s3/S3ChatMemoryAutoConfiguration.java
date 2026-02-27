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

import java.net.URI;
import java.util.Objects;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.StorageClass;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.s3.S3ChatMemoryRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration for S3 chat memory repository.
 *
 * @author Yuriy Bezsonov
 * @since 2.0.0
 */
@AutoConfiguration
@ConditionalOnClass({ S3Client.class, ChatMemoryRepository.class })
@EnableConfigurationProperties(S3ChatMemoryProperties.class)
@ConditionalOnProperty(prefix = "spring.ai.chat.memory.repository.s3", name = "bucket-name")
public class S3ChatMemoryAutoConfiguration {

	/**
	 * Creates an S3Client bean if one is not already present.
	 * @param properties the S3 chat memory properties
	 * @return configured S3Client
	 */
	@Bean
	@ConditionalOnMissingBean
	public S3Client s3Client(final S3ChatMemoryProperties properties) {
		S3ClientBuilder builder = S3Client.builder();

		// Set region
		if (StringUtils.hasText(properties.getRegion())) {
			builder.region(Region.of(properties.getRegion()));
		}

		// Support for custom endpoint (useful for S3-compatible services
		// like MinIO)
		String endpoint = System.getProperty("spring.ai.chat.memory.repository.s3.endpoint");
		if (StringUtils.hasText(endpoint)) {
			builder.endpointOverride(URI.create(endpoint));
		}

		return builder.build();
	}

	/**
	 * Creates an S3ChatMemoryRepository bean if one is not already present.
	 * @param s3Client the S3 client
	 * @param properties the S3 chat memory properties
	 * @return configured S3ChatMemoryRepository
	 */
	@Bean
	@ConditionalOnMissingBean({ S3ChatMemoryRepository.class, ChatMemory.class, ChatMemoryRepository.class })
	public S3ChatMemoryRepository s3ChatMemoryRepository(final S3Client s3Client,
			final S3ChatMemoryProperties properties) {
		StorageClass storageClass = StorageClass.fromValue(properties.getStorageClass());

		return S3ChatMemoryRepository.builder()
			.s3Client(s3Client)
			.bucketName(Objects.requireNonNull(properties.getBucketName()))
			.keyPrefix(properties.getKeyPrefix())
			.initializeBucket(properties.isInitializeBucket())
			.storageClass(storageClass)
			.build();
	}

}
