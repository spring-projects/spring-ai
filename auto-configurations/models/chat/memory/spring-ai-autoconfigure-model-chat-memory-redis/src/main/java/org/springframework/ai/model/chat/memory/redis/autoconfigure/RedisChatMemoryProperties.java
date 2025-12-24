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

package org.springframework.ai.model.chat.memory.redis.autoconfigure;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Redis-based chat memory.
 *
 * @author Brian Sam-Bodden
 */
@ConfigurationProperties(prefix = "spring.ai.chat.memory.redis")
public class RedisChatMemoryProperties {

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
	private Duration timeToLive;

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
	private List<Map<String, String>> metadataFields;

	public String getHost() {
		return this.host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return this.port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public String getKeyPrefix() {
		return this.keyPrefix;
	}

	public void setKeyPrefix(String keyPrefix) {
		this.keyPrefix = keyPrefix;
	}

	public Duration getTimeToLive() {
		return this.timeToLive;
	}

	public void setTimeToLive(Duration timeToLive) {
		this.timeToLive = timeToLive;
	}

	public Boolean getInitializeSchema() {
		return this.initializeSchema;
	}

	public void setInitializeSchema(Boolean initializeSchema) {
		this.initializeSchema = initializeSchema;
	}

	public Integer getMaxConversationIds() {
		return this.maxConversationIds;
	}

	public void setMaxConversationIds(Integer maxConversationIds) {
		this.maxConversationIds = maxConversationIds;
	}

	public Integer getMaxMessagesPerConversation() {
		return this.maxMessagesPerConversation;
	}

	public void setMaxMessagesPerConversation(Integer maxMessagesPerConversation) {
		this.maxMessagesPerConversation = maxMessagesPerConversation;
	}

	public List<Map<String, String>> getMetadataFields() {
		return this.metadataFields;
	}

	public void setMetadataFields(List<Map<String, String>> metadataFields) {
		this.metadataFields = metadataFields;
	}

}
