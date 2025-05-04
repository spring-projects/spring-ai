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
package org.springframework.ai.chat.memory.redis;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.content.MediaContent;
import org.springframework.util.Assert;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.json.Path2;
import redis.clients.jedis.search.*;
import redis.clients.jedis.search.aggr.AggregationBuilder;
import redis.clients.jedis.search.aggr.AggregationResult;
import redis.clients.jedis.search.aggr.Reducers;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis implementation of {@link ChatMemory} using Redis (JSON + Query Engine). Stores
 * chat messages as JSON documents and uses the Redis Query Engine for querying.
 *
 * @author Brian Sam-Bodden
 */
public final class RedisChatMemory implements ChatMemory, ChatMemoryRepository {

	private static final Logger logger = LoggerFactory.getLogger(RedisChatMemory.class);

	private static final Gson gson = new Gson();

	private static final Path2 ROOT_PATH = Path2.of("$");

	private final RedisChatMemoryConfig config;

	private final JedisPooled jedis;

	public RedisChatMemory(RedisChatMemoryConfig config) {
		Assert.notNull(config, "Config must not be null");
		this.config = config;
		this.jedis = config.getJedisClient();

		if (config.isInitializeSchema()) {
			initializeSchema();
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public void add(String conversationId, List<Message> messages) {
		Assert.notNull(conversationId, "Conversation ID must not be null");
		Assert.notNull(messages, "Messages must not be null");

		final AtomicLong timestampSequence = new AtomicLong(Instant.now().toEpochMilli());
		try (Pipeline pipeline = jedis.pipelined()) {
			for (Message message : messages) {
				String key = createKey(conversationId, timestampSequence.getAndIncrement());
				String json = gson.toJson(createMessageDocument(conversationId, message));
				pipeline.jsonSet(key, ROOT_PATH, json);

				if (config.getTimeToLiveSeconds() != -1) {
					pipeline.expire(key, config.getTimeToLiveSeconds());
				}
			}
			pipeline.sync();
		}
	}

	@Override
	public void add(String conversationId, Message message) {
		Assert.notNull(conversationId, "Conversation ID must not be null");
		Assert.notNull(message, "Message must not be null");

		String key = createKey(conversationId, Instant.now().toEpochMilli());
		String json = gson.toJson(createMessageDocument(conversationId, message));

		jedis.jsonSet(key, ROOT_PATH, json);
		if (config.getTimeToLiveSeconds() != -1) {
			jedis.expire(key, config.getTimeToLiveSeconds());
		}
	}

	@Override
	public List<Message> get(String conversationId, int lastN) {
		Assert.notNull(conversationId, "Conversation ID must not be null");
		Assert.isTrue(lastN > 0, "LastN must be greater than 0");

		String queryStr = String.format("@conversation_id:{%s}", RediSearchUtil.escape(conversationId));
		// Use ascending order (oldest first) to match test expectations
		Query query = new Query(queryStr).setSortBy("timestamp", true).limit(0, lastN);

		SearchResult result = jedis.ftSearch(config.getIndexName(), query);

		if (logger.isDebugEnabled()) {
			logger.debug("Redis search for conversation {} returned {} results", conversationId,
					result.getDocuments().size());
			result.getDocuments().forEach(doc -> {
				if (doc.get("$") != null) {
					JsonObject json = gson.fromJson(doc.getString("$"), JsonObject.class);
					logger.debug("Document: {}", json);
				}
			});
		}

		List<Message> messages = new ArrayList<>();
		result.getDocuments().forEach(doc -> {
			if (doc.get("$") != null) {
				JsonObject json = gson.fromJson(doc.getString("$"), JsonObject.class);
				String type = json.get("type").getAsString();
				String content = json.get("content").getAsString();

				// Convert metadata from JSON to Map if present
				Map<String, Object> metadata = new HashMap<>();
				if (json.has("metadata") && json.get("metadata").isJsonObject()) {
					JsonObject metadataJson = json.getAsJsonObject("metadata");
					metadataJson.entrySet().forEach(entry -> {
						metadata.put(entry.getKey(), gson.fromJson(entry.getValue(), Object.class));
					});
				}

				if (MessageType.ASSISTANT.toString().equals(type)) {
					// Handle tool calls if present
					List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
					if (json.has("toolCalls") && json.get("toolCalls").isJsonArray()) {
						json.getAsJsonArray("toolCalls").forEach(element -> {
							JsonObject toolCallJson = element.getAsJsonObject();
							toolCalls.add(new AssistantMessage.ToolCall(
									toolCallJson.has("id") ? toolCallJson.get("id").getAsString() : "",
									toolCallJson.has("type") ? toolCallJson.get("type").getAsString() : "",
									toolCallJson.has("name") ? toolCallJson.get("name").getAsString() : "",
									toolCallJson.has("arguments") ? toolCallJson.get("arguments").getAsString() : ""));
						});
					}

					// Handle media if present
					List<Media> media = new ArrayList<>();
					if (json.has("media") && json.get("media").isJsonArray()) {
						// Media deserialization would go here if needed
						// Left as empty list for simplicity
					}

					messages.add(new AssistantMessage(content, metadata, toolCalls, media));
				}
				else if (MessageType.USER.toString().equals(type)) {
					// Create a UserMessage with the builder to properly set metadata
					List<Media> userMedia = new ArrayList<>();
					if (json.has("media") && json.get("media").isJsonArray()) {
						// Media deserialization would go here if needed
					}
					messages.add(UserMessage.builder().text(content).metadata(metadata).media(userMedia).build());
				}
				// Add handling for other message types if needed
			}
		});

		if (logger.isDebugEnabled()) {
			logger.debug("Returning {} messages for conversation {}", messages.size(), conversationId);
			messages.forEach(message -> logger.debug("Message type: {}, content: {}", message.getMessageType(),
					message.getText()));
		}

		return messages;
	}

	@Override
	public void clear(String conversationId) {
		Assert.notNull(conversationId, "Conversation ID must not be null");

		String queryStr = String.format("@conversation_id:{%s}", RediSearchUtil.escape(conversationId));
		Query query = new Query(queryStr);
		SearchResult result = jedis.ftSearch(config.getIndexName(), query);

		try (Pipeline pipeline = jedis.pipelined()) {
			result.getDocuments().forEach(doc -> pipeline.del(doc.getId()));
			pipeline.sync();
		}
	}

	private void initializeSchema() {
		try {
			if (!jedis.ftList().contains(config.getIndexName())) {
				List<SchemaField> schemaFields = new ArrayList<>();
				schemaFields.add(new TextField("$.content").as("content"));
				schemaFields.add(new TextField("$.type").as("type"));
				schemaFields.add(new TagField("$.conversation_id").as("conversation_id"));
				schemaFields.add(new NumericField("$.timestamp").as("timestamp"));

				String response = jedis.ftCreate(config.getIndexName(),
						FTCreateParams.createParams().on(IndexDataType.JSON).prefix(config.getKeyPrefix()),
						schemaFields.toArray(new SchemaField[0]));

				if (!response.equals("OK")) {
					throw new IllegalStateException("Failed to create index: " + response);
				}
			}
		}
		catch (Exception e) {
			logger.error("Failed to initialize Redis schema", e);
			throw new IllegalStateException("Could not initialize Redis schema", e);
		}
	}

	private String createKey(String conversationId, long timestamp) {
		return String.format("%s%s:%d", config.getKeyPrefix(), escapeKey(conversationId), timestamp);
	}

	private Map<String, Object> createMessageDocument(String conversationId, Message message) {
		Map<String, Object> documentMap = new HashMap<>();
		documentMap.put("type", message.getMessageType().toString());
		documentMap.put("content", message.getText());
		documentMap.put("conversation_id", conversationId);
		documentMap.put("timestamp", Instant.now().toEpochMilli());

		// Store metadata/properties
		if (message.getMetadata() != null && !message.getMetadata().isEmpty()) {
			documentMap.put("metadata", message.getMetadata());
		}

		// Handle tool calls for AssistantMessage
		if (message instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls()) {
			documentMap.put("toolCalls", assistantMessage.getToolCalls());
		}

		// Handle media content
		if (message instanceof MediaContent mediaContent && !mediaContent.getMedia().isEmpty()) {
			documentMap.put("media", mediaContent.getMedia());
		}

		return documentMap;
	}

	private String escapeKey(String key) {
		return key.replace(":", "\\:");
	}

	// ChatMemoryRepository implementation

	/**
	 * Finds all unique conversation IDs using Redis aggregation. This method is optimized
	 * to perform the deduplication on the Redis server side.
	 * @return a list of unique conversation IDs
	 */
	@Override
	public List<String> findConversationIds() {
		try {
			// Use Redis aggregation to get distinct conversation_ids
			AggregationBuilder aggregation = new AggregationBuilder("*")
				.groupBy("@conversation_id", Reducers.count().as("count"))
				.limit(0, config.getMaxConversationIds()); // Use configured limit

			AggregationResult result = jedis.ftAggregate(config.getIndexName(), aggregation);

			List<String> conversationIds = new ArrayList<>();
			result.getResults().forEach(row -> {
				String conversationId = (String) row.get("conversation_id");
				if (conversationId != null) {
					conversationIds.add(conversationId);
				}
			});

			if (logger.isDebugEnabled()) {
				logger.debug("Found {} unique conversation IDs using Redis aggregation", conversationIds.size());
				conversationIds.forEach(id -> logger.debug("Conversation ID: {}", id));
			}

			return conversationIds;
		}
		catch (Exception e) {
			logger.warn("Error executing Redis aggregation for conversation IDs, falling back to client-side approach",
					e);
			return findConversationIdsLegacy();
		}
	}

	/**
	 * Fallback method to find conversation IDs if aggregation fails. This is less
	 * efficient as it requires fetching all documents and deduplicating on the client
	 * side.
	 * @return a list of unique conversation IDs
	 */
	private List<String> findConversationIdsLegacy() {
		// Keep the current implementation as a fallback
		String queryStr = "*"; // Match all documents
		Query query = new Query(queryStr);
		query.limit(0, config.getMaxConversationIds()); // Use configured limit

		SearchResult result = jedis.ftSearch(config.getIndexName(), query);

		// Use a Set to deduplicate conversation IDs
		Set<String> conversationIds = new HashSet<>();

		result.getDocuments().forEach(doc -> {
			if (doc.get("$") != null) {
				JsonObject json = gson.fromJson(doc.getString("$"), JsonObject.class);
				if (json.has("conversation_id")) {
					conversationIds.add(json.get("conversation_id").getAsString());
				}
			}
		});

		if (logger.isDebugEnabled()) {
			logger.debug("Found {} unique conversation IDs using legacy method", conversationIds.size());
		}

		return new ArrayList<>(conversationIds);
	}

	/**
	 * Finds all messages for a given conversation ID. Uses the configured maximum
	 * messages per conversation limit to avoid exceeding Redis limits.
	 * @param conversationId the conversation ID to find messages for
	 * @return a list of messages for the conversation
	 */
	@Override
	public List<Message> findByConversationId(String conversationId) {
		// Reuse existing get method with the configured limit
		return get(conversationId, config.getMaxMessagesPerConversation());
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		// First clear any existing messages for this conversation
		clear(conversationId);

		// Then add all the new messages
		add(conversationId, messages);
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		// Reuse existing clear method
		clear(conversationId);
	}

	/**
	 * Builder for RedisChatMemory configuration.
	 */
	public static class Builder {

		private final RedisChatMemoryConfig.Builder configBuilder = RedisChatMemoryConfig.builder();

		public Builder jedisClient(JedisPooled jedisClient) {
			configBuilder.jedisClient(jedisClient);
			return this;
		}

		public Builder timeToLive(Duration ttl) {
			configBuilder.timeToLive(ttl);
			return this;
		}

		public Builder indexName(String indexName) {
			configBuilder.indexName(indexName);
			return this;
		}

		public Builder keyPrefix(String keyPrefix) {
			configBuilder.keyPrefix(keyPrefix);
			return this;
		}

		public Builder initializeSchema(boolean initialize) {
			configBuilder.initializeSchema(initialize);
			return this;
		}

		public RedisChatMemory build() {
			return new RedisChatMemory(configBuilder.build());
		}

	}

}
