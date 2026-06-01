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
	public void messageChatMemory() {
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

		ChatResponse chatResponse = chatClient.prompt()
			.user("my name is John")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "test-session"))
			.call()
			.chatResponse();

		String content = chatResponse.getResult().getOutput().getText();
		assertThat(content).isEqualTo("Hello John");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(2);
		Message systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("Default system text.");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getText()).isEqualTo("my name is John");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);

		content = chatClient.prompt()
			.user("What is my name?")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "test-session"))
			.call()
			.content();

		assertThat(content).isEqualTo("Your name is John");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(4);
		systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("Default system text.");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		Message memoryUserMessage = this.promptCaptor.getValue().getInstructions().get(1);
		assertThat(memoryUserMessage.getText()).isEqualTo("my name is John");
		assertThat(memoryUserMessage.getMessageType()).isEqualTo(MessageType.USER);

		Message memoryAssistantMessage = this.promptCaptor.getValue().getInstructions().get(2);
		assertThat(memoryAssistantMessage.getText()).isEqualTo("Hello John");
		assertThat(memoryAssistantMessage.getMessageType()).isEqualTo(MessageType.ASSISTANT);

		userMessage = this.promptCaptor.getValue().getInstructions().get(3);
		assertThat(userMessage.getText()).isEqualTo("What is my name?");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
	}

	@Test
	public void streamingMessageChatMemory() {
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

		var content = join(chatClient.prompt()
			.user("my name is John")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "test-session"))
			.stream()
			.content());

		assertThat(content).isEqualTo("Hello John");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(2);
		Message systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("Default system text.");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getText()).isEqualTo("my name is John");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);

		content = join(chatClient.prompt()
			.user("What is my name?")
			.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "test-session"))
			.stream()
			.content());

		assertThat(content).isEqualTo("Your name is John");

		assertThat(this.promptCaptor.getValue().getInstructions()).hasSize(4);
		systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualTo("Default system text.");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		Message memoryUserMessage = this.promptCaptor.getValue().getInstructions().get(1);
		assertThat(memoryUserMessage.getText()).isEqualTo("my name is John");
		assertThat(memoryUserMessage.getMessageType()).isEqualTo(MessageType.USER);

		Message memoryAssistantMessage = this.promptCaptor.getValue().getInstructions().get(2);
		assertThat(memoryAssistantMessage.getText()).isEqualTo("Hello John");
		assertThat(memoryAssistantMessage.getMessageType()).isEqualTo(MessageType.ASSISTANT);

		userMessage = this.promptCaptor.getValue().getInstructions().get(3);
		assertThat(userMessage.getText()).isEqualTo("What is my name?");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
	}

}
