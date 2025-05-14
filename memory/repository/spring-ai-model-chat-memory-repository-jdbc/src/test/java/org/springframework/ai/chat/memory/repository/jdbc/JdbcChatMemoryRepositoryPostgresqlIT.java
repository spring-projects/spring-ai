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
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JdbcChatMemoryRepository} with PostgreSQL.
 *
 * @author Jonathan Leijendekker
 * @author Thomas Vitale
 * @author Mark Pollack
 */
@SpringBootTest(classes = JdbcChatMemoryRepositoryPostgresqlIT.TestConfiguration.class)
@TestPropertySource(properties = "spring.datasource.url=jdbc:tc:postgresql:17:///")
@Sql(scripts = "classpath:org/springframework/ai/chat/memory/repository/jdbc/schema-postgresql.sql")
class JdbcChatMemoryRepositoryPostgresqlIT extends AbstractJdbcChatMemoryRepositoryIT {

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
	static class TestConfiguration extends BaseTestConfiguration {

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
