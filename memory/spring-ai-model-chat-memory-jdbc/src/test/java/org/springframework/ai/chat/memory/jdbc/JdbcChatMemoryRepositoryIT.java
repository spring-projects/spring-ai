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

package org.springframework.ai.chat.memory.jdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JdbcChatMemoryRepository}.
 *
 * @author Jonathan Leijendekker
 * @author Thomas Vitale
 */
@Testcontainers
class JdbcChatMemoryRepositoryIT {

	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:17")
		.withDatabaseName("chat_memory_test")
		.withUsername("postgres")
		.withPassword("postgres")
		.withCopyFileToContainer(
				MountableFile.forClasspathResource("org/springframework/ai/chat/memory/jdbc/schema-postgresql.sql"),
				"/docker-entrypoint-initdb.d/schema.sql");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(JdbcChatMemoryRepositoryIT.TestApplication.class)
		.withPropertyValues(String.format("myapp.datasource.url=%s", postgresContainer.getJdbcUrl()),
				String.format("myapp.datasource.username=%s", postgresContainer.getUsername()),
				String.format("myapp.datasource.password=%s", postgresContainer.getPassword()));

	@Test
	void correctChatMemoryRepositoryInstance() {
		this.contextRunner.run(context -> {
			var chatMemoryRepository = context.getBean(ChatMemoryRepository.class);
			assertThat(chatMemoryRepository).isInstanceOf(ChatMemoryRepository.class);
		});
	}

	@ParameterizedTest
	@CsvSource({ "Message from assistant,ASSISTANT", "Message from user,USER", "Message from system,SYSTEM" })
	void saveSingleMessage(String content, MessageType messageType) {
		this.contextRunner.run(context -> {
			var chatMemoryRepository = context.getBean(ChatMemoryRepository.class);
			var conversationId = UUID.randomUUID().toString();
			var message = switch (messageType) {
				case ASSISTANT -> new AssistantMessage(content + " - " + conversationId);
				case USER -> new UserMessage(content + " - " + conversationId);
				case SYSTEM -> new SystemMessage(content + " - " + conversationId);
				default -> throw new IllegalArgumentException("Type not supported: " + messageType);
			};

			chatMemoryRepository.save(conversationId, List.of(message));

			var jdbcTemplate = context.getBean(JdbcTemplate.class);
			var query = "SELECT conversation_id, content, type, \"timestamp\" FROM ai_chat_memory WHERE conversation_id = ?";
			var result = jdbcTemplate.queryForMap(query, conversationId);

			assertThat(result.size()).isEqualTo(4);
			assertThat(result.get("conversation_id")).isEqualTo(conversationId);
			assertThat(result.get("content")).isEqualTo(message.getText());
			assertThat(result.get("type")).isEqualTo(messageType.name());
			assertThat(result.get("timestamp")).isInstanceOf(Timestamp.class);
		});
	}

	@Test
	void saveMultipleMessages() {
		this.contextRunner.run(context -> {
			var chatMemoryRepository = context.getBean(ChatMemoryRepository.class);
			var conversationId = UUID.randomUUID().toString();
			var messages = List.<Message>of(new AssistantMessage("Message from assistant - " + conversationId),
					new UserMessage("Message from user - " + conversationId),
					new SystemMessage("Message from system - " + conversationId));

			chatMemoryRepository.save(conversationId, messages);

			var jdbcTemplate = context.getBean(JdbcTemplate.class);
			var query = "SELECT conversation_id, content, type, \"timestamp\" FROM ai_chat_memory WHERE conversation_id = ?";
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
		});
	}

	@Test
	void findMessagesByConversationId() {
		this.contextRunner.run(context -> {
			var chatMemoryRepository = context.getBean(ChatMemoryRepository.class);
			var conversationId = UUID.randomUUID().toString();
			var messages = List.<Message>of(new AssistantMessage("Message from assistant 1 - " + conversationId),
					new AssistantMessage("Message from assistant 2 - " + conversationId),
					new UserMessage("Message from user - " + conversationId),
					new SystemMessage("Message from system - " + conversationId));

			chatMemoryRepository.save(conversationId, messages);

			var results = chatMemoryRepository.findById(conversationId);

			assertThat(results.size()).isEqualTo(messages.size());
			assertThat(results).isEqualTo(messages);
		});
	}

	@Test
	void deleteMessagesByConversationId() {
		this.contextRunner.run(context -> {
			var chatMemoryRepository = context.getBean(ChatMemoryRepository.class);
			var conversationId = UUID.randomUUID().toString();
			var messages = List.<Message>of(new AssistantMessage("Message from assistant - " + conversationId),
					new UserMessage("Message from user - " + conversationId),
					new SystemMessage("Message from system - " + conversationId));

			chatMemoryRepository.save(conversationId, messages);

			chatMemoryRepository.deleteById(conversationId);

			var jdbcTemplate = context.getBean(JdbcTemplate.class);
			var count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_chat_memory WHERE conversation_id = ?",
					Integer.class, conversationId);

			assertThat(count).isZero();
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	static class TestApplication {

		@Bean
		ChatMemoryRepository chatMemoryRepository(JdbcTemplate jdbcTemplate) {
			JdbcChatMemoryConfig config = JdbcChatMemoryConfig.builder().jdbcTemplate(jdbcTemplate).build();
			return JdbcChatMemoryRepository.create(config);
		}

		@Bean
		JdbcTemplate jdbcTemplate(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean
		@Primary
		@ConfigurationProperties("myapp.datasource")
		DataSourceProperties dataSourceProperties() {
			return new DataSourceProperties();
		}

		@Bean
		public DataSource dataSource(DataSourceProperties dataSourceProperties) {
			return dataSourceProperties.initializeDataSourceBuilder().build();
		}

	}

}
