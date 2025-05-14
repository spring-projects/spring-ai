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

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
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
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JdbcChatMemoryRepository}.
 *
 * @author Jonathan Leijendekker
 * @author Thomas Vitale
 */
@SpringBootTest(classes = JdbcChatMemoryRepositoryPostgresqlIT.TestConfiguration.class)
@TestPropertySource(properties = "spring.datasource.url=jdbc:tc:postgresql:17:///")
@Sql(scripts = "classpath:org/springframework/ai/chat/memory/repository/jdbc/schema-postgresql.sql")
class JdbcChatMemoryRepositoryPostgresqlIT {

	@Autowired
	private ChatMemoryRepository chatMemoryRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void correctChatMemoryRepositoryInstance() {
		assertThat(chatMemoryRepository).isInstanceOf(ChatMemoryRepository.class);
	}

	@ParameterizedTest
	@CsvSource({ "Message from assistant,ASSISTANT", "Message from user,USER", "Message from system,SYSTEM" })
	void saveMessagesSingleMessage(String content, MessageType messageType) {
		var conversationId = UUID.randomUUID().toString();
		var message = switch (messageType) {
			case ASSISTANT -> new AssistantMessage(content + " - " + conversationId);
			case USER -> new UserMessage(content + " - " + conversationId);
			case SYSTEM -> new SystemMessage(content + " - " + conversationId);
			default -> throw new IllegalArgumentException("Type not supported: " + messageType);
		};

		chatMemoryRepository.saveAll(conversationId, List.of(message));

		var query = "SELECT conversation_id, content, type, \"timestamp\" FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?";
		var result = jdbcTemplate.queryForMap(query, conversationId);

		assertThat(result.size()).isEqualTo(4);
		assertThat(result.get("conversation_id")).isEqualTo(conversationId);
		assertThat(result.get("content")).isEqualTo(message.getText());
		assertThat(result.get("type")).isEqualTo(messageType.name());
		assertThat(result.get("timestamp")).isInstanceOf(Timestamp.class);
	}

	@Test
	void saveMessagesMultipleMessages() {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new AssistantMessage("Message from assistant - " + conversationId),
				new UserMessage("Message from user - " + conversationId),
				new SystemMessage("Message from system - " + conversationId));

		chatMemoryRepository.saveAll(conversationId, messages);

		var query = "SELECT conversation_id, content, type, \"timestamp\" FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?";
		var results = jdbcTemplate.queryForList(query, conversationId);

		assertThat(results.size()).isEqualTo(messages.size());

		for (var i = 0; i < messages.size(); i++) {
			var message = messages.get(i);
			var result = results.get(i);

			assertThat(result.get("conversation_id")).isNotNull();
			assertThat(result.get("conversation_id")).isEqualTo(conversationId);
			assertThat(result.get("content")).isEqualTo(message.getText());
			assertThat(result.get("type")).isEqualTo(message.getMessageType().name());
			assertThat(result.get("timestamp")).isInstanceOf(Timestamp.class);
		}

		var count = chatMemoryRepository.findByConversationId(conversationId).size();
		assertThat(count).isEqualTo(messages.size());

		chatMemoryRepository.saveAll(conversationId, List.of(new UserMessage("Hello")));

		count = chatMemoryRepository.findByConversationId(conversationId).size();
		assertThat(count).isEqualTo(1);
	}

	@Test
	void findMessagesByConversationId() {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new AssistantMessage("Message from assistant 1 - " + conversationId),
				new AssistantMessage("Message from assistant 2 - " + conversationId),
				new UserMessage("Message from user - " + conversationId),
				new SystemMessage("Message from system - " + conversationId));

		chatMemoryRepository.saveAll(conversationId, messages);

		var results = chatMemoryRepository.findByConversationId(conversationId);

		assertThat(results.size()).isEqualTo(messages.size());
		assertThat(results).isEqualTo(messages);
	}

	@Test
	void deleteMessagesByConversationId() {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new AssistantMessage("Message from assistant - " + conversationId),
				new UserMessage("Message from user - " + conversationId),
				new SystemMessage("Message from system - " + conversationId));

		chatMemoryRepository.saveAll(conversationId, messages);

		chatMemoryRepository.deleteByConversationId(conversationId);

		var count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?",
				Integer.class, conversationId);

		assertThat(count).isZero();
	}

	@Test
	void repositoryWithExplicitTransactionManager() {
		// Get the repository with explicit transaction manager
		ChatMemoryRepository repositoryWithTxManager = TestConfiguration
			.chatMemoryRepositoryWithTransactionManager(jdbcTemplate, jdbcTemplate.getDataSource());

		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new AssistantMessage("Message with transaction manager - " + conversationId),
				new UserMessage("User message with transaction manager - " + conversationId));

		// Save messages using the repository with explicit transaction manager
		repositoryWithTxManager.saveAll(conversationId, messages);

		// Verify messages were saved correctly
		var savedMessages = repositoryWithTxManager.findByConversationId(conversationId);
		assertThat(savedMessages).hasSize(2);
		assertThat(savedMessages).isEqualTo(messages);

		// Verify transaction works by updating and checking atomicity
		var newMessages = List.<Message>of(new SystemMessage("New system message - " + conversationId));
		repositoryWithTxManager.saveAll(conversationId, newMessages);

		// The old messages should be deleted and only the new one should exist
		var updatedMessages = repositoryWithTxManager.findByConversationId(conversationId);
		assertThat(updatedMessages).hasSize(1);
		assertThat(updatedMessages).isEqualTo(newMessages);
	}

	@SpringBootConfiguration
	@ImportAutoConfiguration({ DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class })
	static class TestConfiguration {

		@Bean
		ChatMemoryRepository chatMemoryRepository(JdbcTemplate jdbcTemplate, DataSource dataSource) {
			return JdbcChatMemoryRepository.builder()
				.jdbcTemplate(jdbcTemplate)
				.dialect(JdbcChatMemoryRepositoryDialect.from(dataSource))
				.build();
		}

		@Bean
		ChatMemoryRepository chatMemoryRepositoryWithTxManager(JdbcTemplate jdbcTemplate, DataSource dataSource) {
			return chatMemoryRepositoryWithTransactionManager(jdbcTemplate, dataSource);
		}

		static ChatMemoryRepository chatMemoryRepositoryWithTransactionManager(JdbcTemplate jdbcTemplate,
				DataSource dataSource) {
			return JdbcChatMemoryRepository.builder()
				.jdbcTemplate(jdbcTemplate)
				.dialect(JdbcChatMemoryRepositoryDialect.from(dataSource))
				.transactionManager(new DataSourceTransactionManager(dataSource))
				.build();
		}

	}

}
