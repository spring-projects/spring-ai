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

package org.springframework.ai.chat.client.advisor;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.source.DocumentRetriever;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link RetrievalAugmentationAdvisor}.
 *
 * @author Thomas Vitale
 */
class RetrievalAugmentationAdvisorTests {

	@Test
	void whenDocumentRetrieverIsNullThenThrow() {
		assertThatThrownBy(() -> RetrievalAugmentationAdvisor.builder().documentRetriever(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("documentRetriever cannot be null");
	}

	@Test
	void theOneWithTheDocumentRetriever() {
		// Chat Model
		var chatModel = mock(ChatModel.class);
		var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture())).willReturn(ChatResponse.builder()
			.withGenerations(List.of(new Generation(new AssistantMessage("Felix Felicis"))))
			.build());

		// Document Retriever
		var documentContext = List.of(Document.builder().withId("1").withContent("doc1").build(),
				Document.builder().withId("2").withContent("doc2").build());
		var documentRetriever = mock(DocumentRetriever.class);
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
		assertThat(chatResponse.getResult().getOutput().getContent()).isEqualTo("Felix Felicis");
		assertThat(chatResponse.getMetadata().<List<Document>>get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT))
			.containsAll(documentContext);

		var query = queryCaptor.getValue();
		assertThat(query.text())
			.isEqualTo("What would I get if I added a pinch of Moonstone to a dash of powdered Gold?");

		var prompt = promptCaptor.getValue();
		assertThat(prompt.getContents()).contains("""
				What would I get if I added a pinch of Moonstone to a dash of powdered Gold?

				Context information is below. Use this information to answer the user query.

				---------------------
				doc1
				doc2
				---------------------

				Given the context and provided history information and not prior knowledge,
				reply to the user query. If the answer is not in the context, inform
				the user that you can't answer the query.
				""");
	}

}
