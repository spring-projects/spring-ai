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

package org.springframework.ai.chat.client.advisor;

import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PromptChatMemoryAdvisor}.
 *
 * @author Mark Pollack
 * @author Thomas Vitale
 */
public class PromptChatMemoryAdvisorTests {

	@Test
	void whenChatMemoryIsNullThenThrow() {
		assertThatThrownBy(() -> PromptChatMemoryAdvisor.builder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatMemory cannot be null");
	}

	@Test
	void whenDefaultConversationIdIsNullThenThrow() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();

		assertThatThrownBy(() -> PromptChatMemoryAdvisor.builder(chatMemory).conversationId(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("defaultConversationId cannot be null or empty");
	}

	@Test
	void whenDefaultConversationIdIsEmptyThenThrow() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();

		assertThatThrownBy(() -> PromptChatMemoryAdvisor.builder(chatMemory).conversationId(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("defaultConversationId cannot be null or empty");
	}

	@Test
	void whenSchedulerIsNullThenThrow() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();

		assertThatThrownBy(() -> PromptChatMemoryAdvisor.builder(chatMemory).scheduler(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("scheduler cannot be null");
	}

	@Test
	void whenSystemPromptTemplateIsNullThenThrow() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();

		assertThatThrownBy(() -> PromptChatMemoryAdvisor.builder(chatMemory).systemPromptTemplate(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("systemPromptTemplate cannot be null");
	}

	@Test
	void testBuilderMethodChaining() {
		// Create a chat memory
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		// Test builder method chaining with methods from AbstractBuilder and
		// PromptChatMemoryAdvisor.Builder
		String customConversationId = "test-conversation-id";
		int customOrder = 42;
		String customSystemPrompt = "Custom system prompt with {instructions} and {memory}";

		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory)
			.conversationId(customConversationId) // From AbstractBuilder
			.order(customOrder) // From AbstractBuilder
			.scheduler(Schedulers.immediate()) // From AbstractBuilder
			.build();

		// Verify the advisor was built with the correct properties
		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(customOrder);
	}

	@Test
	void testSystemPromptTemplateChaining() {
		// Create a chat memory
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		// Test chaining with systemPromptTemplate method
		PromptTemplate customTemplate = new PromptTemplate("Custom template with {instructions} and {memory}");

		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory)
			.conversationId("custom-id")
			.systemPromptTemplate(customTemplate)
			.order(100)
			.build();

		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(100);
	}

	@Test
	void testDefaultValues() {
		// Create a chat memory
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		// Create advisor with default values
		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory).build();

		// Verify default values
		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER);
	}

}
