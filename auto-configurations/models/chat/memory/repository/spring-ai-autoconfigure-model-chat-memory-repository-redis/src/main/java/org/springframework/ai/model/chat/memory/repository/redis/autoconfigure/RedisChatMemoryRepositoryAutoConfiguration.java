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

package org.springframework.ai.model.chat.memory.repository.redis.autoconfigure;

import redis.clients.jedis.RedisClient;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration for Redis-based chat memory implementation.
 *
 * @author Brian Sam-Bodden
 * @author Yanming Zhou
 * @author guan xu
 * @since 2.0.1
 */
@AutoConfiguration(before = ChatMemoryAutoConfiguration.class)
@ConditionalOnClass({ RedisChatMemoryRepository.class, RedisClient.class })
public class RedisChatMemoryRepositoryAutoConfiguration {

	/**
	 * Provides Redis chat memory repository configuration properties. Binds from
	 * {@code spring.ai.chat.memory.repository.redis} and falls back to the legacy
	 * {@code spring.ai.chat.memory.redis} prefix for smooth transition from the
	 * deprecated {@code spring-ai-autoconfigure-model-chat-memory-redis} module.
	 * @param environment the Spring environment for property binding
	 * @return the Redis chat memory repository properties
	 */
	@Bean
	@ConditionalOnMissingBean
	public RedisChatMemoryRepositoryProperties redisChatMemoryRepositoryProperties(Environment environment) {
		RedisChatMemoryRepositoryProperties properties = new RedisChatMemoryRepositoryProperties();
		Binder binder = Binder.get(environment);
		BindResult<RedisChatMemoryRepositoryProperties> result = binder
			.bind(RedisChatMemoryRepositoryProperties.CONFIG_PREFIX, Bindable.ofInstance(properties));

		// Fallback to legacy prefix
		if (!result.isBound()) {
			binder.bind("spring.ai.chat.memory.redis", Bindable.ofInstance(properties));
		}

		return properties;
	}

	@Bean
	@ConditionalOnMissingBean({ RedisChatMemoryRepository.class, ChatMemory.class, ChatMemoryRepository.class })
	public RedisChatMemoryRepository redisChatMemoryRepository(RedisChatMemoryRepositoryProperties properties) {
		RedisClient jedisClient = jedisClient(properties);
		RedisChatMemoryRepository.Builder builder = RedisChatMemoryRepository.builder().jedisClient(jedisClient);

		// Apply configuration if provided
		if (StringUtils.hasText(properties.getIndexName())) {
			builder.indexName(properties.getIndexName());
		}

		if (StringUtils.hasText(properties.getKeyPrefix())) {
			builder.keyPrefix(properties.getKeyPrefix());
		}

		if (properties.getTimeToLive() != null && properties.getTimeToLive().toSeconds() > 0) {
			builder.timeToLive(properties.getTimeToLive());
		}

		if (properties.getInitializeSchema() != null) {
			builder.initializeSchema(properties.getInitializeSchema());
		}

		if (properties.getMaxConversationIds() != null) {
			builder.maxConversationIds(properties.getMaxConversationIds());
		}

		if (properties.getMaxMessagesPerConversation() != null) {
			builder.maxMessagesPerConversation(properties.getMaxMessagesPerConversation());
		}

		if (properties.getMetadataFields() != null && !properties.getMetadataFields().isEmpty()) {
			builder.metadataFields(properties.getMetadataFields());
		}

		return builder.build();
	}

	private RedisClient jedisClient(RedisChatMemoryRepositoryProperties properties) {
		return RedisClient.builder().hostAndPort(properties.getHost(), properties.getPort()).build();
	}

}
