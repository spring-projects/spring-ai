/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.chat.client;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

/**
 * Tests for the ChatClient with a focus on verifying the handling of conversation memory
 * and the integration of MessageChatMemoryAdvisor to ensure accurate responses based on
 * previous interactions.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 */
@ExtendWith(MockitoExtension.class)
public class ChatClientAdvisorTests {

	@Mock
	ChatModel chatModel;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	private String join(Flux<String> fluxContent) {
		return fluxContent.collectList().block().stream().collect(Collectors.joining());
	}

	@Test
	public void promptChatMemory() {
		ChatResponseMetadata chatResponseMetadata = ChatResponseMetadata.builder().build();

		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(
					new ChatResponse(List.of(new Generation(new AssistantMessage("Hello John"))), chatResponseMetadata))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Your name is John"))),
					chatResponseMetadata));
		when(this.chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());

		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		var chatClient = ChatClient.builder(this.chatModel)
			.defaultSystem("Default system text.")
			.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
			.build();

		// Turn 1: no memory yet — only system + user message sent to model
		ChatResponse chatResponse = chatClient.prompt()
			.user("my name is John")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "dumy-conversation-id"))
			.call()
			.chatResponse();

		assertThat(chatResponse.getResult().getOutput().getText()).isEqualTo("Hello John");

		List<Message> instructions = this.promptCaptor.getValue().getInstructions();
		assertThat(instructions).hasSize(2);

		assertThat(instructions.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(instructions.get(0).getText()).isEqualToIgnoringWhitespace("Default system text.");

		assertThat(instructions.get(1).getMessageType()).isEqualTo(MessageType.USER);
		assertThat(instructions.get(1).getText()).isEqualTo("my name is John");

		// Turn 2: memory from turn 1 is prepended as typed messages before the new user
		// message
		String content = chatClient.prompt()
			.user("What is my name?")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "dumy-conversation-id"))
			.call()
			.content();

		assertThat(content).isEqualTo("Your name is John");

		instructions = this.promptCaptor.getValue().getInstructions();
		assertThat(instructions).hasSize(4);

		assertThat(instructions.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(instructions.get(0).getText()).isEqualToIgnoringWhitespace("Default system text.");

		assertThat(instructions.get(1).getMessageType()).isEqualTo(MessageType.USER);
		assertThat(instructions.get(1).getText()).isEqualTo("my name is John");

		assertThat(instructions.get(2).getMessageType()).isEqualTo(MessageType.ASSISTANT);
		assertThat(instructions.get(2).getText()).isEqualTo("Hello John");

		assertThat(instructions.get(3).getMessageType()).isEqualTo(MessageType.USER);
		assertThat(instructions.get(3).getText()).isEqualTo("What is my name?");
	}

	@Test
	public void streamingPromptChatMemory() {
		given(this.chatModel.stream(this.promptCaptor.capture())).willReturn(Flux.generate(
				() -> new ChatResponse(List.of(new Generation(new AssistantMessage("Hello John")))), (state, sink) -> {
					sink.next(state);
					sink.complete();
					return state;
				}))
			.willReturn(Flux.generate(
					() -> new ChatResponse(List.of(new Generation(new AssistantMessage("Your name is John")))),
					(state, sink) -> {
						sink.next(state);
						sink.complete();
						return state;
					}));
		when(this.chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());

		ChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(new InMemoryChatMemoryRepository())
			.build();

		var chatClient = ChatClient.builder(this.chatModel)
			.defaultSystem("Default system text.")
			.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
			.build();

		// Turn 1: no memory yet — only system + user message sent to model
		var content = join(chatClient.prompt()
			.user("my name is John")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "dumy-conversation-id"))
			.stream()
			.content());

		assertThat(content).isEqualTo("Hello John");

		List<Message> instructions = this.promptCaptor.getValue().getInstructions();
		assertThat(instructions).hasSize(2);

		assertThat(instructions.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(instructions.get(0).getText()).isEqualToIgnoringWhitespace("Default system text.");

		assertThat(instructions.get(1).getMessageType()).isEqualTo(MessageType.USER);
		assertThat(instructions.get(1).getText()).isEqualTo("my name is John");
		// Turn 2: memory from turn 1 is prepended as typed messages before the new user
		// message
		content = join(chatClient.prompt()
			.user("What is my name?")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "dumy-conversation-id"))
			.stream()
			.content());

		assertThat(content).isEqualTo("Your name is John");

		instructions = this.promptCaptor.getValue().getInstructions();
		assertThat(instructions).hasSize(4);

		assertThat(instructions.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(instructions.get(0).getText()).isEqualToIgnoringWhitespace("Default system text.");

		assertThat(instructions.get(1).getMessageType()).isEqualTo(MessageType.USER);
		assertThat(instructions.get(1).getText()).isEqualTo("my name is John");

		assertThat(instructions.get(2).getMessageType()).isEqualTo(MessageType.ASSISTANT);
		assertThat(instructions.get(2).getText()).isEqualTo("Hello John");
		assertThat(instructions.get(3).getMessageType()).isEqualTo(MessageType.USER);
		assertThat(instructions.get(3).getText()).isEqualTo("What is my name?");
	}

}
