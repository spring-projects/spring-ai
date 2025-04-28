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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.Assert;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis(<a href="https://github.com/redis/redis">Redis</a>) Chat Memory Repository, using
 * spring RedisTemplate.
 *
 * @author lambochen
 * @see org.springframework.data.redis.core.StringRedisTemplate
 * @see org.springframework.data.redis.core.RedisTemplate
 * @since 1.0.0
 */
public class RedisChatMemoryRepository implements ChatMemoryRepository {

	private static final Logger log = LoggerFactory.getLogger(RedisChatMemoryRepository.class);

	private final StringRedisTemplate redisTemplate;

	/**
	 * Key prefix for Redis keys. The full key is "{keyPrefix}{conversationId}"
	 */
	private final String keyPrefix;

	/**
	 * ObjectMapper for serializing and deserializing {@link RedisMessage}.
	 */
	private final ObjectMapper objectMapper;

	/**
	 * Request timeout for Redis operations.
	 */
	private final long requestTimeout;

	/**
	 * Request time unit for Redis operations.
	 */
	private final TimeUnit requestTimeUnit;

	/**
	 * Default key prefix is blank, as the full key is "{conversationId}"
	 */
	private static final String DEFAULT_KEY_PREFIX = "";

	/**
	 * Default request timeout is -1, which means no timeout.
	 */
	private static final long DEFAULT_REQUEST_TIMEOUT = -1;

	/**
	 * Default request time unit is milliseconds.
	 */
	private static final TimeUnit DEFAULT_REQUEST_TIME_UNIT = TimeUnit.MILLISECONDS;

	/**
	 * Create a new {@link RedisChatMemoryRepository} instance. The key prefix is
	 * {@link #DEFAULT_KEY_PREFIX}, as the full key is "{conversationId}"
	 * @param redisTemplate the StringRedisTemplate instance
	 */
	public RedisChatMemoryRepository(StringRedisTemplate redisTemplate) {
		this(redisTemplate, DEFAULT_KEY_PREFIX);
	}

	/**
	 * Create a new {@link RedisChatMemoryRepository} instance. The key prefix is
	 * {@link #DEFAULT_KEY_PREFIX}, as the full key is "{keyPrefix}{conversationId}" The
	 * ObjectMapper is {@link #defaultObjectMapper()}.
	 * @param redisTemplate the StringRedisTemplate instance
	 * @param keyPrefix the key prefix for Redis keys
	 */
	public RedisChatMemoryRepository(StringRedisTemplate redisTemplate, String keyPrefix) {
		this(redisTemplate, keyPrefix, defaultObjectMapper());
	}

	/**
	 * Create a new {@link RedisChatMemoryRepository} instance.
	 * @param redisTemplate the StringRedisTemplate instance
	 * @param keyPrefix the key prefix for Redis keys
	 * @param objectMapper the ObjectMapper instance, for serializing and deserializing
	 * {@link RedisMessage}
	 */
	public RedisChatMemoryRepository(StringRedisTemplate redisTemplate, String keyPrefix, ObjectMapper objectMapper) {
		this(redisTemplate, keyPrefix, objectMapper, DEFAULT_REQUEST_TIMEOUT, DEFAULT_REQUEST_TIME_UNIT);
	}

	/**
	 * Create a new {@link RedisChatMemoryRepository} instance.
	 * @param redisTemplate the StringRedisTemplate instance
	 * @param keyPrefix the key prefix for Redis keys
	 * @param objectMapper the ObjectMapper instance, for serializing and deserializing
	 * {@link RedisMessage}
	 * @param requestTimeout the request timeout for Redis operations
	 * @param requestTimeUnit the request time unit for Redis operations
	 */
	public RedisChatMemoryRepository(StringRedisTemplate redisTemplate, String keyPrefix, ObjectMapper objectMapper,
			long requestTimeout, TimeUnit requestTimeUnit) {
		this.redisTemplate = redisTemplate;
		this.keyPrefix = keyPrefix;
		if (null == objectMapper) {
			// default
			this.objectMapper = defaultObjectMapper();
		}
		else {
			this.objectMapper = objectMapper;
		}

		this.requestTimeout = requestTimeout;
		this.requestTimeUnit = requestTimeUnit;
	}

