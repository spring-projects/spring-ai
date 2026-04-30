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

package org.springframework.ai.chat.client.advisor.vectorstore;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.scheduler.Scheduler;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link VectorStoreChatMemoryAdvisor}.
 *
 * @author Thomas Vitale
 */
class VectorStoreChatMemoryAdvisorTests {

	// -------------------------------------------------------------------------
	// Builder validation
	// -------------------------------------------------------------------------

	@Test
	void whenVectorStoreIsNullThenThrow() {
		assertThatThrownBy(() -> VectorStoreChatMemoryAdvisor.builder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("vectorStore cannot be null");
	}

	@Test
	void whenSchedulerIsNullThenThrow() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);
		assertThatThrownBy(() -> VectorStoreChatMemoryAdvisor.builder(vectorStore).scheduler(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("scheduler cannot be null");
	}

	@Test
	void whenSystemPromptTemplateIsNullThenThrow() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);
		assertThatThrownBy(() -> VectorStoreChatMemoryAdvisor.builder(vectorStore).systemPromptTemplate(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("systemPromptTemplate cannot be null");
	}

	@Test
	void whenDefaultTopKIsZeroThenThrow() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);
		assertThatThrownBy(() -> VectorStoreChatMemoryAdvisor.builder(vectorStore).defaultTopK(0).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("topK must be greater than 0");
	}

	@Test
	void whenDefaultTopKIsNegativeThenThrow() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);
		assertThatThrownBy(() -> VectorStoreChatMemoryAdvisor.builder(vectorStore).defaultTopK(-1).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("topK must be greater than 0");
	}

	// -------------------------------------------------------------------------
	// Builder success
	// -------------------------------------------------------------------------

	@Test
	void whenBuilderWithValidVectorStoreThenSuccess() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);
		VectorStoreChatMemoryAdvisor advisor = VectorStoreChatMemoryAdvisor.builder(vectorStore).build();
		assertThat(advisor).isNotNull();
	}

	@Test
	void whenBuilderWithAllValidParametersThenSuccess() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);
		Scheduler scheduler = Mockito.mock(Scheduler.class);
		PromptTemplate systemPromptTemplate = Mockito.mock(PromptTemplate.class);
		VectorStoreChatMemoryAdvisor advisor = VectorStoreChatMemoryAdvisor.builder(vectorStore)
			.scheduler(scheduler)
			.systemPromptTemplate(systemPromptTemplate)
			.defaultTopK(5)
			.build();
		assertThat(advisor).isNotNull();
	}

	@Test
	void whenBuilderWithMinimumTopKThenSuccess() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);
		VectorStoreChatMemoryAdvisor advisor = VectorStoreChatMemoryAdvisor.builder(vectorStore).defaultTopK(1).build();
		assertThat(advisor).isNotNull();
	}

	@Test
	void whenBuilderReusedThenCreatesSeparateInstances() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);
		var builder = VectorStoreChatMemoryAdvisor.builder(vectorStore);
		VectorStoreChatMemoryAdvisor advisor1 = builder.build();
		VectorStoreChatMemoryAdvisor advisor2 = builder.build();
		assertThat(advisor1).isNotSameAs(advisor2);
	}

	// -------------------------------------------------------------------------
	// Conversation ID resolution from request context
	// -------------------------------------------------------------------------

	@Test
	void whenConversationIdAbsentFromContextThenThrow() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);
		VectorStoreChatMemoryAdvisor advisor = VectorStoreChatMemoryAdvisor.builder(vectorStore).build();

		assertThatThrownBy(() -> advisor.getConversationId(Map.of())).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null");
	}

	@Test
	void whenConversationIdPresentInContextThenReturn() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);
		VectorStoreChatMemoryAdvisor advisor = VectorStoreChatMemoryAdvisor.builder(vectorStore).build();

		String result = advisor.getConversationId(Map.of(ChatMemory.CONVERSATION_ID, "session-42"));

		assertThat(result).isEqualTo("session-42");
	}

}
