/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.chat.memory.repository.bedrock.agentcore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.BatchCreateMemoryRecordsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.BatchCreateMemoryRecordsResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryContent;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordCreateInput;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordSummary;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration tests for {@link BedrockAgentCoreChatMemoryRepository}.
 *
 * <p>
 * Requires the following environment variables:
 * <ul>
 * <li>{@code BEDROCK_AGENTCORE_MEMORY_ID} — the Bedrock AgentCore memory store ID
 * (required for all tests)</li>
 * <li>{@code BEDROCK_AGENTCORE_MEMORY_STRATEGY_ID} — the memory strategy ID used for
 * long-term memory tests; tests requiring this are skipped if not set</li>
 * </ul>
 *
 * @author Chaemin Lee
 */
@RequiresAwsCredentials
class BedrockAgentCoreChatMemoryRepositoryIT {

	private static final String MEMORY_ID_PROPERTY = "BEDROCK_AGENTCORE_MEMORY_ID";

	private static final String MEMORY_STRATEGY_ID_PROPERTY = "BEDROCK_AGENTCORE_MEMORY_STRATEGY_ID";

	private BedrockAgentCoreClient awsClient;

	private BedrockAgentCoreChatMemoryRepository repository;

	private String memoryId;

	private String memoryStrategyId;

	private String conversationId;

	private String testNamespace;

	private final List<String> createdRecordIds = new ArrayList<>();

	@BeforeEach
	void setUp() {
		this.memoryId = System.getenv(MEMORY_ID_PROPERTY);
		if (this.memoryId == null) {
			this.memoryId = System.getProperty(MEMORY_ID_PROPERTY);
		}
		Assumptions.assumeThat(this.memoryId)
			.as(MEMORY_ID_PROPERTY + " environment variable must be set")
			.isNotNull()
			.isNotBlank();

		this.memoryStrategyId = System.getenv(MEMORY_STRATEGY_ID_PROPERTY);
		if (this.memoryStrategyId == null) {
			this.memoryStrategyId = System.getProperty(MEMORY_STRATEGY_ID_PROPERTY);
		}

		this.awsClient = BedrockAgentCoreClient.builder()
			.credentialsProvider(DefaultCredentialsProvider.builder().build())
			.region(Region.US_EAST_1)
			.build();

		BedrockAgentCoreChatMemoryConfig config = BedrockAgentCoreChatMemoryConfig.builder()
			.bedrockAgentCoreClient(this.awsClient)
			.memoryId(this.memoryId)
			.build();

		this.repository = new BedrockAgentCoreChatMemoryRepository(config);
		this.conversationId = "springai-test-" + UUID.randomUUID();
		this.testNamespace = "springai-test-" + UUID.randomUUID();
	}

	@AfterEach
	void tearDown() {
		this.repository.deleteByConversationId(this.conversationId);
		for (String recordId : this.createdRecordIds) {
			try {
				this.repository.deleteMemoryRecord(recordId);
			}
			catch (Exception ignored) {
			}
		}
	}

	@Test
	void saveAllAndFindByConversationId() {
		List<Message> messages = List.of(UserMessage.builder().text("Hello from Spring AI").build(),
				AssistantMessage.builder().content("Hi! How can I help?").build());

		this.repository.saveAll(this.conversationId, messages);

		List<Message> found = this.repository.findByConversationId(this.conversationId);
		assertThat(found).hasSize(2);
		assertThat(found.get(0).getText()).isEqualTo("Hello from Spring AI");
		assertThat(found.get(1).getText()).isEqualTo("Hi! How can I help?");
	}

	@Test
	void saveAllReplacesExistingMessages() {
		this.repository.saveAll(this.conversationId, List.of(UserMessage.builder().text("First message").build()));
		this.repository.saveAll(this.conversationId, List.of(UserMessage.builder().text("Replaced message").build()));

		List<Message> found = this.repository.findByConversationId(this.conversationId);
		assertThat(found).hasSize(1);
		assertThat(found.get(0).getText()).isEqualTo("Replaced message");
	}

	@Test
	void deleteByConversationIdRemovesAllMessages() {
		this.repository.saveAll(this.conversationId, List.of(UserMessage.builder().text("To be deleted").build()));

		this.repository.deleteByConversationId(this.conversationId);

		List<Message> found = this.repository.findByConversationId(this.conversationId);
		assertThat(found).isEmpty();
	}

