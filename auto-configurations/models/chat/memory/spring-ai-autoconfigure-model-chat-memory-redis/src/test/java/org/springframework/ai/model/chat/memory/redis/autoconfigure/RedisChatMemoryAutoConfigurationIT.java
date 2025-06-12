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

import com.redis.testcontainers.RedisStackContainer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.redis.RedisChatMemory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisChatMemoryAutoConfigurationIT {

	private static final Logger logger = LoggerFactory.getLogger(RedisChatMemoryAutoConfigurationIT.class);

	@Container
	static RedisStackContainer redisContainer = new RedisStackContainer(
			RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG))
		.withExposedPorts(6379);

	@BeforeAll
	static void setup() {
		logger.info("Redis container running on host: {} and port: {}", redisContainer.getHost(),
				redisContainer.getFirstMappedPort());
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(RedisChatMemoryAutoConfiguration.class, RedisAutoConfiguration.class))
		.withPropertyValues("spring.data.redis.host=" + redisContainer.getHost(),
				"spring.data.redis.port=" + redisContainer.getFirstMappedPort(),
				// Pass the same Redis connection properties to our chat memory properties
				"spring.ai.chat.memory.redis.host=" + redisContainer.getHost(),
				"spring.ai.chat.memory.redis.port=" + redisContainer.getFirstMappedPort());

	@Test
	void autoConfigurationRegistersExpectedBeans() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(RedisChatMemory.class);
			assertThat(context).hasSingleBean(ChatMemory.class);
			assertThat(context).hasSingleBean(ChatMemoryRepository.class);
		});
	}

	@Test
	void customPropertiesAreApplied() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.memory.redis.index-name=custom-index",
					"spring.ai.chat.memory.redis.key-prefix=custom-prefix:",
					"spring.ai.chat.memory.redis.time-to-live=300s")
			.run(context -> {
				RedisChatMemory chatMemory = context.getBean(RedisChatMemory.class);
				assertThat(chatMemory).isNotNull();
			});
	}

	@Test
	void chatMemoryRepositoryIsProvidedByRedisChatMemory() {
		this.contextRunner.run(context -> {
			RedisChatMemory redisChatMemory = context.getBean(RedisChatMemory.class);
			ChatMemory chatMemory = context.getBean(ChatMemory.class);
			ChatMemoryRepository repository = context.getBean(ChatMemoryRepository.class);

			assertThat(chatMemory).isSameAs(redisChatMemory);
			assertThat(repository).isSameAs(redisChatMemory);
		});
	}

}