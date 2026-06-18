/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.chat.memory.redis.autoconfigure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryConfig;
import org.springframework.ai.model.chat.memory.repository.redis.autoconfigure.RedisChatMemoryRepositoryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Configuration properties for Redis-based chat memory.
 *
 * @author Brian Sam-Bodden
 * @deprecated Use {@link RedisChatMemoryRepositoryProperties} instead.
 */
@Deprecated(since = "2.0.1", forRemoval = true)
@ConfigurationProperties(prefix = RedisChatMemoryProperties.CONFIG_PREFIX)
public class RedisChatMemoryProperties {

	public static final String CONFIG_PREFIX = "spring.ai.chat.memory.redis";

	/**
	 * Redis server host.
	 */
	private String host = "localhost";

	/**
	 * Redis server port.
	 */
	private int port = 6379;

	/**
	 * Name of the Redis search index.
	 */
	private String indexName = RedisChatMemoryConfig.DEFAULT_INDEX_NAME;

	/**
	 * Key prefix for Redis chat memory entries.
	 */
	private String keyPrefix = RedisChatMemoryConfig.DEFAULT_KEY_PREFIX;

	/**
	 * Time to live for chat memory entries. Default is no expiration.
	 */
	private @Nullable Duration timeToLive;

	/**
	 * Whether to initialize the Redis schema. Default is true.
	 */
	private Boolean initializeSchema = true;

	/**
	 * Maximum number of conversation IDs to return (defaults to 1000).
	 */
	private Integer maxConversationIds = RedisChatMemoryConfig.DEFAULT_MAX_RESULTS;

	/**
	 * Maximum number of messages to return per conversation (defaults to 1000).
	 */
	private Integer maxMessagesPerConversation = RedisChatMemoryConfig.DEFAULT_MAX_RESULTS;

	/**
	 * Metadata field definitions for proper indexing. Compatible with RedisVL schema
	 * format. Example: <pre>
	 * spring.ai.chat.memory.redis.metadata-fields[0].name=priority
	 * spring.ai.chat.memory.redis.metadata-fields[0].type=tag
	 * spring.ai.chat.memory.redis.metadata-fields[1].name=score
	 * spring.ai.chat.memory.redis.metadata-fields[1].type=numeric
	 * </pre>
	 */
	private List<Map<String, String>> metadataFields = new ArrayList<>();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.chat.memory.repository.redis.host")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public String getHost() {
		return this.host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.chat.memory.repository.redis.port")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public int getPort() {
		return this.port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.chat.memory.repository.redis.index-name")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.chat.memory.repository.redis.key-prefix")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public String getKeyPrefix() {
		return this.keyPrefix;
	}

	public void setKeyPrefix(String keyPrefix) {
		this.keyPrefix = keyPrefix;
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.chat.memory.repository.redis.time-to-live")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public @Nullable Duration getTimeToLive() {
		return this.timeToLive;
	}

	public void setTimeToLive(@Nullable Duration timeToLive) {
		this.timeToLive = timeToLive;
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.chat.memory.repository.redis.initialize-schema")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public Boolean getInitializeSchema() {
		return this.initializeSchema;
	}

	public void setInitializeSchema(Boolean initializeSchema) {
		this.initializeSchema = initializeSchema;
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.chat.memory.repository.redis.max-conversation-ids")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public Integer getMaxConversationIds() {
		return this.maxConversationIds;
	}

	public void setMaxConversationIds(Integer maxConversationIds) {
		this.maxConversationIds = maxConversationIds;
	}

	@DeprecatedConfigurationProperty(
			replacement = "spring.ai.chat.memory.repository.redis.max-messages-per-conversation")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public Integer getMaxMessagesPerConversation() {
		return this.maxMessagesPerConversation;
	}

	public void setMaxMessagesPerConversation(Integer maxMessagesPerConversation) {
		this.maxMessagesPerConversation = maxMessagesPerConversation;
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.chat.memory.repository.redis.metadata-fields")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public List<Map<String, String>> getMetadataFields() {
		return this.metadataFields;
	}

	public void setMetadataFields(List<Map<String, String>> metadataFields) {
		this.metadataFields = metadataFields;
	}

}
