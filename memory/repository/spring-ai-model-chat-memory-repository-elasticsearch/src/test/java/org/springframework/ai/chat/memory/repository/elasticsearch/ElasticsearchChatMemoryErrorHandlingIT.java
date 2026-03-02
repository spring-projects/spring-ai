/*
 * Copyright 2026-2026 the original author or authors.
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
package org.springframework.ai.chat.memory.repository.elasticsearch;

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Integration tests for ElasticsearchChatMemoryRepository focused on error handling
 * scenarios.
 *
 * @author Laura Trotta
 */
@Testcontainers
class ElasticsearchChatMemoryErrorHandlingIT {

	private static final Logger logger = LoggerFactory.getLogger(ElasticsearchChatMemoryErrorHandlingIT.class);

	@Container
	static ElasticsearchContainer elasticsearch = new ElasticsearchContainer("elasticsearch:9.3.0")
		.withEnv("xpack.security.enabled", "false");

	private ElasticsearchChatMemoryRepository repository;

	@BeforeEach
	void setUp() throws URISyntaxException {
		Rest5Client restClient = Rest5Client.builder(HttpHost.create(elasticsearch.getHttpHostAddress())).build();

		repository = ElasticsearchChatMemoryRepository.builder(restClient).indexName("test-chat-memory").build();

		for (String id : repository.findConversationIds()) {
			repository.deleteByConversationId(id);
		}
	}

	@AfterEach
	void tearDown() {
		for (String id : repository.findConversationIds()) {
			repository.deleteByConversationId(id);
		}
	}

	@Test
	void shouldHandleInvalidConversationId() {
		// Using null conversation ID
		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> repository.saveAll(null, List.of(new UserMessage("Test message"))))
			.withMessageContaining("Conversation ID must not be null");

		// Using empty conversation ID
		UserMessage message = new UserMessage("Test message");
		assertThatCode(() -> repository.saveAll("", List.of(message))).doesNotThrowAnyException();

		// Reading with null conversation ID
		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> repository.findByConversationId(null))
			.withMessageContaining("Conversation ID must not be null");

		// Reading with non-existent conversation ID should return empty list
		List<Message> messages = repository.findByConversationId("non-existent-id");
		assertThat(messages).isNotNull().isEmpty();

		// Clearing with null conversation ID
		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> repository.deleteByConversationId(null))
			.withMessageContaining("Conversation ID must not be null");

		// Clearing non-existent conversation should not throw exception
		assertThatCode(() -> repository.deleteByConversationId("non-existent-id")).doesNotThrowAnyException();
	}

	@Test
	void shouldHandleInvalidMessageParameters() {
		String conversationId = UUID.randomUUID().toString();

		// Null message list
		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> repository.saveAll(conversationId, null))
			.withMessageContaining("Messages must not be null");

		// Empty message list should not throw exception
		assertThatCode(() -> repository.saveAll(conversationId, List.of())).doesNotThrowAnyException();

		// Message with empty content (not null - which is not allowed)
		UserMessage emptyContentMessage = UserMessage.builder().text("").build();
		assertThatCode(() -> repository.saveAll(conversationId, List.of(emptyContentMessage)))
			.doesNotThrowAnyException();

		// Message with empty metadata
		UserMessage userMessage = UserMessage.builder().text("Hello").build();
		assertThatCode(() -> repository.saveAll(conversationId, List.of(userMessage))).doesNotThrowAnyException();
	}

	@Test
	void shouldHandleEdgeCaseConversationIds() {
		// Test with a simple conversation ID first to verify basic functionality
		String simpleId = "simple-test-id";
		UserMessage simpleMessage = new UserMessage("Simple test message");
		repository.saveAll(simpleId, List.of(simpleMessage));

		List<Message> simpleMessages = repository.findByConversationId(simpleId);
		assertThat(simpleMessages).hasSize(1);
		assertThat(simpleMessages.get(0).getText()).isEqualTo("Simple test message");

		// Test with conversation IDs containing special characters
		String specialCharsId = "test_conversation_with_special_chars_123";
		String specialMessage = "Message with special character conversation ID";
		UserMessage message = new UserMessage(specialMessage);

		repository.saveAll(specialCharsId, List.of(message));

		List<Message> specialCharMessages = repository.findByConversationId(specialCharsId);
		assertThat(specialCharMessages).hasSize(1);
		assertThat(specialCharMessages.get(0).getText()).isEqualTo(specialMessage);

		// Test with non-alphanumeric characters in ID
		String complexId = "test-with:complex@chars#123";
		String complexMessage = "Message with complex ID";
		UserMessage complexIdMessage = new UserMessage(complexMessage);

		repository.saveAll(complexId, List.of(complexIdMessage));
		List<Message> complexIdMessages = repository.findByConversationId(complexId);
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

		repository.saveAll(longId, List.of(longIdMessage));
		List<Message> longIdMessages = repository.findByConversationId(longId);
		assertThat(longIdMessages).hasSize(1);
		assertThat(longIdMessages.get(0).getText()).isEqualTo(longIdMessageText);
	}

}