	@Test
	void findConversationIdsContainsSavedConversation() {
		this.repository.saveAll(this.conversationId, List.of(UserMessage.builder().text("Hello").build()));

		List<String> ids = this.repository.findConversationIds();
		assertThat(ids).contains(this.conversationId);
	}

	@Test
	void findByConversationIdReturnsEmptyForNonExistentConversation() {
		List<Message> found = this.repository.findByConversationId("non-existent-" + UUID.randomUUID());
		assertThat(found).isEmpty();
	}

	@Test
	void deleteByConversationIdDoesNotThrowForNonExistentConversation() {
		assertThatCode(() -> this.repository.deleteByConversationId("non-existent-" + UUID.randomUUID()))
			.doesNotThrowAnyException();
	}

	@Test
	void saveAllPreservesMessageTypes() {
		List<Message> messages = List.of(UserMessage.builder().text("User message").build(),
				AssistantMessage.builder().content("Assistant message").build(),
				SystemMessage.builder().text("System message").build());

		this.repository.saveAll(this.conversationId, messages);

		List<Message> found = this.repository.findByConversationId(this.conversationId);
		assertThat(found).hasSize(3);
		assertThat(found.get(0).getMessageType()).isEqualTo(MessageType.USER);
		assertThat(found.get(1).getMessageType()).isEqualTo(MessageType.ASSISTANT);
		assertThat(found.get(2).getMessageType()).isEqualTo(MessageType.SYSTEM);
	}

	@Test
	void listMemoryRecordsDoesNotThrow() {
		// listMemoryRecords relies on async backend indexing after record creation,
		// so we cannot assert on specific results. We verify only that the API call
		// succeeds and returns a non-null list.
		List<MemoryRecordSummary> records = this.repository.listMemoryRecords(this.testNamespace);

		assertThat(records).isNotNull();
	}

	@Test
	void listMemoryRecordsIsEmptyForUnknownNamespace() {
		List<MemoryRecordSummary> records = this.repository
			.listMemoryRecords("springai-nonexistent-" + UUID.randomUUID());

		assertThat(records).isEmpty();
	}

	@Test
	void deleteMemoryRecordDoesNotThrow() {
		String recordId = createTestMemoryRecord("some fact to be deleted");
		this.createdRecordIds.remove(recordId);

		assertThatCode(() -> this.repository.deleteMemoryRecord(recordId)).doesNotThrowAnyException();
	}

	@Test
	void retrieveMemoryRecordsDoesNotThrow() {
		// retrieveMemoryRecords relies on async semantic indexing after record creation,
		// so we cannot assert on specific results. We verify only that the API call
		// succeeds and returns a non-null list.
		List<MemoryRecordSummary> records = this.repository.retrieveMemoryRecords(this.testNamespace,
				"user preferences");

		assertThat(records).isNotNull();
	}

	/**
	 * Creates a long-term memory record directly via the AWS SDK (bypassing the
	 * repository) and registers it for cleanup in {@link #tearDown()}.
	 * <p>
	 * Skips the calling test if {@code BEDROCK_AGENTCORE_MEMORY_STRATEGY_ID} is not set.
	 */
	private String createTestMemoryRecord(String text) {
		Assumptions.assumeThat(this.memoryStrategyId)
			.as(MEMORY_STRATEGY_ID_PROPERTY + " must be set for long-term memory tests")
			.isNotNull()
			.isNotBlank();

		String requestIdentifier = UUID.randomUUID().toString();
		BatchCreateMemoryRecordsResponse response = this.awsClient
			.batchCreateMemoryRecords(BatchCreateMemoryRecordsRequest.builder()
				.memoryId(this.memoryId)
				.records(List.of(MemoryRecordCreateInput.builder()
					.requestIdentifier(requestIdentifier)
					.content(MemoryContent.fromText(text))
					.namespaces(List.of(this.testNamespace))
					.memoryStrategyId(this.memoryStrategyId)
					.timestamp(Instant.now())
					.build()))
				.build());

		assertThat(response.successfulRecords()).as("test setup: batchCreateMemoryRecords should succeed").hasSize(1);

		String recordId = response.successfulRecords().get(0).memoryRecordId();
		this.createdRecordIds.add(recordId);
		return recordId;
	}

}
