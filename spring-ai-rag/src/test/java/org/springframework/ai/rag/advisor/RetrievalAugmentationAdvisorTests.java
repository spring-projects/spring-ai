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

package org.springframework.ai.rag.advisor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RetrievalAugmentationAdvisor}.
 */
class RetrievalAugmentationAdvisorTests {

	@Test
	void whenDocumentContextAlreadyExistsThenSkipRagProcessing() {
		DocumentRetriever documentRetriever = mock(DocumentRetriever.class);

		RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
			.documentRetriever(documentRetriever)
			.build();

		// Simulate a request that already has DOCUMENT_CONTEXT in context
		// (e.g. from a previous iteration in a tool call loop)
		List<Document> existingDocuments = List.of(new Document("existing context"));
		Map<String, Object> context = new HashMap<>();
		context.put(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, existingDocuments);

		Prompt prompt = new Prompt(new UserMessage("test query"));
		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).context(context).build();

		ChatClientRequest result = advisor.before(request, null);

		// Should return the original request unchanged
		assertThat(result).isSameAs(request);

		// Document retriever should NOT have been called
		verify(documentRetriever, never()).retrieve(any());
	}

	@Test
	void whenDocumentContextDoesNotExistThenExecuteRagProcessing() {
		DocumentRetriever documentRetriever = mock(DocumentRetriever.class);
		List<Document> retrievedDocuments = List.of(new Document("retrieved doc"));
		when(documentRetriever.retrieve(any())).thenReturn(retrievedDocuments);

		RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
			.documentRetriever(documentRetriever)
			.build();

		Prompt prompt = new Prompt(new UserMessage("test query"));
		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		ChatClientRequest result = advisor.before(request, null);

		// Document retriever should have been called
		verify(documentRetriever).retrieve(any());

		// The result should be different from the original request (augmented)
		assertThat(result).isNotSameAs(request);
	}

}