	@Override
	public List<String> findConversationIds() {
		// FIXME: "RedisTemplate.keys(pattern)" is a low performance operation.
		Set<String> allKeys = redisTemplate.keys("*");
		if (null == allKeys || allKeys.isEmpty()) {
			return List.of();
		}

		return allKeys.stream().map(this::parseConversationId).collect(Collectors.toList());
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		String key = generateKey(conversationId);
		String serializedMessages;
		if (noRequestTimeout()) {
			serializedMessages = redisTemplate.opsForValue().get(key);
		}
		else {
			serializedMessages = redisTemplate.opsForValue().getAndExpire(key, requestTimeout, requestTimeUnit);
		}

		if (null == serializedMessages || serializedMessages.isEmpty()) {
			return List.of();
		}
		return toMessages(deserialize(serializedMessages));
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Assert.notNull(messages, "messages cannot be null");
		Assert.noNullElements(messages, "messages cannot contain null elements");
		this.deleteByConversationId(conversationId);

		List<RedisMessage> redisMessages = toRedisMessages(messages);
		String key = generateKey(conversationId);
		String serializedMessages = serialize(redisMessages);
		if (noRequestTimeout()) {
			this.redisTemplate.opsForValue().set(key, serializedMessages);
		}
		else {
			this.redisTemplate.opsForValue().set(key, serializedMessages, requestTimeout, requestTimeUnit);
		}
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		this.redisTemplate.delete(generateKey(conversationId));
	}

	/**
	 * RedisMessage is the actual model of data storage.
	 */
	private static class RedisMessage implements Serializable {

		private static final long serialVersionUID = -6620540028783509268L;

		private String type;

		private String text;

		public RedisMessage() {
		}

		public RedisMessage(String type, String text) {
			this.type = type;
			this.text = text;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}

	}

	private List<RedisMessage> toRedisMessages(List<Message> messages) {
		return messages.stream().map(msg -> {
			RedisMessage redisMessage = new RedisMessage();
			redisMessage.setType(msg.getMessageType().name());
			redisMessage.setText(msg.getText());
			return redisMessage;
		}).collect(Collectors.toList());
	}

	private List<Message> toMessages(List<RedisMessage> redisMessages) {
		return redisMessages.stream().map(redisMessage -> {
			var type = MessageType.valueOf(redisMessage.getType());
			var text = redisMessage.getText();

			return switch (type) {
				case USER -> new UserMessage(text);
				case ASSISTANT -> new AssistantMessage(text);
				case SYSTEM -> new SystemMessage(text);
				case TOOL -> new ToolResponseMessage(List.of());
			};
		}).collect(Collectors.toList());
	}

	private String serialize(List<RedisMessage> messages) {
		try {
			return objectMapper.writeValueAsString(messages);
		}
		catch (JsonProcessingException e) {
			log.error("serialize messages to json failed", e);
			throw new RuntimeException(e);
		}
	}

	private List<RedisMessage> deserialize(String serializedMessages) {
		try {
			return objectMapper.readValue(serializedMessages, new TypeReference<List<RedisMessage>>() {
			});
		}
		catch (JsonProcessingException e) {
			log.error("deserialize messages from json failed", e);
			throw new RuntimeException(e);
		}
	}

	private static ObjectMapper defaultObjectMapper() {
		return new ObjectMapper();
	}

	/**
	 * The full key is "{keyPrefix}{conversationId}"
	 * @param conversationId conversation id
	 * @return the full key
	 */
	private String generateKey(String conversationId) {
		if (null == keyPrefix || keyPrefix.isEmpty()) {
			return conversationId;
		}

		return keyPrefix + conversationId;
	}

	/**
	 * Parse conversation id from the full key.
	 * @param key the full key
	 * @return conversation id
	 */
	private String parseConversationId(String key) {
		if (null == keyPrefix || keyPrefix.isEmpty()) {
			return key;
		}

		return key.replaceFirst(keyPrefix, "");
	}

	/**
	 * Check if there is no request timeout.
	 * @return true if there is no request timeout
	 */
	private boolean noRequestTimeout() {
		return DEFAULT_REQUEST_TIMEOUT == requestTimeout;
	}

}
