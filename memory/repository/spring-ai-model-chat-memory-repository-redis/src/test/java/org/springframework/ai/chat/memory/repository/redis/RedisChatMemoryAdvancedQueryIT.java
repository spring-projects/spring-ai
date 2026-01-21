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
import java.util.UUID;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RedisChatMemoryRepository advanced query capabilities.
 *
 * @author Brian Sam-Bodden
 */
@Testcontainers
class RedisChatMemoryAdvancedQueryIT {

	@Container
	static RedisContainer redisContainer = new RedisContainer("redis/redis-stack:latest");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@Test
	void shouldFindMessagesByType_singleConversation() {
		this.contextRunner.run(context -> {
			RedisChatMemoryRepository chatMemory = context.getBean(RedisChatMemoryRepository.class);

			// Clear any existing test data
			chatMemory.findConversationIds().forEach(chatMemory::clear);

			String conversationId = "test-find-by-type";

			// Add various message types to a single conversation
			chatMemory.add(conversationId, new SystemMessage("System message 1"));
			chatMemory.add(conversationId, new UserMessage("User message 1"));
			chatMemory.add(conversationId, new AssistantMessage("Assistant message 1"));
			chatMemory.add(conversationId, new UserMessage("User message 2"));
			chatMemory.add(conversationId, new AssistantMessage("Assistant message 2"));
			chatMemory.add(conversationId, new SystemMessage("System message 2"));

			// Test finding by USER type
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> userMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByType(MessageType.USER, 10);

			assertThat(userMessages).hasSize(2);
			assertThat(userMessages.get(0).message().getText()).isEqualTo("User message 1");
			assertThat(userMessages.get(1).message().getText()).isEqualTo("User message 2");
			assertThat(userMessages.get(0).conversationId()).isEqualTo(conversationId);
			assertThat(userMessages.get(1).conversationId()).isEqualTo(conversationId);

			// Test finding by SYSTEM type
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> systemMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByType(MessageType.SYSTEM, 10);

			assertThat(systemMessages).hasSize(2);
			assertThat(systemMessages.get(0).message().getText()).isEqualTo("System message 1");
			assertThat(systemMessages.get(1).message().getText()).isEqualTo("System message 2");

			// Test finding by ASSISTANT type
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> assistantMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByType(MessageType.ASSISTANT, 10);

			assertThat(assistantMessages).hasSize(2);
			assertThat(assistantMessages.get(0).message().getText()).isEqualTo("Assistant message 1");
			assertThat(assistantMessages.get(1).message().getText()).isEqualTo("Assistant message 2");

			// Test finding by TOOL type (should be empty)
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> toolMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByType(MessageType.TOOL, 10);

			assertThat(toolMessages).isEmpty();
		});
	}

	@Test
	void shouldFindMessagesByType_multipleConversations() {
		this.contextRunner.run(context -> {
			RedisChatMemoryRepository chatMemory = context.getBean(RedisChatMemoryRepository.class);
			String conversationId1 = "conv-1-" + UUID.randomUUID();
			String conversationId2 = "conv-2-" + UUID.randomUUID();

			// Add messages to first conversation
			chatMemory.add(conversationId1, new UserMessage("User in conv 1"));
			chatMemory.add(conversationId1, new AssistantMessage("Assistant in conv 1"));
			chatMemory.add(conversationId1, new SystemMessage("System in conv 1"));

			// Add messages to second conversation
			chatMemory.add(conversationId2, new UserMessage("User in conv 2"));
			chatMemory.add(conversationId2, new AssistantMessage("Assistant in conv 2"));
			chatMemory.add(conversationId2, new SystemMessage("System in conv 2"));
			chatMemory.add(conversationId2, new UserMessage("Second user in conv 2"));

			// Find all USER messages across conversations
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> userMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByType(MessageType.USER, 10);

			assertThat(userMessages).hasSize(3);

			// Verify messages from both conversations are included
			List<String> conversationIds = userMessages.stream().map(msg -> msg.conversationId()).distinct().toList();

			assertThat(conversationIds).containsExactlyInAnyOrder(conversationId1, conversationId2);

			// Count messages from each conversation
			long conv1Count = userMessages.stream().filter(msg -> msg.conversationId().equals(conversationId1)).count();
			long conv2Count = userMessages.stream().filter(msg -> msg.conversationId().equals(conversationId2)).count();

			assertThat(conv1Count).isEqualTo(1);
			assertThat(conv2Count).isEqualTo(2);
		});
	}

