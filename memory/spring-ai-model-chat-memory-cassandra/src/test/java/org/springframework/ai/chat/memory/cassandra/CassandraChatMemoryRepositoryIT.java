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

package org.springframework.ai.chat.memory.cassandra;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.ai.chat.memory.ChatMemoryRepository;

/**
 * Use `mvn failsafe:integration-test -Dit.test=CassandraChatMemoryRepositoryIT`
 *
 * @author Mick Semb Wever
 * @author Thomas Vitale
 * @since 1.0.0
 */
@Testcontainers
class CassandraChatMemoryRepositoryIT {

	@Container
	static CassandraContainer cassandraContainer = new CassandraContainer(CassandraImage.DEFAULT_IMAGE);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(CassandraChatMemoryRepositoryIT.TestApplication.class);

	@Test
	void ensureBeansGetsCreated() {
		this.contextRunner.run(context -> {
			CassandraChatMemoryRepository memory = context.getBean(CassandraChatMemoryRepository.class);
			Assertions.assertNotNull(memory);
			memory.conf.checkSchemaValid();
		});
	}

	@ParameterizedTest
	@CsvSource({ "Message from assistant,ASSISTANT", "Message from user,USER" })
	void add_shouldInsertSingleMessage(String content, MessageType messageType) {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemoryRepository.class);
			assertThat(chatMemory instanceof CassandraChatMemoryRepository);
			var sessionId = UUID.randomUUID().toString();
			var message = switch (messageType) {
				case ASSISTANT -> new AssistantMessage(content);
				case USER -> new UserMessage(content);
				default -> throw new IllegalArgumentException("Type not supported: " + messageType);
			};

			chatMemory.saveAll(sessionId, List.of(message));

			var cqlSession = context.getBean(CqlSession.class);
			var query = """
					SELECT session_id, message_timestamp, a, u
					FROM test_springframework.ai_chat_memory
					WHERE session_id = ?
					""";
			ResultSet resultSet = cqlSession.execute(query, sessionId);
			var rows = resultSet.all();

			assertThat(rows.size()).isEqualTo(1);

			var firstRow = rows.get(0);

