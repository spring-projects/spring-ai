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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;

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

	private static @NonNull ChatModel chatModelAlwaysReturnsTheSameReply() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> argumentCaptor = ArgumentCaptor.forClass(Prompt.class);
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				Why don't scientists trust atoms?
				Because they make up everything!
				"""))));
		given(chatModel.call(argumentCaptor.capture())).willReturn(chatResponse);
		return chatModel;
	}

	private static void initStore(PgVectorStore store, String conversationId) {
		store.afterPropertiesSet();
		// fill the store
		store.add(List.of(new Document("Tell me a good joke", Map.of("conversationId", conversationId)),
				new Document("Tell me a bad joke", Map.of("conversationId", conversationId, "messageType", "USER"))));
	}

	private static PgVectorStore createPgVectorStoreUsingTestcontainer(EmbeddingModel embeddingModel) throws Exception {
		JdbcTemplate jdbcTemplate = createJdbcTemplateWithConnectionToTestcontainer();
		return PgVectorStore.builder(jdbcTemplate, embeddingModel)
			.dimensions(3) // match
			// embeddings
			.initializeSchema(true)
			.build();
	}

	private static @NonNull JdbcTemplate createJdbcTemplateWithConnectionToTestcontainer() {
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
		assertThat(promptCaptor.getValue().getInstructions().get(0).getText()).isEqualToIgnoringWhitespace("""

				Use the long term conversation memory from the LONG_TERM_MEMORY section to provide accurate answers.

				---------------------
				LONG_TERM_MEMORY:
				Tell me a good joke
				Tell me a bad joke
				---------------------
				""");
	}

	/**
	 * Create a mock ChatModel that supports streaming responses for testing.
	 * @return A mock ChatModel that returns a predefined streaming response
	 */
	private static @NonNull ChatModel chatModelWithStreamingSupport() {
		ChatModel chatModel = mock(ChatModel.class);

		// Mock the regular call method
		ArgumentCaptor<Prompt> argumentCaptor = ArgumentCaptor.forClass(Prompt.class);
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				Why don't scientists trust atoms?
				Because they make up everything!
				"""))));
		given(chatModel.call(argumentCaptor.capture())).willReturn(chatResponse);

		// Mock the streaming method
		ArgumentCaptor<Prompt> streamArgumentCaptor = ArgumentCaptor.forClass(Prompt.class);
		Flux<ChatResponse> streamingResponse = Flux.just(
				new ChatResponse(List.of(new Generation(new AssistantMessage("Why")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(" don't")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(" scientists")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(" trust")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(" atoms?")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage("\nBecause")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(" they")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(" make")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(" up")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(" everything!")))));
		given(chatModel.stream(streamArgumentCaptor.capture())).willReturn(streamingResponse);

		return chatModel;
	}

	/**
	 * Create a mock ChatModel that simulates the problematic streaming behavior. This
	 * mock includes a final empty message that triggers the bug in
	 * VectorStoreChatMemoryAdvisor.
	 * @return A mock ChatModel that returns a problematic streaming response
	 */
	private static @NonNull ChatModel chatModelWithProblematicStreamingBehavior() {
		ChatModel chatModel = mock(ChatModel.class);

		// Mock the regular call method
		ArgumentCaptor<Prompt> argumentCaptor = ArgumentCaptor.forClass(Prompt.class);
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				Why don't scientists trust atoms?
				Because they make up everything!
				"""))));
		given(chatModel.call(argumentCaptor.capture())).willReturn(chatResponse);

		// Mock the streaming method with a problematic final message (empty content)
		// This simulates the real-world condition that triggers the bug
		ArgumentCaptor<Prompt> streamArgumentCaptor = ArgumentCaptor.forClass(Prompt.class);
		Flux<ChatResponse> streamingResponse = Flux.just(
				new ChatResponse(List.of(new Generation(new AssistantMessage("Why")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(" don't")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(" scientists")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(" trust")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(" atoms?")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage("\nBecause")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(" they")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(" make")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(" up")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(" everything!")))),
				// This final empty message triggers the bug in
				// VectorStoreChatMemoryAdvisor
				new ChatResponse(List.of(new Generation(new AssistantMessage("")))));
		given(chatModel.stream(streamArgumentCaptor.capture())).willReturn(streamingResponse);

		return chatModel;
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
		String conversationId = UUID.randomUUID().toString();
		initStore(store, conversationId);

		// do the chat
		ChatClient.builder(chatModel)
			.build()
			.prompt()
			.user("joke")
			.advisors(a -> a.advisors(VectorStoreChatMemoryAdvisor.builder(store).build())
				.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.chatResponse();

		verifyRequestHasBeenAdvisedWithMessagesFromVectorStore(chatModel);
	}

	@Test
	void advisedChatShouldHaveSimilarMessagesFromVectorStoreWhenSystemMessageProvided() throws Exception {
		// faked ChatModel
		ChatModel chatModel = chatModelAlwaysReturnsTheSameReply();
		// faked embedding model
		EmbeddingModel embeddingModel = embeddingNModelShouldAlwaysReturnFakedEmbed();
		PgVectorStore store = createPgVectorStoreUsingTestcontainer(embeddingModel);
		String conversationId = UUID.randomUUID().toString();
		initStore(store, conversationId);

		// do the chat
		ChatClient.builder(chatModel)
			.build()
			.prompt()
			.system("You are a helpful assistant.")
			.user("joke")
			.advisors(a -> a.advisors(VectorStoreChatMemoryAdvisor.builder(store).build())
				.param(ChatMemory.CONVERSATION_ID, conversationId))
			.call()
			.chatResponse();

		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		verify(chatModel).call(promptCaptor.capture());
		assertThat(promptCaptor.getValue().getInstructions().get(0)).isInstanceOf(SystemMessage.class);
		assertThat(promptCaptor.getValue().getInstructions().get(0).getText()).isEqualToIgnoringWhitespace("""
				You are a helpful assistant.

				Use the long term conversation memory from the LONG_TERM_MEMORY section to provide accurate answers.

				---------------------
				LONG_TERM_MEMORY:
				Tell me a good joke
				Tell me a bad joke
				---------------------
				""");
	}

	/**
	 * Test that streaming chats with {@link VectorStoreChatMemoryAdvisor} get advised
	 * with similar messages from the vector store and properly handle streaming
	 * responses.
	 *
	 * This test verifies that the fix for the bug reported in
	 * https://github.com/spring-projects/spring-ai/issues/3152 works correctly. The
	 * VectorStoreChatMemoryAdvisor now properly handles streaming responses and saves the
	 * assistant's messages to the vector store.
	 */
	@Test
	void advisedStreamingChatShouldHaveSimilarMessagesFromVectorStore() throws Exception {
		// Create a ChatModel with streaming support
		ChatModel chatModel = chatModelWithStreamingSupport();

		// Create the embedding model
		EmbeddingModel embeddingModel = embeddingNModelShouldAlwaysReturnFakedEmbed();

		// Create and initialize the vector store
		PgVectorStore store = createPgVectorStoreUsingTestcontainer(embeddingModel);
		String conversationId = UUID.randomUUID().toString();
		initStore(store, conversationId);

		// Create a chat client with the VectorStoreChatMemoryAdvisor
		ChatClient chatClient = ChatClient.builder(chatModel).build();

		// Execute a streaming chat request
		Flux<String> responseStream = chatClient.prompt()
			.user("joke")
			.advisors(a -> a.advisors(VectorStoreChatMemoryAdvisor.builder(store).build())
				.param(ChatMemory.CONVERSATION_ID, conversationId))
			.stream()
			.content();

		// Collect all streaming chunks
		List<String> streamingChunks = responseStream.collectList().block();

		// Verify the streaming response
		assertThat(streamingChunks).isNotNull();
		String completeResponse = String.join("", streamingChunks);
		assertThat(completeResponse).contains("scientists", "atoms", "everything");

		// Verify the request was properly advised with vector store content
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		verify(chatModel).stream(promptCaptor.capture());
		Prompt capturedPrompt = promptCaptor.getValue();
		assertThat(capturedPrompt.getInstructions().get(0)).isInstanceOf(SystemMessage.class);
		assertThat(capturedPrompt.getInstructions().get(0).getText()).isEqualToIgnoringWhitespace("""

				Use the long term conversation memory from the LONG_TERM_MEMORY section to provide accurate answers.

				---------------------
				LONG_TERM_MEMORY:
				Tell me a good joke
				Tell me a bad joke
				---------------------
				""");

		// Verify that the assistant's response was properly added to the vector store
		// after
		// streaming completed
		// This verifies that the fix for the adviseStream implementation works correctly
		String filter = "conversationId=='" + conversationId + "' && messageType=='ASSISTANT'";
		var searchRequest = SearchRequest.builder().query("atoms").filterExpression(filter).build();

		List<Document> assistantDocuments = store.similaritySearch(searchRequest);

		// With our fix, the assistant's response should be saved to the vector store
		assertThat(assistantDocuments).isNotEmpty();
		assertThat(assistantDocuments.get(0).getText()).contains("scientists", "atoms", "everything");
	}

	/**
	 * Test that verifies the fix for the bug reported in
	 * https://github.com/spring-projects/spring-ai/issues/3152. The
	 * VectorStoreChatMemoryAdvisor now properly handles streaming responses with empty
	 * messages by using ChatClientMessageAggregator to aggregate messages before calling
	 * the after method.
	 */
	@Test
	void vectorStoreChatMemoryAdvisorShouldHandleEmptyMessagesInStream() throws Exception {
		// Create a ChatModel with problematic streaming behavior
		ChatModel chatModel = chatModelWithProblematicStreamingBehavior();

		// Create the embedding model
		EmbeddingModel embeddingModel = embeddingNModelShouldAlwaysReturnFakedEmbed();

		// Create and initialize the vector store
		PgVectorStore store = createPgVectorStoreUsingTestcontainer(embeddingModel);
		String conversationId = UUID.randomUUID().toString();
		initStore(store, conversationId);

		// Create a chat client with the VectorStoreChatMemoryAdvisor
		ChatClient chatClient = ChatClient.builder(chatModel).build();

		// Execute a streaming chat request
		// This should now succeed with our fix
		Flux<String> responseStream = chatClient.prompt()
			.user("joke")
			.advisors(a -> a.advisors(VectorStoreChatMemoryAdvisor.builder(store).build())
				.param(ChatMemory.CONVERSATION_ID, conversationId))
			.stream()
			.content();

		// Collect all streaming chunks - this should no longer throw an exception
		List<String> streamingChunks = responseStream.collectList().block();

		// Verify the streaming response
		assertThat(streamingChunks).isNotNull();
		String completeResponse = String.join("", streamingChunks);
		assertThat(completeResponse).contains("scientists", "atoms", "everything");

		// Verify that the assistant's response was properly added to the vector store
		// This verifies that our fix works correctly
		String filter = "conversationId=='" + conversationId + "' && messageType=='ASSISTANT'";
		var searchRequest = SearchRequest.builder().query("atoms").filterExpression(filter).build();

		List<Document> assistantDocuments = store.similaritySearch(searchRequest);
		assertThat(assistantDocuments).isNotEmpty();
		assertThat(assistantDocuments.get(0).getText()).contains("scientists", "atoms", "everything");
	}

	/**
	 * Helper method to get the root cause of an exception
	 */
	private Throwable getRootCause(Throwable throwable) {
		Throwable cause = throwable;
		while (cause.getCause() != null && cause.getCause() != cause) {
			cause = cause.getCause();
		}
		return cause;
	}

	@SuppressWarnings("unchecked")
	private @NonNull EmbeddingModel embeddingNModelShouldAlwaysReturnFakedEmbed() {
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

		Mockito.doAnswer(invocationOnMock -> List.of(this.embed, this.embed))
			.when(embeddingModel)
			.embed(ArgumentMatchers.any(), any(), any());
		given(embeddingModel.embed(any(String.class))).willReturn(this.embed);
		return embeddingModel;
	}

}
