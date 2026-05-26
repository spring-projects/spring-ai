package org.springframework.ai.chat.memory.repository.oracle;

import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.oracle.OracleContainer;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OracleChatMemoryRepository}.
 */
@SpringBootTest(classes = OracleChatMemoryRepositoryIT.TestConfiguration.class)
class OracleChatMemoryRepositoryIT {

	private static final String ORACLE_DATABASE_URL = System.getenv("ORACLE_DATABASE_URL");

	private static final String ORACLE_DATABASE_USERNAME = System.getenv("ORACLE_DATABASE_USERNAME");

	private static final String ORACLE_DATABASE_PASSWORD = System.getenv("ORACLE_DATABASE_PASSWORD");

	private static final boolean USE_EXISTING_DATABASE = StringUtils.hasText(ORACLE_DATABASE_URL);

	private static final OracleContainer ORACLE_CONTAINER = USE_EXISTING_DATABASE ? null
			: new OracleContainer("gvenzl/oracle-free:slim-faststart");

	static {
		if (!USE_EXISTING_DATABASE) {
			ORACLE_CONTAINER.start();
		}
	}

	@Autowired
	private ChatMemoryRepository chatMemoryRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void configureDatasourceProperties(DynamicPropertyRegistry registry) {
		if (USE_EXISTING_DATABASE) {
			registry.add("spring.datasource.url", () -> ORACLE_DATABASE_URL);
			registry.add("spring.datasource.username",
					() -> StringUtils.hasText(ORACLE_DATABASE_USERNAME) ? ORACLE_DATABASE_USERNAME : "test");
			registry.add("spring.datasource.password",
					() -> StringUtils.hasText(ORACLE_DATABASE_PASSWORD) ? ORACLE_DATABASE_PASSWORD : "test");
		}
		else {
			registry.add("spring.datasource.url", ORACLE_CONTAINER::getJdbcUrl);
			registry.add("spring.datasource.username", ORACLE_CONTAINER::getUsername);
			registry.add("spring.datasource.password", ORACLE_CONTAINER::getPassword);
		}
	}

	@AfterAll
	static void stopContainer() {
		if (ORACLE_CONTAINER != null) {
			ORACLE_CONTAINER.stop();
		}
	}

	@BeforeEach
	void resetSchema() {
		this.jdbcTemplate.execute("""
				BEGIN
					EXECUTE IMMEDIATE 'DROP TABLE SPRING_AI_CHAT_MEMORY';
				EXCEPTION
					WHEN OTHERS THEN
						IF SQLCODE != -942 THEN
							RAISE;
						END IF;
				END;
				""");
		this.jdbcTemplate.execute("""
				CREATE TABLE SPRING_AI_CHAT_MEMORY (
					CONVERSATION_ID VARCHAR2(36 CHAR) NOT NULL,
					CONTENT CLOB NOT NULL,
					"TYPE" VARCHAR2(10 CHAR) NOT NULL CHECK ("TYPE" IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
					"TIMESTAMP" TIMESTAMP NOT NULL
				)
				""");
		this.jdbcTemplate.execute("""
				CREATE INDEX SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
				ON SPRING_AI_CHAT_MEMORY(CONVERSATION_ID, "TIMESTAMP")
				""");
	}

	@Test
	void saveAndReadConversation() {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new UserMessage("user message"), new AssistantMessage("assistant message"),
				new SystemMessage("system message"));

		this.chatMemoryRepository.saveAll(conversationId, messages);

		assertThat(this.chatMemoryRepository.findConversationIds()).contains(conversationId);
		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).isEqualTo(messages);
	}

	@ParameterizedTest
	@CsvSource({ "Message from assistant,ASSISTANT", "Message from user,USER", "Message from system,SYSTEM" })
	void saveSingleMessage(String content, MessageType messageType) {
		var conversationId = UUID.randomUUID().toString();
		var message = switch (messageType) {
			case ASSISTANT -> new AssistantMessage(content + " - " + conversationId);
			case USER -> new UserMessage(content + " - " + conversationId);
			case SYSTEM -> new SystemMessage(content + " - " + conversationId);
			case TOOL -> throw new IllegalArgumentException("TOOL message type not supported in this test");
		};

		this.chatMemoryRepository.saveAll(conversationId, List.of(message));

		assertThat(this.chatMemoryRepository.findConversationIds()).contains(conversationId);
		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).containsExactly(message);
	}

	@Test
	void saveMultipleMessagesAndOverwriteConversation() {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new AssistantMessage("assistant - " + conversationId),
				new UserMessage("user - " + conversationId), new SystemMessage("system - " + conversationId));

		this.chatMemoryRepository.saveAll(conversationId, messages);
		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).containsExactlyElementsOf(messages);

		var replacement = List.<Message>of(new UserMessage("replacement"));
		this.chatMemoryRepository.saveAll(conversationId, replacement);
		assertThat(this.chatMemoryRepository.findByConversationId(conversationId))
			.containsExactlyElementsOf(replacement);
	}

	@Test
	void findConversationIdsAcrossConversations() {
		var conversationA = UUID.randomUUID().toString();
		var conversationB = UUID.randomUUID().toString();

		this.chatMemoryRepository.saveAll(conversationA, List.of(new UserMessage("a1")));
		this.chatMemoryRepository.saveAll(conversationB, List.of(new UserMessage("b1")));

		assertThat(this.chatMemoryRepository.findConversationIds()).contains(conversationA, conversationB);
	}

	@Test
	void messagesAreReturnedInChronologicalOrder() {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new UserMessage("1-first"), new AssistantMessage("2-second"),
				new UserMessage("3-third"), new SystemMessage("4-fourth"));

		this.chatMemoryRepository.saveAll(conversationId, messages);

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).containsExactlyElementsOf(messages);
	}

	@Test
	void messagesAreReturnedInOrderForLargeBatch() {
		var conversationId = UUID.randomUUID().toString();
		List<Message> messages = new java.util.ArrayList<>();
		for (int i = 0; i < 50; i++) {
			messages.add(new UserMessage("Message " + i));
		}

		this.chatMemoryRepository.saveAll(conversationId, messages);
		var retrieved = this.chatMemoryRepository.findByConversationId(conversationId);

		assertThat(retrieved).hasSize(50);
		for (int i = 0; i < 50; i++) {
			assertThat(retrieved.get(i).getText()).isEqualTo("Message " + i);
		}
	}

	@Test
	void deleteConversation() {
		var conversationId = UUID.randomUUID().toString();
		this.chatMemoryRepository.saveAll(conversationId, List.of(new UserMessage("hello")));

		this.chatMemoryRepository.deleteByConversationId(conversationId);

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).isEmpty();
	}

	@SpringBootConfiguration
	@ImportAutoConfiguration({ DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class })
	static class TestConfiguration {

		@Bean
		ChatMemoryRepository chatMemoryRepository(DataSource dataSource) {
			return OracleChatMemoryRepository.builder().dataSource(dataSource).build();
		}
	}
}
