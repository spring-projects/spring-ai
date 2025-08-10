/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.chat.memory.repository.neo4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
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
		if (driver != null) {
			driver.close();
		}
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
				if ("session_conversation_id_index".equals(name)) {
					sessionIndexFound = true;
				}
				if ("message_index_index".equals(name)) {
					messageIndexFound = true;
				}
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
