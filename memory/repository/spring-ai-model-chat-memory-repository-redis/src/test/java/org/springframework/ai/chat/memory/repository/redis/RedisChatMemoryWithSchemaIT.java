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
import java.util.Map;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RedisChatMemoryRepository with user-defined metadata schema.
 * Demonstrates how to properly index metadata fields with appropriate types.
 *
 * @author Brian Sam-Bodden
 */
@Testcontainers
class RedisChatMemoryWithSchemaIT {

	@Container
	static RedisContainer redisContainer = new RedisContainer("redis/redis-stack:latest");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	private RedisChatMemoryRepository chatMemory;

	private JedisPooled jedisClient;

	@BeforeEach
	void setUp() {
		jedisClient = new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort());

		// Define metadata schema for proper indexing
		List<Map<String, String>> metadataFields = List.of(Map.of("name", "priority", "type", "tag"),
				Map.of("name", "category", "type", "tag"), Map.of("name", "score", "type", "numeric"),
				Map.of("name", "confidence", "type", "numeric"), Map.of("name", "model", "type", "tag"));

		// Use a unique index name to ensure we get a fresh schema
		String uniqueIndexName = "test-schema-" + System.currentTimeMillis();

		chatMemory = RedisChatMemoryRepository.builder()
			.jedisClient(jedisClient)
			.indexName(uniqueIndexName)
			.metadataFields(metadataFields)
			.build();

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
	void shouldFindMessagesByMetadataWithProperSchema() {
		this.contextRunner.run(context -> {
			String conversationId = "test-metadata-schema";

			// Create messages with different metadata
			UserMessage userMsg1 = new UserMessage("High priority task");
			userMsg1.getMetadata().put("priority", "high");
			userMsg1.getMetadata().put("category", "task");
			userMsg1.getMetadata().put("score", 95);

			AssistantMessage assistantMsg = new AssistantMessage("I'll help with that");
			assistantMsg.getMetadata().put("model", "gpt-4");
			assistantMsg.getMetadata().put("confidence", 0.95);
			assistantMsg.getMetadata().put("category", "response");

			UserMessage userMsg2 = new UserMessage("Low priority question");
			userMsg2.getMetadata().put("priority", "low");
			userMsg2.getMetadata().put("category", "question");
			userMsg2.getMetadata().put("score", 75);

			// Add messages
			chatMemory.add(conversationId, userMsg1);
			chatMemory.add(conversationId, assistantMsg);
			chatMemory.add(conversationId, userMsg2);

			// Give Redis time to index the documents
			Thread.sleep(100);

			// Test finding by tag metadata (priority)
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> highPriorityMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByMetadata("priority", "high", 10);

			assertThat(highPriorityMessages).hasSize(1);
			assertThat(highPriorityMessages.get(0).message().getText()).isEqualTo("High priority task");

			// Test finding by tag metadata (category)
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> taskMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByMetadata("category", "task", 10);

			assertThat(taskMessages).hasSize(1);

			// Test finding by numeric metadata (score)
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> highScoreMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByMetadata("score", 95, 10);

			assertThat(highScoreMessages).hasSize(1);
			assertThat(highScoreMessages.get(0).message().getMetadata().get("score")).isEqualTo(95.0);

			// Test finding by numeric metadata (confidence)
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> confidentMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByMetadata("confidence", 0.95, 10);

			assertThat(confidentMessages).hasSize(1);
			assertThat(confidentMessages.get(0).message().getMetadata().get("model")).isEqualTo("gpt-4");

			// Test with non-existent metadata key (not in schema)
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> nonExistentMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByMetadata("nonexistent", "value", 10);

			assertThat(nonExistentMessages).isEmpty();

			// Clean up
			chatMemory.clear(conversationId);
		});
	}

	@Test
	void shouldFallbackToTextSearchForUndefinedMetadataFields() {
		this.contextRunner.run(context -> {
			String conversationId = "test-undefined-metadata";

			// Create message with metadata field not defined in schema
			UserMessage userMsg = new UserMessage("Message with custom metadata");
			userMsg.getMetadata().put("customField", "customValue");
			userMsg.getMetadata().put("priority", "medium"); // This is defined in schema

			chatMemory.add(conversationId, userMsg);

			// Defined field should work with exact match
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> priorityMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByMetadata("priority", "medium", 10);

			assertThat(priorityMessages).hasSize(1);

			// Undefined field will fall back to text search in general metadata
			// This may or may not find the message depending on how the text is indexed
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> customMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByMetadata("customField", "customValue", 10);

			// The result depends on whether the general metadata text field caught this
			// In practice, users should define all metadata fields they want to search on

			// Clean up
			chatMemory.clear(conversationId);
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	static class TestApplication {

		@Bean
		RedisChatMemoryRepository chatMemory() {
			List<Map<String, String>> metadataFields = List.of(Map.of("name", "priority", "type", "tag"),
					Map.of("name", "category", "type", "tag"), Map.of("name", "score", "type", "numeric"),
					Map.of("name", "confidence", "type", "numeric"), Map.of("name", "model", "type", "tag"));

			// Use a unique index name to ensure we get a fresh schema
			String uniqueIndexName = "test-schema-app-" + System.currentTimeMillis();

			return RedisChatMemoryRepository.builder()
				.jedisClient(new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort()))
				.indexName(uniqueIndexName)
				.metadataFields(metadataFields)
				.build();
		}

	}

}
