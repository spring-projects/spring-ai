/*
 * Copyright 2026-2026 the original author or authors.
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

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.repository.elasticsearch.AdvancedElasticsearchChatMemoryRepository.MessageWithConversation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ElasticsearchChatMemoryRepository} with metadata schema
 * validation. Demonstrates that Elasticsearch's dynamic mapping properly indexes metadata
 * fields with appropriate types (string and numeric).
 *
 * @author Laura Trotta
 */
@Testcontainers
class ElasticsearchChatMemoryWithSchemaIT {

	@Container
	static ElasticsearchContainer elasticsearch = new ElasticsearchContainer("elasticsearch:9.3.0")
		.withEnv("xpack.security.enabled", "false");

	private ElasticsearchChatMemoryRepository repository;

	@BeforeEach
	void setUp() throws URISyntaxException {
		Rest5Client restClient = Rest5Client.builder(HttpHost.create(elasticsearch.getHttpHostAddress())).build();

		String uniqueIndexName = "test-schema-" + System.currentTimeMillis();

		repository = ElasticsearchChatMemoryRepository.builder(restClient).indexName(uniqueIndexName).build();
	}

	@AfterEach
	void tearDown() {
		for (String id : repository.findConversationIds()) {
			repository.deleteByConversationId(id);
		}
	}

	@Test
	void shouldFindMessagesByMetadataWithProperSchema() {
		String conversationId = "test-metadata-schema";

		UserMessage userMsg1 = UserMessage.builder()
			.text("High priority task")
			.metadata(Map.of("priority", "high", "category", "task", "score", 95))
			.build();

		AssistantMessage assistantMsg = AssistantMessage.builder()
			.content("I'll help with that")
			.properties(Map.of("model", "gpt-4", "confidence", 0.95, "category", "response"))
			.build();

		UserMessage userMsg2 = UserMessage.builder()
			.text("Low priority question")
			.metadata(Map.of("priority", "low", "category", "question", "score", 75))
			.build();

		repository.saveAll(conversationId, List.of(userMsg1, assistantMsg, userMsg2));

		// Find by string metadata (priority)
		List<MessageWithConversation> highPriorityMessages = repository.findByMetadata("priority", "high", 10);

		assertThat(highPriorityMessages).hasSize(1);
		assertThat(highPriorityMessages.get(0).message().getText()).isEqualTo("High priority task");

		// Find by string metadata (category)
		List<MessageWithConversation> taskMessages = repository.findByMetadata("category", "task", 10);

		assertThat(taskMessages).hasSize(1);

		// Find by numeric metadata (integer score)
		List<MessageWithConversation> highScoreMessages = repository.findByMetadata("score", 95, 10);

		assertThat(highScoreMessages).hasSize(1);
		assertThat(highScoreMessages.get(0).message().getText()).isEqualTo("High priority task");

		// Find by numeric metadata (floating-point confidence)
		List<MessageWithConversation> confidentMessages = repository.findByMetadata("confidence", 0.95, 10);

		assertThat(confidentMessages).hasSize(1);
		assertThat(confidentMessages.get(0).message().getMetadata().get("model")).isEqualTo("gpt-4");

		// Non-existent metadata key should return empty results
		List<MessageWithConversation> nonExistentMessages = repository.findByMetadata("nonexistent", "value", 10);

		assertThat(nonExistentMessages).isEmpty();
	}

	@Test
	void shouldSearchDynamicallyMappedMetadataFields() {
		String conversationId = "test-dynamic-metadata";

		UserMessage userMsg = UserMessage.builder()
			.text("Message with custom metadata")
			.metadata(Map.of("customfield", "customvalue", "priority", "medium"))
			.build();

		repository.saveAll(conversationId, List.of(userMsg));

		// Explicitly defined-style field should work
		List<MessageWithConversation> priorityMessages = repository.findByMetadata("priority", "medium", 10);

		assertThat(priorityMessages).hasSize(1);

		// Dynamically mapped custom field should also be searchable since
		// Elasticsearch auto-detects and indexes all metadata sub-fields
		List<MessageWithConversation> customMessages = repository.findByMetadata("customfield", "customvalue", 10);

		assertThat(customMessages).hasSize(1);
		assertThat(customMessages.get(0).message().getText()).isEqualTo("Message with custom metadata");
	}

}
