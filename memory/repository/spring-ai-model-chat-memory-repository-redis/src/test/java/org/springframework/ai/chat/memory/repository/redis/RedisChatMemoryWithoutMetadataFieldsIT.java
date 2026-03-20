/*
 * Copyright 2023-2026 the original author or authors.
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

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration tests for RedisChatMemoryRepository without a metadataFields. Verifies
 * behavior when metadata fields are not explicitly indexed.
 *
 * @author JunSeop Lee
 * @since 2.0.0
 */
@Testcontainers
class RedisChatMemoryWithoutMetadataFieldsIT {

	@Container
	static RedisContainer redisContainer = new RedisContainer("redis/redis-stack:latest");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	private RedisChatMemoryRepository chatMemory;

	private JedisPooled jedisClient;

	@BeforeEach
	void setUp() {
		jedisClient = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());

		// Use a unique index name to ensure we get a fresh schema
		String uniqueIndexName = "test-schema-" + System.currentTimeMillis();

		chatMemory = RedisChatMemoryRepository.builder().jedisClient(jedisClient).indexName(uniqueIndexName).build();

		// Clear existing test data
		chatMemory.findConversationIds().forEach(chatMemory::clear);
	}

	@AfterEach
	void tearDown() {
		if (jedisClient != null) {
			jedisClient.close();
		}
	}

	@Test
	void shouldNotThrowWhenQueryingMetadataWithoutMetadataFields() {
		this.contextRunner.run(context -> {
			String conversationId = "test-metadata-schema";

			UserMessage userMsg = new UserMessage("User message");
			userMsg.getMetadata().put("priority", "high");
			userMsg.getMetadata().put("category", "question");
			userMsg.getMetadata().put("score", 95);

			chatMemory.add(conversationId, userMsg);

			assertThatCode(
					() -> ((AdvancedRedisChatMemoryRepository) chatMemory).findByMetadata("priority", "high", 10))
				.doesNotThrowAnyException();
		});
	}

	@Test
	void shouldReturnEmptyWhenSearchingMetadataWithoutMetadataFields() {
		this.contextRunner.run(context -> {
			String conversationId = "test-metadata-schema";

			UserMessage userMsg1 = new UserMessage("First user message");
			userMsg1.getMetadata().put("priority", "high");
			userMsg1.getMetadata().put("category", "question");
			userMsg1.getMetadata().put("score", 95);

			AssistantMessage assistantMsg = new AssistantMessage("First assistant message");
			assistantMsg.getMetadata().put("model", "gpt-4");
			assistantMsg.getMetadata().put("confidence", 0.95);
			assistantMsg.getMetadata().put("category", "response");

			UserMessage userMsg2 = new UserMessage("Second user message");
			userMsg2.getMetadata().put("priority", "low");
			userMsg2.getMetadata().put("category", "question");
			userMsg2.getMetadata().put("score", 75);

			chatMemory.add(conversationId, userMsg1);
			chatMemory.add(conversationId, assistantMsg);
			chatMemory.add(conversationId, userMsg2);

			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> highPriorityMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByMetadata("priority", "high", 10);

			assertThat(highPriorityMessages).isEmpty();

			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> questionCategoryMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByMetadata("category", "question", 10);

			assertThat(questionCategoryMessages).isEmpty();

			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> confidentMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByMetadata("confidence", 0.95, 10);

			assertThat(confidentMessages).isEmpty();
		});
	}

	@Test
	void shouldPersistMetadataEvenWhenNotIndexed() {
		this.contextRunner.run(context -> {
			String conversationId = "test-metadata-persistence";

			UserMessage userMsg = new UserMessage("User message");
			userMsg.getMetadata().put("priority", "high");
			userMsg.getMetadata().put("category", "question");
			userMsg.getMetadata().put("score", 95);

			chatMemory.add(conversationId, userMsg);

			List<Message> findAllMessages = chatMemory.findByConversationId(conversationId);

			assertThat(findAllMessages).hasSize(1);

			assertThat(findAllMessages.get(0).getMetadata()).containsEntry("priority", "high")
				.containsEntry("category", "question");

			Object score0 = findAllMessages.get(0).getMetadata().get("score");
			assertThat(score0).isInstanceOf(Number.class);
			assertThat(((Number) score0).intValue()).isEqualTo(95);
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	static class TestApplication {

		@Bean
		RedisChatMemoryRepository chatMemory() {
			// Use a unique index name to ensure we get a fresh schema
			String uniqueIndexName = "test-schema-app-" + System.currentTimeMillis();

			return RedisChatMemoryRepository.builder()
				.jedisClient(new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort()))
				.indexName(uniqueIndexName)
				.build();
		}

	}

}
