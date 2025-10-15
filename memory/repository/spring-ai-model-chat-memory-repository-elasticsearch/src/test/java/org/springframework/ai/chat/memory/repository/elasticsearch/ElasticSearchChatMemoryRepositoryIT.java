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

package org.springframework.ai.chat.memory.repository.elasticsearch;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
 * Integration tests for {@link ElasticSearchChatMemoryRepository}.
 *
 * @author Fu Jian
 * @since 1.1.0
 */
@Testcontainers
class ElasticSearchChatMemoryRepositoryIT {

	@Container
	static ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer(
			"docker.elastic.co/elasticsearch/elasticsearch:8.10.2")
		.withEnv("xpack.security.enabled", "false")
		.withEnv("xpack.security.http.ssl.enabled", "false");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(ElasticSearchChatMemoryRepositoryIT.TestApplication.class);

	@Test
	void ensureBeansGetsCreated() {
		this.contextRunner.run(context -> {
			ElasticSearchChatMemoryRepository memory = context.getBean(ElasticSearchChatMemoryRepository.class);
			Assertions.assertNotNull(memory);
		});
	}

	@ParameterizedTest
	@CsvSource({ "Message from assistant,ASSISTANT", "Message from user,USER", "Message from system,SYSTEM" })
	void add_shouldInsertSingleMessage(String content, MessageType messageType) {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemoryRepository.class);
			assertThat(chatMemory).isInstanceOf(ElasticSearchChatMemoryRepository.class);

			var conversationId = UUID.randomUUID().toString();
			var message = switch (messageType) {
				case ASSISTANT -> new AssistantMessage(content);
				case USER -> new UserMessage(content);
				case SYSTEM -> new SystemMessage(content);
				default -> throw new IllegalArgumentException("Type not supported: " + messageType);
			};

			java.time.Instant beforeSave = java.time.Instant.now();
			chatMemory.saveAll(conversationId, List.of(message));
			java.time.Instant afterSave = java.time.Instant.now();

			sleepForSearchable();
			assertThat(chatMemory.findConversationIds()).isNotEmpty();
			assertThat(chatMemory.findConversationIds()).contains(conversationId);

			List<Message> retrievedMessages = chatMemory.findByConversationId(conversationId);
			assertThat(retrievedMessages).hasSize(1);
			assertThat(retrievedMessages.get(0).getText()).isEqualTo(content);
			assertThat(retrievedMessages.get(0).getMessageType()).isEqualTo(messageType);

