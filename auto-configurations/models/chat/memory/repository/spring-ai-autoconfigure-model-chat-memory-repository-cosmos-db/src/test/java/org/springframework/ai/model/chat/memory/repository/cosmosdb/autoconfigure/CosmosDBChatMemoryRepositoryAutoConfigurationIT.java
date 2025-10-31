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

package org.springframework.ai.model.chat.memory.repository.cosmosdb.autoconfigure;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.memory.repository.cosmosdb.CosmosDBChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link CosmosDBChatMemoryRepositoryAutoConfiguration}.
 *
 * @author Theo van Kraay
 * @since 1.1.0
 */
@EnabledIfEnvironmentVariable(named = "AZURE_COSMOSDB_ENDPOINT", matches = ".+")
class CosmosDBChatMemoryRepositoryAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(CosmosDBChatMemoryRepositoryAutoConfiguration.class))
		.withPropertyValues(
				"spring.ai.chat.memory.repository.cosmosdb.endpoint=" + System.getenv("AZURE_COSMOSDB_ENDPOINT"))
		.withPropertyValues("spring.ai.chat.memory.repository.cosmosdb.database-name=test-database")
		.withPropertyValues("spring.ai.chat.memory.repository.cosmosdb.container-name=autoconfig-test-container");

	@Test
	void addAndGet() {
		this.contextRunner.run(context -> {
			CosmosDBChatMemoryRepository memory = context.getBean(CosmosDBChatMemoryRepository.class);

			String conversationId = UUID.randomUUID().toString();
			assertThat(memory.findByConversationId(conversationId)).isEmpty();

			memory.saveAll(conversationId, List.of(new UserMessage("test question")));

			assertThat(memory.findByConversationId(conversationId)).hasSize(1);
			assertThat(memory.findByConversationId(conversationId).get(0).getMessageType()).isEqualTo(MessageType.USER);
			assertThat(memory.findByConversationId(conversationId).get(0).getText()).isEqualTo("test question");

			memory.deleteByConversationId(conversationId);
			assertThat(memory.findByConversationId(conversationId)).isEmpty();

			memory.saveAll(conversationId,
					List.of(new UserMessage("test question"), new AssistantMessage("test answer")));

			assertThat(memory.findByConversationId(conversationId)).hasSize(2);
			assertThat(memory.findByConversationId(conversationId).get(0).getMessageType()).isEqualTo(MessageType.USER);
			assertThat(memory.findByConversationId(conversationId).get(0).getText()).isEqualTo("test question");
			assertThat(memory.findByConversationId(conversationId).get(1).getMessageType())
				.isEqualTo(MessageType.ASSISTANT);
			assertThat(memory.findByConversationId(conversationId).get(1).getText()).isEqualTo("test answer");
		});
	}

	@Test
	void propertiesConfiguration() {
		this.contextRunner
			.withPropertyValues(
					"spring.ai.chat.memory.repository.cosmosdb.endpoint=" + System.getenv("AZURE_COSMOSDB_ENDPOINT"))
			.withPropertyValues("spring.ai.chat.memory.repository.cosmosdb.database-name=test-database")
			.withPropertyValues("spring.ai.chat.memory.repository.cosmosdb.container-name=custom-testcontainer")
			.withPropertyValues("spring.ai.chat.memory.repository.cosmosdb.partition-key-path=/customPartitionKey")
			.run(context -> {
				CosmosDBChatMemoryRepositoryProperties properties = context
					.getBean(CosmosDBChatMemoryRepositoryProperties.class);
				assertThat(properties.getEndpoint()).isEqualTo(System.getenv("AZURE_COSMOSDB_ENDPOINT"));
				assertThat(properties.getDatabaseName()).isEqualTo("test-database");
				assertThat(properties.getContainerName()).isEqualTo("custom-testcontainer");
				assertThat(properties.getPartitionKeyPath()).isEqualTo("/customPartitionKey");
			});
	}

	@Test
	void findConversationIds() {
		this.contextRunner.run(context -> {
			CosmosDBChatMemoryRepository memory = context.getBean(CosmosDBChatMemoryRepository.class);

			String conversationId1 = UUID.randomUUID().toString();
			String conversationId2 = UUID.randomUUID().toString();

			memory.saveAll(conversationId1, List.of(new UserMessage("test question 1")));
			memory.saveAll(conversationId2, List.of(new UserMessage("test question 2")));

			List<String> conversationIds = memory.findConversationIds();
			assertThat(conversationIds).contains(conversationId1, conversationId2);
		});
	}

}
