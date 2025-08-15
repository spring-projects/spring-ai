/*
 * Copyright 2024-2025 the original author or authors.
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

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jonathan Leijendekker
 */
@Testcontainers
class JdbcChatMemoryIT {

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
		.withUserConfiguration(TestApplication.class)
		.withPropertyValues(String.format("app.datasource.url=%s", postgresContainer.getJdbcUrl()),
				String.format("app.datasource.username=%s", postgresContainer.getUsername()),
				String.format("app.datasource.password=%s", postgresContainer.getPassword()));

	@BeforeAll
	static void beforeAll() {

	}

	@Test
	void correctChatMemoryInstance() {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemory.class);

			assertThat(chatMemory).isInstanceOf(JdbcChatMemory.class);
		});
	}

	@ParameterizedTest
	@CsvSource({ "Message from assistant,ASSISTANT", "Message from user,USER", "Message from system,SYSTEM" })
	void add_shouldInsertSingleMessage(String content, MessageType messageType) {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemory.class);
			var conversationId = UUID.randomUUID().toString();
			var message = switch (messageType) {
				case ASSISTANT -> new AssistantMessage(content + " - " + conversationId);
				case USER -> new UserMessage(content + " - " + conversationId);
				case SYSTEM -> new SystemMessage(content + " - " + conversationId);
				default -> throw new IllegalArgumentException("Type not supported: " + messageType);
			};

			chatMemory.add(conversationId, message);

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
	void add_shouldInsertMessages() {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemory.class);
			var conversationId = UUID.randomUUID().toString();
			var messages = List.<Message>of(new AssistantMessage("Message from assistant - " + conversationId),
					new UserMessage("Message from user - " + conversationId),
					new SystemMessage("Message from system - " + conversationId));

			chatMemory.add(conversationId, messages);

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
	void get_shouldReturnMessages() {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemory.class);
			var conversationId = UUID.randomUUID().toString();
			var messages = List.<Message>of(new AssistantMessage("Message from assistant 1 - " + conversationId),
					new AssistantMessage("Message from assistant 2 - " + conversationId),
					new UserMessage("Message from user - " + conversationId),
					new SystemMessage("Message from system - " + conversationId));

			chatMemory.add(conversationId, messages);

			var results = chatMemory.get(conversationId, Integer.MAX_VALUE);

			assertThat(results.size()).isEqualTo(messages.size());
			assertThat(results).isEqualTo(messages);
		});
	}

	@Test
	void clear_shouldDeleteMessages() {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemory.class);
			var conversationId = UUID.randomUUID().toString();
			var messages = List.<Message>of(new AssistantMessage("Message from assistant - " + conversationId),
					new UserMessage("Message from user - " + conversationId),
					new SystemMessage("Message from system - " + conversationId));

			chatMemory.add(conversationId, messages);

			chatMemory.clear(conversationId);

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
		public ChatMemory chatMemory(JdbcTemplate jdbcTemplate) {
			var config = JdbcChatMemoryConfig.builder().jdbcTemplate(jdbcTemplate).build();

			return JdbcChatMemory.create(config);
		}

		@Bean
		public JdbcTemplate jdbcTemplate(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean
		@Primary
		@ConfigurationProperties("app.datasource")
		public DataSourceProperties dataSourceProperties() {
			return new DataSourceProperties();
		}

		@Bean
		public DataSource dataSource(DataSourceProperties dataSourceProperties) {
			return dataSourceProperties.initializeDataSourceBuilder().build();
		}

	}

}
