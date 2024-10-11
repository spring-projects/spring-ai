package org.springframework.ai.autoconfigure.chat.memory.pgvector;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.PgVectorChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jonathan Leijendekker
 */
@Testcontainers
class PgVectorChatMemoryAutoConfigurationIT {

	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
		.withUsername("postgres")
		.withPassword("postgres");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(PgVectorChatMemoryAutoConfiguration.class,
				JdbcTemplateAutoConfiguration.class, DataSourceAutoConfiguration.class))
		.withPropertyValues("spring.ai.chat.memory.pgvector.schemaName=test_autoconfigure",
				// JdbcTemplate configuration
				String.format("spring.datasource.url=jdbc:postgresql://%s:%d/%s", postgresContainer.getHost(),
						postgresContainer.getMappedPort(5432), postgresContainer.getDatabaseName()),
				"spring.datasource.username=" + postgresContainer.getUsername(),
				"spring.datasource.password=" + postgresContainer.getPassword());

	@Test
	void addGetAndClear_shouldAllExecute() {
		contextRunner.run(context -> {
			var chatMemory = context.getBean(PgVectorChatMemory.class);
			var conversationId = UUID.randomUUID().toString();
			var userMessage = new UserMessage("Message from the user");

			chatMemory.add(conversationId, userMessage);

			assertThat(chatMemory.get(conversationId, Integer.MAX_VALUE)).hasSize(1);
			assertThat(chatMemory.get(conversationId, Integer.MAX_VALUE)).isEqualTo(List.of(userMessage));

			chatMemory.clear(conversationId);

			assertThat(chatMemory.get(conversationId, Integer.MAX_VALUE)).isEmpty();

			var multipleMessages = List.<Message>of(new UserMessage("Message from the user 1"),
					new AssistantMessage("Message from the assistant 1"));

			chatMemory.add(conversationId, multipleMessages);

			assertThat(chatMemory.get(conversationId, Integer.MAX_VALUE)).hasSize(multipleMessages.size());
			assertThat(chatMemory.get(conversationId, Integer.MAX_VALUE)).isEqualTo(multipleMessages);
		});
	}

}
