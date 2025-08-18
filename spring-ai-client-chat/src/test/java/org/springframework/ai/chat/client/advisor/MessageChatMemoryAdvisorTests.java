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
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;

import java.util.List;

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

	@Test
	void testMessageOrder() {
		// Arrange: Set up the test conditions.
		String conversationId = "test-conversation-123";

		ChatMemory chatMemory = MessageWindowChatMemory.builder()
				.chatMemoryRepository(new InMemoryChatMemoryRepository())
				.build();

		// Set up the past conversation history in memory.
		chatMemory.add(conversationId, new UserMessage("This is user message history"));
		chatMemory.add(conversationId, new AssistantMessage("This is assistant message history"));

		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory)
				.conversationId(conversationId)
				.build();

		// Create the new user request, which includes a System message.
		ChatClientRequest testRequest = ChatClientRequest.builder()
				.prompt(new Prompt(List.of(
						new SystemMessage("This is system message"),
						new UserMessage("This is user message"))))
				.build();

		// Act
		ChatClientRequest advisedRequest = advisor.before(testRequest, null);

		// Assert
		assertThat(advisedRequest).isNotNull();
		List<Message> messages = advisedRequest.prompt().getInstructions();

		assertThat(messages).hasSize(4);

		assertThat(messages.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(messages.get(0).getText()).isEqualTo("This is system message");

		assertThat(messages.get(1).getMessageType()).isEqualTo(MessageType.USER);
		assertThat(messages.get(1).getText()).isEqualTo("This is user message history");

		assertThat(messages.get(2).getMessageType()).isEqualTo(MessageType.ASSISTANT);
		assertThat(messages.get(2).getText()).isEqualTo("This is assistant message history");

		assertThat(messages.get(3).getMessageType()).isEqualTo(MessageType.USER);
		assertThat(messages.get(3).getText()).isEqualTo("This is user message");
	}
}
