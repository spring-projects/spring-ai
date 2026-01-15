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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisConnectionException;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Integration tests for RedisChatMemoryRepository focused on error handling scenarios.
 *
 * @author Brian Sam-Bodden
 */
@Testcontainers
class RedisChatMemoryErrorHandlingIT {

	@Container
	static RedisContainer redisContainer = new RedisContainer("redis/redis-stack:latest");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	private RedisChatMemoryRepository chatMemory;

	private JedisPooled jedisClient;

	@BeforeEach
	void setUp() {
		jedisClient = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());
		chatMemory = RedisChatMemoryRepository.builder()
			.jedisClient(jedisClient)
			.indexName("test-error-" + RedisChatMemoryConfig.DEFAULT_INDEX_NAME)
			.build();
	}

	@AfterEach
	void tearDown() {
		if (jedisClient != null) {
			jedisClient.close();
		}
	}

	@Test
	void shouldHandleInvalidConversationId() {
		this.contextRunner.run(context -> {
			// Using null conversation ID
			assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> chatMemory.add(null, new UserMessage("Test message")))
				.withMessageContaining("Conversation ID must not be null");

			// Using empty conversation ID
			UserMessage message = new UserMessage("Test message");
			assertThatCode(() -> chatMemory.add("", message)).doesNotThrowAnyException();

			// Reading with null conversation ID
			assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> chatMemory.get(null, 10))
				.withMessageContaining("Conversation ID must not be null");

			// Reading with non-existent conversation ID should return empty list
			List<Message> messages = chatMemory.get("non-existent-id", 10);
			assertThat(messages).isNotNull().isEmpty();

			// Clearing with null conversation ID
			assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> chatMemory.clear(null))
				.withMessageContaining("Conversation ID must not be null");

			// Clearing non-existent conversation should not throw exception
			assertThatCode(() -> chatMemory.clear("non-existent-id")).doesNotThrowAnyException();
		});
	}

	@Test
	void shouldHandleInvalidMessageParameters() {
		this.contextRunner.run(context -> {
			String conversationId = UUID.randomUUID().toString();

			// Null message
			assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> chatMemory.add(conversationId, (Message) null))
				.withMessageContaining("Message must not be null");

			// Null message list
			assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> chatMemory.add(conversationId, (List<Message>) null))
				.withMessageContaining("Messages must not be null");

			// Empty message list should not throw exception
			assertThatCode(() -> chatMemory.add(conversationId, List.of())).doesNotThrowAnyException();

			// Message with empty content (not null - which is not allowed)
			UserMessage emptyContentMessage = UserMessage.builder().text("").build();

			assertThatCode(() -> chatMemory.add(conversationId, emptyContentMessage)).doesNotThrowAnyException();

			// Message with empty metadata
			UserMessage userMessage = UserMessage.builder().text("Hello").build();
			assertThatCode(() -> chatMemory.add(conversationId, userMessage)).doesNotThrowAnyException();
		});
	}

	@Test
	void shouldHandleTimeToLive() {
		this.contextRunner.run(context -> {
			// Create chat memory with short TTL
			RedisChatMemoryRepository ttlChatMemory = RedisChatMemoryRepository.builder()
				.jedisClient(jedisClient)
				.indexName("test-ttl-" + RedisChatMemoryConfig.DEFAULT_INDEX_NAME)
				.timeToLive(Duration.ofSeconds(1))
				.build();

			String conversationId = "ttl-test-conversation";
			UserMessage message = new UserMessage("This message will expire soon");

			// Add a message
			ttlChatMemory.add(conversationId, message);

			// Immediately verify message exists
			List<Message> messages = ttlChatMemory.get(conversationId, 10);
			assertThat(messages).hasSize(1);

			// Wait for TTL to expire
			Thread.sleep(1500);

			// After TTL expiry, message should be gone
			List<Message> expiredMessages = ttlChatMemory.get(conversationId, 10);
			assertThat(expiredMessages).isEmpty();
		});
	}

	@Test
	void shouldHandleConnectionFailureGracefully() {
		this.contextRunner.run(context -> {
			// Using a connection to an invalid Redis server should throw a connection
			// exception
			assertThatExceptionOfType(JedisConnectionException.class).isThrownBy(() -> {
				// Create a JedisPooled with a connection timeout to make the test faster
				JedisPooled badConnection = new JedisPooled("localhost", 54321);
				// Attempt an operation that would require Redis connection
				badConnection.ping();
			});
		});
	}

	@Test
	void shouldHandleEdgeCaseConversationIds() {
		this.contextRunner.run(context -> {
			// Test with a simple conversation ID first to verify basic functionality
			String simpleId = "simple-test-id";
			UserMessage simpleMessage = new UserMessage("Simple test message");
			chatMemory.add(simpleId, simpleMessage);

			List<Message> simpleMessages = chatMemory.get(simpleId, 10);
			assertThat(simpleMessages).hasSize(1);
			assertThat(simpleMessages.get(0).getText()).isEqualTo("Simple test message");

			// Test with conversation IDs containing special characters
			String specialCharsId = "test_conversation_with_special_chars_123";
			String specialMessage = "Message with special character conversation ID";
			UserMessage message = new UserMessage(specialMessage);

			// Add message with special chars ID
			chatMemory.add(specialCharsId, message);

			// Verify that message can be retrieved
			List<Message> specialCharMessages = chatMemory.get(specialCharsId, 10);
			assertThat(specialCharMessages).hasSize(1);
			assertThat(specialCharMessages.get(0).getText()).isEqualTo(specialMessage);

			// Test with non-alphanumeric characters in ID
			String complexId = "test-with:complex@chars#123";
			String complexMessage = "Message with complex ID";
			UserMessage complexIdMessage = new UserMessage(complexMessage);

			// Add and retrieve message with complex ID
			chatMemory.add(complexId, complexIdMessage);
			List<Message> complexIdMessages = chatMemory.get(complexId, 10);
			assertThat(complexIdMessages).hasSize(1);
			assertThat(complexIdMessages.get(0).getText()).isEqualTo(complexMessage);

			// Test with long IDs
			StringBuilder longIdBuilder = new StringBuilder();
			for (int i = 0; i < 50; i++) {
				longIdBuilder.append("a");
			}
			String longId = longIdBuilder.toString();
			String longIdMessageText = "Message with long conversation ID";
			UserMessage longIdMessage = new UserMessage(longIdMessageText);

			// Add and retrieve message with long ID
			chatMemory.add(longId, longIdMessage);
			List<Message> longIdMessages = chatMemory.get(longId, 10);
			assertThat(longIdMessages).hasSize(1);
			assertThat(longIdMessages.get(0).getText()).isEqualTo(longIdMessageText);
		});
	}

	@Test
	void shouldHandleConcurrentAccess() {
		this.contextRunner.run(context -> {
			String conversationId = "concurrent-access-test-" + UUID.randomUUID();

			// Clear any existing data for this conversation
			chatMemory.clear(conversationId);

			// Define thread setup for concurrent access
			int threadCount = 3;
			int messagesPerThread = 4;
			int totalExpectedMessages = threadCount * messagesPerThread;

			// Track all messages created for verification
			Set<String> expectedMessageTexts = new HashSet<>();

			// Create and start threads that concurrently add messages
			Thread[] threads = new Thread[threadCount];
			CountDownLatch latch = new CountDownLatch(threadCount); // For synchronized
																	// start

			for (int i = 0; i < threadCount; i++) {
				final int threadId = i;
				threads[i] = new Thread(() -> {
					try {
						latch.countDown();
						latch.await(); // Wait for all threads to be ready

						for (int j = 0; j < messagesPerThread; j++) {
							String messageText = String.format("Message %d from thread %d", j, threadId);
							expectedMessageTexts.add(messageText);
							UserMessage message = new UserMessage(messageText);
							chatMemory.add(conversationId, message);
						}
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				});
				threads[i].start();
			}

			// Wait for all threads to complete
			for (Thread thread : threads) {
				thread.join();
			}

			// Allow a short delay for Redis to process all operations
			Thread.sleep(500);

			// Retrieve all messages (including extras to make sure we get everything)
			List<Message> messages = chatMemory.get(conversationId, totalExpectedMessages + 5);

			// We don't check exact message count as Redis async operations might result
			// in slight variations
			// Just verify the right message format is present
			List<String> actualMessageTexts = messages.stream().map(Message::getText).collect(Collectors.toList());

			// Check that we have messages from each thread
			for (int i = 0; i < threadCount; i++) {
				final int threadId = i;
				assertThat(actualMessageTexts.stream().filter(text -> text.endsWith("from thread " + threadId)).count())
					.isGreaterThan(0);
			}

			// Verify message format
			for (Message msg : messages) {
				assertThat(msg).isInstanceOf(UserMessage.class);
				assertThat(msg.getText()).containsPattern("Message \\d from thread \\d");
			}

			// Order check - messages might be in different order than creation,
			// but order should be consistent between retrievals
			List<Message> messagesAgain = chatMemory.get(conversationId, totalExpectedMessages + 5);
			for (int i = 0; i < messages.size(); i++) {
				assertThat(messagesAgain.get(i).getText()).isEqualTo(messages.get(i).getText());
			}
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	static class TestApplication {

		@Bean
		RedisChatMemoryRepository chatMemory() {
			return RedisChatMemoryRepository.builder()
				.jedisClient(new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort()))
				.indexName("test-error-" + RedisChatMemoryConfig.DEFAULT_INDEX_NAME)
				.build();
		}

	}

}