	@Test
	void shouldRespectLimitParameter() {
		this.contextRunner.run(context -> {
			RedisChatMemoryRepository chatMemory = context.getBean(RedisChatMemoryRepository.class);
			String conversationId = "test-limit-parameter";

			// Add multiple messages of the same type
			chatMemory.add(conversationId, new UserMessage("User message 1"));
			chatMemory.add(conversationId, new UserMessage("User message 2"));
			chatMemory.add(conversationId, new UserMessage("User message 3"));
			chatMemory.add(conversationId, new UserMessage("User message 4"));
			chatMemory.add(conversationId, new UserMessage("User message 5"));

			// Retrieve with a limit of 3
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> messages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByType(MessageType.USER, 3);

			// Verify only 3 messages are returned
			assertThat(messages).hasSize(3);
		});
	}

	@Test
	void shouldHandleToolMessages() {
		this.contextRunner.run(context -> {
			RedisChatMemoryRepository chatMemory = context.getBean(RedisChatMemoryRepository.class);
			String conversationId = "test-tool-messages";

			// Create a ToolResponseMessage
			ToolResponseMessage.ToolResponse toolResponse = new ToolResponseMessage.ToolResponse("tool-1", "weather",
					"{\"temperature\":\"22°C\"}");
			ToolResponseMessage toolMessage = ToolResponseMessage.builder().responses(List.of(toolResponse)).build();

			// Add various message types
			chatMemory.add(conversationId, new UserMessage("Weather query"));
			chatMemory.add(conversationId, toolMessage);
			chatMemory.add(conversationId, new AssistantMessage("It's 22°C"));

			// Find TOOL type messages
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> toolMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByType(MessageType.TOOL, 10);

			assertThat(toolMessages).hasSize(1);
			assertThat(toolMessages.get(0).message()).isInstanceOf(ToolResponseMessage.class);

			ToolResponseMessage retrievedToolMessage = (ToolResponseMessage) toolMessages.get(0).message();
			assertThat(retrievedToolMessage.getResponses()).hasSize(1);
			assertThat(retrievedToolMessage.getResponses().get(0).name()).isEqualTo("weather");
		});
	}

	@Test
	void shouldReturnEmptyListWhenNoMessagesOfTypeExist() {
		this.contextRunner.run(context -> {
			RedisChatMemoryRepository chatMemory = context.getBean(RedisChatMemoryRepository.class);

			// Clear any existing test data
			chatMemory.findConversationIds().forEach(chatMemory::clear);

			String conversationId = "test-empty-type";

			// Add only user and assistant messages
			chatMemory.add(conversationId, new UserMessage("Hello"));
			chatMemory.add(conversationId, new AssistantMessage("Hi there"));

			// Search for system messages which don't exist
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> systemMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByType(MessageType.SYSTEM, 10);

			// Verify an empty list is returned (not null)
			assertThat(systemMessages).isNotNull().isEmpty();
		});
	}

	@Test
	void shouldFindMessagesByContent() {
		this.contextRunner.run(context -> {
			RedisChatMemoryRepository chatMemory = context.getBean(RedisChatMemoryRepository.class);
			String conversationId1 = "test-content-1";
			String conversationId2 = "test-content-2";

			// Add messages with different content patterns
			chatMemory.add(conversationId1, new UserMessage("I love programming in Java"));
			chatMemory.add(conversationId1, new AssistantMessage("Java is a great programming language"));
			chatMemory.add(conversationId2, new UserMessage("Python programming is fun"));
			chatMemory.add(conversationId2, new AssistantMessage("Tell me about Spring Boot"));
			chatMemory.add(conversationId1, new UserMessage("What about JavaScript programming?"));

			// Search for messages containing "programming"
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> programmingMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByContent("programming", 10);

			assertThat(programmingMessages).hasSize(4);
			// Verify all messages contain "programming"
			programmingMessages
				.forEach(msg -> assertThat(msg.message().getText().toLowerCase()).contains("programming"));

			// Search for messages containing "Java"
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> javaMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByContent("Java", 10);

			assertThat(javaMessages).hasSize(2); // Only exact case matches
			// Verify messages are from conversation 1 only
			assertThat(javaMessages.stream().map(m -> m.conversationId()).distinct()).hasSize(1);

			// Search for messages containing "Spring"
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> springMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByContent("Spring", 10);

			assertThat(springMessages).hasSize(1);
			assertThat(springMessages.get(0).message().getText()).contains("Spring Boot");

			// Test with limit
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> limitedMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByContent("programming", 2);

			assertThat(limitedMessages).hasSize(2);

			// Clean up
			chatMemory.clear(conversationId1);
			chatMemory.clear(conversationId2);
		});
	}

