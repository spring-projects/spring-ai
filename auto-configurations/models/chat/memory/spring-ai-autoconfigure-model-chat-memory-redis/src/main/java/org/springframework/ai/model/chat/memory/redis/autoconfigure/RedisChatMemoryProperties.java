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
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.model.chat.memory.repository.redis.autoconfigure.RedisChatMemoryRepositoryProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Configuration properties for Redis-based chat memory. Every getter/setter delegates to
 * the corresponding {@link RedisChatMemoryRepositoryProperties} instance, so that legacy
 * {@code spring.ai.chat.memory.redis} properties keep applying to the same underlying
 * configuration as the current {@code spring.ai.chat.memory.repository.redis} prefix.
 *
 * @author Brian Sam-Bodden
 * @author Sebastien Deleuze
 * @deprecated Use {@link RedisChatMemoryRepositoryProperties} instead.
 */
@Deprecated(since = "2.0.1", forRemoval = true)
public class RedisChatMemoryProperties {

	public static final String CONFIG_PREFIX = "spring.ai.chat.memory.redis";

	private final RedisChatMemoryRepositoryProperties delegate;

	public RedisChatMemoryProperties(RedisChatMemoryRepositoryProperties delegate) {
		this.delegate = delegate;
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.chat.memory.repository.redis.host")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public String getHost() {
		return this.delegate.getHost();
	}

	public void setHost(String host) {
		this.delegate.setHost(host);
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.chat.memory.repository.redis.port")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public int getPort() {
		return this.delegate.getPort();
	}

	public void setPort(int port) {
		this.delegate.setPort(port);
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.chat.memory.repository.redis.password")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public @Nullable String getPassword() {
		return this.delegate.getPassword();
	}

	public void setPassword(@Nullable String password) {
		this.delegate.setPassword(password);
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.chat.memory.repository.redis.index-name")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public String getIndexName() {
		return this.delegate.getIndexName();
	}

	public void setIndexName(String indexName) {
		this.delegate.setIndexName(indexName);
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.chat.memory.repository.redis.key-prefix")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public String getKeyPrefix() {
		return this.delegate.getKeyPrefix();
	}

	public void setKeyPrefix(String keyPrefix) {
		this.delegate.setKeyPrefix(keyPrefix);
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.chat.memory.repository.redis.time-to-live")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public @Nullable Duration getTimeToLive() {
		return this.delegate.getTimeToLive();
	}

	public void setTimeToLive(@Nullable Duration timeToLive) {
		this.delegate.setTimeToLive(timeToLive);
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.chat.memory.repository.redis.initialize-schema")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public Boolean getInitializeSchema() {
		return this.delegate.getInitializeSchema();
	}

	public void setInitializeSchema(Boolean initializeSchema) {
		this.delegate.setInitializeSchema(initializeSchema);
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.chat.memory.repository.redis.max-conversation-ids")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public Integer getMaxConversationIds() {
		return this.delegate.getMaxConversationIds();
	}

	public void setMaxConversationIds(Integer maxConversationIds) {
		this.delegate.setMaxConversationIds(maxConversationIds);
	}

	@DeprecatedConfigurationProperty(
			replacement = "spring.ai.chat.memory.repository.redis.max-messages-per-conversation")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public Integer getMaxMessagesPerConversation() {
		return this.delegate.getMaxMessagesPerConversation();
	}

	public void setMaxMessagesPerConversation(Integer maxMessagesPerConversation) {
		this.delegate.setMaxMessagesPerConversation(maxMessagesPerConversation);
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.chat.memory.repository.redis.metadata-fields")
	@Deprecated(since = "2.0.1", forRemoval = true)
	public List<Map<String, String>> getMetadataFields() {
		return this.delegate.getMetadataFields();
	}

	public void setMetadataFields(List<Map<String, String>> metadataFields) {
		this.delegate.setMetadataFields(metadataFields);
	}

}
