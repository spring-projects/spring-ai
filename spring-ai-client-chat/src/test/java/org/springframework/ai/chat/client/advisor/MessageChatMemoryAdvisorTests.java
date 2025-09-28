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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MessageChatMemoryAdvisor}.
 *
 * @author Mark Pollack
 * @author Thomas Vitale
 */
public class MessageChatMemoryAdvisorTests {

	@Test
	void whenChatMemoryIsNullThenThrow() {
		assertThatThrownBy(() -> MessageChatMemoryAdvisor.builder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatMemory cannot be null");
	}

	@Test
	void whenDefaultConversationIdIsNullThenThrow() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();

		assertThatThrownBy(() -> MessageChatMemoryAdvisor.builder(chatMemory).conversationId(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("defaultConversationId cannot be null or empty");
	}

	@Test
	void whenDefaultConversationIdIsEmptyThenThrow() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();

		assertThatThrownBy(() -> MessageChatMemoryAdvisor.builder(chatMemory).conversationId(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("defaultConversationId cannot be null or empty");
	}

	@Test
	void whenSchedulerIsNullThenThrow() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();

		assertThatThrownBy(() -> MessageChatMemoryAdvisor.builder(chatMemory).scheduler(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("scheduler cannot be null");
	}

	@Test
	void testBuilderMethodChaining() {
		// Create a chat memory
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		// Test builder method chaining with methods from AbstractBuilder
		String customConversationId = "test-conversation-id";
		int customOrder = 42;

		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory)
			.conversationId(customConversationId)
			.order(customOrder)
			.scheduler(Schedulers.immediate())
			.build();

		// Verify the advisor was built with the correct properties
		assertThat(advisor).isNotNull();
		// We can't directly access private fields, but we can test the behavior
		// by checking the order which is exposed via a getter
		assertThat(advisor.getOrder()).isEqualTo(customOrder);
	}

	@Test
	void testDefaultValues() {
		// Create a chat memory
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		// Create advisor with default values
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

		// Verify default values
		assertThat(advisor).isNotNull();
		assertThat(advisor.getOrder()).isEqualTo(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER);
	}

}
