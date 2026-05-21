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

	// -------------------------------------------------------------------------
	// Builder validation
	// -------------------------------------------------------------------------

	@Test
	void whenChatMemoryIsNullThenThrow() {
		assertThatThrownBy(() -> MessageChatMemoryAdvisor.builder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatMemory cannot be null");
	}

	@Test
	void whenSchedulerIsNullThenThrow() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
		assertThatThrownBy(() -> MessageChatMemoryAdvisor.builder(chatMemory).scheduler(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("scheduler cannot be null");
	}

	@Test
	void whenBuilderWithDefaultsThenSuccess() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		assertThat(advisor.getOrder()).isEqualTo(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER);
	}

	@Test
	void whenCustomOrderIsSetThenGetOrderReturnsIt() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory)
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
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

		assertThatThrownBy(() -> advisor.getConversationId(Map.of())).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null");
	}

	@Test
	void whenConversationIdPresentInContextThenReturn() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

		String result = advisor.getConversationId(Map.of(ChatMemory.CONVERSATION_ID, "session-42"));

		assertThat(result).isEqualTo("session-42");
	}

	// -------------------------------------------------------------------------
	// before() behavior
	// -------------------------------------------------------------------------

	@Test
	void whenBeforeWithUserMessageThenStoreInMemory() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
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
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
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

	@Test
	void whenBeforeMovesSystemMessageToFirstPosition() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		chatMemory.add("test-conversation",
				List.of(new UserMessage("Previous question"), new AssistantMessage("Previous answer")));
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		Prompt prompt = Prompt.builder()
			.messages(new UserMessage("Hello"), new SystemMessage("You are a helpful assistant"))
			.build();
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.context(ChatMemory.CONVERSATION_ID, "test-conversation")
			.build();
		AdvisorChain chain = mock(AdvisorChain.class);

		ChatClientRequest processedRequest = advisor.before(request, chain);

		List<Message> processedMessages = processedRequest.prompt().getInstructions();
		assertThat(processedMessages.get(0)).isInstanceOf(SystemMessage.class);
		assertThat(processedMessages.get(0).getText()).isEqualTo("You are a helpful assistant");
	}

	@Test
	void whenBeforeSystemMessageAlreadyFirstThenKeepOrder() {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();
		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		Prompt prompt = Prompt.builder()
			.messages(new SystemMessage("You are a helpful assistant"), new UserMessage("Hello"))
			.build();
		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.context(ChatMemory.CONVERSATION_ID, "test-conversation")
			.build();
		AdvisorChain chain = mock(AdvisorChain.class);

		ChatClientRequest processedRequest = advisor.before(request, chain);

		List<Message> processedMessages = processedRequest.prompt().getInstructions();
		assertThat(processedMessages.get(0)).isInstanceOf(SystemMessage.class);
		assertThat(processedMessages.get(0).getText()).isEqualTo("You are a helpful assistant");
		assertThat(processedMessages.get(1)).isInstanceOf(UserMessage.class);
	}

}