			assertThat(firstRow.getString("session_id")).isEqualTo(sessionId);
			assertThat(firstRow.getInstant("message_timestamp")).isNotNull();
			if (messageType == MessageType.ASSISTANT) {
				assertThat(firstRow.getString("a")).isEqualTo(content);
				assertThat(firstRow.getString("u")).isNull();
			}
			else if (messageType == MessageType.USER) {
				assertThat(firstRow.getString("a")).isNull();
				assertThat(firstRow.getString("u")).isEqualTo(content);
			}
		});
	}

	@Test
	void add_shouldInsertMessages() {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemoryRepository.class);
			assertThat(chatMemory instanceof CassandraChatMemoryRepository);
			var sessionId = UUID.randomUUID().toString();
			var messages = List.<Message>of(new AssistantMessage("Message from assistant"),
					new UserMessage("Message from user"));

			chatMemory.saveAll(sessionId, messages);

			var cqlSession = context.getBean(CqlSession.class);
			var query = """
					SELECT session_id, message_timestamp, a, u
					FROM test_springframework.ai_chat_memory
					WHERE session_id = ?
					ORDER BY message_timestamp ASC
					""";
			ResultSet resultSet = cqlSession.execute(query, sessionId);
			var rows = resultSet.all();

			assertThat(rows.size()).isEqualTo(messages.size());

			for (var i = 0; i < messages.size(); i++) {
				var message = messages.get(i);
				var result = rows.get(i);

				assertThat(result.getString("session_id")).isNotNull();
				assertThat(result.getString("session_id")).isEqualTo(sessionId);
				if (message.getMessageType() == MessageType.ASSISTANT) {
					assertThat(result.getString("a")).isEqualTo(message.getText());
					assertThat(result.getString("u")).isNull();
				}
				else if (message.getMessageType() == MessageType.USER) {
					assertThat(result.getString("a")).isNull();
					assertThat(result.getString("u")).isEqualTo(message.getText());
				}
				assertThat(result.getInstant("message_timestamp")).isNotNull();
			}
		});
	}

	@Test
	void get_shouldReturnMessages() {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemoryRepository.class);
			assertThat(chatMemory instanceof CassandraChatMemoryRepository);
			var sessionId = UUID.randomUUID().toString();
			var messages = List.<Message>of(new AssistantMessage("Message from assistant 1 - " + sessionId),
					new AssistantMessage("Message from assistant 2 - " + sessionId),
					new UserMessage("Message from user - " + sessionId));

			chatMemory.saveAll(sessionId, messages);

			assertThat(chatMemory.findConversationIds()).isNotEmpty();

			var results = chatMemory.findByConversationId(sessionId);

			assertThat(results.size()).isEqualTo(messages.size());

			for (var i = 0; i < messages.size(); i++) {
				var message = messages.get(i);
				var result = results.get(i);

				assertThat(result.getMessageType()).isEqualTo(message.getMessageType());
				assertThat(result.getText()).isEqualTo(message.getText());
			}
		});
	}

	@Test
	void get_afterMultipleAdds_shouldReturnMessagesInSameOrder() {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemoryRepository.class);
			assertThat(chatMemory instanceof CassandraChatMemoryRepository);
			var sessionId = UUID.randomUUID().toString();
			var userMessage = new UserMessage("Message from user - " + sessionId);
			var assistantMessage = new AssistantMessage("Message from assistant - " + sessionId);

			chatMemory.saveAll(sessionId, List.of(userMessage, assistantMessage));

			assertThat(chatMemory.findConversationIds()).isNotEmpty();

			var results = chatMemory.findByConversationId(sessionId);

			assertThat(results.size()).isEqualTo(2);

			var messages = List.<Message>of(userMessage, assistantMessage);
			for (var i = 0; i < messages.size(); i++) {
				var message = messages.get(i);
				var result = results.get(i);

				assertThat(result.getMessageType()).isEqualTo(message.getMessageType());
				assertThat(result.getText()).isEqualTo(message.getText());
			}
		});
	}

	@Test
	void clear_shouldDeleteMessages() {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemoryRepository.class);
			assertThat(chatMemory instanceof CassandraChatMemoryRepository);
			var sessionId = UUID.randomUUID().toString();
			var messages = List.<Message>of(new AssistantMessage("Message from assistant - " + sessionId),
					new UserMessage("Message from user - " + sessionId));

			chatMemory.saveAll(sessionId, messages);

			assertThat(chatMemory.findConversationIds()).isNotEmpty();

			chatMemory.deleteByConversationId(sessionId);

			var cqlSession = context.getBean(CqlSession.class);
			var query = """
					SELECT COUNT(*)
					FROM test_springframework.ai_chat_memory
					WHERE session_id = ?
					""";
			ResultSet resultSet = cqlSession.execute(query, sessionId);
			var count = resultSet.all().get(0).getLong(0);

			assertThat(count).isZero();
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Bean
		public CassandraChatMemoryRepository memory(CqlSession cqlSession) {

			var conf = CassandraChatMemoryRepositoryConfig.builder()
				.withCqlSession(cqlSession)
				.withKeyspaceName("test_" + CassandraChatMemoryRepositoryConfig.DEFAULT_KEYSPACE_NAME)
				.withAssistantColumnName("a")
				.withUserColumnName("u")
				.withTimeToLive(Duration.ofMinutes(1))
				.build();

			conf.dropKeyspace();
			return CassandraChatMemoryRepository.create(conf);
		}

		@Bean
		public CqlSession cqlSession() {
			return new CqlSessionBuilder()
				// comment next two lines out to connect to a local C* cluster
				.addContactPoint(cassandraContainer.getContactPoint())
				.withLocalDatacenter(cassandraContainer.getLocalDatacenter())
				.build();
		}

	}

}
