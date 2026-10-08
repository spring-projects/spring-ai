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

package org.springframework.ai.chat.client.advisor;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RetrievalAugmentationAdvisor}.
 *
 * @author Thomas Vitale
 * @author Sebastien Deleuze
 * @author Xuhan Zhuang
 */
class RetrievalAugmentationAdvisorTests {

	@Test
	void whenQueryTransformersContainNullElementsThenThrow() {
		assertThatThrownBy(() -> RetrievalAugmentationAdvisor.builder()
			.queryTransformers(Mockito.mock(QueryTransformer.class), null)
			.documentRetriever(Mockito.mock(DocumentRetriever.class))
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("queryTransformers cannot contain null elements");
	}

	@Test
	void whenDocumentRetrieverIsNullThenThrow() {
		assertThatThrownBy(() -> RetrievalAugmentationAdvisor.builder().documentRetriever(null).build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("documentRetriever cannot be null");
	}

	@Test
	void theOneWithTheDocumentRetriever() {
		// Chat Model
		var chatModel = mock(ChatModel.class);
		when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
		var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture())).willReturn(ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("Felix Felicis"))))
			.build());

		// Document Retriever
		var documentContext = List.of(Document.builder().id("1").text("doc1").build(),
				Document.builder().id("2").text("doc2").build());
		var documentRetriever = Mockito.mock(DocumentRetriever.class);
		var queryCaptor = ArgumentCaptor.forClass(Query.class);
		given(documentRetriever.retrieve(queryCaptor.capture())).willReturn(documentContext);

		// Advisor
		var advisor = RetrievalAugmentationAdvisor.builder().documentRetriever(documentRetriever).build();

		// Chat Client
		var chatClient = ChatClient.builder(chatModel)
			.defaultAdvisors(advisor)
			.defaultSystem("You are a wizard!")
			.build();

		// Call
		var chatResponse = chatClient.prompt()
			.user(user -> user.text("What would I get if I added {ingredient1} to {ingredient2}?")
				.param("ingredient1", "a pinch of Moonstone")
				.param("ingredient2", "a dash of powdered Gold"))
			.call()
			.chatResponse();

		// Verify
		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("Felix Felicis");
		assertThat(chatResponse.getMetadata().<List<Document>>get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT))
			.containsAll(documentContext);

		var query = queryCaptor.getValue();
		assertThat(query.text())
			.isEqualTo("What would I get if I added a pinch of Moonstone to a dash of powdered Gold?");
		assertThat(query.history()).extracting(Message::getText).containsExactly("You are a wizard!");

		var prompt = promptCaptor.getValue();
		assertThat(prompt.getContents()).containsIgnoringNewLines("""
				Context information is below.

				---------------------
				doc1
				doc2
				---------------------

				Given the context information and no prior knowledge, answer the query.

				Follow these rules:

				1. If the answer is not in the context, just say that you don't know.
				2. Avoid statements like "Based on the context..." or "The provided information...".

				Query: What would I get if I added a pinch of Moonstone to a dash of powdered Gold?

				Answer:
				""");
	}

	@Test
	void whenConversationHistoryThenQueryHistoryExcludesTheCurrentUserMessage() {
		// Chat Model
		var chatModel = mock(ChatModel.class);
		when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
		given(chatModel.call(any(Prompt.class))).willReturn(ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("Felix Felicis"))))
			.build());

		// Document Retriever
		var documentRetriever = Mockito.mock(DocumentRetriever.class);
		var queryCaptor = ArgumentCaptor.forClass(Query.class);
		given(documentRetriever.retrieve(queryCaptor.capture())).willReturn(List.of());

		// Advisor
		var advisor = RetrievalAugmentationAdvisor.builder().documentRetriever(documentRetriever).build();

		// Chat Client
		var chatClient = ChatClient.builder(chatModel).defaultAdvisors(advisor).build();

		// Call
		var previousUserMessage = new UserMessage("What is a Moonstone?");
		var previousAssistantMessage = new AssistantMessage("A silvery-white gemstone.");
		var currentUserMessage = new UserMessage("And a powdered Gold?");
		chatClient.prompt()
			.messages(previousUserMessage, previousAssistantMessage, currentUserMessage)
			.call()
			.chatResponse();

		// Verify
		var query = queryCaptor.getValue();
		assertThat(query.text()).isEqualTo("And a powdered Gold?");
		assertThat(query.history()).containsExactly(previousUserMessage, previousAssistantMessage);
	}

	@Test
	void whenUserMessageIsRepeatedThenOnlyTheCurrentOneIsExcludedFromQueryHistory() {
		// Chat Model
		var chatModel = mock(ChatModel.class);
		when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
		given(chatModel.call(any(Prompt.class))).willReturn(ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("Felix Felicis"))))
			.build());

		// Document Retriever
		var documentRetriever = Mockito.mock(DocumentRetriever.class);
		var queryCaptor = ArgumentCaptor.forClass(Query.class);
		given(documentRetriever.retrieve(queryCaptor.capture())).willReturn(List.of());

		// Advisor
		var advisor = RetrievalAugmentationAdvisor.builder().documentRetriever(documentRetriever).build();

		// Chat Client
		var chatClient = ChatClient.builder(chatModel).defaultAdvisors(advisor).build();

		// Call
		var previousUserMessage = new UserMessage("Tell me more");
		var previousAssistantMessage = new AssistantMessage("A silvery-white gemstone.");
		var currentUserMessage = new UserMessage("Tell me more");
		chatClient.prompt()
			.messages(previousUserMessage, previousAssistantMessage, currentUserMessage)
			.call()
			.chatResponse();

		// Verify
		var query = queryCaptor.getValue();
		assertThat(query.history()).containsExactly(previousUserMessage, previousAssistantMessage);
	}

	@Test
	void whenChatMemoryAndCompressionThenOnlyFollowUpTurnsAreCompressed() {
		// Chat Model, answering a compression prompt with the standalone query and any
		// other prompt with the final answer.
		var chatModel = mock(ChatModel.class);
		when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
		var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture())).willAnswer(invocation -> {
			Prompt prompt = invocation.getArgument(0);
			String answer = isCompressionPrompt(prompt) ? "Did Anacletus and Birba meet any cow?"
					: "They met Fergus the cow.";
			return ChatResponse.builder().generations(List.of(new Generation(new AssistantMessage(answer)))).build();
		});

		// Document Retriever
		var documentRetriever = Mockito.mock(DocumentRetriever.class);
		var queryCaptor = ArgumentCaptor.forClass(Query.class);
		given(documentRetriever.retrieve(queryCaptor.capture()))
			.willReturn(List.of(Document.builder().id("1").text("doc1").build()));

		// Advisors
		var memoryAdvisor = MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build();
		var ragAdvisor = RetrievalAugmentationAdvisor.builder()
			.documentRetriever(documentRetriever)
			.queryTransformers(
					CompressionQueryTransformer.builder().chatClientBuilder(ChatClient.builder(chatModel)).build())
			.build();

		// Chat Client
		var chatClient = ChatClient.builder(chatModel).defaultAdvisors(memoryAdvisor, ragAdvisor).build();

		// First turn: no conversation history yet, so nothing to compress.
		chatClient.prompt()
			.user("Where does the adventure of Anacletus and Birba take place?")
			.advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, "007"))
			.call()
			.chatResponse();

		assertThat(promptCaptor.getAllValues()).noneMatch(RetrievalAugmentationAdvisorTests::isCompressionPrompt);
		assertThat(queryCaptor.getValue().text())
			.isEqualTo("Where does the adventure of Anacletus and Birba take place?");
		assertThat(queryCaptor.getValue().history()).isEmpty();

		// Second turn: the previous turn is history, the current question is not.
		chatClient.prompt()
			.user("Did they meet any cow?")
			.advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, "007"))
			.call()
			.chatResponse();

		var compressionPrompts = promptCaptor.getAllValues()
			.stream()
			.filter(RetrievalAugmentationAdvisorTests::isCompressionPrompt)
			.toList();
		assertThat(compressionPrompts).hasSize(1);
		assertThat(compressionPrompts.get(0).getContents()).containsIgnoringNewLines("""
				Conversation history:
				USER: Where does the adventure of Anacletus and Birba take place?
				ASSISTANT: They met Fergus the cow.

				Follow-up query:
				Did they meet any cow?
				""");
		assertThat(queryCaptor.getValue().text()).isEqualTo("Did Anacletus and Birba meet any cow?");
	}

	private static boolean isCompressionPrompt(Prompt prompt) {
		return prompt.getContents().contains("Standalone query:");
	}

}
