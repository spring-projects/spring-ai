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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for S3ChatMemoryRepository using LocalStack.
 *
 * @author Yuriy Bezsonov
 */
@Testcontainers
class S3ChatMemoryRepositoryIT {

	private static final String BUCKET_NAME = "test-chat-memory";

	@Container
	static final LocalStackContainer localstack = initializeLocalStack();

	private static LocalStackContainer initializeLocalStack() {
		LocalStackContainer container = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"));
		container.withServices("s3");
		return container;
	}

	private S3Client s3Client;

	private S3ChatMemoryRepository repository;

	@BeforeEach
	void setUp() throws InterruptedException {
		// Wait a bit for LocalStack to be fully ready
		Thread.sleep(1000);

		// Create S3 client pointing to LocalStack with path-style access
		s3Client = S3Client.builder()
			.endpointOverride(localstack.getEndpoint())
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
			.region(Region.of(localstack.getRegion()))
			.forcePathStyle(true) // Required for LocalStack
			.build();

		// Create bucket with retry logic
		boolean bucketCreated = false;
		int attempts = 0;
		while (!bucketCreated && attempts < 5) {
			try {
				s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
				bucketCreated = true;
			}
			catch (software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException e) {
				// Bucket already exists, which is fine
				bucketCreated = true;
			}
			catch (software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException e) {
				// Bucket already owned by us, which is fine
				bucketCreated = true;
			}
			catch (Exception e) {
				attempts++;
				if (attempts >= 5) {
					throw new RuntimeException("Failed to create bucket after 5 attempts", e);
				}
				Thread.sleep(1000); // Wait 1 second before retry
			}
		}

		// Create repository with unique prefix for each test
		String uniquePrefix = "test-" + System.currentTimeMillis();

		repository = S3ChatMemoryRepository.builder()
			.s3Client(s3Client)
			.bucketName(BUCKET_NAME)
			.keyPrefix(uniquePrefix)

			.build();
	}

	@Test
	void testFullCrudOperations() {
		// Given: A conversation with messages
		String conversationId = "test-conversation-1";
		List<Message> messages = List.of(UserMessage.builder().text("Hello").build(),
				AssistantMessage.builder().content("Hi there!").build(),
				SystemMessage.builder().text("System message").build());

		// When: Saving messages
		repository.saveAll(conversationId, messages);

		// Then: Messages can be retrieved
		List<Message> retrieved = repository.findByConversationId(conversationId);
		assertThat(retrieved).hasSize(3);
		assertThat(retrieved.get(0).getText()).isEqualTo("Hello");
		assertThat(retrieved.get(1).getText()).isEqualTo("Hi there!");
		assertThat(retrieved.get(2).getText()).isEqualTo("System message");

		// And: Conversation ID appears in list
		List<String> conversationIds = repository.findConversationIds();
		assertThat(conversationIds).contains(conversationId);

		// When: Deleting conversation
		repository.deleteByConversationId(conversationId);

		// Then: Conversation no longer exists
		List<Message> afterDelete = repository.findByConversationId(conversationId);
		assertThat(afterDelete).isEmpty();

		List<String> idsAfterDelete = repository.findConversationIds();
		assertThat(idsAfterDelete).doesNotContain(conversationId);
	}

	@Test
	void testMultipleConversations() {
		// Given: Multiple conversations
		for (int i = 1; i <= 5; i++) {
			String conversationId = "conversation-" + i;
			List<Message> messages = List.of(UserMessage.builder().text("Message " + i).build());
			repository.saveAll(conversationId, messages);
		}

		// When: Listing all conversations
		List<String> conversationIds = repository.findConversationIds();

		// Then: All conversations are present
		assertThat(conversationIds).hasSize(5);
		assertThat(conversationIds).contains("conversation-1", "conversation-2", "conversation-3", "conversation-4",
				"conversation-5");
	}

	@Test
	void testPaginationWithLargeDataset() {
		// Given: Many conversations (more than typical page size)
		int conversationCount = 25;
		for (int i = 1; i <= conversationCount; i++) {
			String conversationId = "conv-" + i;
			List<Message> messages = List.of(UserMessage.builder().text("Test message " + i).build());
			repository.saveAll(conversationId, messages);
		}

		// When: Listing all conversations
		List<String> conversationIds = repository.findConversationIds();

		// Then: All conversations are returned (pagination handled internally)
		assertThat(conversationIds).hasSize(conversationCount);
	}

