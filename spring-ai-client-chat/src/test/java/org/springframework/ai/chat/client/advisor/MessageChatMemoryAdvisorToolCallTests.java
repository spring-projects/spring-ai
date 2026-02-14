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

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link MessageChatMemoryAdvisor} tool call persistence.
 * <p>
 * These tests verify that tool call messages (AssistantMessage with toolCalls and
 * ToolResponseMessage) are correctly persisted in chat memory when used together with
 * {@link ToolCallAdvisor}.
 *
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/2101">Issue 2101</a>
 */
class MessageChatMemoryAdvisorToolCallTests {

	private final AdvisorChain chain = mock(AdvisorChain.class);

	@Test
	void toolCallMessagesShouldBePersistedInMemory() {
		// Simulates the full flow of ToolCallAdvisor + MessageChatMemoryAdvisor
		// where tool call messages should be persisted across iterations.

		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).conversationId("test").build();

		// --- Iteration 1: User message ---
		Prompt prompt1 = Prompt.builder()
			.messages(new SystemMessage("You are helpful"), new UserMessage("What's the weather in Paris?"))
			.build();
		ChatClientRequest request1 = ChatClientRequest.builder().prompt(prompt1).build();

		advisor.before(request1, this.chain);

		// Simulate LLM returning AssistantMessage with tool calls
		AssistantMessage assistantWithToolCalls = AssistantMessage.builder()
			.content("Let me check the weather.")
			.toolCalls(
					List.of(new AssistantMessage.ToolCall("call-1", "function", "getWeather", "{\"city\":\"Paris\"}")))
			.build();
		ChatResponse toolCallResponse = ChatResponse.builder()
			.generations(List.of(new Generation(assistantWithToolCalls)))
			.build();
		ChatClientResponse clientResponse1 = ChatClientResponse.builder().chatResponse(toolCallResponse).build();

		advisor.after(clientResponse1, this.chain);

		// Verify: memory should contain UserMessage + AssistantMessage(toolCalls)
		List<Message> memoryAfterIteration1 = chatMemory.get("test");
		assertThat(memoryAfterIteration1).hasSize(2);
		assertThat(memoryAfterIteration1.get(0)).isInstanceOf(UserMessage.class);
		assertThat(memoryAfterIteration1.get(1)).isInstanceOf(AssistantMessage.class);
		assertThat(((AssistantMessage) memoryAfterIteration1.get(1)).getToolCalls()).hasSize(1);

		// --- Iteration 2: ToolCallAdvisor provides full history with ToolResponseMessage
		// ---
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "getWeather", "Sunny, 22°C in Paris")))
			.build();

		// ToolCallAdvisor builds instructions with full conversation history
		Prompt prompt2 = Prompt.builder()
			.messages(new SystemMessage("You are helpful"), new UserMessage("What's the weather in Paris?"),
					assistantWithToolCalls, toolResponse)
			.build();
		ChatClientRequest request2 = ChatClientRequest.builder().prompt(prompt2).build();

		advisor.before(request2, this.chain);

		// Verify: memory should now also include ToolResponseMessage
		List<Message> memoryAfterIteration2Before = chatMemory.get("test");
		assertThat(memoryAfterIteration2Before).hasSize(3);
		assertThat(memoryAfterIteration2Before.get(0)).isInstanceOf(UserMessage.class);
		assertThat(memoryAfterIteration2Before.get(1)).isInstanceOf(AssistantMessage.class);
		assertThat(memoryAfterIteration2Before.get(2)).isInstanceOf(ToolResponseMessage.class);

		// Simulate LLM returning final answer
		AssistantMessage finalAnswer = new AssistantMessage("It's sunny and 22°C in Paris.");
		ChatResponse finalResponse = ChatResponse.builder().generations(List.of(new Generation(finalAnswer))).build();
		ChatClientResponse clientResponse2 = ChatClientResponse.builder().chatResponse(finalResponse).build();

		advisor.after(clientResponse2, this.chain);

		// Verify: complete conversation should be in memory
		List<Message> finalMemory = chatMemory.get("test");
		assertThat(finalMemory).hasSize(4);
		assertThat(finalMemory.get(0)).isInstanceOf(UserMessage.class);
		assertThat(finalMemory.get(1)).isInstanceOf(AssistantMessage.class);
		assertThat(((AssistantMessage) finalMemory.get(1)).getToolCalls()).hasSize(1);
		assertThat(finalMemory.get(2)).isInstanceOf(ToolResponseMessage.class);
		assertThat(finalMemory.get(3)).isInstanceOf(AssistantMessage.class);
		assertThat(finalMemory.get(3).getText()).isEqualTo("It's sunny and 22°C in Paris.");
	}

	@Test
	void beforeShouldNotDuplicateMessagesWhenInstructionsContainHistory() {
		// When ToolCallAdvisor provides full conversation history in instructions,
		// MessageChatMemoryAdvisor should not prepend memory messages to avoid
		// duplication.

		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		// Pre-populate memory (as if iteration 1 already ran)
		UserMessage userMsg = new UserMessage("What's the weather?");
		AssistantMessage assistantWithTools = AssistantMessage.builder()
			.content("Checking...")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "getWeather", "{}")))
			.build();
		chatMemory.add("test", List.of(userMsg, assistantWithTools));

		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).conversationId("test").build();

		// ToolCallAdvisor provides full history (including messages already in memory)
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "getWeather", "Sunny")))
			.build();

		Prompt prompt = Prompt.builder()
			.messages(new SystemMessage("Be helpful"), userMsg, assistantWithTools, toolResponse)
			.build();
		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		ChatClientRequest processedRequest = advisor.before(request, this.chain);

		// Verify: no duplicate UserMessage or AssistantMessage in the processed messages
		List<Message> processedMessages = processedRequest.prompt().getInstructions();

		long userMessageCount = processedMessages.stream().filter(m -> m instanceof UserMessage).count();
		long assistantMessageCount = processedMessages.stream().filter(m -> m instanceof AssistantMessage).count();

		assertThat(userMessageCount).isEqualTo(1);
		assertThat(assistantMessageCount).isEqualTo(1);
		assertThat(processedMessages.get(0)).isInstanceOf(SystemMessage.class);
	}

	@Test
	void beforeShouldStillPrependMemoryForNormalUserMessages() {
		// Without ToolCallAdvisor (normal flow), memory should still be prepended.

		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		// Pre-populate memory from previous turn
		chatMemory.add("test", List.of(new UserMessage("Hello"), new AssistantMessage("Hi! How can I help?")));

		MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(chatMemory).conversationId("test").build();

		// New turn with just a UserMessage (no ToolCallAdvisor involved)
		Prompt prompt = Prompt.builder()
			.messages(new SystemMessage("Be helpful"), new UserMessage("What's 2+2?"))
			.build();
		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		ChatClientRequest processedRequest = advisor.before(request, this.chain);

		// Verify: memory messages should be prepended (backward compatible behavior)
		List<Message> processedMessages = processedRequest.prompt().getInstructions();
		assertThat(processedMessages).hasSizeGreaterThanOrEqualTo(4); // System + prev
																		// user + prev
																		// assistant + new
																		// user
		assertThat(processedMessages.get(0)).isInstanceOf(SystemMessage.class);
		// Previous messages from memory should be present
		assertThat(processedMessages.stream().filter(m -> m instanceof UserMessage).count()).isEqualTo(2);
		assertThat(processedMessages.stream().filter(m -> m instanceof AssistantMessage).count()).isEqualTo(1);
	}

}
