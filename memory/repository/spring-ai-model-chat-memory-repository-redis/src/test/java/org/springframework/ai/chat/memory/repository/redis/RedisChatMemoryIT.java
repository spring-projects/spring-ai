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
package org.springframework.ai.chat.memory.repository.redis;

import java.time.Duration;
import java.util.List;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RedisChatMemoryRepository using Redis Stack TestContainer.
 *
 * @author Brian Sam-Bodden
 */
@Testcontainers
class RedisChatMemoryIT {

	@Container
	static RedisContainer redisContainer = new RedisContainer("redis/redis-stack:latest");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	private RedisChatMemoryRepository chatMemory;

	private JedisPooled jedisClient;

	@BeforeEach
	void setUp() {
		// Create JedisPooled directly with container properties for more reliable
		// connection
		jedisClient = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());
		chatMemory = RedisChatMemoryRepository.builder()
			.jedisClient(jedisClient)
			.indexName("test-" + RedisChatMemoryConfig.DEFAULT_INDEX_NAME)
			.build();

		chatMemory.clear("test-conversation");
	}

	@AfterEach
	void tearDown() {
		if (jedisClient != null) {
			jedisClient.close();
		}
	}

	@Test
	void shouldStoreAndRetrieveMessages() {
		this.contextRunner.run(context -> {
			String conversationId = "test-conversation";

			// Add messages
			chatMemory.add(conversationId, new UserMessage("Hello"));
			chatMemory.add(conversationId, new AssistantMessage("Hi there!"));
			chatMemory.add(conversationId, new UserMessage("How are you?"));

			// Retrieve messages
			List<Message> messages = chatMemory.get(conversationId, 10);

			assertThat(messages).hasSize(3);
			assertThat(messages.get(0).getText()).isEqualTo("Hello");
			assertThat(messages.get(1).getText()).isEqualTo("Hi there!");
			assertThat(messages.get(2).getText()).isEqualTo("How are you?");
		});
	}

	@Test
	void shouldRespectMessageLimit() {
		this.contextRunner.run(context -> {
			String conversationId = "test-conversation";

			// Add messages
			chatMemory.add(conversationId, new UserMessage("Message 1"));
			chatMemory.add(conversationId, new AssistantMessage("Message 2"));
			chatMemory.add(conversationId, new UserMessage("Message 3"));

			// Retrieve limited messages
			List<Message> messages = chatMemory.get(conversationId, 2);

			assertThat(messages).hasSize(2);
		});
	}

	@Test
	void shouldClearConversation() {
		this.contextRunner.run(context -> {
			String conversationId = "test-conversation";

			// Add messages
			chatMemory.add(conversationId, new UserMessage("Hello"));
			chatMemory.add(conversationId, new AssistantMessage("Hi"));

			// Clear conversation
			chatMemory.clear(conversationId);

			// Verify messages are cleared
			List<Message> messages = chatMemory.get(conversationId, 10);
			assertThat(messages).isEmpty();
		});
	}

	@Test
	void shouldHandleBatchMessageAddition() {
		this.contextRunner.run(context -> {
			String conversationId = "test-conversation";
			List<Message> messageBatch = List.of(new UserMessage("Message 1"), //
					new AssistantMessage("Response 1"), //
					new UserMessage("Message 2"), //
					new AssistantMessage("Response 2") //
			);

			// Add batch of messages
			chatMemory.add(conversationId, messageBatch);

			// Verify all messages were stored
			List<Message> retrievedMessages = chatMemory.get(conversationId, 10);
			assertThat(retrievedMessages).hasSize(4);
		});
	}

	@Test
	void shouldHandleTimeToLive() throws InterruptedException {
		this.contextRunner.run(context -> {
			RedisChatMemoryRepository shortTtlMemory = RedisChatMemoryRepository.builder()
				.jedisClient(jedisClient)
				.indexName("test-ttl-" + RedisChatMemoryConfig.DEFAULT_INDEX_NAME)
				.timeToLive(Duration.ofSeconds(2))
				.keyPrefix("short-lived:")
				.build();

			String conversationId = "test-conversation";
			shortTtlMemory.add(conversationId, new UserMessage("This should expire"));

			// Verify message exists
			assertThat(shortTtlMemory.get(conversationId, 1)).hasSize(1);

			// Wait for TTL to expire
			Thread.sleep(2000);

			// Verify message is gone
			assertThat(shortTtlMemory.get(conversationId, 1)).isEmpty();
		});
	}

	@Test
	void shouldMaintainMessageOrder() {
		this.contextRunner.run(context -> {
			String conversationId = "test-conversation";
			// Add messages with minimal delay to test timestamp ordering
			chatMemory.add(conversationId, new UserMessage("First"));
			Thread.sleep(10);
			chatMemory.add(conversationId, new AssistantMessage("Second"));
			Thread.sleep(10);
			chatMemory.add(conversationId, new UserMessage("Third"));

			List<Message> messages = chatMemory.get(conversationId, 10);
			assertThat(messages).hasSize(3);
			assertThat(messages.get(0).getText()).isEqualTo("First");
			assertThat(messages.get(1).getText()).isEqualTo("Second");
			assertThat(messages.get(2).getText()).isEqualTo("Third");
		});
	}

	@Test
	void shouldHandleMultipleConversations() {
		this.contextRunner.run(context -> {
			String conv1 = "conversation-1";
			String conv2 = "conversation-2";

			chatMemory.add(conv1, new UserMessage("Conv1 Message"));
			chatMemory.add(conv2, new UserMessage("Conv2 Message"));

			List<Message> conv1Messages = chatMemory.get(conv1, 10);
			List<Message> conv2Messages = chatMemory.get(conv2, 10);

			assertThat(conv1Messages).hasSize(1);
			assertThat(conv2Messages).hasSize(1);
			assertThat(conv1Messages.get(0).getText()).isEqualTo("Conv1 Message");
			assertThat(conv2Messages.get(0).getText()).isEqualTo("Conv2 Message");
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	static class TestApplication {

		@Bean
		RedisChatMemoryRepository chatMemory() {
			return RedisChatMemoryRepository.builder()
				.jedisClient(new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort()))
				.indexName("test-" + RedisChatMemoryConfig.DEFAULT_INDEX_NAME)
				.timeToLive(Duration.ofMinutes(5))
				.build();
		}

	}

}
