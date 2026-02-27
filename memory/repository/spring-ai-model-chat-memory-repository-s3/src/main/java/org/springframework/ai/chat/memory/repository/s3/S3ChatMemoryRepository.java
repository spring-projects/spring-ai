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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;

/**
 * An implementation of {@link ChatMemoryRepository} for Amazon S3 using a simple JSON
 * format for storing conversation messages.
 *
 * @author Yuriy Bezsonov
 * @since 2.0.0
 */
public final class S3ChatMemoryRepository implements ChatMemoryRepository {

	/** JSON file extension for stored conversations. */
	private static final String JSON_EXTENSION = ".json";

	/** JSON content type for S3 objects. */
	private static final String JSON_CONTENT_TYPE = "application/json";

	/** Shared ObjectMapper instance (thread-safe after configuration). */
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/** The S3 configuration. */
	private final S3ChatMemoryConfig config;

	/** Flag indicating if bucket existence has been verified. */
	private volatile boolean bucketVerified = false;

	/**
	 * Creates a new S3ChatMemoryRepository.
	 * @param configuration the S3 configuration
	 */
	public S3ChatMemoryRepository(final S3ChatMemoryConfig configuration) {
		Assert.notNull(configuration, "config cannot be null");
		this.config = configuration;
	}

	/**
	 * Ensures the S3 bucket exists, creating it if necessary. Uses double-checked locking
	 * for thread-safe lazy initialization.
	 */
	private void ensureBucketExists() {
		if (this.bucketVerified) {
			return;
		}

		synchronized (this) {
			if (this.bucketVerified) {
				return;
			}

			try {
				this.config.getS3Client()
					.headBucket(HeadBucketRequest.builder().bucket(this.config.getBucketName()).build());
				this.bucketVerified = true;
			}
			catch (NoSuchBucketException e) {
				if (this.config.isInitializeBucket()) {
					try {
						this.config.getS3Client()
							.createBucket(CreateBucketRequest.builder().bucket(this.config.getBucketName()).build());
						this.bucketVerified = true;
					}
					catch (S3Exception createException) {
						throw new IllegalStateException("Failed to create S3 bucket '" + this.config.getBucketName()
								+ "': " + createException.getMessage(), createException);
					}
				}
				else {
					throw new IllegalStateException("S3 bucket '" + this.config.getBucketName() + "' does not exist. "
							+ "Create the bucket manually or set " + "spring.ai.chat.memory.repository.s3."
							+ "initialize-bucket=true");
				}
			}
			catch (S3Exception e) {
				throw new IllegalStateException(
						"Failed to check S3 bucket '" + this.config.getBucketName() + "': " + e.getMessage(), e);
			}
		}
	}

	/**
	 * Normalizes a key prefix by removing trailing slash if present.
	 * @param prefix the prefix to normalize
	 * @return the normalized prefix without trailing slash
	 */
	private String normalizePrefix(final String prefix) {
		return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
	}

	/**
	 * Normalizes conversation ID, using default if null or empty.
	 * @param conversationId the conversation ID to normalize
	 * @return the normalized conversation ID
	 */
	private String normalizeConversationId(final @Nullable String conversationId) {
		return (conversationId == null || conversationId.trim().isEmpty()) ? ChatMemory.DEFAULT_CONVERSATION_ID
				: conversationId.trim();
	}

	/**
	 * Generates S3 key for conversation. When a custom key resolver is configured,
	 * delegates to it. Otherwise uses the default pattern: {prefix}/{conversationId}.json
	 * @param conversationId the conversation ID
	 * @param prefix the key prefix
	 * @return the S3 key
	 */
	private String generateKey(final String conversationId, final String prefix) {
		String normalizedConversationId = normalizeConversationId(conversationId);
		Function<String, String> resolver = this.config.getKeyResolver();
		if (resolver != null) {
			return resolver.apply(normalizedConversationId);
		}
		Assert.hasText(prefix, "prefix cannot be null or empty");

		return normalizePrefix(prefix) + "/" + normalizedConversationId + JSON_EXTENSION;
	}

	/**
	 * Extracts conversation ID from S3 key. When a custom conversation ID extractor is
	 * configured, delegates to it. Otherwise uses the default extraction logic based on
	 * the key prefix.
	 * @param key the S3 key
	 * @param prefix the key prefix
	 * @return the conversation ID or null if invalid
	 */
	private @Nullable String extractConversationId(final String key, final String prefix) {
		Function<String, String> extractor = this.config.getConversationIdExtractor();
		if (extractor != null) {
			return extractor.apply(key);
		}

		Assert.hasText(key, "key cannot be null or empty");
		Assert.hasText(prefix, "prefix cannot be null or empty");

		String normalizedPrefix = normalizePrefix(prefix);

		if (!key.startsWith(normalizedPrefix + "/") || !key.endsWith(JSON_EXTENSION)) {
			return null;
		}

		int startIndex = normalizedPrefix.length() + 1;
		int endIndex = key.length() - JSON_EXTENSION.length();

		if (startIndex >= endIndex) {
			return null;
		}

		String conversationId = key.substring(startIndex, endIndex);

		if (conversationId.contains("/")) {
			return null;
		}

		return conversationId.isEmpty() ? null : conversationId;
	}

