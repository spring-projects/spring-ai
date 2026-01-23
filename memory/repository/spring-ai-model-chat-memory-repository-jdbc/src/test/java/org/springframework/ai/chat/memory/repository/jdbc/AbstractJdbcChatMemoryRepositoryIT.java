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

package org.springframework.ai.chat.memory.repository.jdbc;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for integration tests for {@link JdbcChatMemoryRepository}.
 *
 * @author Mark Pollack
 * @author Yanming Zhou
 */
@ContextConfiguration(classes = AbstractJdbcChatMemoryRepositoryIT.TestConfiguration.class)
public abstract class AbstractJdbcChatMemoryRepositoryIT {

	@Autowired
	protected JdbcChatMemoryRepository chatMemoryRepository;

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	@ParameterizedTest
	@CsvSource({ "Message from assistant,ASSISTANT", "Message from user,USER", "Message from system,SYSTEM" })
	void saveMessagesSingleMessage(String content, MessageType messageType) {
		String conversationId = UUID.randomUUID().toString();
		var message = switch (messageType) {
			case ASSISTANT -> new AssistantMessage(content + " - " + conversationId);
			case USER -> new UserMessage(content + " - " + conversationId);
			case SYSTEM -> new SystemMessage(content + " - " + conversationId);
			case TOOL -> throw new IllegalArgumentException("TOOL message type not supported in this test");
		};

		this.chatMemoryRepository.saveAll(conversationId, List.of(message));

		assertThat(this.chatMemoryRepository.findConversationIds()).contains(conversationId);

		// Use dialect to get the appropriate SQL query
		JdbcChatMemoryRepositoryDialect dialect = JdbcChatMemoryRepositoryDialect
			.from(this.jdbcTemplate.getDataSource());
		String selectSql = dialect.getSelectMessagesSql()
			.replace("content, type", "conversation_id, content, type, timestamp");
		var result = this.jdbcTemplate.queryForMap(selectSql, conversationId);

		assertThat(result.size()).isEqualTo(4);
		assertThat(result.get("conversation_id")).isEqualTo(conversationId);
		assertThat(result.get("content")).isEqualTo(message.getText());
		assertThat(result.get("type")).isEqualTo(messageType.name());
		assertThat(result.get("timestamp")).isNotNull();
	}

	@Test
	void saveMessagesMultipleMessages() {
		String conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new AssistantMessage("Message from assistant - " + conversationId),
				new UserMessage("Message from user - " + conversationId),
				new SystemMessage("Message from system - " + conversationId));

		this.chatMemoryRepository.saveAll(conversationId, messages);

		assertThat(this.chatMemoryRepository.findConversationIds()).contains(conversationId);

		// Use dialect to get the appropriate SQL query
		JdbcChatMemoryRepositoryDialect dialect = JdbcChatMemoryRepositoryDialect
			.from(this.jdbcTemplate.getDataSource());
		String selectSql = dialect.getSelectMessagesSql()
			.replace("content, type", "conversation_id, content, type, timestamp");
		var results = this.jdbcTemplate.queryForList(selectSql, conversationId);

		assertThat(results).hasSize(messages.size());

		for (int i = 0; i < messages.size(); i++) {
			var message = messages.get(i);
			var result = results.get(i);

			assertThat(result.get("conversation_id")).isEqualTo(conversationId);
			assertThat(result.get("content")).isEqualTo(message.getText());
			assertThat(result.get("type")).isEqualTo(message.getMessageType().name());
			assertThat(result.get("timestamp")).isNotNull();
		}

		var count = this.chatMemoryRepository.findByConversationId(conversationId).size();
		assertThat(count).isEqualTo(messages.size());

		this.chatMemoryRepository.saveAll(conversationId, List.of(new UserMessage("Hello")));

		count = this.chatMemoryRepository.findByConversationId(conversationId).size();
		assertThat(count).isEqualTo(1);
	}

	@Test
	void findMessagesByConversationId() {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new AssistantMessage("Message from assistant 1 - " + conversationId),
				new AssistantMessage("Message from assistant 2 - " + conversationId),
				new UserMessage("Message from user - " + conversationId),
				new SystemMessage("Message from system - " + conversationId));

		this.chatMemoryRepository.saveAll(conversationId, messages);

		var results = this.chatMemoryRepository.findByConversationId(conversationId);

		assertThat(results.size()).isEqualTo(messages.size());
		assertThat(results).isEqualTo(messages);
	}

	@Test
	void deleteMessagesByConversationId() {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new AssistantMessage("Message from assistant - " + conversationId),
				new UserMessage("Message from user - " + conversationId),
				new SystemMessage("Message from system - " + conversationId));

		this.chatMemoryRepository.saveAll(conversationId, messages);

		this.chatMemoryRepository.deleteByConversationId(conversationId);

		var count = this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?", Integer.class, conversationId);

		assertThat(count).isZero();
	}

	@Test
	void testMessageOrder() {

		var conversationId = UUID.randomUUID().toString();

		// Create messages with very distinct content to make order obvious
		var firstMessage = new UserMessage("1-First message");
		var secondMessage = new AssistantMessage("2-Second message");
		var thirdMessage = new UserMessage("3-Third message");
		var fourthMessage = new SystemMessage("4-Fourth message");

		// Save messages in the expected order
		List<Message> orderedMessages = List.of(firstMessage, secondMessage, thirdMessage, fourthMessage);
		this.chatMemoryRepository.saveAll(conversationId, orderedMessages);

		// Retrieve messages using the repository
		List<Message> retrievedMessages = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(retrievedMessages).hasSize(4);

		// Get the actual order from the retrieved messages
		List<String> retrievedContents = retrievedMessages.stream().map(Message::getText).collect(Collectors.toList());

		// Messages should be in the original order (ASC)
		assertThat(retrievedContents).containsExactly("1-First message", "2-Second message", "3-Third message",
				"4-Fourth message");
	}

	@Test
	void testMessageOrderWithLargeBatch() {
		var conversationId = UUID.randomUUID().toString();

		// Create a large batch of 50 messages to ensure timestamp ordering issues
		// are detected. With the old millisecond-precision code, MySQL/MariaDB's
		// second-precision TIMESTAMP columns would truncate all timestamps to the
		// same value, causing random ordering. This test validates the fix.
		List<Message> messages = new java.util.ArrayList<>();
		for (int i = 0; i < 50; i++) {
			messages.add(new UserMessage("Message " + i));
		}

		this.chatMemoryRepository.saveAll(conversationId, messages);

		List<Message> retrievedMessages = this.chatMemoryRepository.findByConversationId(conversationId);

		// Verify we got all messages back in the exact order they were saved
		assertThat(retrievedMessages).hasSize(50);
		for (int i = 0; i < 50; i++) {
			assertThat(retrievedMessages.get(i).getText()).isEqualTo("Message " + i);
		}
	}

	/**
	 * Base configuration for all integration tests.
	 */
	@ImportAutoConfiguration({ DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class })
	static class TestConfiguration {

		@Bean
		ChatMemoryRepository chatMemoryRepository(DataSource dataSource) {
			return JdbcChatMemoryRepository.builder().dataSource(dataSource).build();
		}

	}

}
