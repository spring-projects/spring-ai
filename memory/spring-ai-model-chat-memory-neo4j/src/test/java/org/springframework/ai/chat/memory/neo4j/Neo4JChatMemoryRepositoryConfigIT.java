package org.springframework.ai.chat.memory.neo4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class Neo4JChatMemoryRepositoryConfigIT {

	@Container
	static final Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:5").withoutAuthentication();

	static Driver driver;

	@BeforeAll
	static void setupDriver() {
		driver = GraphDatabase.driver(neo4jContainer.getBoltUrl());
	}

	@AfterAll
	static void closeDriver() {
		if (driver != null)
			driver.close();
	}

	@Test
	void shouldCreateRequiredIndexes() {
		// Given
		Neo4jChatMemoryRepositoryConfig config = Neo4jChatMemoryRepositoryConfig.builder().withDriver(driver).build();
		// When
		try (Session session = driver.session()) {
			Result result = session.run("SHOW INDEXES");
			boolean sessionIndexFound = false;
			boolean messageIndexFound = false;
			while (result.hasNext()) {
				var record = result.next();
				String name = record.get("name").asString();
				if ("session_conversation_id_index".equals(name))
					sessionIndexFound = true;
				if ("message_index_index".equals(name))
					messageIndexFound = true;
			}
			// Then
			assertThat(sessionIndexFound).isTrue();
			assertThat(messageIndexFound).isTrue();
		}
	}

	@Test
	void builderShouldSetCustomLabels() {
		String customSessionLabel = "ChatSession";
		String customMessageLabel = "ChatMessage";
		Neo4jChatMemoryRepositoryConfig config = Neo4jChatMemoryRepositoryConfig.builder()
			.withDriver(driver)
			.withSessionLabel(customSessionLabel)
			.withMessageLabel(customMessageLabel)
			.build();
		assertThat(config.getSessionLabel()).isEqualTo(customSessionLabel);
		assertThat(config.getMessageLabel()).isEqualTo(customMessageLabel);
	}

	@Test
	void gettersShouldReturnConfiguredValues() {
		Neo4jChatMemoryRepositoryConfig config = Neo4jChatMemoryRepositoryConfig.builder()
			.withDriver(driver)
			.withSessionLabel("Session")
			.withToolCallLabel("ToolCall")
			.withMetadataLabel("Metadata")
			.withMessageLabel("Message")
			.withToolResponseLabel("ToolResponse")
			.withMediaLabel("Media")
			.build();
		assertThat(config.getSessionLabel()).isEqualTo("Session");
		assertThat(config.getToolCallLabel()).isEqualTo("ToolCall");
		assertThat(config.getMetadataLabel()).isEqualTo("Metadata");
		assertThat(config.getMessageLabel()).isEqualTo("Message");
		assertThat(config.getToolResponseLabel()).isEqualTo("ToolResponse");
		assertThat(config.getMediaLabel()).isEqualTo("Media");
		assertThat(config.getDriver()).isNotNull();
	}

}
