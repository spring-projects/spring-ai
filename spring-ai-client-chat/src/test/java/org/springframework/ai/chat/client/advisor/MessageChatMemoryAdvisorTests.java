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

import java.util.List;

import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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
	void beforeMethodHandlesToolResponseMessage() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory)
			.conversationId("test-conversation")
			.build();

		// Create a prompt with a ToolResponseMessage as the last message
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("weatherTool", "getWeather", "Sunny, 72°F")))
			.build();

		Prompt prompt = Prompt.builder()
			.messages(new UserMessage("What's the weather?"), new AssistantMessage("Let me check..."), toolResponse)
			.build();

		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();
		AdvisorChain chain = mock(AdvisorChain.class);

		advisor.before(request, chain);

		// Verify that the ToolResponseMessage was added to memory
		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0)).isInstanceOf(ToolResponseMessage.class);
	}

	@Test
	void beforeMethodHandlesUserMessageWhenNoToolResponse() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory)
			.conversationId("test-conversation")
			.build();

		Prompt prompt = Prompt.builder().messages(new UserMessage("Hello")).build();

		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();
		AdvisorChain chain = mock(AdvisorChain.class);

		advisor.before(request, chain);

		// Verify that the UserMessage was added to memory
		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
		assertThat(messages.get(0).getText()).isEqualTo("Hello");
	}

	@Test
	void beforeMethodHandlesToolResponseAfterUserMessage() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory)
			.conversationId("test-conversation")
			.build();

		AdvisorChain chain = mock(AdvisorChain.class);

		// First request with user message
		Prompt prompt1 = Prompt.builder().messages(new UserMessage("What's the weather?")).build();
		ChatClientRequest request1 = ChatClientRequest.builder().prompt(prompt1).build();

		advisor.before(request1, chain);

		// Second request with tool response as the last message
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("weatherTool", "getWeather", "Sunny, 72°F")))
			.build();
		Prompt prompt2 = Prompt.builder()
			.messages(new UserMessage("What's the weather?"), new AssistantMessage("Let me check..."), toolResponse)
			.build();
		ChatClientRequest request2 = ChatClientRequest.builder().prompt(prompt2).build();

		advisor.before(request2, chain);

		// Verify that both messages were added to memory
		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).hasSize(2);
		assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
		assertThat(messages.get(1)).isInstanceOf(ToolResponseMessage.class);
	}

	@Test
	void beforeMethodMovesSystemMessageToFirstPosition() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		// Pre-populate memory with some messages (no system message in memory)
		chatMemory.add("test-conversation",
				List.of(new UserMessage("Previous question"), new AssistantMessage("Previous answer")));

		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory)
			.conversationId("test-conversation")
			.build();

		// Create a prompt with system message NOT at the first position
		// The system message is in the instructions, after user message
		Prompt prompt = Prompt.builder()
			.messages(new UserMessage("Hello"), new SystemMessage("You are a helpful assistant"))
			.build();

		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();
		AdvisorChain chain = mock(AdvisorChain.class);

		ChatClientRequest processedRequest = advisor.before(request, chain);

		// Verify that the system message is now first in the processed messages
		List<Message> processedMessages = processedRequest.prompt().getInstructions();
		assertThat(processedMessages).isNotEmpty();
		assertThat(processedMessages.get(0)).isInstanceOf(SystemMessage.class);
		assertThat(processedMessages.get(0).getText()).isEqualTo("You are a helpful assistant");
	}

	@Test
	void beforeMethodKeepsSystemMessageFirstWhenAlreadyFirst() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory)
			.conversationId("test-conversation")
			.build();

		// Create a prompt with system message already at first position
		Prompt prompt = Prompt.builder()
			.messages(new SystemMessage("You are a helpful assistant"), new UserMessage("Hello"))
			.build();

		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();
		AdvisorChain chain = mock(AdvisorChain.class);

		ChatClientRequest processedRequest = advisor.before(request, chain);

		// Verify that the system message remains first
		List<Message> processedMessages = processedRequest.prompt().getInstructions();
		assertThat(processedMessages).isNotEmpty();
		assertThat(processedMessages.get(0)).isInstanceOf(SystemMessage.class);
		assertThat(processedMessages.get(0).getText()).isEqualTo("You are a helpful assistant");
		assertThat(processedMessages.get(1)).isInstanceOf(UserMessage.class);
	}

}
