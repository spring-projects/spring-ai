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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.json.Path2;
import redis.clients.jedis.search.*;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis implementation of {@link ChatMemory} using Redis Stack (RedisJSON + RediSearch).
 * Stores chat messages as JSON documents and uses RediSearch for querying.
 *
 * @author Brian Sam-Bodden
 */
public final class RedisChatMemory implements ChatMemory {

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
		Query query = new Query(queryStr).setSortBy("timestamp", true).limit(0, lastN);

		SearchResult result = jedis.ftSearch(config.getIndexName(), query);

		List<Message> messages = new ArrayList<>();
		result.getDocuments().forEach(doc -> {
			if (doc.get("$") != null) {
				JsonObject json = gson.fromJson(doc.getString("$"), JsonObject.class);
				String type = json.get("type").getAsString();
				String content = json.get("content").getAsString();

				if (MessageType.ASSISTANT.toString().equals(type)) {
					messages.add(new AssistantMessage(content));
				}
				else if (MessageType.USER.toString().equals(type)) {
					messages.add(new UserMessage(content));
				}
			}
		});

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
		return Map.of("type", message.getMessageType().toString(), "content", message.getText(), "conversation_id",
				conversationId, "timestamp", Instant.now().toEpochMilli());
	}

	private String escapeKey(String key) {
		return key.replace(":", "\\:");
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
