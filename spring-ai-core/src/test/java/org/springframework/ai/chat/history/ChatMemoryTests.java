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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.chatbot.ChatBotResponse;
import org.springframework.ai.chat.chatbot.DefaultChatBot;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.transformer.PromptContext;
import org.springframework.ai.model.Content;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class ChatMemoryTests {

	@Mock
	ChatClient chatClient;

	@Mock
	StreamingChatClient streamingChatClient;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	@Test
	public void chatMemoryMessageListAugmentor() {

		ChatMemory chatHistory = new InMemoryChatMemory();

		DefaultChatBot chatAgent = DefaultChatBot.builder(chatClient)
			.withRetrievers(List.of(new ChatMemoryRetriever(chatHistory)))
			.withContentPostProcessors(
					List.of(new LastMaxTokenSizeContentTransformer(new JTokkitTokenCountEstimator(), 10)))
			.withAugmentors(List.of(new MessageChatMemoryAugmentor()))
			.withChatAgentListeners(List.of(new ChatMemoryAgentListener(chatHistory)))
			.build();

		chatClientUserMessages(chatAgent, chatHistory);
	}

	@Test
	public void chatMemorySystemPromptAugmentor() {

		ChatMemory chatHistory = new InMemoryChatMemory();

		DefaultChatBot chatAgent = DefaultChatBot.builder(chatClient)
			.withRetrievers(List.of(new ChatMemoryRetriever(chatHistory)))
			.withContentPostProcessors(
					List.of(new LastMaxTokenSizeContentTransformer(new JTokkitTokenCountEstimator(), 10)))
			.withAugmentors(List.of(new SystemPromptChatMemoryAugmentor()))
			.withChatAgentListeners(List.of(new ChatMemoryAgentListener(chatHistory)))
			.build();

		chatClientUserMessages(chatAgent, chatHistory);
	}

	public void chatClientUserMessages(DefaultChatBot chatAgent, ChatMemory chatHistory) {

		when(chatClient.call(promptCaptor.capture()))
				.thenReturn(new ChatResponse(List.of(new Generation("assistant:1"))))
				.thenReturn(new ChatResponse(List.of(new Generation("assistant:2"))))
				.thenReturn(new ChatResponse(List.of(new Generation("assistant:3"))));

		var promptContext = PromptContext.builder()
				.withConversationId("test-session-id")
				.withPrompt(new Prompt(
						List.of(new UserMessage("user:1"), new UserMessage("user:2"), new UserMessage("user:3"),
								new UserMessage("user:4"), new UserMessage("user:5"))))
				.build();

		ChatBotResponse response1 = chatAgent.call(promptContext);

		assertThat(response1.getChatResponse().getResult().getOutput().getContent()).isEqualTo("assistant:1");

		List<Content> contents = response1.getPromptContext().getContents();
		assertThat(contents).hasSize(0);

		List<Message> history = chatHistory.get("test-session-id", 1000);
		assertThat(history).hasSize(6);

		ChatBotResponse response2 = chatAgent.call(PromptContext.builder()
				.withConversationId("test-session-id")
				.withPrompt(new Prompt(
						List.of(new UserMessage("user:6"), new UserMessage("user:7"), new UserMessage("user:8"))))
				.build());

		assertThat(response2.getChatResponse().getResult().getOutput().getContent()).isEqualTo("assistant:2");

		history = chatHistory.get("test-session-id", 1000);
		assertThat(history).hasSize(10);

		contents = response2.getPromptContext().getContents();
		assertThat(contents).hasSize(3);
		assertThat(contents.get(0).getContent()).isEqualTo("user:4");
		assertThat(contents.get(1).getContent()).isEqualTo("user:5");
		assertThat(contents.get(2).getContent()).isEqualTo("assistant:1");

		ChatBotResponse response3 = chatAgent.call(PromptContext.builder()
				.withConversationId("test-session-id")
				.withPrompt(new Prompt(List.of(new UserMessage("user:9")))).build());
		assertThat(response3.getChatResponse().getResult().getOutput().getContent()).isEqualTo("assistant:3");

		history = chatHistory.get("test-session-id", 1000);
		assertThat(history).hasSize(12);

		contents = response3.getPromptContext().getContents();
		assertThat(contents).hasSize(3);
		assertThat(contents.get(0).getContent()).isEqualTo("user:7");
		assertThat(contents.get(1).getContent()).isEqualTo("user:8");
		assertThat(contents.get(2).getContent()).isEqualTo("assistant:2");
	}

}
