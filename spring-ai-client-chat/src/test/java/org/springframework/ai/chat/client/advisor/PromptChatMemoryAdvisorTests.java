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

package org.springframework.ai.chat.client.advisor;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

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

	// -------------------------------------------------------------------------
	// Builder validation
	// -------------------------------------------------------------------------

	@Test
	void whenChatMemoryIsNullThenThrow() {
		assertThatThrownBy(() -> PromptChatMemoryAdvisor.builder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatMemory cannot be null");
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
	void whenBuilderWithDefaultsThenSuccess() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory).build();
		assertThat(advisor.getOrder()).isEqualTo(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER);
	}

	@Test
	void whenCustomOrderIsSetThenGetOrderReturnsIt() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory)
			.order(42)
			.scheduler(Schedulers.immediate())
			.build();
		assertThat(advisor.getOrder()).isEqualTo(42);
	}

	// -------------------------------------------------------------------------
	// Conversation ID resolution from request context
	// -------------------------------------------------------------------------

	@Test
	void whenConversationIdAbsentFromContextThenThrow() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory).build();

		assertThatThrownBy(() -> advisor.getConversationId(Map.of())).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null");
	}

	@Test
	void whenConversationIdPresentInContextThenReturn() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory).build();

		String result = advisor.getConversationId(Map.of(ChatMemory.CONVERSATION_ID, "session-42"));

		assertThat(result).isEqualTo("session-42");
	}

	// -------------------------------------------------------------------------
	// after() behavior
	// -------------------------------------------------------------------------

	@Test
	void whenAfterWithNullChatResponseThenReturnWithoutStoringMemory() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory).build();
		ChatClientResponse mockResponse = mock(ChatClientResponse.class);
		AdvisorChain mockChain = mock(AdvisorChain.class);
		when(mockResponse.chatResponse()).thenReturn(null);

		ChatClientResponse result = advisor.after(mockResponse, mockChain);

		assertThat(result).isEqualTo(mockResponse);
		assertThat(chatMemory.get("any-conversation")).isEmpty();
	}

	@Test
	void whenAfterWithEmptyResultsThenReturnWithoutStoringMemory() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory).build();
		ChatClientResponse mockResponse = mock(ChatClientResponse.class);
		ChatResponse mockChatResponse = mock(ChatResponse.class);
		AdvisorChain mockChain = mock(AdvisorChain.class);
		when(mockResponse.chatResponse()).thenReturn(mockChatResponse);
		when(mockChatResponse.getResults()).thenReturn(List.of());

		ChatClientResponse result = advisor.after(mockResponse, mockChain);

		assertThat(result).isEqualTo(mockResponse);
		assertThat(chatMemory.get("any-conversation")).isEmpty();
	}

	@Test
	void whenAfterWithSingleGenerationThenStoreInMemory() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory).build();
		ChatClientResponse mockResponse = mock(ChatClientResponse.class);
		ChatResponse mockChatResponse = mock(ChatResponse.class);
		Generation mockGeneration = mock(Generation.class);
		AdvisorChain mockChain = mock(AdvisorChain.class);
		when(mockResponse.chatResponse()).thenReturn(mockChatResponse);
		when(mockResponse.context()).thenReturn(Map.of(ChatMemory.CONVERSATION_ID, "test-conversation"));
		when(mockChatResponse.getResults()).thenReturn(List.of(mockGeneration));
		when(mockGeneration.getOutput()).thenReturn(new AssistantMessage("Hello"));

		ChatClientResponse result = advisor.after(mockResponse, mockChain);

		assertThat(result).isEqualTo(mockResponse);
		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0).getText()).isEqualTo("Hello");
	}

	@Test
	void whenAfterWithMultipleGenerationsThenStoreAllInMemory() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory).build();
		ChatClientResponse mockResponse = mock(ChatClientResponse.class);
		ChatResponse mockChatResponse = mock(ChatResponse.class);
		Generation mockGen1 = mock(Generation.class);
		Generation mockGen2 = mock(Generation.class);
		AdvisorChain mockChain = mock(AdvisorChain.class);
		when(mockResponse.chatResponse()).thenReturn(mockChatResponse);
		when(mockResponse.context()).thenReturn(Map.of(ChatMemory.CONVERSATION_ID, "test-conversation"));
		when(mockChatResponse.getResults()).thenReturn(List.of(mockGen1, mockGen2));
		when(mockGen1.getOutput()).thenReturn(new AssistantMessage("Response 1"));
		when(mockGen2.getOutput()).thenReturn(new AssistantMessage("Response 2"));

		ChatClientResponse result = advisor.after(mockResponse, mockChain);

		assertThat(result).isEqualTo(mockResponse);
		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).hasSize(2);
		assertThat(messages.get(0).getText()).isEqualTo("Response 1");
		assertThat(messages.get(1).getText()).isEqualTo("Response 2");
	}

	// -------------------------------------------------------------------------
	// before() behavior
	// -------------------------------------------------------------------------

	@Test
	void whenBeforeWithUserMessageThenStoreInMemory() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory).build();
		Prompt prompt = Prompt.builder().messages(new UserMessage("Hello")).build();
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.context(ChatMemory.CONVERSATION_ID, "test-conversation")
			.build();
		AdvisorChain chain = mock(AdvisorChain.class);

		advisor.before(request, chain);

		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
		assertThat(messages.get(0).getText()).isEqualTo("Hello");
	}

	@Test
	void whenBeforeWithToolResponseMessageThenStoreInMemory() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		PromptChatMemoryAdvisor advisor = PromptChatMemoryAdvisor.builder(chatMemory).build();
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("weatherTool", "getWeather", "Sunny, 72°F")))
			.build();
		Prompt prompt = Prompt.builder()
			.messages(new UserMessage("What's the weather?"), new AssistantMessage("Let me check..."), toolResponse)
			.build();
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.context(ChatMemory.CONVERSATION_ID, "test-conversation")
			.build();
		AdvisorChain chain = mock(AdvisorChain.class);

		advisor.before(request, chain);

		List<Message> messages = chatMemory.get("test-conversation");
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0)).isInstanceOf(ToolResponseMessage.class);
	}

}
