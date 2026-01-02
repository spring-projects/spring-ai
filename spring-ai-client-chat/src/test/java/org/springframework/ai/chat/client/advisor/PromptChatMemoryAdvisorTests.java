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

import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.PromptTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PromptChatMemoryAdvisor}.
 *
 * @author Mark Pollack
 * @author Thomas Vitale
 * @author Soby Chacko
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

	@Test
	void testAfterMethodHandlesSingleGeneration() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory)
			.conversationId("test-conversation")
			.build();

		ChatClientResponse mockResponse = mock(ChatClientResponse.class);
		ChatResponse mockChatResponse = mock(ChatResponse.class);
		Generation mockGeneration = mock(Generation.class);
		AdvisorChain mockChain = mock(AdvisorChain.class);

		when(mockResponse.chatResponse()).thenReturn(mockChatResponse);
		when(mockChatResponse.getResults()).thenReturn(List.of(mockGeneration)); // Single
																					// result
		when(mockGeneration.getOutput()).thenReturn(new AssistantMessage("Single response"));

		ChatClientResponse result = advisor.after(mockResponse, mockChain);

		assertThat(result).isEqualTo(mockResponse); // Should return the same response

		// Verify single message stored in memory
		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0).getText()).isEqualTo("Single response");
	}

	@Test
	void testAfterMethodHandlesMultipleGenerations() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory)
			.conversationId("test-conversation")
			.build();

		ChatClientResponse mockResponse = mock(ChatClientResponse.class);
		ChatResponse mockChatResponse = mock(ChatResponse.class);
		Generation mockGen1 = mock(Generation.class);
		Generation mockGen2 = mock(Generation.class);
		Generation mockGen3 = mock(Generation.class);
		AdvisorChain mockChain = mock(AdvisorChain.class);

		when(mockResponse.chatResponse()).thenReturn(mockChatResponse);
		when(mockChatResponse.getResults()).thenReturn(List.of(mockGen1, mockGen2, mockGen3)); // Multiple
																								// results
		when(mockGen1.getOutput()).thenReturn(new AssistantMessage("Response 1"));
		when(mockGen2.getOutput()).thenReturn(new AssistantMessage("Response 2"));
		when(mockGen3.getOutput()).thenReturn(new AssistantMessage("Response 3"));

		ChatClientResponse result = advisor.after(mockResponse, mockChain);

		assertThat(result).isEqualTo(mockResponse); // Should return the same response

		// Verify all messages were stored in memory
		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).hasSize(3);
		assertThat(messages.get(0).getText()).isEqualTo("Response 1");
		assertThat(messages.get(1).getText()).isEqualTo("Response 2");
		assertThat(messages.get(2).getText()).isEqualTo("Response 3");
	}

	@Test
	void testAfterMethodHandlesEmptyResults() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory)
			.conversationId("test-conversation")
			.build();

		ChatClientResponse mockResponse = mock(ChatClientResponse.class);
		ChatResponse mockChatResponse = mock(ChatResponse.class);
		AdvisorChain mockChain = mock(AdvisorChain.class);

		when(mockResponse.chatResponse()).thenReturn(mockChatResponse);
		when(mockChatResponse.getResults()).thenReturn(List.of());

		ChatClientResponse result = advisor.after(mockResponse, mockChain);

		assertThat(result).isEqualTo(mockResponse);

		// Verify no messages were stored in memory
		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).isEmpty();
	}

	@Test
	void testAfterMethodHandlesNullChatResponse() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory)
			.conversationId("test-conversation")
			.build();

		ChatClientResponse mockResponse = mock(ChatClientResponse.class);
		AdvisorChain mockChain = mock(AdvisorChain.class);

		when(mockResponse.chatResponse()).thenReturn(null);

		ChatClientResponse result = advisor.after(mockResponse, mockChain);

		assertThat(result).isEqualTo(mockResponse);

		// Verify no messages were stored in memory
		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).isEmpty();
	}

	@Test
	void beforeMethodHandlesToolResponseMessage() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory)
			.conversationId("test-conversation")
			.build();

		// Create a prompt with a ToolResponseMessage as the last message
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("weatherTool", "getWeather", "Sunny, 72°F")))
			.build();

		org.springframework.ai.chat.prompt.Prompt prompt = org.springframework.ai.chat.prompt.Prompt.builder()
			.messages(new org.springframework.ai.chat.messages.UserMessage("What's the weather?"),
					new org.springframework.ai.chat.messages.AssistantMessage("Let me check..."), toolResponse)
			.build();

		org.springframework.ai.chat.client.ChatClientRequest request = org.springframework.ai.chat.client.ChatClientRequest
			.builder()
			.prompt(prompt)
			.build();
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

		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory)
			.conversationId("test-conversation")
			.build();

		org.springframework.ai.chat.prompt.Prompt prompt = org.springframework.ai.chat.prompt.Prompt.builder()
			.messages(new org.springframework.ai.chat.messages.UserMessage("Hello"))
			.build();

		org.springframework.ai.chat.client.ChatClientRequest request = org.springframework.ai.chat.client.ChatClientRequest
			.builder()
			.prompt(prompt)
			.build();
		AdvisorChain chain = mock(AdvisorChain.class);

		advisor.before(request, chain);

		// Verify that the UserMessage was added to memory
		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0)).isInstanceOf(org.springframework.ai.chat.messages.UserMessage.class);
		assertThat(messages.get(0).getText()).isEqualTo("Hello");
	}

	@Test
	void beforeMethodHandlesToolResponseAfterUserMessage() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory)
			.conversationId("test-conversation")
			.build();

		AdvisorChain chain = mock(AdvisorChain.class);

		// First request with user message
		org.springframework.ai.chat.prompt.Prompt prompt1 = org.springframework.ai.chat.prompt.Prompt.builder()
			.messages(new org.springframework.ai.chat.messages.UserMessage("What's the weather?"))
			.build();
		org.springframework.ai.chat.client.ChatClientRequest request1 = org.springframework.ai.chat.client.ChatClientRequest
			.builder()
			.prompt(prompt1)
			.build();

		advisor.before(request1, chain);

		// Second request with tool response as the last message
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("weatherTool", "getWeather", "Sunny, 72°F")))
			.build();
		org.springframework.ai.chat.prompt.Prompt prompt2 = org.springframework.ai.chat.prompt.Prompt.builder()
			.messages(new org.springframework.ai.chat.messages.UserMessage("What's the weather?"),
					new org.springframework.ai.chat.messages.AssistantMessage("Let me check..."), toolResponse)
			.build();
		org.springframework.ai.chat.client.ChatClientRequest request2 = org.springframework.ai.chat.client.ChatClientRequest
			.builder()
			.prompt(prompt2)
			.build();

		advisor.before(request2, chain);

		// Verify that both messages were added to memory
		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).hasSize(2);
		assertThat(messages.get(0)).isInstanceOf(org.springframework.ai.chat.messages.UserMessage.class);
		assertThat(messages.get(1)).isInstanceOf(ToolResponseMessage.class);
	}

}
