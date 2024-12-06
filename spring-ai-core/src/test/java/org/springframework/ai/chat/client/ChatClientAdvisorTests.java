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

import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * @author Christian Tzolov
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

		var builder = ChatResponseMetadata.builder()
			.withId("124")
			.withUsage(new MessageAggregator.DefaultUsage(1, 2, 3))
			.withModel("gpt4o")
			.withKeyValue("created", 0L)
			.withKeyValue("system-fingerprint", "john doe");
		ChatResponseMetadata chatResponseMetadata = builder.build();

		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(
					new ChatResponse(List.of(new Generation(new AssistantMessage("Hello John"))), chatResponseMetadata))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Your name is John"))),
					chatResponseMetadata));

		ChatMemory chatMemory = new InMemoryChatMemory();

		var chatClient = ChatClient.builder(this.chatModel)
			.defaultSystem("Default system text.")
			.defaultAdvisors(new PromptChatMemoryAdvisor(chatMemory))
			.build();

		ChatResponse chatResponse = chatClient.prompt().user("my name is John").call().chatResponse();

		String content = chatResponse.getResult().getOutput().getText();
		assertThat(content).isEqualTo("Hello John");

		Message systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualToIgnoringWhitespace("""
				Default system text.

				Use the conversation memory from the MEMORY section to provide accurate answers.

				---------------------
				MEMORY:
				---------------------
				""");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getText()).isEqualToIgnoringWhitespace("my name is John");

		content = chatClient.prompt().user("What is my name?").call().content();

		assertThat(content).isEqualTo("Your name is John");

		systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualToIgnoringWhitespace("""
				Default system text.

				Use the conversation memory from the MEMORY section to provide accurate answers.

				---------------------
				MEMORY:
				USER:my name is John
				ASSISTANT:Hello John
				---------------------
				""");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		userMessage = this.promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getText()).isEqualToIgnoringWhitespace("What is my name?");
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

		ChatMemory chatMemory = new InMemoryChatMemory();

		var chatClient = ChatClient.builder(this.chatModel)
			.defaultSystem("Default system text.")
			.defaultAdvisors(new PromptChatMemoryAdvisor(chatMemory))
			.build();

		var content = join(chatClient.prompt().user("my name is John").stream().content());

		assertThat(content).isEqualTo("Hello John");

		Message systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualToIgnoringWhitespace("""
				Default system text.

				Use the conversation memory from the MEMORY section to provide accurate answers.

				---------------------
				MEMORY:
				---------------------
				""");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getText()).isEqualToIgnoringWhitespace("my name is John");

		content = join(chatClient.prompt().user("What is my name?").stream().content());

		assertThat(content).isEqualTo("Your name is John");

		systemMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getText()).isEqualToIgnoringWhitespace("""
				Default system text.

				Use the conversation memory from the MEMORY section to provide accurate answers.

				---------------------
				MEMORY:
				USER:my name is John
				ASSISTANT:Hello John
				---------------------
				""");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		userMessage = this.promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getText()).isEqualToIgnoringWhitespace("What is my name?");
	}

}