	@Test
	void shouldFindMessagesByTimeRange() throws InterruptedException {
		this.contextRunner.run(context -> {
			RedisChatMemoryRepository chatMemory = context.getBean(RedisChatMemoryRepository.class);
			String conversationId1 = "test-time-1";
			String conversationId2 = "test-time-2";

			// Record time before adding messages
			long startTime = System.currentTimeMillis();
			Thread.sleep(10); // Small delay to ensure timestamps are different

			// Add messages to first conversation
			chatMemory.add(conversationId1, new UserMessage("First message"));
			Thread.sleep(10);
			chatMemory.add(conversationId1, new AssistantMessage("Second message"));
			Thread.sleep(10);

			long midTime = System.currentTimeMillis();
			Thread.sleep(10);

			// Add messages to second conversation
			chatMemory.add(conversationId2, new UserMessage("Third message"));
			Thread.sleep(10);
			chatMemory.add(conversationId2, new AssistantMessage("Fourth message"));
			Thread.sleep(10);

			long endTime = System.currentTimeMillis();

			// Test finding messages in full time range across all conversations
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> allMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByTimeRange(null, java.time.Instant.ofEpochMilli(startTime),
						java.time.Instant.ofEpochMilli(endTime), 10);

			assertThat(allMessages).hasSize(4);

			// Test finding messages in first half of time range
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> firstHalfMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByTimeRange(null, java.time.Instant.ofEpochMilli(startTime),
						java.time.Instant.ofEpochMilli(midTime), 10);

			assertThat(firstHalfMessages).hasSize(2);
			assertThat(firstHalfMessages.stream().allMatch(m -> m.conversationId().equals(conversationId1))).isTrue();

			// Test finding messages in specific conversation within time range
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> conv2Messages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByTimeRange(conversationId2, java.time.Instant.ofEpochMilli(startTime),
						java.time.Instant.ofEpochMilli(endTime), 10);

			assertThat(conv2Messages).hasSize(2);
			assertThat(conv2Messages.stream().allMatch(m -> m.conversationId().equals(conversationId2))).isTrue();

			// Test with limit
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> limitedTimeMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByTimeRange(null, java.time.Instant.ofEpochMilli(startTime),
						java.time.Instant.ofEpochMilli(endTime), 2);

			assertThat(limitedTimeMessages).hasSize(2);

			// Clean up
			chatMemory.clear(conversationId1);
			chatMemory.clear(conversationId2);
		});
	}

	@Test
	void shouldFindMessagesByMetadata() {
		this.contextRunner.run(context -> {
			RedisChatMemoryRepository chatMemory = context.getBean(RedisChatMemoryRepository.class);
			String conversationId = "test-metadata";

			// Create messages with different metadata
			UserMessage userMsg1 = new UserMessage("User message with metadata");
			userMsg1.getMetadata().put("priority", "high");
			userMsg1.getMetadata().put("category", "question");
			userMsg1.getMetadata().put("score", 95);

			AssistantMessage assistantMsg = new AssistantMessage("Assistant response");
			assistantMsg.getMetadata().put("model", "gpt-4");
			assistantMsg.getMetadata().put("confidence", 0.95);
			assistantMsg.getMetadata().put("category", "answer");

			UserMessage userMsg2 = new UserMessage("Another user message");
			userMsg2.getMetadata().put("priority", "low");
			userMsg2.getMetadata().put("category", "question");
			userMsg2.getMetadata().put("score", 75);

			// Add messages
			chatMemory.add(conversationId, userMsg1);
			chatMemory.add(conversationId, assistantMsg);
			chatMemory.add(conversationId, userMsg2);

			// Give Redis time to index the documents
			Thread.sleep(100);

			// Test finding by string metadata
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> highPriorityMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByMetadata("priority", "high", 10);

			assertThat(highPriorityMessages).hasSize(1);
			assertThat(highPriorityMessages.get(0).message().getText()).isEqualTo("User message with metadata");

			// Test finding by category
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> questionMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByMetadata("category", "question", 10);

			assertThat(questionMessages).hasSize(2);

			// Test finding by numeric metadata
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> highScoreMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByMetadata("score", 95, 10);

			assertThat(highScoreMessages).hasSize(1);
			assertThat(highScoreMessages.get(0).message().getMetadata().get("score")).isEqualTo(95.0);

			// Test finding by double metadata
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> confidentMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByMetadata("confidence", 0.95, 10);

			assertThat(confidentMessages).hasSize(1);
			assertThat(confidentMessages.get(0).message().getMessageType()).isEqualTo(MessageType.ASSISTANT);

			// Test with non-existent metadata
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> nonExistentMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByMetadata("nonexistent", "value", 10);

			assertThat(nonExistentMessages).isEmpty();

			// Clean up
			chatMemory.clear(conversationId);
		});
	}

