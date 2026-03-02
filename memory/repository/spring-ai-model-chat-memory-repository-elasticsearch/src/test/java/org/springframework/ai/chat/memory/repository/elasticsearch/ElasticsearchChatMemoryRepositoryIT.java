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

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.repository.elasticsearch.AdvancedElasticsearchChatMemoryRepository.MessageWithConversation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ElasticsearchChatMemoryRepository}.
 *
 * @author Laura Trotta
 */
@Testcontainers
class ElasticsearchChatMemoryRepositoryIT {

	@Container
	static ElasticsearchContainer elasticsearch = new ElasticsearchContainer("elasticsearch:9.3.0")
		.withEnv("xpack.security.enabled", "false");

	private ElasticsearchChatMemoryRepository repository;

	@BeforeEach
	void setUp() throws URISyntaxException {
		Rest5Client restClient = Rest5Client.builder(HttpHost.create(elasticsearch.getHttpHostAddress())).build();

		repository = ElasticsearchChatMemoryRepository.builder(restClient).indexName("test-chat-memory").build();

		for (String id : repository.findConversationIds()) {
			repository.deleteByConversationId(id);
		}
	}

	@AfterEach
	void tearDown() {
		for (String id : repository.findConversationIds()) {
			repository.deleteByConversationId(id);
		}
	}

	@Test
	void shouldSaveAndFindByConversationId() {
		List<Message> messages = List.of(new UserMessage("Hello"), new AssistantMessage("Hi there!"));
		repository.saveAll("conv-1", messages);

		List<Message> retrieved = repository.findByConversationId("conv-1");
		assertThat(retrieved).hasSize(2);
		assertThat(retrieved.get(0).getText()).isEqualTo("Hello");
		assertThat(retrieved.get(1).getText()).isEqualTo("Hi there!");
	}

	@Test
	void shouldFindConversationIds() {
		repository.saveAll("conv-1", List.of(new UserMessage("Hello")));
		repository.saveAll("conv-2", List.of(new UserMessage("World")));

		List<String> ids = repository.findConversationIds();
		assertThat(ids).containsExactlyInAnyOrder("conv-1", "conv-2");
	}

	@Test
	void shouldDeleteByConversationId() {
		repository.saveAll("conv-1", List.of(new UserMessage("Hello")));
		assertThat(repository.findByConversationId("conv-1")).hasSize(1);

		repository.deleteByConversationId("conv-1");

		assertThat(repository.findByConversationId("conv-1")).isEmpty();
	}

	@Test
	void shouldFindByContent() {
		repository.saveAll("conv-1", List.of(new UserMessage("The weather is nice today")));
		repository.saveAll("conv-2", List.of(new UserMessage("Something else entirely")));

		List<MessageWithConversation> results = repository.findByContent("weather", 10);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).conversationId()).isEqualTo("conv-1");
	}

	@Test
	void shouldFindByType() {
		repository.saveAll("conv-1", List.of(new UserMessage("Hello"), new AssistantMessage("Hi")));

		List<MessageWithConversation> results = repository.findByType(MessageType.ASSISTANT, 10);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).message().getText()).isEqualTo("Hi");
	}

	@Test
	void shouldFindByTimeRange() {
		Instant before = Instant.now();
		repository.saveAll("conv-1", List.of(new UserMessage("Hello")));
		Instant after = Instant.now().plusSeconds(1);

		List<MessageWithConversation> results = repository.findByTimeRange("conv-1", before, after, 10);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).message().getText()).isEqualTo("Hello");
	}

	@Test
	void shouldFindByMetadata() {
		Message msg = UserMessage.builder().text("tagged message").metadata(Map.of("priority", "high")).build();
		repository.saveAll("conv-1", List.of(msg));

		List<MessageWithConversation> results = repository.findByMetadata("priority", "high", 10);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).message().getText()).isEqualTo("tagged message");
	}

	@Test
	void shouldExecuteCustomQuery() {
		repository.saveAll("conv-1", List.of(new UserMessage("findme")));

		// with json query
		String query = """
				{"match": {"content": "findme"}}
				""";

		List<MessageWithConversation> results = repository.executeQuery(query, 10);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).message().getText()).isEqualTo("findme");

		// with elasticsearch java client Query
		Query query1 = Query.of(q -> q.match(m -> m.field("content").query("findme")));

		results = repository.executeQuery(query1, 10);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).message().getText()).isEqualTo("findme");
	}

}
