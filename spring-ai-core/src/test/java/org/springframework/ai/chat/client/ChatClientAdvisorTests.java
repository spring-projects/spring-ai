/*
 * Copyright 2024-2024 the original author or authors.
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
import java.util.Map;
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
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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

		when(chatModel.call(promptCaptor.capture()))
				.thenReturn(new ChatResponse(List.of(new Generation("Hello John"))))
				.thenReturn(new ChatResponse(List.of(new Generation("Your name is John"))));

		ChatMemory chatMemory = new InMemoryChatMemory();

		var chatClient = ChatClient.builder(chatModel)
				.defaultSystem("Default system text.")
				.defaultAdvisors(new PromptChatMemoryAdvisor(chatMemory))
				.build();

		var content = chatClient.prompt()
				.user("my name is John")
				.call().content();

		assertThat(content).isEqualTo("Hello John");

		Message systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualToIgnoringWhitespace("""
				Default system text.

				Use the conversation memory from the MEMORY section to provide accurate answers.

				---------------------
				MEMORY:
				---------------------
				""");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		Message userMessage = promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getContent()).isEqualToIgnoringWhitespace("my name is John");

		content = chatClient.prompt()
				.user("What is my name?")
				.call().content();

		assertThat(content).isEqualTo("Your name is John");

		systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualToIgnoringWhitespace("""
				Default system text.

				Use the conversation memory from the MEMORY section to provide accurate answers.

				---------------------
				MEMORY:
				USER:my name is John
				ASSISTANT:Hello John
				---------------------
				""");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		userMessage = promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getContent()).isEqualToIgnoringWhitespace("What is my name?");
	}

	@Test
	public void streamingPromptChatMemory() {

		when(chatModel.stream(promptCaptor.capture()))
				.thenReturn(
						Flux.generate(() -> new ChatResponse(List.of(new Generation("Hello John"))), (state, sink) -> {
							sink.next(state);
							sink.complete();
							return state;
						}))
				.thenReturn(
						Flux.generate(() -> new ChatResponse(List.of(new Generation("Your name is John"))),
								(state, sink) -> {
									sink.next(state);
									sink.complete();
									return state;
								}));

		ChatMemory chatMemory = new InMemoryChatMemory();

		var chatClient = ChatClient.builder(chatModel)
				.defaultSystem("Default system text.")
				.defaultAdvisors(new PromptChatMemoryAdvisor(chatMemory))
				.build();

		var content = join(chatClient.prompt()
				.user("my name is John")
				.stream().content());

		assertThat(content).isEqualTo("Hello John");

		Message systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualToIgnoringWhitespace("""
				Default system text.

				Use the conversation memory from the MEMORY section to provide accurate answers.

				---------------------
				MEMORY:
				---------------------
				""");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		Message userMessage = promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getContent()).isEqualToIgnoringWhitespace("my name is John");

		content = join(chatClient.prompt()
				.user("What is my name?")
				.stream().content());

		assertThat(content).isEqualTo("Your name is John");

		systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualToIgnoringWhitespace("""
				Default system text.

				Use the conversation memory from the MEMORY section to provide accurate answers.

				---------------------
				MEMORY:
				USER:my name is John
				ASSISTANT:Hello John
				---------------------
				""");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		userMessage = promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getContent()).isEqualToIgnoringWhitespace("What is my name?");
	}

	public static class MockAdvisor implements RequestResponseAdvisor {

		public AdvisedRequest advisedRequest;

		public Map<String, Object> advisedRequestContext;

		public Map<String, Object> chatResponseContext;

		public ChatResponse chatResponse;

		public Map<String, Object> fluxChatResponseContext;

		public Flux<ChatResponse> fluxChatResponse;

		@Override
		public AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> context) {
			advisedRequest = request;
			advisedRequestContext = context;

			context.put("adviseRequest", "adviseRequest");

			return request;
		}

		@Override
		public ChatResponse adviseResponse(ChatResponse response, Map<String, Object> context) {
			chatResponse = response;
			chatResponseContext = context;

			context.put("adviseResponse", "adviseResponse");
			return response;
		}

		@Override
		public Flux<ChatResponse> adviseResponse(Flux<ChatResponse> fluxResponse, Map<String, Object> context) {
			fluxChatResponse = fluxResponse;
			fluxChatResponseContext = context;

			context.put("fluxAdviseResponse", "fluxAdviseResponse");

			return fluxResponse;
		}

	};

	@Test
	public void advisors() {

		var mockAdvisor = new MockAdvisor();

		when(chatModel.call(promptCaptor.capture())).thenReturn(new ChatResponse(List.of(new Generation("Hello John"))))
			.thenReturn(new ChatResponse(List.of(new Generation("Your name is John"))));

		when(chatModel.call(promptCaptor.capture())).thenReturn(new ChatResponse(List.of(new Generation("Hello John"))))
			.thenReturn(new ChatResponse(List.of(new Generation("Your name is John"))));

		var chatClient = ChatClient.builder(chatModel)
			.defaultSystem("Default system text.")
			.defaultAdvisors(mockAdvisor)
			.build();

		var content = chatClient.prompt()
			.user("my name is John")
			.advisors(a -> a.param("key1", "value1").params(Map.of("key2", "value2")))
			.call()
			.content();

		assertThat(content).isEqualTo("Hello John");

		assertThat(mockAdvisor.advisedRequestContext).containsEntry("key1", "value1")
			.containsEntry("key2", "value2")
			.containsEntry("adviseRequest", "adviseRequest");
		assertThat(mockAdvisor.advisedRequest.advisorParams()).containsEntry("key1", "value1")
			.containsEntry("key2", "value2")
			.doesNotContainKey("adviseRequest");

		assertThat(mockAdvisor.chatResponseContext).containsEntry("key1", "value1")
			.containsEntry("key2", "value2")
			.containsEntry("adviseRequest", "adviseRequest")
			.containsEntry("adviseResponse", "adviseResponse");
		assertThat(mockAdvisor.chatResponse).isNotNull();
	}

}
