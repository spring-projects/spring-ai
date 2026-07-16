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

import redis.clients.jedis.RedisClient;

import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.ai.model.chat.memory.repository.redis.autoconfigure.RedisChatMemoryRepositoryAutoConfiguration;
import org.springframework.ai.model.chat.memory.repository.redis.autoconfigure.RedisChatMemoryRepositoryProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Redis-based chat memory implementation.
 *
 * @author Brian Sam-Bodden
 * @author Yanming Zhou
 * @author Sebastien Deleuze
 * @deprecated Use {@link RedisChatMemoryRepositoryAutoConfiguration} instead.
 */
@Deprecated(since = "2.0.1", forRemoval = true)
@SuppressWarnings("removal")
@AutoConfiguration(before = ChatMemoryAutoConfiguration.class)
@ConditionalOnClass({ RedisChatMemoryRepository.class, RedisClient.class })
public class RedisChatMemoryAutoConfiguration {

	/**
	 * Binds the legacy {@code spring.ai.chat.memory.redis} properties onto the same
	 * {@link RedisChatMemoryRepositoryProperties} instance used by
	 * {@link RedisChatMemoryRepositoryAutoConfiguration}, so that existing configuration
	 * keeps working during the migration to the current
	 * {@code spring.ai.chat.memory.repository.redis} prefix.
	 * @param delegate the current Redis chat memory repository properties
	 * @return the deprecated Redis chat memory properties
	 * @deprecated for removal in favor of {@link RedisChatMemoryRepositoryProperties}
	 * @since 2.0.1
	 */
	@Deprecated(since = "2.0.1", forRemoval = true)
	@Bean
	@ConfigurationProperties(prefix = RedisChatMemoryProperties.CONFIG_PREFIX)
	RedisChatMemoryProperties redisChatMemoryProperties(RedisChatMemoryRepositoryProperties delegate) {
		return new RedisChatMemoryProperties(delegate);
	}

}