			// Verify timestamp was automatically added
			Object timestampObj = retrievedMessages.get(0)
				.getMetadata()
				.get(ElasticSearchChatMemoryRepository.CONVERSATION_TS);
			assertThat(timestampObj).as("Timestamp should be automatically added").isNotNull();
			assertThat(timestampObj).isInstanceOf(java.time.Instant.class);
			java.time.Instant timestamp = (java.time.Instant) timestampObj;
			assertThat(timestamp).isBetween(beforeSave.minusSeconds(1), afterSave.plusSeconds(1));
		});
	}

	private static void sleepForSearchable() throws InterruptedException {
		TimeUnit.SECONDS.sleep(2);
	}

	@Test
	void shouldSaveAndRetrieveMultipleMessages() {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemoryRepository.class);
			assertThat(chatMemory).isInstanceOf(ElasticSearchChatMemoryRepository.class);

			var conversationId = UUID.randomUUID().toString();

			// Use 5 messages to verify sequenceNumber preserves order
			List<Message> messages = List.of(new SystemMessage("System message"), new UserMessage("User message"),
					new AssistantMessage("Assistant message"), new UserMessage("Second user message"),
					new AssistantMessage("Second assistant message"));

			java.time.Instant beforeSave = java.time.Instant.now();
			chatMemory.saveAll(conversationId, messages);
			java.time.Instant afterSave = java.time.Instant.now();

			sleepForSearchable();

			List<Message> retrievedMessages = chatMemory.findByConversationId(conversationId);
			assertThat(retrievedMessages).hasSize(5);

			// Verify sequenceNumber preserves message order
			assertThat(retrievedMessages.get(0).getText()).isEqualTo("System message");
			assertThat(retrievedMessages.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);

			assertThat(retrievedMessages.get(1).getText()).isEqualTo("User message");
			assertThat(retrievedMessages.get(1).getMessageType()).isEqualTo(MessageType.USER);

			assertThat(retrievedMessages.get(2).getText()).isEqualTo("Assistant message");
			assertThat(retrievedMessages.get(2).getMessageType()).isEqualTo(MessageType.ASSISTANT);

			assertThat(retrievedMessages.get(3).getText()).isEqualTo("Second user message");
			assertThat(retrievedMessages.get(3).getMessageType()).isEqualTo(MessageType.USER);

			assertThat(retrievedMessages.get(4).getText()).isEqualTo("Second assistant message");
			assertThat(retrievedMessages.get(4).getMessageType()).isEqualTo(MessageType.ASSISTANT);

			// Verify timestamp consistency: all messages saved together should have the
			// same timestamp
			java.time.Instant firstTimestamp = null;
			for (Message message : retrievedMessages) {
				Object timestampObj = message.getMetadata().get(ElasticSearchChatMemoryRepository.CONVERSATION_TS);
				assertThat(timestampObj).isNotNull();

				java.time.Instant timestamp = (java.time.Instant) timestampObj;
				assertThat(timestamp).isBetween(beforeSave.minusSeconds(1), afterSave.plusSeconds(1));

				if (firstTimestamp == null) {
					firstTimestamp = timestamp;
				}
				else {
					// All messages in same saveAll should have identical timestamp
					assertThat(timestamp).isEqualTo(firstTimestamp);
				}
			}
		});
	}

	@Test
	void shouldReplaceExistingMessages() {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemoryRepository.class);
			var conversationId = UUID.randomUUID().toString();

			// Save initial messages (3 messages)
			List<Message> initialMessages = List.of(new UserMessage("Initial user message"),
					new AssistantMessage("Initial assistant message"), new UserMessage("Initial third message"));
			chatMemory.saveAll(conversationId, initialMessages);

			sleepForSearchable();

			// Verify initial save and capture timestamp
			List<Message> retrievedMessages = chatMemory.findByConversationId(conversationId);
			assertThat(retrievedMessages).hasSize(3);
			java.time.Instant initialTimestamp = (java.time.Instant) retrievedMessages.get(0)
				.getMetadata()
				.get(ElasticSearchChatMemoryRepository.CONVERSATION_TS);

			// Wait to ensure timestamp difference
			TimeUnit.MILLISECONDS.sleep(100);

			// Replace with new messages (2 messages - verify sequenceNumber restarts)
			List<Message> newMessages = List.of(new SystemMessage("New system message"),
					new UserMessage("New user message"));
			chatMemory.saveAll(conversationId, newMessages);

			sleepForSearchable();

			// Verify replacement
			retrievedMessages = chatMemory.findByConversationId(conversationId);
			assertThat(retrievedMessages).hasSize(2);
			assertThat(retrievedMessages.get(0).getText()).isEqualTo("New system message");
			assertThat(retrievedMessages.get(1).getText()).isEqualTo("New user message");

			// Verify timestamp was updated (should be after initial timestamp)
			java.time.Instant updatedTimestamp = (java.time.Instant) retrievedMessages.get(0)
				.getMetadata()
				.get(ElasticSearchChatMemoryRepository.CONVERSATION_TS);
			assertThat(updatedTimestamp).isAfter(initialTimestamp);
		});
	}

	@Test
	void shouldDeleteConversation() {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemoryRepository.class);
			var conversationId = UUID.randomUUID().toString();

			// Save messages
			List<Message> messages = List.of(new UserMessage("User message"),
					new AssistantMessage("Assistant message"));
			chatMemory.saveAll(conversationId, messages);

			sleepForSearchable();

			// Verify messages exist
			assertThat(chatMemory.findByConversationId(conversationId)).hasSize(2);

			// Delete conversation
			chatMemory.deleteByConversationId(conversationId);

			sleepForSearchable();

			// Verify messages are deleted
			assertThat(chatMemory.findByConversationId(conversationId)).isEmpty();
		});
	}

	@Test
	void shouldFindAllConversationIds() {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemoryRepository.class);
			var conversationId1 = UUID.randomUUID().toString();
			var conversationId2 = UUID.randomUUID().toString();

			// Save messages for two conversations
			chatMemory.saveAll(conversationId1, List.of(new UserMessage("Message 1")));
			chatMemory.saveAll(conversationId2, List.of(new UserMessage("Message 2")));

			sleepForSearchable();

			// Verify both conversation IDs are found
			List<String> conversationIds = chatMemory.findConversationIds();
			assertThat(conversationIds).contains(conversationId1, conversationId2);
		});
	}

	@Test
	void shouldHandleEmptyConversation() {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemoryRepository.class);
			var conversationId = UUID.randomUUID().toString();

			// Try to find messages for non-existent conversation
			List<Message> messages = chatMemory.findByConversationId(conversationId);
			assertThat(messages).isEmpty();

			// Delete non-existent conversation (should not throw)
			chatMemory.deleteByConversationId(conversationId);
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestApplication {

		@Bean
		public ElasticsearchClient elasticsearchClient() throws IOException {
			var credentialsProvider = new BasicCredentialsProvider();
			credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("elastic", "changeme"));

			var restClient = RestClient.builder(HttpHost.create(elasticsearchContainer.getHttpHostAddress())).build();

			var transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
			return new ElasticsearchClient(transport);
		}

		@Bean
		public ElasticSearchChatMemoryRepositoryConfig elasticSearchChatMemoryRepositoryConfig(
				ElasticsearchClient elasticsearchClient) {
			return ElasticSearchChatMemoryRepositoryConfig.builder()
				.withClient(elasticsearchClient)
				.withIndexName("test-chat-memory")
				.build();
		}

		@Bean
		public ElasticSearchChatMemoryRepository elasticSearchChatMemoryRepository(
				ElasticSearchChatMemoryRepositoryConfig config) {
			return ElasticSearchChatMemoryRepository.create(config);
		}

	}

}
