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

package org.springframework.ai.vectorstore.pgvector;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class PgVectorStoreVectorStoreChatMemoryAdvisorIT {

	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(PgVectorImage.DEFAULT_IMAGE)
		.withUsername("postgres")
		.withPassword("postgres");

	@Autowired
	protected org.springframework.ai.chat.model.ChatModel chatModel;

	@Test
	void testUseCustomConversationId() throws Exception {
		String apiKey = System.getenv("OPENAI_API_KEY");
		org.junit.jupiter.api.Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
				"OPENAI_API_KEY must be set for this test");

		// Use a real OpenAI embedding model
		EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(apiKey).build());

		// Create PgVectorStore
		JdbcTemplate jdbcTemplate = createJdbcTemplateWithConnectionToTestcontainer();
		PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel)
			.dimensions(1536) // OpenAI default embedding size (adjust if needed)
			.initializeSchema(true)
			.build();
		store.afterPropertiesSet();

		// Add a document to the store for recall
		String conversationId = UUID.randomUUID().toString();
		store.add(java.util.List
			.of(new Document("Hello from memory", java.util.Map.of("conversationId", conversationId))));

		// Build ChatClient with VectorStoreChatMemoryAdvisor
		ChatClient chatClient = ChatClient.builder(this.chatModel)
			.defaultAdvisors(VectorStoreChatMemoryAdvisor.builder(store).build())
			.build();

		// Send a prompt
		String answer = chatClient.prompt()
			.user("Say hello")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();

		assertThat(answer).containsIgnoringCase("hello");

	}

	@Test
	void testSemanticSearchRetrievesRelevantMemory() throws Exception {
		String apiKey = System.getenv("OPENAI_API_KEY");
		org.junit.jupiter.api.Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
				"OPENAI_API_KEY must be set for this test");

		EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(apiKey).build());
		JdbcTemplate jdbcTemplate = createJdbcTemplateWithConnectionToTestcontainer();
		PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel)
			.dimensions(1536)
			.initializeSchema(true)
			.build();
		store.afterPropertiesSet();

		String conversationId = UUID.randomUUID().toString();
		// Store diverse messages
		store.add(java.util.List.of(
				new Document("The Eiffel Tower is in Paris.", java.util.Map.of("conversationId", conversationId)),
				new Document("Bananas are yellow.", java.util.Map.of("conversationId", conversationId)),
				new Document("Mount Everest is the tallest mountain in the world.",
						java.util.Map.of("conversationId", conversationId)),
				new Document("Dogs are loyal pets.", java.util.Map.of("conversationId", conversationId))));

		ChatClient chatClient = ChatClient.builder(this.chatModel)
			.defaultAdvisors(VectorStoreChatMemoryAdvisor.builder(store).defaultTopK(1).build())
			.build();

		// Send a semantically related query
		String answer = chatClient.prompt()
			.user("Where is the Eiffel Tower located?")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();

		// Assert that the answer is based on the correct semantic memory
		assertThat(answer).containsIgnoringCase("paris");
		assertThat(answer).doesNotContain("Bananas are yellow");
		assertThat(answer).doesNotContain("Mount Everest");
		assertThat(answer).doesNotContain("Dogs are loyal pets");
	}

	@Test
	void testSemanticSynonymRetrieval() throws Exception {
		String apiKey = System.getenv("OPENAI_API_KEY");
		org.junit.jupiter.api.Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
				"OPENAI_API_KEY must be set for this test");

		EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(apiKey).build());
		JdbcTemplate jdbcTemplate = createJdbcTemplateWithConnectionToTestcontainer();
		PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel)
			.dimensions(1536)
			.initializeSchema(true)
			.build();
		store.afterPropertiesSet();

		String conversationId = UUID.randomUUID().toString();
		store.add(java.util.List
			.of(new Document("Automobiles are fast.", java.util.Map.of("conversationId", conversationId))));

		ChatClient chatClient = ChatClient.builder(this.chatModel)
			.defaultAdvisors(VectorStoreChatMemoryAdvisor.builder(store).defaultTopK(1).build())
			.build();

		String answer = chatClient.prompt()
			.user("Tell me about cars.")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();
		assertThat(answer).satisfiesAnyOf(a -> assertThat(a).containsIgnoringCase("automobile"),
				a -> assertThat(a).containsIgnoringCase("fast"));
	}

	@Test
	void testIrrelevantMessageExclusion() throws Exception {
		String apiKey = System.getenv("OPENAI_API_KEY");
		org.junit.jupiter.api.Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
				"OPENAI_API_KEY must be set for this test");

		EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(apiKey).build());
		JdbcTemplate jdbcTemplate = createJdbcTemplateWithConnectionToTestcontainer();
		PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel)
			.dimensions(1536)
			.initializeSchema(true)
			.build();
		store.afterPropertiesSet();

		String conversationId = UUID.randomUUID().toString();
		store.add(java.util.List.of(
				new Document("The capital of Italy is Rome.", java.util.Map.of("conversationId", conversationId)),
				new Document("Bananas are yellow.", java.util.Map.of("conversationId", conversationId))));

		ChatClient chatClient = ChatClient.builder(this.chatModel)
			.defaultAdvisors(VectorStoreChatMemoryAdvisor.builder(store).defaultTopK(2).build())
			.build();

		String answer = chatClient.prompt()
			.user("What is the capital of Italy?")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();
		assertThat(answer).containsIgnoringCase("rome");
		assertThat(answer).doesNotContain("banana");
	}

	@Test
	void testTopKSemanticRelevance() throws Exception {
		String apiKey = System.getenv("OPENAI_API_KEY");
		org.junit.jupiter.api.Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
				"OPENAI_API_KEY must be set for this test");

		EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(apiKey).build());
		JdbcTemplate jdbcTemplate = createJdbcTemplateWithConnectionToTestcontainer();
		PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel)
			.dimensions(1536)
			.initializeSchema(true)
			.build();
		store.afterPropertiesSet();

		String conversationId = UUID.randomUUID().toString();
		store.add(java.util.List.of(
				new Document("The cat sat on the mat.", java.util.Map.of("conversationId", conversationId)),
				new Document("A cat is a small domesticated animal.",
						java.util.Map.of("conversationId", conversationId)),
				new Document("Dogs are loyal pets.", java.util.Map.of("conversationId", conversationId))));

		ChatClient chatClient = ChatClient.builder(this.chatModel)
			.defaultAdvisors(VectorStoreChatMemoryAdvisor.builder(store).defaultTopK(1).build())
			.build();

		String answer = chatClient.prompt()
			.user("What can you tell me about cats?")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();
		assertThat(answer).containsIgnoringCase("cat");
		assertThat(answer).doesNotContain("dog");
	}

	@Test
	void testSemanticRetrievalWithParaphrasing() throws Exception {
		String apiKey = System.getenv("OPENAI_API_KEY");
		org.junit.jupiter.api.Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
				"OPENAI_API_KEY must be set for this test");

		EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(apiKey).build());
		JdbcTemplate jdbcTemplate = createJdbcTemplateWithConnectionToTestcontainer();
		PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel)
			.dimensions(1536)
			.initializeSchema(true)
			.build();
		store.afterPropertiesSet();

		String conversationId = UUID.randomUUID().toString();
		store.add(java.util.List.of(new Document("The quick brown fox jumps over the lazy dog.",
				java.util.Map.of("conversationId", conversationId))));

		ChatClient chatClient = ChatClient.builder(this.chatModel)
			.defaultAdvisors(VectorStoreChatMemoryAdvisor.builder(store).defaultTopK(1).build())
			.build();

		String answer = chatClient.prompt()
			.user("Tell me about a fast animal leaping over another.")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();
		assertThat(answer).satisfiesAnyOf(a -> assertThat(a).containsIgnoringCase("fox"),
				a -> assertThat(a).containsIgnoringCase("dog"));
	}

	@Test
	void testMultipleRelevantMemoriesTopK() throws Exception {
		String apiKey = System.getenv("OPENAI_API_KEY");
		org.junit.jupiter.api.Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
				"OPENAI_API_KEY must be set for this test");

		EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(apiKey).build());
		JdbcTemplate jdbcTemplate = createJdbcTemplateWithConnectionToTestcontainer();
		PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel)
			.dimensions(1536)
			.initializeSchema(true)
			.build();
		store.afterPropertiesSet();

		String conversationId = UUID.randomUUID().toString();
		store.add(java.util.List.of(new Document("Apples are red.", java.util.Map.of("conversationId", conversationId)),
				new Document("Strawberries are also red.", java.util.Map.of("conversationId", conversationId)),
				new Document("Bananas are yellow.", java.util.Map.of("conversationId", conversationId))));

		ChatClient chatClient = ChatClient.builder(this.chatModel)
			.defaultAdvisors(VectorStoreChatMemoryAdvisor.builder(store).defaultTopK(2).build())
			.build();

		String answer = chatClient.prompt()
			.user("What fruits are red?")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();
		assertThat(answer).containsIgnoringCase("apple");
		assertThat(answer).containsIgnoringCase("strawber");
		assertThat(answer).doesNotContain("banana");
	}

	@Test
	void testNoRelevantMemory() throws Exception {
		String apiKey = System.getenv("OPENAI_API_KEY");
		org.junit.jupiter.api.Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
				"OPENAI_API_KEY must be set for this test");

		EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(OpenAiApi.builder().apiKey(apiKey).build());
		JdbcTemplate jdbcTemplate = createJdbcTemplateWithConnectionToTestcontainer();
		PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel)
			.dimensions(1536)
			.initializeSchema(true)
			.build();
		store.afterPropertiesSet();

		String conversationId = UUID.randomUUID().toString();
		store.add(java.util.List
			.of(new Document("The sun is a star.", java.util.Map.of("conversationId", conversationId))));

		ChatClient chatClient = ChatClient.builder(this.chatModel)
			.defaultAdvisors(VectorStoreChatMemoryAdvisor.builder(store).defaultTopK(1).build())
			.build();

		String answer = chatClient.prompt()
			.user("What is the capital of Spain?")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.content();
		assertThat(answer).doesNotContain("sun");
		assertThat(answer).doesNotContain("star");
	}

	private static JdbcTemplate createJdbcTemplateWithConnectionToTestcontainer() {
		org.postgresql.ds.PGSimpleDataSource ds = new org.postgresql.ds.PGSimpleDataSource();
		ds.setUrl("jdbc:postgresql://localhost:" + postgresContainer.getMappedPort(5432) + "/postgres");
		ds.setUser(postgresContainer.getUsername());
		ds.setPassword(postgresContainer.getPassword());
		return new JdbcTemplate(ds);
	}

	@Configuration
	public static class OpenAiTestConfiguration {

		@Bean
		public OpenAiApi openAiApi() {
			return OpenAiApi.builder().apiKey(getApiKey()).build();
		}

		private ApiKey getApiKey() {
			String apiKey = System.getenv("OPENAI_API_KEY");
			if (!org.springframework.util.StringUtils.hasText(apiKey)) {
				throw new IllegalArgumentException(
						"You must provide an API key.  Put it in an environment variable under the name OPENAI_API_KEY");
			}
			return new SimpleApiKey(apiKey);
		}

		@Bean
		public OpenAiChatModel openAiChatModel(OpenAiApi api) {
			return OpenAiChatModel.builder()
				.openAiApi(api)
				.defaultOptions(OpenAiChatOptions.builder().model(OpenAiApi.ChatModel.GPT_4_O_MINI).build())
				.build();
		}

	}

}