	/**
	 * Serializes conversation messages to JSON.
	 * @param conversationId the conversation ID
	 * @param messages the messages to serialize
	 * @return the JSON string
	 */
	private String serialize(final String conversationId, final List<Message> messages) {
		try {
			Map<String, Object> payload = new HashMap<>();
			payload.put("conversationId", conversationId);

			List<Map<String, Object>> messageList = new ArrayList<>();
			// Sequential timestamps for message ordering (JSON array order
			// already preserves sequence)
			long baseTimestamp = Instant.now().getEpochSecond();

			for (int i = 0; i < messages.size(); i++) {
				Message message = messages.get(i);
				Map<String, Object> messageMap = new HashMap<>();
				messageMap.put("type", message.getMessageType().name());
				messageMap.put("content", message.getText());
				messageMap.put("timestamp", baseTimestamp + i);
				messageMap.put("metadata", message.getMetadata());
				messageList.add(messageMap);
			}

			payload.put("messages", messageList);

			return OBJECT_MAPPER.writeValueAsString(payload);
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException(
					"Failed to serialize messages for " + "conversation '" + conversationId + "': " + e.getMessage(),
					e);
		}
	}

	/**
	 * Deserializes JSON to conversation messages.
	 * @param json the JSON string
	 * @return the list of messages
	 */
	private List<Message> deserialize(final String json) {
		try {
			JsonNode root = OBJECT_MAPPER.readTree(json);

			if (!root.has("messages")) {
				throw new IllegalStateException("JSON does not contain 'messages' field");
			}

			JsonNode messagesNode = root.get("messages");
			List<Message> messages = new ArrayList<>();

			for (JsonNode messageNode : messagesNode) {
				String typeStr = messageNode.get("type").asText();
				String content = messageNode.get("content").asText();

				Map<String, Object> metadata = new HashMap<>();
				if (messageNode.has("metadata") && !messageNode.get("metadata").isNull()) {
					metadata = convertMetadata(messageNode.get("metadata"));
				}

				MessageType type = MessageType.valueOf(typeStr);
				Message message = createMessage(type, content, metadata);

				messages.add(message);
			}

			return messages;
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to deserialize messages: " + e.getMessage(), e);
		}
	}

