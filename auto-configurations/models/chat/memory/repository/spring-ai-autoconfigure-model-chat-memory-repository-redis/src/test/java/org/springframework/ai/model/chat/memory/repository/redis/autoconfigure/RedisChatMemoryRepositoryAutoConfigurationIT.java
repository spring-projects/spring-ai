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

import java.time.Duration;

import com.redis.testcontainers.RedisStackContainer;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisChatMemoryRepositoryAutoConfigurationIT {

	@Container
	static RedisStackContainer redisContainer = new RedisStackContainer(
			RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG))
		.withExposedPorts(6379);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(RedisChatMemoryRepositoryAutoConfiguration.class,
				DataRedisAutoConfiguration.class))
		.withPropertyValues("spring.data.redis.host=" + redisContainer.getHost(),
				"spring.data.redis.port=" + redisContainer.getFirstMappedPort(),
				"spring.ai.chat.memory.repository.redis.host=" + redisContainer.getHost(),
				"spring.ai.chat.memory.repository.redis.port=" + redisContainer.getFirstMappedPort());

	@Test
	void autoConfigurationRegistersExpectedBeans() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(RedisChatMemoryRepository.class);
			assertThat(context).hasSingleBean(ChatMemoryRepository.class);
		});
	}

	@Test
	void customPropertiesAreApplied() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.memory.repository.redis.index-name=custom-index",
					"spring.ai.chat.memory.repository.redis.key-prefix=custom-prefix:",
					"spring.ai.chat.memory.repository.redis.time-to-live=300s",
					"spring.ai.chat.memory.repository.redis.initialize-schema=false",
					"spring.ai.chat.memory.repository.redis.max-conversation-ids=42",
					"spring.ai.chat.memory.repository.redis.max-messages-per-conversation=24")
			.run(context -> {
				RedisChatMemoryRepositoryProperties properties = context
					.getBean(RedisChatMemoryRepositoryProperties.class);
				assertThat(properties.getIndexName()).isEqualTo("custom-index");
				assertThat(properties.getKeyPrefix()).isEqualTo("custom-prefix:");
				assertThat(properties.getTimeToLive()).isEqualTo(Duration.ofSeconds(300));
				assertThat(properties.getInitializeSchema()).isFalse();
				assertThat(properties.getMaxConversationIds()).isEqualTo(42);
				assertThat(properties.getMaxMessagesPerConversation()).isEqualTo(24);

				RedisChatMemoryRepository chatMemory = context.getBean(RedisChatMemoryRepository.class);
				assertThat(chatMemory.getIndexName()).isEqualTo("custom-index");
			});
	}

	@Test
	void chatMemoryRepositoryIsProvidedByRedisChatMemory() {
		this.contextRunner.run(context -> {
			RedisChatMemoryRepository redisChatMemory = context.getBean(RedisChatMemoryRepository.class);
			ChatMemoryRepository repository = context.getBean(ChatMemoryRepository.class);

			assertThat(repository).isSameAs(redisChatMemory);
		});
	}

	@Test
	void defaultChatMemoryUsesRedisRepository() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(ChatMemoryAutoConfiguration.class)).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(ChatMemoryRepository.class);
			assertThat(context).hasSingleBean(RedisChatMemoryRepository.class);
			assertThat(context).hasSingleBean(ChatMemory.class);

			ChatMemory chatMemory = context.getBean(ChatMemory.class);
			chatMemory.add("default-chat-memory", new UserMessage("Hello"));

			RedisChatMemoryRepository repository = context.getBean(RedisChatMemoryRepository.class);
			assertThat(repository.findByConversationId("default-chat-memory")).extracting(Message::getText)
				.containsExactly("Hello");
		});
	}

	@Test
	void customChatMemoryUsesRedisRepository() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(ChatMemoryAutoConfiguration.class))
			.withUserConfiguration(CustomChatMemoryConfiguration.class)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(ChatMemoryRepository.class);
				assertThat(context).hasSingleBean(RedisChatMemoryRepository.class);
				assertThat(context).hasSingleBean(ChatMemory.class);
				assertThat(context.getBean(ChatMemory.class)).isSameAs(context.getBean("customChatMemory"));

				ChatMemory chatMemory = context.getBean(ChatMemory.class);
				chatMemory.add("custom-chat-memory", new UserMessage("Hello"));

				RedisChatMemoryRepository repository = context.getBean(RedisChatMemoryRepository.class);
				assertThat(repository.findByConversationId("custom-chat-memory")).extracting(Message::getText)
					.containsExactly("Hello");
			});
	}

	@Test
	void customChatMemoryRepositoryTakesPrecedence() {
		ChatMemoryRepository repository = new InMemoryChatMemoryRepository();
		this.contextRunner.withConfiguration(AutoConfigurations.of(ChatMemoryAutoConfiguration.class))
			.withBean(ChatMemoryRepository.class, () -> repository)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(ChatMemoryRepository.class);
				assertThat(context).doesNotHaveBean(RedisChatMemoryRepository.class);
				assertThat(context.getBean(ChatMemoryRepository.class)).isSameAs(repository);
				assertThat(context).hasSingleBean(ChatMemory.class);

				context.getBean(ChatMemory.class).add("custom-repository", new UserMessage("Hello"));
				assertThat(repository.findByConversationId("custom-repository")).extracting(Message::getText)
					.containsExactly("Hello");
			});
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomChatMemoryConfiguration {

		@Bean
		ChatMemory customChatMemory(RedisChatMemoryRepository repository) {
			return MessageWindowChatMemory.builder().chatMemoryRepository(repository).maxMessages(10).build();
		}

	}

}
