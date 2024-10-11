package org.springframework.ai.chat.memory;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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
				String.format("app.datasource.url=jdbc:postgresql://%s:%d/%s", postgresContainer.getHost(),
						postgresContainer.getMappedPort(5432), "postgres"),
				"app.datasource.username=postgres", "app.datasource.password=postgres",
				"app.datasource.type=com.zaxxer.hikari.HikariDataSource");

	static String schemaName = "public_test";
	static String tableName = "ai_chat_memory_test";
	static String sessionIdColumnName = "session_id_test";
	static String exchangeIdColumnName = "message_timestamp_test";
	static String assistantColumnName = "assistant_test";
	static String userColumnName = "\"user_test\"";

	@Test
	void correctChatMemoryInstance() {
		contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemory.class);

			assertInstanceOf(PgVectorChatMemory.class, chatMemory);
		});
	}

	@ParameterizedTest
	@CsvSource({ "Message from assistant,ASSISTANT", "Message from user,USER" })
	void add_shouldInsertSingleMessage(String content, MessageType messageType) {
		contextRunner.run(context -> {
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

			assertEquals(4, result.size());
			assertEquals(conversationId, result.get(sessionIdColumnName));
			assertInstanceOf(Timestamp.class, result.get(exchangeIdColumnName));
			assertNotNull(result.get(exchangeIdColumnName));
			assertEquals(assistantContent, result.get(assistantColumnName));
			assertEquals(userContent, result.get(userColumnName.replace("\"", "")));
		});
	}

	@Test
	void add_shouldInsertMessages() {
		contextRunner.run(context -> {
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

			assertEquals(messages.size(), results.size());

			for (var i = 0; i < messages.size(); i++) {
				var message = messages.get(i);
				var result = results.get(i);

				assertEquals(conversationId, result.get(sessionIdColumnName));
				assertInstanceOf(Timestamp.class, result.get(exchangeIdColumnName));
				assertNotNull(result.get(exchangeIdColumnName));

				if (message.getMessageType() == MessageType.ASSISTANT) {
					assertEquals(message.getContent(), result.get(assistantColumnName));
				}
				else {
					assertEquals(message.getContent(), result.get(userColumnName.replace("\"", "")));
				}
			}
		});
	}

	@Test
	void get_shouldReturnMessages() {
		contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemory.class);
			var conversationId = UUID.randomUUID().toString();
			var messages = List.<Message>of(new AssistantMessage("Message from assistant 1 - " + conversationId),
					new AssistantMessage("Message from assistant 2 - " + conversationId),
					new UserMessage("Message from user - " + conversationId));

			chatMemory.add(conversationId, messages);

			var results = chatMemory.get(conversationId, Integer.MAX_VALUE);

			assertEquals(messages.size(), results.size());
			assertEquals(messages, results);
		});
	}

	@Test
	void clear_shouldDeleteMessages() {
		contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemory.class);
			var conversationId = UUID.randomUUID().toString();
			var messages = List.<Message>of(new AssistantMessage("Message from assistant - " + conversationId),
					new UserMessage("Message from user - " + conversationId));

			chatMemory.add(conversationId, messages);

			chatMemory.clear(conversationId);

			var jdbcTemplate = context.getBean(JdbcTemplate.class);
			var count = jdbcTemplate.queryForObject(String.format("SELECT COUNT(*) FROM %s.%s WHERE %s = ?", schemaName,
					tableName, sessionIdColumnName), Integer.class, conversationId);

			assertEquals(0, count);
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
