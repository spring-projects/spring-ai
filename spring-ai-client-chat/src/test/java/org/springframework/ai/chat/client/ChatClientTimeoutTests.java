/*
 * Copyright 2025-2025 the original author or authors.
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

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for timeout functionality in ChatClient.
 *
 * @author Hyunsang Han
 */
class ChatClientTimeoutTests {

	@Test
	void requestLevelTimeout() {
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.call(any(Prompt.class))).thenReturn(mock(ChatResponse.class));

		ChatClient chatClient = ChatClient.builder(chatModel).build();

		ChatClientRequest request = DefaultChatClientUtils
			.toChatClientRequest((DefaultChatClient.DefaultChatClientRequestSpec) chatClient.prompt("Hello")
				.timeout(Duration.ofSeconds(30)));

		// Verify timeout is stored in context
		assertThat(request.context()).containsKey(ChatClientAttributes.TIMEOUT.getKey());
		assertThat(request.context().get(ChatClientAttributes.TIMEOUT.getKey())).isEqualTo(Duration.ofSeconds(30));
	}

	@Test
	void builderDefaultTimeout() {
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.call(any(Prompt.class))).thenReturn(mock(ChatResponse.class));

		ChatClient chatClient = ChatClient.builder(chatModel).defaultTimeout(Duration.ofMinutes(2)).build();

		ChatClientRequest request = DefaultChatClientUtils
			.toChatClientRequest((DefaultChatClient.DefaultChatClientRequestSpec) chatClient.prompt("Hello"));

		// Verify default timeout is stored in context
		assertThat(request.context()).containsKey(ChatClientAttributes.TIMEOUT.getKey());
		assertThat(request.context().get(ChatClientAttributes.TIMEOUT.getKey())).isEqualTo(Duration.ofMinutes(2));
	}

	@Test
	void chatOptionsTimeout() {
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.call(any(Prompt.class))).thenReturn(mock(ChatResponse.class));

		ChatOptions options = ChatOptions.builder().timeout(Duration.ofMinutes(1)).build();

		ChatClient chatClient = ChatClient.builder(chatModel).build();

		ChatClientRequest request = DefaultChatClientUtils.toChatClientRequest(
				(DefaultChatClient.DefaultChatClientRequestSpec) chatClient.prompt("Hello").options(options));

		// Verify ChatOptions timeout is stored in context
		assertThat(request.context()).containsKey(ChatClientAttributes.TIMEOUT.getKey());
		assertThat(request.context().get(ChatClientAttributes.TIMEOUT.getKey())).isEqualTo(Duration.ofMinutes(1));
	}

	@Test
	void timeoutPriority() {
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.call(any(Prompt.class))).thenReturn(mock(ChatResponse.class));

		ChatOptions options = ChatOptions.builder().timeout(Duration.ofMinutes(1)).build();

		ChatClient chatClient = ChatClient.builder(chatModel).defaultTimeout(Duration.ofMinutes(2)).build();

		ChatClientRequest request = DefaultChatClientUtils
			.toChatClientRequest((DefaultChatClient.DefaultChatClientRequestSpec) chatClient.prompt("Hello")
				.options(options)
				.timeout(Duration.ofSeconds(30)));

		// Request-level timeout should take precedence
		assertThat(request.context()).containsKey(ChatClientAttributes.TIMEOUT.getKey());
		assertThat(request.context().get(ChatClientAttributes.TIMEOUT.getKey())).isEqualTo(Duration.ofSeconds(30));
	}

	@Test
	void noTimeoutWhenNotSet() {
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.call(any(Prompt.class))).thenReturn(mock(ChatResponse.class));

		ChatClient chatClient = ChatClient.builder(chatModel).build();

		ChatClientRequest request = DefaultChatClientUtils
			.toChatClientRequest((DefaultChatClient.DefaultChatClientRequestSpec) chatClient.prompt("Hello"));

		// No timeout should be set in context
		assertThat(request.context()).doesNotContainKey(ChatClientAttributes.TIMEOUT.getKey());
	}

}
