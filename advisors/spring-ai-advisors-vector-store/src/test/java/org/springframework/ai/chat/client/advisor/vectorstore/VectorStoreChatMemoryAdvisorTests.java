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
import reactor.core.scheduler.Scheduler;

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

	@Test
	void whenBuilderWithEmptyStringConversationIdThenThrow() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);

		assertThatThrownBy(() -> VectorStoreChatMemoryAdvisor.builder(vectorStore).conversationId("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("defaultConversationId cannot be null or empty");
	}

	@Test
	void whenBuilderWithWhitespaceOnlyConversationIdThenThrow() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);

		assertThatThrownBy(() -> VectorStoreChatMemoryAdvisor.builder(vectorStore).conversationId("\t\n\r ").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("defaultConversationId cannot be null or empty");
	}

	@Test
	void whenBuilderWithSpecialCharactersInConversationIdThenSuccess() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);

		VectorStoreChatMemoryAdvisor advisor = VectorStoreChatMemoryAdvisor.builder(vectorStore)
			.conversationId("conversation-id_123@domain.com")
			.build();

		assertThat(advisor).isNotNull();
	}

	@Test
	void whenBuilderWithMaxIntegerTopKThenSuccess() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);

		VectorStoreChatMemoryAdvisor advisor = VectorStoreChatMemoryAdvisor.builder(vectorStore)
			.defaultTopK(Integer.MAX_VALUE)
			.build();

		assertThat(advisor).isNotNull();
	}

	@Test
	void whenBuilderWithNegativeTopKThenThrow() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);

		assertThatThrownBy(() -> VectorStoreChatMemoryAdvisor.builder(vectorStore).defaultTopK(-100).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("topK must be greater than 0");
	}

	@Test
	void whenBuilderChainedWithAllParametersThenSuccess() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);
		Scheduler scheduler = Mockito.mock(Scheduler.class);
		PromptTemplate systemPromptTemplate = Mockito.mock(PromptTemplate.class);

		VectorStoreChatMemoryAdvisor advisor = VectorStoreChatMemoryAdvisor.builder(vectorStore)
			.conversationId("chained-test")
			.defaultTopK(42)
			.scheduler(scheduler)
			.systemPromptTemplate(systemPromptTemplate)
			.build();

		assertThat(advisor).isNotNull();
	}

	@Test
	void whenBuilderParametersSetInDifferentOrderThenSuccess() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);
		Scheduler scheduler = Mockito.mock(Scheduler.class);
		PromptTemplate systemPromptTemplate = Mockito.mock(PromptTemplate.class);

		VectorStoreChatMemoryAdvisor advisor = VectorStoreChatMemoryAdvisor.builder(vectorStore)
			.systemPromptTemplate(systemPromptTemplate)
			.defaultTopK(7)
			.scheduler(scheduler)
			.conversationId("order-test")
			.build();

		assertThat(advisor).isNotNull();
	}

	@Test
	void whenBuilderWithOverriddenParametersThenUseLastValue() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);

		VectorStoreChatMemoryAdvisor advisor = VectorStoreChatMemoryAdvisor.builder(vectorStore)
			.conversationId("first-id")
			.conversationId("second-id") // This should override the first
			.defaultTopK(5)
			.defaultTopK(10) // This should override the first
			.build();

		assertThat(advisor).isNotNull();
	}

	@Test
	void whenBuilderReusedThenCreatesSeparateInstances() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);

		// Simulate builder reuse (if the builder itself is stateful)
		var builder = VectorStoreChatMemoryAdvisor.builder(vectorStore).conversationId("shared-config");

		VectorStoreChatMemoryAdvisor advisor1 = builder.build();
		VectorStoreChatMemoryAdvisor advisor2 = builder.build();

		assertThat(advisor1).isNotNull();
		assertThat(advisor2).isNotNull();
		assertThat(advisor1).isNotSameAs(advisor2);
	}

	@Test
	void whenBuilderWithLongConversationIdThenSuccess() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);
		String longId = "a".repeat(1000); // 1000 character conversation ID

		VectorStoreChatMemoryAdvisor advisor = VectorStoreChatMemoryAdvisor.builder(vectorStore)
			.conversationId(longId)
			.build();

		assertThat(advisor).isNotNull();
	}

	@Test
	void whenBuilderCalledWithNullAfterValidValueThenThrow() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);

		assertThatThrownBy(() -> VectorStoreChatMemoryAdvisor.builder(vectorStore)
			.conversationId("valid-id")
			.conversationId(null) // Set to null after valid value
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("defaultConversationId cannot be null or empty");
	}

	@Test
	void whenBuilderWithTopKBoundaryValuesThenSuccess() {
		VectorStore vectorStore = Mockito.mock(VectorStore.class);

		// Test with value 1 (minimum valid)
		VectorStoreChatMemoryAdvisor advisor1 = VectorStoreChatMemoryAdvisor.builder(vectorStore)
			.defaultTopK(1)
			.build();

		// Test with a reasonable upper bound
		VectorStoreChatMemoryAdvisor advisor2 = VectorStoreChatMemoryAdvisor.builder(vectorStore)
			.defaultTopK(10000)
			.build();

		assertThat(advisor1).isNotNull();
		assertThat(advisor2).isNotNull();
	}

}
