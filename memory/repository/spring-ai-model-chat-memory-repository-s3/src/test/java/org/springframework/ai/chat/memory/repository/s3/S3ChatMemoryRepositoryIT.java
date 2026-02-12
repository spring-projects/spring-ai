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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
	void setUp() {
		// Create S3 client pointing to LocalStack with path-style access
		this.s3Client = S3Client.builder()
			.endpointOverride(localstack.getEndpoint())
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
			.region(Region.of(localstack.getRegion()))
			.forcePathStyle(true) // Required for LocalStack
			.build();

		// Create bucket if it doesn't exist
		try {
			this.s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
		}
		catch (software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException
				| software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException e) {
			// Bucket already exists, which is fine
		}

		// Create this.repository with unique prefix for each test
		String uniquePrefix = "test-" + System.currentTimeMillis();

		this.repository = S3ChatMemoryRepository.builder()
			.s3Client(this.s3Client)
			.bucketName(BUCKET_NAME)
			.keyPrefix(uniquePrefix)
			.build();
	}

	@AfterEach
	void tearDown() {
		if (this.s3Client != null) {
			this.s3Client.close();
		}
	}

	@Test
	void testFullCrudOperations() {
		// Given: A conversation with messages
		String conversationId = "test-conversation-1";
		List<Message> messages = List.of(UserMessage.builder().text("Hello").build(),
				AssistantMessage.builder().content("Hi there!").build(),
				SystemMessage.builder().text("System message").build());

		// When: Saving messages
		this.repository.saveAll(conversationId, messages);

		// Then: Messages can be retrieved
		List<Message> retrieved = this.repository.findByConversationId(conversationId);
		assertThat(retrieved).hasSize(3);
		assertThat(retrieved.get(0).getText()).isEqualTo("Hello");
		assertThat(retrieved.get(1).getText()).isEqualTo("Hi there!");
		assertThat(retrieved.get(2).getText()).isEqualTo("System message");

		// And: Conversation ID appears in list
		List<String> conversationIds = this.repository.findConversationIds();
		assertThat(conversationIds).contains(conversationId);

		// When: Deleting conversation
		this.repository.deleteByConversationId(conversationId);

		// Then: Conversation no longer exists
		List<Message> afterDelete = this.repository.findByConversationId(conversationId);
		assertThat(afterDelete).isEmpty();

		List<String> idsAfterDelete = this.repository.findConversationIds();
		assertThat(idsAfterDelete).doesNotContain(conversationId);
	}

	@Test
	void testMultipleConversations() {
		// Given: Multiple conversations
		for (int i = 1; i <= 5; i++) {
			String conversationId = "conversation-" + i;
			List<Message> messages = List.of(UserMessage.builder().text("Message " + i).build());
			this.repository.saveAll(conversationId, messages);
		}

		// When: Listing all conversations
		List<String> conversationIds = this.repository.findConversationIds();

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
			this.repository.saveAll(conversationId, messages);
		}

		// When: Listing all conversations
		List<String> conversationIds = this.repository.findConversationIds();

		// Then: All conversations are returned (pagination handled internally)
		assertThat(conversationIds).hasSize(conversationCount);
	}

	@Test
	void testConversationReplacement() {
		// Given: A conversation with initial messages
		String conversationId = "replacement-test";
		List<Message> initialMessages = List.of(UserMessage.builder().text("Initial message").build());
		this.repository.saveAll(conversationId, initialMessages);

		// When: Replacing with new messages
		List<Message> newMessages = List.of(UserMessage.builder().text("New message 1").build(),
				UserMessage.builder().text("New message 2").build());
		this.repository.saveAll(conversationId, newMessages);

		// Then: Only new messages are present
		List<Message> retrieved = this.repository.findByConversationId(conversationId);
		assertThat(retrieved).hasSize(2);
		assertThat(retrieved.get(0).getText()).isEqualTo("New message 1");
		assertThat(retrieved.get(1).getText()).isEqualTo("New message 2");
	}

	@Test
	void testEmptyMessageListDeletesConversation() {
		// Given: A conversation with messages
		String conversationId = "empty-test";
		List<Message> messages = List.of(UserMessage.builder().text("Test").build());
		this.repository.saveAll(conversationId, messages);

		// When: Saving empty message list
		this.repository.saveAll(conversationId, List.of());

		// Then: Conversation is deleted
		List<Message> retrieved = this.repository.findByConversationId(conversationId);
		assertThat(retrieved).isEmpty();

		List<String> conversationIds = this.repository.findConversationIds();
		assertThat(conversationIds).doesNotContain(conversationId);
	}

	@Test
	void testNonExistentConversation() {
		// When: Retrieving non-existent conversation
		List<Message> messages = this.repository.findByConversationId("non-existent");

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
		this.repository.saveAll(conversationId, messages);
		List<Message> retrieved = this.repository.findByConversationId(conversationId);

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
		this.repository.saveAll(conversationId, messages);
		List<Message> retrieved = this.repository.findByConversationId(conversationId);

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
			.s3Client(this.s3Client)
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

	@Test
	void testNullMessagesThrowsException() {
		// When/Then: Saving null messages should throw IllegalArgumentException
		assertThatThrownBy(() -> this.repository.saveAll("test-conversation", null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testSpecialCharactersInConversationId() {
		// Given: Conversation ID with special characters (URL-safe)
		String conversationId = "user-123_session-456";
		List<Message> messages = List.of(UserMessage.builder().text("Test message").build());

		// When: Saving and retrieving
		this.repository.saveAll(conversationId, messages);
		List<Message> retrieved = this.repository.findByConversationId(conversationId);

		// Then: Messages are correctly stored and retrieved
		assertThat(retrieved).hasSize(1);
		assertThat(retrieved.get(0).getText()).isEqualTo("Test message");
	}

	@Test
	void testUnicodeInMessageContent() {
		// Given: Messages with unicode content
		String conversationId = "unicode-test";
		List<Message> messages = List.of(UserMessage.builder().text("Hello 你好 مرحبا 🎉").build(),
				AssistantMessage.builder().content("Response with émojis 🚀 and spëcial çharacters").build());

		// When: Saving and retrieving
		this.repository.saveAll(conversationId, messages);
		List<Message> retrieved = this.repository.findByConversationId(conversationId);

		// Then: Unicode content is preserved
		assertThat(retrieved).hasSize(2);
		assertThat(retrieved.get(0).getText()).isEqualTo("Hello 你好 مرحبا 🎉");
		assertThat(retrieved.get(1).getText()).isEqualTo("Response with émojis 🚀 and spëcial çharacters");
	}

	@Test
	void testLargeMessageContent() {
		// Given: A message with large content
		String conversationId = "large-content-test";
		String largeContent = "x".repeat(100_000); // 100KB of content
		List<Message> messages = List.of(UserMessage.builder().text(largeContent).build());

		// When: Saving and retrieving
		this.repository.saveAll(conversationId, messages);
		List<Message> retrieved = this.repository.findByConversationId(conversationId);

		// Then: Large content is preserved
		assertThat(retrieved).hasSize(1);
		assertThat(retrieved.get(0).getText()).isEqualTo(largeContent);
	}

	@Test
	void testInitializeBucketCreatesNewBucket() {
		// Given: A repository configured to auto-create bucket
		String newBucketName = "auto-created-bucket-" + System.currentTimeMillis();
		S3ChatMemoryRepository autoInitRepository = S3ChatMemoryRepository.builder()
			.s3Client(this.s3Client)
			.bucketName(newBucketName)
			.keyPrefix("test")
			.initializeBucket(true)
			.build();

		// When: Saving messages (triggers bucket creation)
		List<Message> messages = List.of(UserMessage.builder().text("Test").build());
		autoInitRepository.saveAll("test-conversation", messages);

		// Then: Messages can be retrieved (bucket was created)
		List<Message> retrieved = autoInitRepository.findByConversationId("test-conversation");
		assertThat(retrieved).hasSize(1);
		assertThat(retrieved.get(0).getText()).isEqualTo("Test");
	}

	@Test
	void testMissingBucketWithoutInitializeThrowsException() {
		// Given: A repository with non-existent bucket and initializeBucket=false
		String nonExistentBucket = "non-existent-bucket-" + System.currentTimeMillis();
		S3ChatMemoryRepository noInitRepository = S3ChatMemoryRepository.builder()
			.s3Client(this.s3Client)
			.bucketName(nonExistentBucket)
			.keyPrefix("test")
			.initializeBucket(false)
			.build();

		// When/Then: Operations should throw IllegalStateException
		assertThatThrownBy(() -> noInitRepository.findConversationIds()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("does not exist");
	}

	@Test
	void testProductDateNamespacePattern() {
		// Given: A repository with product/date namespace pattern
		// Pattern: memory/{product}/{date}/{conversationId}.json
		String prefix = "memory-" + System.currentTimeMillis();
		S3ChatMemoryRepository nsRepository = S3ChatMemoryRepository.builder()
			.s3Client(this.s3Client)
			.bucketName(BUCKET_NAME)
			.keyPrefix(prefix)
			.keyResolver(conversationId -> {
				String[] parts = conversationId.split(":");
				return prefix + "/" + parts[0] + "/" + parts[1] + "/" + parts[2] + ".json";
			})
			.conversationIdExtractor(s3Key -> {
				String path = s3Key.substring(prefix.length() + 1, s3Key.length() - 5); // strip
																						// prefix/
																						// and
																						// .json
				String[] parts = path.split("/");
				return parts[0] + ":" + parts[1] + ":" + parts[2];
			})
			.build();

		// When: Saving conversations for different products and dates
		List<Message> messages1 = List.of(UserMessage.builder().text("Product A message").build());
		List<Message> messages2 = List.of(UserMessage.builder().text("Product B message").build());
		nsRepository.saveAll("productA:2024-01-15:conv1", messages1);
		nsRepository.saveAll("productB:2024-01-16:conv2", messages2);

		// Then: Messages can be retrieved by composite conversationId
		List<Message> retrieved1 = nsRepository.findByConversationId("productA:2024-01-15:conv1");
		assertThat(retrieved1).hasSize(1);
		assertThat(retrieved1.get(0).getText()).isEqualTo("Product A message");

		List<Message> retrieved2 = nsRepository.findByConversationId("productB:2024-01-16:conv2");
		assertThat(retrieved2).hasSize(1);
		assertThat(retrieved2.get(0).getText()).isEqualTo("Product B message");

		// And: findConversationIds returns all composite IDs
		List<String> conversationIds = nsRepository.findConversationIds();
		assertThat(conversationIds).containsExactlyInAnyOrder("productA:2024-01-15:conv1", "productB:2024-01-16:conv2");
	}

	@Test
	void testActorSessionNamespacePattern() {
		// Given: A repository with actor/session namespace pattern
		// Pattern: actor/{actorId}/session/{sessionId}.json
		String prefix = "actor-" + System.currentTimeMillis();
		S3ChatMemoryRepository nsRepository = S3ChatMemoryRepository.builder()
			.s3Client(this.s3Client)
			.bucketName(BUCKET_NAME)
			.keyPrefix(prefix)
			.keyResolver(conversationId -> {
				String[] parts = conversationId.split(":");
				return prefix + "/" + parts[0] + "/session/" + parts[1] + ".json";
			})
			.conversationIdExtractor(s3Key -> {
				String path = s3Key.substring(prefix.length() + 1, s3Key.length() - 5); // strip
																						// prefix/
																						// and
																						// .json
				return path.replaceAll("/session/", ":").replace(".json", "");
			})
			.build();

		// When: Saving conversations for different actors and sessions
		List<Message> messages1 = List.of(UserMessage.builder().text("Hello from user-1").build(),
				AssistantMessage.builder().content("Hi user-1!").build());
		List<Message> messages2 = List.of(UserMessage.builder().text("Hello from user-2").build());
		nsRepository.saveAll("user-1:session-100", messages1);
		nsRepository.saveAll("user-2:session-200", messages2);

		// Then: Messages can be retrieved
		List<Message> retrieved1 = nsRepository.findByConversationId("user-1:session-100");
		assertThat(retrieved1).hasSize(2);
		assertThat(retrieved1.get(0).getText()).isEqualTo("Hello from user-1");
		assertThat(retrieved1.get(1).getText()).isEqualTo("Hi user-1!");

		// And: findConversationIds returns correct composite IDs
		List<String> conversationIds = nsRepository.findConversationIds();
		assertThat(conversationIds).containsExactlyInAnyOrder("user-1:session-100", "user-2:session-200");

		// When: Deleting a conversation
		nsRepository.deleteByConversationId("user-1:session-100");

		// Then: Only the deleted conversation is gone
		assertThat(nsRepository.findByConversationId("user-1:session-100")).isEmpty();
		assertThat(nsRepository.findByConversationId("user-2:session-200")).hasSize(1);
		assertThat(nsRepository.findConversationIds()).containsExactly("user-2:session-200");
	}

	@Test
	void testCustomKeyResolverDelete() {
		// Given: A repository with custom key resolver
		String prefix = "delete-test-" + System.currentTimeMillis();
		S3ChatMemoryRepository nsRepository = S3ChatMemoryRepository.builder()
			.s3Client(this.s3Client)
			.bucketName(BUCKET_NAME)
			.keyPrefix(prefix)
			.keyResolver(conversationId -> {
				String[] parts = conversationId.split(":");
				return prefix + "/" + parts[0] + "/" + parts[1] + ".json";
			})
			.conversationIdExtractor(s3Key -> {
				String path = s3Key.substring(prefix.length() + 1, s3Key.length() - 5);
				String[] parts = path.split("/");
				return parts[0] + ":" + parts[1];
			})
			.build();

		// When: Saving and then deleting
		List<Message> messages = List.of(UserMessage.builder().text("To be deleted").build());
		nsRepository.saveAll("tenant1:conv1", messages);
		assertThat(nsRepository.findByConversationId("tenant1:conv1")).hasSize(1);

		nsRepository.deleteByConversationId("tenant1:conv1");

		// Then: Conversation is deleted
		assertThat(nsRepository.findByConversationId("tenant1:conv1")).isEmpty();
		assertThat(nsRepository.findConversationIds()).isEmpty();
	}

	@Test
	void testKeyResolverWithoutExtractorThrows() {
		// When/Then: Setting keyResolver without conversationIdExtractor should throw
		assertThatThrownBy(() -> S3ChatMemoryRepository.builder()
			.s3Client(this.s3Client)
			.bucketName(BUCKET_NAME)
			.keyPrefix("test")
			.keyResolver(id -> "prefix/" + id + ".json")
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("keyResolver and conversationIdExtractor must both be provided or both be null");
	}

	@Test
	void testExtractorWithoutKeyResolverThrows() {
		// When/Then: Setting conversationIdExtractor without keyResolver should throw
		assertThatThrownBy(() -> S3ChatMemoryRepository.builder()
			.s3Client(this.s3Client)
			.bucketName(BUCKET_NAME)
			.keyPrefix("test")
			.conversationIdExtractor(key -> key)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("keyResolver and conversationIdExtractor must both be provided or both be null");
	}

	@Test
	void testDefaultBehaviorUnchangedWithoutResolver() {
		// Given: A repository without custom resolver (default behavior)
		String uniquePrefix = "default-behavior-" + System.currentTimeMillis();
		S3ChatMemoryRepository defaultRepository = S3ChatMemoryRepository.builder()
			.s3Client(this.s3Client)
			.bucketName(BUCKET_NAME)
			.keyPrefix(uniquePrefix)
			.build();

		// When: Performing standard CRUD operations
		String conversationId = "simple-conversation";
		List<Message> messages = List.of(UserMessage.builder().text("Default behavior test").build());
		defaultRepository.saveAll(conversationId, messages);

		// Then: Default key pattern (prefix/conversationId.json) works as before
		List<Message> retrieved = defaultRepository.findByConversationId(conversationId);
		assertThat(retrieved).hasSize(1);
		assertThat(retrieved.get(0).getText()).isEqualTo("Default behavior test");

		List<String> conversationIds = defaultRepository.findConversationIds();
		assertThat(conversationIds).containsExactly(conversationId);

		defaultRepository.deleteByConversationId(conversationId);
		assertThat(defaultRepository.findConversationIds()).isEmpty();
	}

}