	/**
	 * Converts JSON metadata node to Map.
	 * @param metadataNode the JSON metadata node
	 * @return the metadata map
	 */
	private Map<String, Object> convertMetadata(final JsonNode metadataNode) {
		return OBJECT_MAPPER.convertValue(metadataNode,
				new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
				});
	}

	/**
	 * Creates a message instance based on type. Note: TOOL messages do not preserve
	 * content as the ToolResponseMessage requires structured ToolResponse objects which
	 * cannot be reconstructed from plain text content.
	 * @param type the message type
	 * @param content the message content
	 * @param metadata the message metadata
	 * @return the message instance
	 */
	private Message createMessage(final MessageType type, final String content, final Map<String, Object> metadata) {
		return switch (type) {
			case USER -> UserMessage.builder().text(content).metadata(metadata).build();
			case ASSISTANT -> AssistantMessage.builder().content(content).properties(metadata).build();
			case SYSTEM -> SystemMessage.builder().text(content).metadata(metadata).build();
			case TOOL -> ToolResponseMessage.builder().responses(List.of()).metadata(metadata).build();
		};
	}

	@Override
	public List<String> findConversationIds() {
		ensureBucketExists();

		try {
			List<String> conversationIds = new ArrayList<>();

			ListObjectsV2Request request = ListObjectsV2Request.builder()
				.bucket(this.config.getBucketName())
				.prefix(normalizePrefix(this.config.getKeyPrefix()) + "/")
				.build();

			ListObjectsV2Response response;
			do {
				response = this.config.getS3Client().listObjectsV2(request);

				for (S3Object s3Object : response.contents()) {
					String key = s3Object.key();
					String conversationId = extractConversationId(key, this.config.getKeyPrefix());
					if (conversationId != null) {
						conversationIds.add(conversationId);
					}
				}

				request = request.toBuilder().continuationToken(response.nextContinuationToken()).build();
			}
			while (response.isTruncated());

			return conversationIds;
		}
		catch (S3Exception e) {
			throw new IllegalStateException("Failed to list conversation IDs " + "from S3 bucket '"
					+ this.config.getBucketName() + "': " + e.getMessage(), e);
		}
	}

	@Override
	public List<Message> findByConversationId(final String conversationId) {
		String normalizedConversationId = normalizeConversationId(conversationId);
		ensureBucketExists();

		try {
			String key = generateKey(normalizedConversationId, this.config.getKeyPrefix());

			GetObjectRequest getObjectRequest = GetObjectRequest.builder()
				.bucket(this.config.getBucketName())
				.key(key)
				.build();

			try (ResponseInputStream<GetObjectResponse> response = this.config.getS3Client()
				.getObject(getObjectRequest)) {
				String json = new String(response.readAllBytes(), StandardCharsets.UTF_8);
				return deserialize(json);
			}
		}
		catch (NoSuchKeyException e) {
			return new ArrayList<>();
		}
		catch (S3Exception e) {
			throw new IllegalStateException("Failed to retrieve conversation '" + normalizedConversationId
					+ "' from S3 bucket '" + this.config.getBucketName() + "': " + e.getMessage(), e);
		}
		catch (Exception e) {
			throw new IllegalStateException(
					"Failed to retrieve conversation '" + normalizedConversationId + "': " + e.getMessage(), e);
		}
	}

	@Override
	public void saveAll(final String conversationId, final List<Message> messages) {
		Assert.notNull(messages, "messages cannot be null");

		String normalizedConversationId = normalizeConversationId(conversationId);

		if (messages.isEmpty()) {
			deleteByConversationId(normalizedConversationId);
			return;
		}

		ensureBucketExists();

		try {
			String key = generateKey(normalizedConversationId, this.config.getKeyPrefix());
			String json = serialize(normalizedConversationId, messages);

			PutObjectRequest putObjectRequest = PutObjectRequest.builder()
				.bucket(this.config.getBucketName())
				.key(key)
				.contentType(JSON_CONTENT_TYPE)
				.storageClass(this.config.getStorageClass())
				.build();

			this.config.getS3Client().putObject(putObjectRequest, RequestBody.fromString(json));
		}
		catch (S3Exception e) {
			throw new IllegalStateException("Failed to save conversation '" + normalizedConversationId
					+ "' to S3 bucket '" + this.config.getBucketName() + "': " + e.getMessage(), e);
		}
	}

	@Override
	public void deleteByConversationId(final String conversationId) {
		String normalizedConversationId = normalizeConversationId(conversationId);
		ensureBucketExists();

		try {
			String key = generateKey(normalizedConversationId, this.config.getKeyPrefix());

			DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
				.bucket(this.config.getBucketName())
				.key(key)
				.build();

			this.config.getS3Client().deleteObject(deleteObjectRequest);
		}
		catch (S3Exception e) {
			throw new IllegalStateException("Failed to delete conversation '" + normalizedConversationId
					+ "' from S3 bucket '" + this.config.getBucketName() + "': " + e.getMessage(), e);
		}
	}

	/**
	 * Creates a new builder for S3ChatMemoryRepository.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for S3ChatMemoryRepository. Delegates to {@link S3ChatMemoryConfig.Builder}
	 * for configuration.
	 */
	public static final class Builder {

		private final S3ChatMemoryConfig.Builder configBuilder = S3ChatMemoryConfig.builder();

		private Builder() {
		}

		/**
		 * Sets the S3 client.
		 * @param client the S3 client
		 * @return this builder
		 */
		public Builder s3Client(final S3Client client) {
			this.configBuilder.s3Client(client);
			return this;
		}

		/**
		 * Sets the bucket name.
		 * @param name the bucket name
		 * @return this builder
		 */
		public Builder bucketName(final String name) {
			this.configBuilder.bucketName(name);
			return this;
		}

		/**
		 * Sets the key prefix.
		 * @param prefix the key prefix
		 * @return this builder
		 */
		public Builder keyPrefix(final String prefix) {
			this.configBuilder.keyPrefix(prefix);
			return this;
		}

		/**
		 * Sets whether to initialize bucket.
		 * @param initialize true to initialize bucket
		 * @return this builder
		 */
		public Builder initializeBucket(final boolean initialize) {
			this.configBuilder.initializeBucket(initialize);
			return this;
		}

		/**
		 * Sets the storage class.
		 * @param storage the storage class
		 * @return this builder
		 */
		public Builder storageClass(final software.amazon.awssdk.services.s3.model.StorageClass storage) {
			this.configBuilder.storageClass(storage);
			return this;
		}

		/**
		 * Sets the key resolver function that maps a conversationId to a full S3 object
		 * key.
		 * @param resolver the key resolver function
		 * @return this builder
		 * @see S3ChatMemoryConfig.Builder#keyResolver(Function)
		 */
		public Builder keyResolver(final Function<String, String> resolver) {
			this.configBuilder.keyResolver(resolver);
			return this;
		}

		/**
		 * Sets the conversation ID extractor function that maps an S3 object key back to
		 * a conversationId.
		 * @param extractor the conversation ID extractor function
		 * @return this builder
		 * @see S3ChatMemoryConfig.Builder#conversationIdExtractor(Function)
		 */
		public Builder conversationIdExtractor(final Function<String, String> extractor) {
			this.configBuilder.conversationIdExtractor(extractor);
			return this;
		}

		/**
		 * Builds the repository.
		 * @return the S3ChatMemoryRepository instance
		 */
		public S3ChatMemoryRepository build() {
			return new S3ChatMemoryRepository(this.configBuilder.build());
		}

	}

}