	@Test
	void shouldExecuteCustomQuery() {
		this.contextRunner.run(context -> {
			RedisChatMemoryRepository chatMemory = context.getBean(RedisChatMemoryRepository.class);
			String conversationId1 = "test-custom-1";
			String conversationId2 = "test-custom-2";

			// Add various messages
			UserMessage userMsg = new UserMessage("I need help with Redis");
			userMsg.getMetadata().put("urgent", "true");

			chatMemory.add(conversationId1, userMsg);
			chatMemory.add(conversationId1, new AssistantMessage("I can help you with Redis"));
			chatMemory.add(conversationId2, new UserMessage("Tell me about Spring"));
			chatMemory.add(conversationId2, new SystemMessage("System initialized"));

			// Test custom query for USER messages containing "Redis"
			String customQuery = "@type:USER @content:Redis";
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> redisUserMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.executeQuery(customQuery, 10);

			assertThat(redisUserMessages).hasSize(1);
			assertThat(redisUserMessages.get(0).message().getText()).contains("Redis");
			assertThat(redisUserMessages.get(0).message().getMessageType()).isEqualTo(MessageType.USER);

			// Test custom query for all messages in a specific conversation
			// Note: conversation_id is a TAG field, so we need to escape special
			// characters
			String escapedConvId = conversationId1.replace("-", "\\-");
			String convQuery = "@conversation_id:{" + escapedConvId + "}";
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> conv1Messages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.executeQuery(convQuery, 10);

			assertThat(conv1Messages).hasSize(2);
			assertThat(conv1Messages.stream().allMatch(m -> m.conversationId().equals(conversationId1))).isTrue();

			// Test complex query combining type and content
			String complexQuery = "(@type:USER | @type:ASSISTANT) @content:Redis";
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> complexResults = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.executeQuery(complexQuery, 10);

			assertThat(complexResults).hasSize(2);

			// Test with limit
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> limitedResults = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.executeQuery("*", 2);

			assertThat(limitedResults).hasSize(2);

			// Clean up
			chatMemory.clear(conversationId1);
			chatMemory.clear(conversationId2);
		});
	}

	@Test
	void shouldHandleSpecialCharactersInQueries() {
		this.contextRunner.run(context -> {
			RedisChatMemoryRepository chatMemory = context.getBean(RedisChatMemoryRepository.class);
			String conversationId = "test-special-chars";

			// Add messages with special characters
			chatMemory.add(conversationId, new UserMessage("What is 2+2?"));
			chatMemory.add(conversationId, new AssistantMessage("The answer is: 4"));
			chatMemory.add(conversationId, new UserMessage("Tell me about C++"));

			// Test finding content with special characters
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> plusMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByContent("C++", 10);

			assertThat(plusMessages).hasSize(1);
			assertThat(plusMessages.get(0).message().getText()).contains("C++");

			// Test finding content with colon - search for "answer is" instead
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> colonMessages = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByContent("answer is", 10);

			assertThat(colonMessages).hasSize(1);

			// Clean up
			chatMemory.clear(conversationId);
		});
	}

	@Test
	void shouldReturnEmptyListForNoMatches() {
		this.contextRunner.run(context -> {
			RedisChatMemoryRepository chatMemory = context.getBean(RedisChatMemoryRepository.class);
			String conversationId = "test-no-matches";

			// Add a simple message
			chatMemory.add(conversationId, new UserMessage("Hello world"));

			// Test content that doesn't exist
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> noContentMatch = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByContent("nonexistent", 10);
			assertThat(noContentMatch).isEmpty();

			// Test time range with no messages
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> noTimeMatch = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByTimeRange(conversationId, java.time.Instant.now().plusSeconds(3600), // Future
																							// time
						java.time.Instant.now().plusSeconds(7200), // Even more future
						10);
			assertThat(noTimeMatch).isEmpty();

			// Test metadata that doesn't exist
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> noMetadataMatch = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.findByMetadata("nonexistent", "value", 10);
			assertThat(noMetadataMatch).isEmpty();

			// Test custom query with no matches
			List<AdvancedRedisChatMemoryRepository.MessageWithConversation> noQueryMatch = ((AdvancedRedisChatMemoryRepository) chatMemory)
				.executeQuery("@type:FUNCTION", 10);
			assertThat(noQueryMatch).isEmpty();

			// Clean up
			chatMemory.clear(conversationId);
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	static class TestApplication {

		@Bean
		RedisChatMemoryRepository chatMemory() {
			// Define metadata fields for proper indexing
			List<Map<String, String>> metadataFields = List.of(Map.of("name", "priority", "type", "tag"),
					Map.of("name", "category", "type", "tag"), Map.of("name", "score", "type", "numeric"),
					Map.of("name", "confidence", "type", "numeric"), Map.of("name", "model", "type", "tag"),
					Map.of("name", "urgent", "type", "tag"));

			// Use a unique index name to avoid conflicts with metadata schema
			String uniqueIndexName = "test-adv-app-" + System.currentTimeMillis();

			return RedisChatMemoryRepository.builder()
				.jedisClient(new JedisPooled(redisContainer.getHost(), redisContainer.getFirstMappedPort()))
				.indexName(uniqueIndexName)
				.metadataFields(metadataFields)
				.build();
		}

	}

}
