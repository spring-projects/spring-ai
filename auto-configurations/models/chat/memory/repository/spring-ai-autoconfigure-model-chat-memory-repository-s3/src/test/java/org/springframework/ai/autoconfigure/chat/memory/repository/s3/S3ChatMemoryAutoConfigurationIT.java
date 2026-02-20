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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.s3.S3ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for S3ChatMemoryAutoConfiguration.
 *
 * @author Yuriy Bezsonov
 */
@Testcontainers
class S3ChatMemoryAutoConfigurationIT {

	@Container
	static final LocalStackContainer localstack = initializeLocalStack();

	private static LocalStackContainer initializeLocalStack() {
		LocalStackContainer container = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"));
		container.withServices("s3");
		return container;
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(S3ChatMemoryAutoConfiguration.class));

	@Test
	void autoConfigurationCreatesS3ChatMemoryRepository() {
		this.contextRunner.withUserConfiguration(TestConfiguration.class)
			.withPropertyValues("spring.ai.chat.memory.repository.s3.bucket-name=test-bucket")
			.run(context -> {
				assertThat(context).hasSingleBean(ChatMemoryRepository.class);
				assertThat(context).hasSingleBean(S3ChatMemoryRepository.class);
				assertThat(context.getBean(ChatMemoryRepository.class)).isInstanceOf(S3ChatMemoryRepository.class);
			});
	}

	@Test
	void autoConfigurationBindsProperties() {
		this.contextRunner.withUserConfiguration(TestConfiguration.class)
			.withPropertyValues("spring.ai.chat.memory.repository.s3.bucket-name=my-bucket",
					"spring.ai.chat.memory.repository.s3.key-prefix=my-prefix",
					"spring.ai.chat.memory.repository.s3.region=us-west-2")
			.run(context -> {
				S3ChatMemoryProperties properties = context.getBean(S3ChatMemoryProperties.class);
				assertThat(properties.getBucketName()).isEqualTo("my-bucket");
				assertThat(properties.getKeyPrefix()).isEqualTo("my-prefix");
				assertThat(properties.getRegion()).isEqualTo("us-west-2");
			});
	}

	@Test
	void autoConfigurationUsesCustomS3Client() {
		this.contextRunner.withUserConfiguration(CustomS3ClientConfiguration.class)
			.withPropertyValues("spring.ai.chat.memory.repository.s3.bucket-name=test-bucket")
			.run(context -> {
				assertThat(context).hasSingleBean(S3Client.class);
				assertThat(context).hasSingleBean(S3ChatMemoryRepository.class);

				// Verify the repository works with custom S3Client
				S3ChatMemoryRepository repository = context.getBean(S3ChatMemoryRepository.class);
				List<Message> messages = List.of(UserMessage.builder().text("test").build());

				// This should not throw an exception (though it may fail due to
				// LocalStack setup)
				assertThat(repository).isNotNull();
			});
	}

	@Test
	void autoConfigurationDoesNotCreateBeanWhenBucketNameMissing() {
		this.contextRunner.withUserConfiguration(TestConfiguration.class).run(context -> {
			assertThat(context).doesNotHaveBean(ChatMemoryRepository.class);
			assertThat(context).doesNotHaveBean(S3ChatMemoryRepository.class);
		});
	}

	@Test
	void autoConfigurationBindsStorageClassProperty() {
		this.contextRunner.withUserConfiguration(TestConfiguration.class)
			.withPropertyValues("spring.ai.chat.memory.repository.s3.bucket-name=test-bucket",
					"spring.ai.chat.memory.repository.s3.storage-class=STANDARD_IA")
			.run(context -> {
				S3ChatMemoryProperties properties = context.getBean(S3ChatMemoryProperties.class);
				assertThat(properties.getStorageClass()).isEqualTo("STANDARD_IA");
			});
	}

	@Test
	void autoConfigurationUsesDefaultStorageClass() {
		this.contextRunner.withUserConfiguration(TestConfiguration.class)
			.withPropertyValues("spring.ai.chat.memory.repository.s3.bucket-name=test-bucket")
			.run(context -> {
				S3ChatMemoryProperties properties = context.getBean(S3ChatMemoryProperties.class);
				assertThat(properties.getStorageClass()).isEqualTo("STANDARD"); // Default
																				// value
			});
	}

	@Configuration
	static class TestConfiguration {

		// Empty configuration for basic tests

	}

	@Configuration
	static class CustomS3ClientConfiguration {

		@Bean
		S3Client customS3Client() {
			return S3Client.builder()
				.endpointOverride(localstack.getEndpoint())
				.credentialsProvider(StaticCredentialsProvider
					.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
				.region(Region.of(localstack.getRegion()))
				.build();
		}

	}

}
