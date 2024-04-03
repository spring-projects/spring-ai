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

package org.springframework.ai.chat.history;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class HistoryChatClientTests {

	@Mock
	ChatClient chatClient;

	@Mock
	StreamingChatClient streamingChatClient;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	private List<Message> flatten(List<ChatMessages> chatMessages) {
		return chatMessages.stream().flatMap(c -> c.getMessages().stream()).toList();
	}

	@Test
	public void chatClientUserMessages() {

		ChatHistory2 chatHistory = new InMemoryChatHistory2();
		ChatHistoryRetriever chatHistoryRetriever = new ChatHistoryRetriever(chatHistory, 10);

		ChatEngine chatEngine = new ChatEngine(chatClient, streamingChatClient, chatHistory, "test-session-id",
				chatHistoryRetriever);

		when(chatClient.call(promptCaptor.capture()))
			.thenReturn(new ChatResponse(List.of(new Generation("assistant:1"))))
			.thenReturn(new ChatResponse(List.of(new Generation("assistant:2"))))
			.thenReturn(new ChatResponse(List.of(new Generation("assistant:3"))));

		EngineResponse response1 = chatEngine
			.call(new Prompt(List.of(new UserMessage("user:1"), new UserMessage("user:2"), new UserMessage("user:3"),
					new UserMessage("user:4"), new UserMessage("user:5"))));

		assertThat(response1.getChatResponse().getResult().getOutput().getContent()).isEqualTo("assistant:1");

		List<Message> history = flatten(chatHistory.get("test-session-id"));

		assertThat(history).hasSize(3);
		assertThat(history.get(0).getContent()).isEqualTo("user:4");
		assertThat(history.get(1).getContent()).isEqualTo("user:5");
		assertThat(history.get(2).getContent()).isEqualTo("assistant:1");

		EngineResponse response2 = chatEngine
			.call(new Prompt(List.of(new UserMessage("user:6"), new UserMessage("user:7"), new UserMessage("user:8"))));

		assertThat(response2.getChatResponse().getResult().getOutput().getContent()).isEqualTo("assistant:2");

		history = flatten(chatHistory.get("test-session-id"));

		assertThat(history).hasSize(3);
		assertThat(history.get(0).getContent()).isEqualTo("user:7");
		assertThat(history.get(1).getContent()).isEqualTo("user:8");
		assertThat(history.get(2).getContent()).isEqualTo("assistant:2");

		EngineResponse response3 = chatEngine.call(new Prompt(List.of(new UserMessage("user:9"))));

		assertThat(response3.getChatResponse().getResult().getOutput().getContent()).isEqualTo("assistant:3");

		history = flatten(chatHistory.get("test-session-id"));

		assertThat(history).hasSize(3);
		assertThat(history.get(0).getContent()).isEqualTo("assistant:2");
		assertThat(history.get(1).getContent()).isEqualTo("user:9");
		assertThat(history.get(2).getContent()).isEqualTo("assistant:3");
	}

	// @Test
	// public void chatClientUserAndSystemMessages() {

	// ChatClientHistoryDecorator historyChatClient = ChatClientHistoryDecorator.builder()
	// .withSessionId("test-session-id")
	// .withChatClient(chatClient)
	// .withChatHistory(new TokenCountSlidingWindowChatHistory(15))
	// .build();

	// when(chatClient.call(promptCaptor.capture()))
	// .thenReturn(new ChatResponse(List.of(new Generation("assistant:1"))))
	// .thenReturn(new ChatResponse(List.of(new Generation("assistant:2"))))
	// .thenReturn(new ChatResponse(List.of(new Generation("assistant:3"))));

	// ChatResponse response1 = historyChatClient.call(
	// new Prompt(List.of(new SystemMessage("system:1"), new UserMessage("user:1"), new
	// UserMessage("user:2"),
	// new UserMessage("user:3"), new UserMessage("user:4"), new UserMessage("user:5"))));

	// assertThat(response1.getResult().getOutput().getContent()).isEqualTo("assistant:1");

	// List<Message> history = historyChatClient.getChatHistory().get("test-session-id");

	// assertThat(history).hasSize(5);
	// assertThat(history.get(0).getContent()).isEqualTo("system:1");
	// assertThat(history.get(1).getContent()).isEqualTo("user:3");
	// assertThat(history.get(2).getContent()).isEqualTo("user:4");
	// assertThat(history.get(3).getContent()).isEqualTo("user:5");
	// assertThat(history.get(4).getContent()).isEqualTo("assistant:1");

	// ChatResponse response2 = historyChatClient
	// .call(new Prompt(List.of(new UserMessage("user:6"), new UserMessage("user:7"), new
	// UserMessage("user:8"))));

	// assertThat(response2.getResult().getOutput().getContent()).isEqualTo("assistant:2");

	// history = historyChatClient.getChatHistory().get("test-session-id");

	// assertThat(history).hasSize(5);
	// assertThat(history.get(0).getContent()).isEqualTo("system:1");
	// assertThat(history.get(1).getContent()).isEqualTo("user:6");
	// assertThat(history.get(2).getContent()).isEqualTo("user:7");
	// assertThat(history.get(3).getContent()).isEqualTo("user:8");
	// assertThat(history.get(4).getContent()).isEqualTo("assistant:2");

	// ChatResponse response3 = historyChatClient.call(new Prompt(List.of(new
	// SystemMessage("system:2"))));

	// assertThat(response3.getResult().getOutput().getContent()).isEqualTo("assistant:3");

	// history = historyChatClient.getChatHistory().get("test-session-id");

	// assertThat(history).hasSize(5);
	// assertThat(history.get(0).getContent()).isEqualTo("user:7");
	// assertThat(history.get(1).getContent()).isEqualTo("user:8");
	// assertThat(history.get(2).getContent()).isEqualTo("assistant:2");
	// assertThat(history.get(3).getContent()).isEqualTo("system:2");
	// assertThat(history.get(4).getContent()).isEqualTo("assistant:3");
	// }

	// @Test
	// public void streamChatClientUserMessages() {

	// ChatClientHistoryDecorator historyChatClient = ChatClientHistoryDecorator.builder()
	// .withSessionId("test-session-id")
	// .withChatClient(chatClient)
	// .withStreamingChatClient(streamingChatClient)
	// .withChatHistory(new TokenCountSlidingWindowChatHistory(10))
	// .build();

	// when(streamingChatClient.stream(promptCaptor.capture()))
	// .thenReturn(Flux.just(new ChatResponse(List.of(new Generation("as"))),
	// new ChatResponse(List.of(new Generation("sis"))), new ChatResponse(List.of(new
	// Generation("tant"))),
	// new ChatResponse(List.of(new Generation(":1")))))
	// .thenReturn(Flux.just(new ChatResponse(List.of(new Generation("as"))),
	// new ChatResponse(List.of(new Generation("sis"))), new ChatResponse(List.of(new
	// Generation("ta"))),
	// new ChatResponse(List.of(new Generation("nt:2")))))
	// .thenReturn(Flux.just(new ChatResponse(List.of(new Generation("assi"))),
	// new ChatResponse(List.of(new Generation("s"))), new ChatResponse(List.of(new
	// Generation("tant"))),
	// new ChatResponse(List.of(new Generation(":3")))));

	// Flux<ChatResponse> response1 = historyChatClient
	// .stream(new Prompt(List.of(new UserMessage("user:1"), new UserMessage("user:2"),
	// new UserMessage("user:3"),
	// new UserMessage("user:4"), new UserMessage("user:5"))));

	// String response1Text = response1.collectList()
	// .block()
	// .stream()
	// .map(r -> r.getResult().getOutput().getContent())
	// .collect(Collectors.joining());

	// assertThat(response1Text).isEqualTo("assistant:1");

	// List<Message> history = historyChatClient.getChatHistory().get("test-session-id");

	// assertThat(history).hasSize(3);
	// assertThat(history.get(0).getContent()).isEqualTo("user:4");
	// assertThat(history.get(1).getContent()).isEqualTo("user:5");
	// assertThat(history.get(2).getContent()).isEqualTo("assistant:1");

	// Flux<ChatResponse> response2 = historyChatClient.stream(
	// new Prompt(List.of(new UserMessage("user:6"), new UserMessage("user:7"), new
	// UserMessage("user:8"))));

	// String response2Text = response2.collectList()
	// .block()
	// .stream()
	// .map(r -> r.getResult().getOutput().getContent())
	// .collect(Collectors.joining());

	// assertThat(response2Text).isEqualTo("assistant:2");

	// history = historyChatClient.getChatHistory().get("test-session-id");

	// assertThat(history).hasSize(3);
	// assertThat(history.get(0).getContent()).isEqualTo("user:7");
	// assertThat(history.get(1).getContent()).isEqualTo("user:8");
	// assertThat(history.get(2).getContent()).isEqualTo("assistant:2");

	// Flux<ChatResponse> response3 = historyChatClient.stream(new Prompt(List.of(new
	// UserMessage("user:9"))));

	// String response3Text = response3.collectList()
	// .block()
	// .stream()
	// .map(r -> r.getResult().getOutput().getContent())
	// .collect(Collectors.joining());

	// assertThat(response3Text).isEqualTo("assistant:3");

	// history = historyChatClient.getChatHistory().get("test-session-id");

	// assertThat(history).hasSize(3);
	// assertThat(history.get(0).getContent()).isEqualTo("assistant:2");
	// assertThat(history.get(1).getContent()).isEqualTo("user:9");
	// assertThat(history.get(2).getContent()).isEqualTo("assistant:3");
	// }

}
