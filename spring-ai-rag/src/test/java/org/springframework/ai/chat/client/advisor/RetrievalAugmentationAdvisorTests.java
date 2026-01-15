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
import org.mockito.Mockito;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

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

		var prompt = promptCaptor.getValue();
		assertThat(prompt.getContents()).contains("""
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

}
