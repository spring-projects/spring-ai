/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.vectorstore;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Fabian Krüger
 * @author Soby Chacko
 * @author Thomas Vitale
 */
@Testcontainers
class PgVectorStoreWithChatMemoryAdvisorIT {

	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(PgVectorImage.DEFAULT_IMAGE)
		.withUsername("postgres")
		.withPassword("postgres");

	float[] embed = { 0.003961659F, -0.0073295482F, 0.02663665F };

	private static @NotNull ChatModel chatModelAlwaysReturnsTheSameReply() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> argumentCaptor = ArgumentCaptor.forClass(Prompt.class);
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				Why don't scientists trust atoms?
				Because they make up everything!
				"""))));
		given(chatModel.call(argumentCaptor.capture())).willReturn(chatResponse);
		return chatModel;
	}

	private static void initStore(PgVectorStore store) throws Exception {
		store.afterPropertiesSet();
		// fill the store
		store.add(List.of(new Document("Tell me a good joke", Map.of("conversationId", "default")),
				new Document("Tell me a bad joke", Map.of("conversationId", "default", "messageType", "USER"))));
	}

	private static PgVectorStore createPgVectorStoreUsingTestcontainer(EmbeddingModel embeddingModel) throws Exception {
		JdbcTemplate jdbcTemplate = createJdbcTemplateWithConnectionToTestcontainer();
		PgVectorStore vectorStore = new PgVectorStore.Builder(jdbcTemplate, embeddingModel).withDimensions(3) // match
			// embeddings
			.withInitializeSchema(true)
			.build();
		initStore(vectorStore);
		return vectorStore;
	}

	private static @NotNull JdbcTemplate createJdbcTemplateWithConnectionToTestcontainer() {
		PGSimpleDataSource ds = new PGSimpleDataSource();
		ds.setUrl("jdbc:postgresql://localhost:" + postgresContainer.getMappedPort(5432) + "/postgres");
		ds.setUser(postgresContainer.getUsername());
		ds.setPassword(postgresContainer.getPassword());
		return new JdbcTemplate(ds);
	}

	private static void verifyRequestHasBeenAdvisedWithMessagesFromVectorStore(ChatModel chatModel) {
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		verify(chatModel).call(promptCaptor.capture());
		assertThat(promptCaptor.getValue().getInstructions().get(0)).isInstanceOf(SystemMessage.class);
		assertThat(promptCaptor.getValue().getInstructions().get(0).getContent()).isEqualTo("""

				Use the long term conversation memory from the LONG_TERM_MEMORY section to provide accurate answers.

				---------------------
				LONG_TERM_MEMORY:
				Tell me a good joke
				Tell me a bad joke
				---------------------
				""");
	}

	/**
	 * Test that chats with {@link VectorStoreChatMemoryAdvisor} get advised with similar
	 * messages from the (gp)vector store.
	 */
	@Test
	@DisplayName("Advised chat should have similar messages from vector store")
	void advisedChatShouldHaveSimilarMessagesFromVectorStore() throws Exception {
		// faked ChatModel
		ChatModel chatModel = chatModelAlwaysReturnsTheSameReply();
		// faked embedding model
		EmbeddingModel embeddingModel = embeddingNModelShouldAlwaysReturnFakedEmbed();
		PgVectorStore store = createPgVectorStoreUsingTestcontainer(embeddingModel);

		// do the chat
		ChatClient.builder(chatModel)
			.build()
			.prompt()
			.user("joke")
			.advisors(new VectorStoreChatMemoryAdvisor(store))
			.call()
			.chatResponse();

		verifyRequestHasBeenAdvisedWithMessagesFromVectorStore(chatModel);
	}

	@SuppressWarnings("unchecked")
	private @NotNull EmbeddingModel embeddingNModelShouldAlwaysReturnFakedEmbed() {
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

		Mockito.doAnswer(invocationOnMock -> {
			return List.of(this.embed, this.embed);
		}).when(embeddingModel).embed(ArgumentMatchers.any(), any(), any());
		given(embeddingModel.embed(any(String.class))).willReturn(this.embed);
		return embeddingModel;
	}

}