	@Test
	void testConversationReplacement() {
		// Given: A conversation with initial messages
		String conversationId = "replacement-test";
		List<Message> initialMessages = List.of(UserMessage.builder().text("Initial message").build());
		repository.saveAll(conversationId, initialMessages);

		// When: Replacing with new messages
		List<Message> newMessages = List.of(UserMessage.builder().text("New message 1").build(),
				UserMessage.builder().text("New message 2").build());
		repository.saveAll(conversationId, newMessages);

		// Then: Only new messages are present
		List<Message> retrieved = repository.findByConversationId(conversationId);
		assertThat(retrieved).hasSize(2);
		assertThat(retrieved.get(0).getText()).isEqualTo("New message 1");
		assertThat(retrieved.get(1).getText()).isEqualTo("New message 2");
	}

	@Test
	void testEmptyMessageListDeletesConversation() {
		// Given: A conversation with messages
		String conversationId = "empty-test";
		List<Message> messages = List.of(UserMessage.builder().text("Test").build());
		repository.saveAll(conversationId, messages);

		// When: Saving empty message list
		repository.saveAll(conversationId, List.of());

		// Then: Conversation is deleted
		List<Message> retrieved = repository.findByConversationId(conversationId);
		assertThat(retrieved).isEmpty();

		List<String> conversationIds = repository.findConversationIds();
		assertThat(conversationIds).doesNotContain(conversationId);
	}

	@Test
	void testNonExistentConversation() {
		// When: Retrieving non-existent conversation
		List<Message> messages = repository.findByConversationId("non-existent");

		// Then: Empty list is returned
		assertThat(messages).isEmpty();
	}

	@Test
	void testMessageMetadataPreservation() {
		// Given: Messages with metadata
		String conversationId = "metadata-test";
		List<Message> messages = List.of(
				UserMessage.builder()
					.text("User message")
					.metadata(java.util.Map.of("key1", "value1", "key2", 42))
					.build(),
				AssistantMessage.builder()
					.content("Assistant message")
					.properties(java.util.Map.of("model", "gpt-4", "temperature", 0.7))
					.build());

		// When: Saving and retrieving
		repository.saveAll(conversationId, messages);
		List<Message> retrieved = repository.findByConversationId(conversationId);

		// Then: Metadata is preserved
		assertThat(retrieved).hasSize(2);
		assertThat(retrieved.get(0).getMetadata()).containsEntry("key1", "value1");
		assertThat(retrieved.get(0).getMetadata()).containsEntry("key2", 42);
		assertThat(retrieved.get(1).getMetadata()).containsEntry("model", "gpt-4");
	}

	@Test
	void testMessageOrderPreservation() {
		// Given: Messages in specific order
		String conversationId = "order-test";
		List<Message> messages = new ArrayList<>();
		for (int i = 1; i <= 10; i++) {
			messages.add(UserMessage.builder().text("Message " + i).build());
		}

		// When: Saving and retrieving
		repository.saveAll(conversationId, messages);
		List<Message> retrieved = repository.findByConversationId(conversationId);

		// Then: Order is preserved
		assertThat(retrieved).hasSize(10);
		for (int i = 0; i < 10; i++) {
			assertThat(retrieved.get(i).getText()).isEqualTo("Message " + (i + 1));
		}
	}

	@Test
	void testStorageClassConfigurationIntegration() {
		// Given: Repository with custom storage class
		String uniquePrefix = "storage-test-" + System.currentTimeMillis();
		S3ChatMemoryRepository storageRepository = S3ChatMemoryRepository.builder()
			.s3Client(s3Client)
			.bucketName(BUCKET_NAME)
			.keyPrefix(uniquePrefix)
			.storageClass(software.amazon.awssdk.services.s3.model.StorageClass.STANDARD_IA)
			.build();

		String conversationId = "storage-conversation";

		// When: Saving messages with custom storage class
		List<Message> messages = List.of(UserMessage.builder().text("Test message").build());
		storageRepository.saveAll(conversationId, messages);

		// Then: Messages should be saved and retrievable (storage class is applied during
		// save)
		List<Message> retrieved = storageRepository.findByConversationId(conversationId);
		assertThat(retrieved).hasSize(1);
		assertThat(retrieved.get(0).getText()).isEqualTo("Test message");
	}

}
