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

import redis.clients.jedis.JedisPooled;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration for Redis-based chat memory implementation.
 *
 * @author Brian Sam-Bodden
 */
@AutoConfiguration(after = DataRedisAutoConfiguration.class)
@ConditionalOnClass({ RedisChatMemoryRepository.class, JedisPooled.class })
@EnableConfigurationProperties(RedisChatMemoryProperties.class)
public class RedisChatMemoryAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public JedisPooled jedisClient(RedisChatMemoryProperties properties) {
		return new JedisPooled(properties.getHost(), properties.getPort());
	}

	@Bean
	@ConditionalOnMissingBean({ RedisChatMemoryRepository.class, ChatMemory.class, ChatMemoryRepository.class })
	public RedisChatMemoryRepository redisChatMemory(JedisPooled jedisClient, RedisChatMemoryProperties properties) {
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

}
