/*
 * Copyright 2025-2025 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.scheduler.Scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link VectorStoreChatMemoryAdvisor}.
 *
 * @author Thomas Vitale
 */
class VectorStoreChatMemoryAdvisorTests {

	@Test
	void whenVectorStoreIsNullThenThrow() {
		assertThatThrownBy(() -> VectorStoreChatMemoryAdvisor.builder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("vectorStore cannot be null");
	}

	@Test
	void whenDefaultConversationIdIsNullThenThrow() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);

		assertThatThrownBy(() -> VectorStoreChatMemoryAdvisor.builder(vectorStore).conversationId(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("defaultConversationId cannot be null or empty");
	}

	@Test
	void whenDefaultConversationIdIsEmptyThenThrow() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);

		assertThatThrownBy(() -> VectorStoreChatMemoryAdvisor.builder(vectorStore).conversationId(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("defaultConversationId cannot be null or empty");
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
			.conversationId("test-conversation")
			.scheduler(scheduler)
			.systemPromptTemplate(systemPromptTemplate)
			.defaultTopK(5)
			.build();

		assertThat(advisor).isNotNull();
	}

	@Test
	void whenDefaultConversationIdIsBlankThenThrow() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);

		assertThatThrownBy(() -> VectorStoreChatMemoryAdvisor.builder(vectorStore).conversationId("   ").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("defaultConversationId cannot be null or empty");
	}

	@Test
	void whenBuilderWithValidConversationIdThenSuccess() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);

		VectorStoreChatMemoryAdvisor advisor = VectorStoreChatMemoryAdvisor.builder(vectorStore)
			.conversationId("valid-id")
			.build();

		assertThat(advisor).isNotNull();
	}

	@Test
	void whenBuilderWithValidTopKThenSuccess() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);

		VectorStoreChatMemoryAdvisor advisor = VectorStoreChatMemoryAdvisor.builder(vectorStore)
			.defaultTopK(10)
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
	void whenBuilderWithLargeTopKThenSuccess() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);

		VectorStoreChatMemoryAdvisor advisor = VectorStoreChatMemoryAdvisor.builder(vectorStore)
			.defaultTopK(1000)
			.build();

		assertThat(advisor).isNotNull();
	}

	@Test
	void whenBuilderCalledMultipleTimesWithSameVectorStoreThenSuccess() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);

		VectorStoreChatMemoryAdvisor advisor1 = VectorStoreChatMemoryAdvisor.builder(vectorStore).build();
		VectorStoreChatMemoryAdvisor advisor2 = VectorStoreChatMemoryAdvisor.builder(vectorStore).build();

		assertThat(advisor1).isNotNull();
		assertThat(advisor2).isNotNull();
		assertThat(advisor1).isNotSameAs(advisor2);
	}

	@Test
	void whenBuilderWithCustomSchedulerThenSuccess() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);
		Scheduler customScheduler = Mockito.mock(Scheduler.class);

		VectorStoreChatMemoryAdvisor advisor = VectorStoreChatMemoryAdvisor.builder(vectorStore)
			.scheduler(customScheduler)
			.build();

		assertThat(advisor).isNotNull();
	}

	@Test
	void whenBuilderWithCustomSystemPromptTemplateThenSuccess() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);
		PromptTemplate customTemplate = Mockito.mock(PromptTemplate.class);

		VectorStoreChatMemoryAdvisor advisor = VectorStoreChatMemoryAdvisor.builder(vectorStore)
			.systemPromptTemplate(customTemplate)
			.build();

		assertThat(advisor).isNotNull();
	}

}
