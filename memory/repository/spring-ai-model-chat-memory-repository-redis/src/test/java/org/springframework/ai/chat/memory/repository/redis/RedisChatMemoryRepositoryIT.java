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

import java.util.List;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
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
 * Integration tests for RedisChatMemoryRepository implementation of ChatMemoryRepository
 * interface.
 *
 * @author Brian Sam-Bodden
 */
@Testcontainers
class RedisChatMemoryRepositoryIT {

	private static final Logger logger = LoggerFactory.getLogger(RedisChatMemoryRepositoryIT.class);

	@Container
	static RedisContainer redisContainer = new RedisContainer("redis/redis-stack:latest");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	private ChatMemoryRepository chatMemoryRepository;

	private JedisPooled jedisClient;

	@BeforeEach
	void setUp() {
		// Create JedisPooled directly with container properties for more reliable
		// connection
		jedisClient = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());
		RedisChatMemoryRepository chatMemory = RedisChatMemoryRepository.builder()
			.jedisClient(jedisClient)
			.indexName("test-" + RedisChatMemoryConfig.DEFAULT_INDEX_NAME)
			.build();

		chatMemoryRepository = chatMemory;

		// Clear any existing data
		for (String conversationId : chatMemoryRepository.findConversationIds()) {
			chatMemoryRepository.deleteByConversationId(conversationId);
		}
	}

	@AfterEach
	void tearDown() {
		if (jedisClient != null) {
			jedisClient.close();
		}
	}

	@Test
	void shouldFindAllConversationIds() {
		this.contextRunner.run(context -> {
			// Add messages for multiple conversations
			chatMemoryRepository.saveAll("conversation-1", List.of(new UserMessage("Hello from conversation 1"),
					new AssistantMessage("Hi there from conversation 1")));

			chatMemoryRepository.saveAll("conversation-2", List.of(new UserMessage("Hello from conversation 2"),
					new AssistantMessage("Hi there from conversation 2")));

			// Verify we can get all conversation IDs
			List<String> conversationIds = chatMemoryRepository.findConversationIds();
			assertThat(conversationIds).hasSize(2);
			assertThat(conversationIds).containsExactlyInAnyOrder("conversation-1", "conversation-2");
		});
	}

	@Test
	void shouldEfficientlyFindAllConversationIdsWithAggregation() {
		this.contextRunner.run(context -> {
			// Add a large number of messages across fewer conversations to verify
			// deduplication
			for (int i = 0; i < 10; i++) {
				chatMemoryRepository.saveAll("conversation-A", List.of(new UserMessage("Message " + i + " in A")));
				chatMemoryRepository.saveAll("conversation-B", List.of(new UserMessage("Message " + i + " in B")));
				chatMemoryRepository.saveAll("conversation-C", List.of(new UserMessage("Message " + i + " in C")));
			}

			List<String> conversationIds = chatMemoryRepository.findConversationIds();

			// Verify correctness
			assertThat(conversationIds).hasSize(3);
			assertThat(conversationIds).containsExactlyInAnyOrder("conversation-A", "conversation-B", "conversation-C");
		});
	}

	@Test
	void shouldFindMessagesByConversationId() {
		this.contextRunner.run(context -> {
			// Add messages for a conversation
			List<Message> messages = List.of(new UserMessage("Hello"), new AssistantMessage("Hi there!"),
					new UserMessage("How are you?"));
			chatMemoryRepository.saveAll("test-conversation", messages);

			// Verify we can retrieve messages by conversation ID
			List<Message> retrievedMessages = chatMemoryRepository.findByConversationId("test-conversation");
			assertThat(retrievedMessages).hasSize(3);
			assertThat(retrievedMessages.get(0).getText()).isEqualTo("Hello");
			assertThat(retrievedMessages.get(1).getText()).isEqualTo("Hi there!");
			assertThat(retrievedMessages.get(2).getText()).isEqualTo("How are you?");
		});
	}

	@Test
	void shouldSaveAllMessagesForConversation() {
		this.contextRunner.run(context -> {
			// Add some initial messages
			chatMemoryRepository.saveAll("test-conversation", List.of(new UserMessage("Initial message")));

			// Verify initial state
			List<Message> initialMessages = chatMemoryRepository.findByConversationId("test-conversation");
			assertThat(initialMessages).hasSize(1);

			// Save all with new messages (should replace existing ones)
			List<Message> newMessages = List.of(new UserMessage("New message 1"), new AssistantMessage("New message 2"),
					new UserMessage("New message 3"));
			chatMemoryRepository.saveAll("test-conversation", newMessages);

			// Verify new state
			List<Message> latestMessages = chatMemoryRepository.findByConversationId("test-conversation");
			assertThat(latestMessages).hasSize(3);
			assertThat(latestMessages.get(0).getText()).isEqualTo("New message 1");
			assertThat(latestMessages.get(1).getText()).isEqualTo("New message 2");
			assertThat(latestMessages.get(2).getText()).isEqualTo("New message 3");
		});
	}

	@Test
	void shouldDeleteConversation() {
		this.contextRunner.run(context -> {
			// Add messages for a conversation
			chatMemoryRepository.saveAll("test-conversation",
					List.of(new UserMessage("Hello"), new AssistantMessage("Hi there!")));

			// Verify initial state
			assertThat(chatMemoryRepository.findByConversationId("test-conversation")).hasSize(2);

			// Delete the conversation
			chatMemoryRepository.deleteByConversationId("test-conversation");

			// Verify conversation is gone
			assertThat(chatMemoryRepository.findByConversationId("test-conversation")).isEmpty();
			assertThat(chatMemoryRepository.findConversationIds()).doesNotContain("test-conversation");
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	static class TestApplication {

		@Bean
		ChatMemoryRepository chatMemoryRepository() {
			return RedisChatMemoryRepository.builder()
				.jedisClient(new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort()))
				.indexName("test-" + RedisChatMemoryConfig.DEFAULT_INDEX_NAME)
				.build();
		}

	}

}
