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

package org.springframework.ai.chat.memory.repository.cosmosdb;

import java.util.List;
import java.util.UUID;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link CosmosDBChatMemoryRepository}.
 *
 * @author Theo van Kraay
 * @since 1.1.0
 */
@EnabledIfEnvironmentVariable(named = "AZURE_COSMOSDB_ENDPOINT", matches = ".+")
class CosmosDBChatMemoryRepositoryIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(CosmosDBChatMemoryRepositoryIT.TestApplication.class);

	private ChatMemoryRepository chatMemoryRepository;

	@BeforeEach
	public void setup() {
		this.contextRunner.run(context -> this.chatMemoryRepository = context.getBean(ChatMemoryRepository.class));
	}

	@Test
	void ensureBeansGetsCreated() {
		this.contextRunner.run(context -> {
			CosmosDBChatMemoryRepository memory = context.getBean(CosmosDBChatMemoryRepository.class);
			Assertions.assertNotNull(memory);
		});
	}

	@ParameterizedTest
	@CsvSource({ "Message from assistant,ASSISTANT", "Message from user,USER", "Message from system,SYSTEM" })
	void add_shouldInsertSingleMessage(String content, MessageType messageType) {
		var conversationId = UUID.randomUUID().toString();
		var message = switch (messageType) {
			case ASSISTANT -> new AssistantMessage(content);
			case USER -> new UserMessage(content);
			case SYSTEM -> new SystemMessage(content);
			default -> throw new IllegalArgumentException("Type not supported: " + messageType);
		};

		this.chatMemoryRepository.saveAll(conversationId, List.of(message));
		assertThat(this.chatMemoryRepository.findConversationIds()).isNotEmpty();
		assertThat(this.chatMemoryRepository.findConversationIds()).contains(conversationId);

		List<Message> retrievedMessages = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(retrievedMessages).hasSize(1);
		assertThat(retrievedMessages.get(0).getText()).isEqualTo(content);
		assertThat(retrievedMessages.get(0).getMessageType()).isEqualTo(messageType);
	}

	@Test
	void shouldSaveAndRetrieveMultipleMessages() {
		var conversationId = UUID.randomUUID().toString();

		List<Message> messages = List.of(new SystemMessage("System message"), new UserMessage("User message"),
				new AssistantMessage("Assistant message"));

		this.chatMemoryRepository.saveAll(conversationId, messages);

		List<Message> retrievedMessages = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(retrievedMessages).hasSize(3);

		// Messages should be in the same order they were saved
		assertThat(retrievedMessages.get(0).getText()).isEqualTo("System message");
		assertThat(retrievedMessages.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);

		assertThat(retrievedMessages.get(1).getText()).isEqualTo("User message");
		assertThat(retrievedMessages.get(1).getMessageType()).isEqualTo(MessageType.USER);

		assertThat(retrievedMessages.get(2).getText()).isEqualTo("Assistant message");
		assertThat(retrievedMessages.get(2).getMessageType()).isEqualTo(MessageType.ASSISTANT);
	}

	@Test
	void shouldReplaceExistingMessages() {
		var conversationId = UUID.randomUUID().toString();

		// Save initial messages
		List<Message> initialMessages = List.of(new UserMessage("Initial user message"),
				new AssistantMessage("Initial assistant message"));
		this.chatMemoryRepository.saveAll(conversationId, initialMessages);

		// Verify initial save
		List<Message> retrievedMessages = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(retrievedMessages).hasSize(2);

		// Replace with new messages
		List<Message> newMessages = List.of(new SystemMessage("New system message"),
				new UserMessage("New user message"));
		this.chatMemoryRepository.saveAll(conversationId, newMessages);

		// Verify replacement
		retrievedMessages = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(retrievedMessages).hasSize(2);
		assertThat(retrievedMessages.get(0).getText()).isEqualTo("New system message");
		assertThat(retrievedMessages.get(1).getText()).isEqualTo("New user message");
	}

	@Test
	void shouldDeleteConversation() {
		var conversationId = UUID.randomUUID().toString();

		// Save messages
		List<Message> messages = List.of(new UserMessage("User message"), new AssistantMessage("Assistant message"));
		this.chatMemoryRepository.saveAll(conversationId, messages);

		// Verify messages exist
		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).hasSize(2);

		// Delete conversation
		this.chatMemoryRepository.deleteByConversationId(conversationId);

		// Verify messages are deleted
		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).isEmpty();
	}

	@Test
	void shouldFindAllConversationIds() {
		var conversationId1 = UUID.randomUUID().toString();
		var conversationId2 = UUID.randomUUID().toString();

		// Save messages for two conversations
		this.chatMemoryRepository.saveAll(conversationId1, List.of(new UserMessage("Message 1")));
		this.chatMemoryRepository.saveAll(conversationId2, List.of(new UserMessage("Message 2")));

		// Verify both conversation IDs are found
		List<String> conversationIds = this.chatMemoryRepository.findConversationIds();
		assertThat(conversationIds).contains(conversationId1, conversationId2);
	}

	@Test
	void shouldHandleEmptyConversation() {
		var conversationId = UUID.randomUUID().toString();

		// Try to find messages for non-existent conversation
		List<Message> messages = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(messages).isEmpty();

		// Delete non-existent conversation (should not throw)
		this.chatMemoryRepository.deleteByConversationId(conversationId);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestApplication {

		@Bean
		public CosmosAsyncClient cosmosAsyncClient() {
			return new CosmosClientBuilder().endpoint(System.getenv("AZURE_COSMOSDB_ENDPOINT"))
				.credential(new DefaultAzureCredentialBuilder().build())
				.userAgentSuffix("SpringAI-CDBNoSQL-ChatMemoryRepository")
				.gatewayMode()
				.buildAsyncClient();
		}

		@Bean
		public CosmosDBChatMemoryRepositoryConfig cosmosDBChatMemoryRepositoryConfig(
				CosmosAsyncClient cosmosAsyncClient) {
			return CosmosDBChatMemoryRepositoryConfig.builder()
				.withCosmosClient(cosmosAsyncClient)
				.withDatabaseName("test-database")
				.withContainerName("chat-memory-test-container")
				.build();
		}

		@Bean
		public CosmosDBChatMemoryRepository cosmosDBChatMemoryRepository(CosmosDBChatMemoryRepositoryConfig config) {
			return CosmosDBChatMemoryRepository.create(config);
		}

	}

}
