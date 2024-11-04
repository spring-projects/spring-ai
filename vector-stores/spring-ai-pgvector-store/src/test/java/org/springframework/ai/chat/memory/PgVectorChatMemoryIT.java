/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.memory;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.vectorstore.PgVectorImage;
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
class PgVectorChatMemoryIT {

	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(PgVectorImage.DEFAULT_IMAGE)
		.withUsername("postgres")
		.withPassword("postgres");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class)
		.withPropertyValues(
				// JdbcTemplate configuration
				String.format("app.datasource.url=%s", postgresContainer.getJdbcUrl()),
				String.format("app.datasource.username=%s", postgresContainer.getUsername()),
				String.format("app.datasource.password=%s", postgresContainer.getPassword()),
				"app.datasource.type=com.zaxxer.hikari.HikariDataSource");

	static String schemaName = "public_test";
	static String tableName = "ai_chat_memory_test";
	static String sessionIdColumnName = "session_id_test";
	static String exchangeIdColumnName = "message_timestamp_test";
	static String assistantColumnName = "assistant_test";
	static String userColumnName = "\"user_test\"";

	@Test
	void correctChatMemoryInstance() {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemory.class);

			assertThat(chatMemory).isInstanceOf(PgVectorChatMemory.class);
		});
	}

	@ParameterizedTest
	@CsvSource({ "Message from assistant,ASSISTANT", "Message from user,USER" })
	void add_shouldInsertSingleMessage(String content, MessageType messageType) {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemory.class);
			var conversationId = UUID.randomUUID().toString();
			String assistantContent = null;
			String userContent = null;
			var message = switch (messageType) {
				case ASSISTANT -> {
					assistantContent = content + " - " + conversationId;
					yield new AssistantMessage(assistantContent);
				}
				case USER -> {
					userContent = content + " - " + conversationId;
					yield new UserMessage(userContent);
				}
				default -> throw new IllegalArgumentException("Type not supported: " + messageType);
			};

			chatMemory.add(conversationId, message);

			var jdbcTemplate = context.getBean(JdbcTemplate.class);
			var query = String.format("SELECT %s, %s, %s, %s FROM %s.%s WHERE %s = ?", sessionIdColumnName,
					exchangeIdColumnName, assistantColumnName, userColumnName, schemaName, tableName,
					sessionIdColumnName);
			var result = jdbcTemplate.queryForMap(query, conversationId);

			assertThat(result.size()).isEqualTo(4);
			assertThat(result.get(sessionIdColumnName)).isEqualTo(conversationId);
			assertThat(result.get(exchangeIdColumnName)).isInstanceOf(Timestamp.class);
			assertThat(result.get(exchangeIdColumnName)).isNotNull();
			assertThat(result.get(assistantColumnName)).isEqualTo(assistantContent);
			assertThat(result.get(userColumnName.replace("\"", ""))).isEqualTo(userContent);
		});
	}

	@Test
	void add_shouldInsertMessages() {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemory.class);
			var conversationId = UUID.randomUUID().toString();
			var messages = List.<Message>of(new AssistantMessage("Message from assistant - " + conversationId),
					new UserMessage("Message from user - " + conversationId));

			chatMemory.add(conversationId, messages);

			var jdbcTemplate = context.getBean(JdbcTemplate.class);
			var query = String.format("SELECT %s, %s, %s, %s FROM %s.%s WHERE %s = ?", sessionIdColumnName,
					exchangeIdColumnName, assistantColumnName, userColumnName, schemaName, tableName,
					sessionIdColumnName);
			var results = jdbcTemplate.queryForList(query, conversationId);

			assertThat(results.size()).isEqualTo(messages.size());

			for (var i = 0; i < messages.size(); i++) {
				var message = messages.get(i);
				var result = results.get(i);

				assertThat(result.get(exchangeIdColumnName)).isNotNull();
				assertThat(result.get(sessionIdColumnName)).isEqualTo(conversationId);
				assertThat(result.get(exchangeIdColumnName)).isInstanceOf(Timestamp.class);

				if (message.getMessageType() == MessageType.ASSISTANT) {
					assertThat(result.get(assistantColumnName)).isEqualTo(message.getContent());
				}
				else {
					assertThat(result.get(userColumnName.replace("\"", ""))).isEqualTo(message.getContent());
				}
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
					new UserMessage("Message from user - " + conversationId));

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
					new UserMessage("Message from user - " + conversationId));

			chatMemory.add(conversationId, messages);

			chatMemory.clear(conversationId);

			var jdbcTemplate = context.getBean(JdbcTemplate.class);
			var count = jdbcTemplate.queryForObject(String.format("SELECT COUNT(*) FROM %s.%s WHERE %s = ?", schemaName,
					tableName, sessionIdColumnName), Integer.class, conversationId);

			assertThat(count).isZero();
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	static class TestApplication {

		@Bean
		public ChatMemory chatMemory(JdbcTemplate jdbcTemplate) {
			var config = PgVectorChatMemoryConfig.builder()
				.withInitializeSchema(true)
				.withSchemaName(schemaName)
				.withTableName(tableName)
				.withSessionIdColumnName(sessionIdColumnName)
				.withExchangeIdColumnName(exchangeIdColumnName)
				.withAssistantColumnName(assistantColumnName)
				.withUserColumnName(userColumnName)
				.withJdbcTemplate(jdbcTemplate)
				.build();

			return PgVectorChatMemory.create(config);
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
		public HikariDataSource dataSource(DataSourceProperties dataSourceProperties) {
			return dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
		}

	}

}
