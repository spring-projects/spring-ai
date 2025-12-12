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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

	private static final String JSON_EXTENSION = ".json";

	private final S3Client s3Client;

	private final S3ChatMemoryConfig config;

	private final ObjectMapper objectMapper;

	public S3ChatMemoryRepository(S3ChatMemoryConfig config) {
		Assert.notNull(config, "config cannot be null");

		this.s3Client = config.getS3Client();
		this.config = config;
		this.objectMapper = new ObjectMapper();
	}

	private void ensureBucketExists() {
		try {
			this.s3Client.headBucket(HeadBucketRequest.builder().bucket(this.config.getBucketName()).build());
		}
		catch (NoSuchBucketException e) {
			if (this.config.isInitializeBucket()) {
				try {
					this.s3Client
						.createBucket(CreateBucketRequest.builder().bucket(this.config.getBucketName()).build());
				}
				catch (S3Exception createException) {
					throw new IllegalStateException("Failed to create S3 bucket '" + this.config.getBucketName() + "': "
							+ createException.getMessage(), createException);
				}
			}
			else {
				throw new IllegalStateException("S3 bucket '" + this.config.getBucketName() + "' does not exist. "
						+ "Create the bucket manually or set spring.ai.chat.memory.repository.s3.initialize-bucket=true");
			}
		}
		catch (S3Exception e) {
			throw new IllegalStateException(
					"Failed to check S3 bucket '" + this.config.getBucketName() + "': " + e.getMessage(), e);
		}
	}

	private String normalizeConversationId(String conversationId) {
		return (conversationId == null || conversationId.trim().isEmpty()) ? ChatMemory.DEFAULT_CONVERSATION_ID
				: conversationId.trim();
	}

	// Pattern: {prefix}/{conversationId}.json
	private String generateKey(String conversationId, String prefix) {
		String normalizedConversationId = normalizeConversationId(conversationId);
		Assert.hasText(prefix, "prefix cannot be null or empty");

		String normalizedPrefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;

		return normalizedPrefix + "/" + normalizedConversationId + JSON_EXTENSION;
	}

	private String extractConversationId(String key, String prefix) {
		Assert.hasText(key, "key cannot be null or empty");
		Assert.hasText(prefix, "prefix cannot be null or empty");

		String normalizedPrefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;

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

	private String serialize(String conversationId, List<Message> messages) {
		try {
			Map<String, Object> payload = new HashMap<>();
			payload.put("conversationId", conversationId);

			List<Map<String, Object>> messageList = new ArrayList<>();
			// Sequential timestamps for message ordering (JSON array order already
			// preserves sequence)
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

			return this.objectMapper.writeValueAsString(payload);
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException(
					"Failed to serialize messages for conversation '" + conversationId + "': " + e.getMessage(), e);
		}
	}

	private List<Message> deserialize(String json) {
		try {
			JsonNode root = this.objectMapper.readTree(json);

			if (!root.has("messages")) {
				throw new IllegalStateException("JSON does not contain 'messages' field");
			}

			JsonNode messagesNode = root.get("messages");
			List<Message> messages = new ArrayList<>();

			for (JsonNode messageNode : messagesNode) {
				String typeStr = messageNode.get("type").asText();
				String content = messageNode.get("content").asText();

				Long timestamp = messageNode.has("timestamp") ? messageNode.get("timestamp").asLong() : null;

				Map<String, Object> metadata = new HashMap<>();
				if (messageNode.has("metadata") && !messageNode.get("metadata").isNull()) {
					metadata = convertMetadata(messageNode.get("metadata"));
				}

				if (timestamp != null) {
					metadata.put("timestamp", timestamp);
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

	private Map<String, Object> convertMetadata(JsonNode metadataNode) {
		return this.objectMapper.convertValue(metadataNode,
				new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
				});
	}

	private Message createMessage(MessageType type, String content, Map<String, Object> metadata) {
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
				.prefix(this.config.getKeyPrefix() + "/")
				.build();

			ListObjectsV2Response response;
			do {
				response = this.s3Client.listObjectsV2(request);

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
			throw new IllegalStateException("Failed to list conversation IDs from S3 bucket '"
					+ this.config.getBucketName() + "': " + e.getMessage(), e);
		}
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		String normalizedConversationId = normalizeConversationId(conversationId);
		ensureBucketExists();

		try {
			String key = generateKey(normalizedConversationId, this.config.getKeyPrefix());

			GetObjectRequest getObjectRequest = GetObjectRequest.builder()
				.bucket(this.config.getBucketName())
				.key(key)
				.build();

			try (ResponseInputStream<GetObjectResponse> response = this.s3Client.getObject(getObjectRequest)) {
				String json = new String(response.readAllBytes());
				return deserialize(json);
			}
		}
		catch (NoSuchKeyException e) {
			return List.of();
		}
		catch (S3Exception e) {
			throw new IllegalStateException("Failed to retrieve conversation '" + normalizedConversationId
					+ "' from S3 bucket '" + this.config.getBucketName() + "': " + e.getMessage(), e);
		}
		catch (Exception e) {
			throw new IllegalStateException(
					"Failed to read conversation '" + normalizedConversationId + "': " + e.getMessage(), e);
		}
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		String normalizedConversationId = normalizeConversationId(conversationId);
		Assert.notNull(messages, "messages cannot be null");
		Assert.noNullElements(messages, "messages cannot contain null elements");

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
				.storageClass(this.config.getStorageClass())
				.build();

			this.s3Client.putObject(putObjectRequest, RequestBody.fromString(json));
		}
		catch (S3Exception e) {
			throw new IllegalStateException("Failed to save conversation '" + normalizedConversationId
					+ "' to S3 bucket '" + this.config.getBucketName() + "': " + e.getMessage(), e);
		}
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		String normalizedConversationId = normalizeConversationId(conversationId);
		ensureBucketExists();

		try {
			String key = generateKey(normalizedConversationId, this.config.getKeyPrefix());

			DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
				.bucket(this.config.getBucketName())
				.key(key)
				.build();

			this.s3Client.deleteObject(deleteObjectRequest);
		}
		catch (S3Exception e) {
			throw new IllegalStateException("Failed to delete conversation '" + normalizedConversationId
					+ "' from S3 bucket '" + this.config.getBucketName() + "': " + e.getMessage(), e);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private S3Client s3Client;

		private String bucketName;

		private String keyPrefix = S3ChatMemoryConfig.DEFAULT_KEY_PREFIX;

		private boolean initializeBucket = false;

		private software.amazon.awssdk.services.s3.model.StorageClass storageClass;

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

		public Builder storageClass(software.amazon.awssdk.services.s3.model.StorageClass storageClass) {
			this.storageClass = storageClass;
			return this;
		}

		public S3ChatMemoryRepository build() {
			Assert.notNull(this.s3Client, "s3Client must be set");

			S3ChatMemoryConfig config = S3ChatMemoryConfig.builder()
				.s3Client(this.s3Client)
				.bucketName(this.bucketName)
				.keyPrefix(this.keyPrefix)
				.initializeBucket(this.initializeBucket)
				.storageClass(this.storageClass)
				.build();

			return new S3ChatMemoryRepository(config);
		}

	}

}
