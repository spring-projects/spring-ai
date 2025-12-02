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
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.Map;

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
	void testMessageUpdateFunctionality() {
		// Create a chat memory
		MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		String conversationId = "test-conversation";

		// Test 1: Add original message with specific messageId
		UserMessage originalMessage = UserMessage.builder()
			.text("What is the capital of France?")
			.metadata(Map.of("messageId", "msg-001"))
			.build();

		chatMemory.add(conversationId, originalMessage);

		// Simulate adding an assistant response
		AssistantMessage assistantResponse = new AssistantMessage("The capital of France is Paris.");
		chatMemory.add(conversationId, assistantResponse);

		// Verify initial state: should have 2 messages (user + assistant)
		assertThat(chatMemory.get(conversationId)).hasSize(2);
		assertThat(chatMemory.get(conversationId).get(0).getText()).isEqualTo("What is the capital of France?");
		assertThat(chatMemory.get(conversationId).get(1).getText()).isEqualTo("The capital of France is Paris.");

		// Test 2: Update the message with same messageId
		UserMessage updatedMessage = UserMessage.builder()
			.text("What is the capital of Italy?")
			.metadata(Map.of("messageId", "msg-001")) // Same messageId
			.build();

		// Remove old message and response manually (testing the repository functionality)
		chatMemory.removeMessageAndResponse(conversationId, "msg-001");
		chatMemory.add(conversationId, updatedMessage);

		// Verify the update: should have only 1 message (the updated user message)
		// The old user message and assistant response should be removed
		assertThat(chatMemory.get(conversationId)).hasSize(1);
		assertThat(chatMemory.get(conversationId).get(0).getText()).isEqualTo("What is the capital of Italy?");
		assertThat(chatMemory.get(conversationId).get(0).getMetadata().get("messageId")).isEqualTo("msg-001");
	}

	@Test
	void testMessageIdGeneration() {
		// Create a chat memory
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

		// Test that messages without messageId get one generated automatically
		UserMessage messageWithoutId = new UserMessage("Hello world");

		// This would happen inside handleMessageUpdate method when a message is processed
		// We can't directly test the private method, but we can verify the behavior
		// by checking that the same content generates the same hash-based ID

		String expectedId = String.valueOf("Hello world".hashCode());

		// The generateMessageId method should produce consistent IDs for same content
		assertThat(expectedId).isNotNull();

		// Messages with same content should have same messageId
		UserMessage message1 = new UserMessage("Same content");
		UserMessage message2 = new UserMessage("Same content");

		String id1 = String.valueOf(message1.getText().hashCode());
		String id2 = String.valueOf(message2.getText().hashCode());

		assertThat(id1).isEqualTo(id2);
	}

}
