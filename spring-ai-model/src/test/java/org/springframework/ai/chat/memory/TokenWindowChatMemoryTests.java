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

package org.springframework.ai.chat.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TokenWindowChatMemory}.
 *
 * @author Sun Yuhan
 * @since 1.1.0
 */
public class TokenWindowChatMemoryTests {

	private final TokenWindowChatMemory chatMemory = TokenWindowChatMemory.builder().build();

	@Test
	void zeroMaxMessagesNotAllowed() {
		assertThatThrownBy(() -> TokenWindowChatMemory.builder().maxTokens(0).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("maxTokens must be greater than 0");
	}

	@Test
	void negativeMaxTokensNotAllowed() {
		assertThatThrownBy(() -> TokenWindowChatMemory.builder().maxTokens(-1).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("maxTokens must be greater than 0");
	}

	@Test
	void handleMultipleMessagesInConversation() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> messages = List.of(new AssistantMessage("I, Robot"), new UserMessage("Hello"));

		this.chatMemory.add(conversationId, messages);

		assertThat(this.chatMemory.get(conversationId)).containsAll(messages);

		this.chatMemory.clear(conversationId);

		assertThat(this.chatMemory.get(conversationId)).isEmpty();
	}

	@Test
	void handleSingleMessageInConversation() {
		String conversationId = UUID.randomUUID().toString();
		Message message = new UserMessage("Hello");

		this.chatMemory.add(conversationId, message);

		assertThat(this.chatMemory.get(conversationId)).contains(message);

		this.chatMemory.clear(conversationId);

		assertThat(this.chatMemory.get(conversationId)).isEmpty();
	}

	@Test
	void nullConversationIdNotAllowed() {
		assertThatThrownBy(() -> this.chatMemory.add(null, List.of(new UserMessage("Hello"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> this.chatMemory.add(null, new UserMessage("Hello")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> this.chatMemory.get(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> this.chatMemory.clear(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");
	}

	@Test
	void emptyConversationIdNotAllowed() {
		assertThatThrownBy(() -> this.chatMemory.add("", List.of(new UserMessage("Hello"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> this.chatMemory.add(null, new UserMessage("Hello")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> this.chatMemory.get("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> this.chatMemory.clear("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");
	}

	@Test
	void nullMessagesNotAllowed() {
		String conversationId = UUID.randomUUID().toString();
		assertThatThrownBy(() -> this.chatMemory.add(conversationId, (List<Message>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages cannot be null");
	}

	@Test
	void nullMessageNotAllowed() {
		String conversationId = UUID.randomUUID().toString();
		assertThatThrownBy(() -> this.chatMemory.add(conversationId, (Message) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("message cannot be null");
	}

	@Test
	void messagesWithNullElementsNotAllowed() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> messagesWithNull = new ArrayList<>();
		messagesWithNull.add(null);

		assertThatThrownBy(() -> this.chatMemory.add(conversationId, messagesWithNull))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages cannot contain null elements");
	}

	@Test
	void customMaxTokens() {
		String conversationId = UUID.randomUUID().toString();
		int customMaxTokens = 15;

		TokenWindowChatMemory customChatMemory = TokenWindowChatMemory.builder().maxTokens(customMaxTokens).build();

		List<Message> messages = List.of(new UserMessage("Message 1"), new AssistantMessage("Response 1"),
				new UserMessage("Message 2"), new AssistantMessage("Response 2"), new UserMessage("Message 3"));

		customChatMemory.add(conversationId, messages);
		List<Message> result = customChatMemory.get(conversationId);

		assertThat(result).hasSize(5);
	}

	@Test
	void customTokenCountEstimator() {
		String conversationId = UUID.randomUUID().toString();

		TokenWindowChatMemory customChatMemory = TokenWindowChatMemory.builder()
			.tokenCountEstimator(new JTokkitTokenCountEstimator())
			.build();

		List<Message> messages = List.of(new UserMessage("Message 1"), new AssistantMessage("Response 1"),
				new UserMessage("Message 2"), new AssistantMessage("Response 2"), new UserMessage("Message 3"));

		customChatMemory.add(conversationId, messages);
		List<Message> result = customChatMemory.get(conversationId);

		assertThat(result).hasSize(5);
	}

	@Test
	void noEvictionWhenMessagesWithinLimit() {
		int maxTokens = 10;
		TokenWindowChatMemory customChatMemory = TokenWindowChatMemory.builder().maxTokens(maxTokens).build();

		String conversationId = UUID.randomUUID().toString();
		List<Message> memoryMessages = new ArrayList<>(
				List.of(new UserMessage("Message 1"), new AssistantMessage("Response 1")));
		customChatMemory.add(conversationId, memoryMessages);

		List<Message> newMessages = new ArrayList<>(List.of(new UserMessage("Message 2")));
		customChatMemory.add(conversationId, newMessages);

		List<Message> result = customChatMemory.get(conversationId);

		assertThat(result).hasSize(3);
		assertThat(result).containsExactly(new UserMessage("Message 1"), new AssistantMessage("Response 1"),
				new UserMessage("Message 2"));
	}

	@Test
	void evictionWhenMessagesExceedLimit() {
		int maxTokens = 3;
		TokenWindowChatMemory customChatMemory = TokenWindowChatMemory.builder().maxTokens(maxTokens).build();

		String conversationId = UUID.randomUUID().toString();
		List<Message> memoryMessages = new ArrayList<>(
				List.of(new UserMessage("Message 1"), new AssistantMessage("Response 1")));
		customChatMemory.add(conversationId, memoryMessages);

		List<Message> newMessages = new ArrayList<>(List.of(new UserMessage("Message 2")));
		customChatMemory.add(conversationId, newMessages);

		List<Message> result = customChatMemory.get(conversationId);

		assertThat(result).hasSize(1);
		assertThat(result).containsExactly(new UserMessage("Message 2"));
	}

	@Test
	void systemMessageIsPreservedDuringEviction() {
		int maxTokens = 9;
		TokenWindowChatMemory customChatMemory = TokenWindowChatMemory.builder().maxTokens(maxTokens).build();

		String conversationId = UUID.randomUUID().toString();
		List<Message> memoryMessages = new ArrayList<>(List.of(new SystemMessage("System 1"),
				new UserMessage("Message 1"), new AssistantMessage("Response 1")));
		customChatMemory.add(conversationId, memoryMessages);

		List<Message> newMessages = new ArrayList<>(
				List.of(new UserMessage("Message 2"), new AssistantMessage("Response 2")));
		customChatMemory.add(conversationId, newMessages);

		List<Message> result = customChatMemory.get(conversationId);

		assertThat(result).hasSize(3);
		assertThat(result).containsExactly(new SystemMessage("System 1"), new UserMessage("Message 2"),
				new AssistantMessage("Response 2"));
	}

	@Test
	void multipleSystemMessagesArePreservedDuringEviction() {
		int maxTokens = 9;
		TokenWindowChatMemory customChatMemory = TokenWindowChatMemory.builder().maxTokens(maxTokens).build();

		String conversationId = UUID.randomUUID().toString();
		List<Message> memoryMessages = new ArrayList<>(List.of(new SystemMessage("System 1"),
				new SystemMessage("System 2"), new UserMessage("Message 1"), new AssistantMessage("Response 1")));
		customChatMemory.add(conversationId, memoryMessages);

		List<Message> newMessages = new ArrayList<>(
				List.of(new UserMessage("Message 2"), new AssistantMessage("Response 2")));
		customChatMemory.add(conversationId, newMessages);

		List<Message> result = customChatMemory.get(conversationId);

		assertThat(result).hasSize(3);
		assertThat(result).containsExactly(new SystemMessage("System 1"), new SystemMessage("System 2"),
				new AssistantMessage("Response 2"));
	}

	@Test
	void emptyMessageList() {
		String conversationId = UUID.randomUUID().toString();

		List<Message> result = this.chatMemory.get(conversationId);

		assertThat(result).isEmpty();
	}

	@Test
	void oldSystemMessagesAreRemovedWhenNewOneAdded() {
		int maxTokens = 3;
		TokenWindowChatMemory customChatMemory = TokenWindowChatMemory.builder().maxTokens(maxTokens).build();

		String conversationId = UUID.randomUUID().toString();
		List<Message> memoryMessages = new ArrayList<>(
				List.of(new SystemMessage("System 1"), new SystemMessage("System 2")));
		customChatMemory.add(conversationId, memoryMessages);

		List<Message> newMessages = new ArrayList<>(List.of(new SystemMessage("System 3")));
		customChatMemory.add(conversationId, newMessages);

		List<Message> result = customChatMemory.get(conversationId);

		assertThat(result).hasSize(1);
		assertThat(result).containsExactly(new SystemMessage("System 3"));
	}

	@Test
	void mixedMessagesWithLimitEqualToSystemMessageCount() {
		int maxTokens = 6;
		TokenWindowChatMemory customChatMemory = TokenWindowChatMemory.builder().maxTokens(maxTokens).build();

		String conversationId = UUID.randomUUID().toString();
		List<Message> memoryMessages = new ArrayList<>(
				List.of(new SystemMessage("System 1"), new SystemMessage("System 2")));
		customChatMemory.add(conversationId, memoryMessages);

		List<Message> newMessages = new ArrayList<>(
				List.of(new UserMessage("Message 1"), new AssistantMessage("Response 1")));
		customChatMemory.add(conversationId, newMessages);

		List<Message> result = customChatMemory.get(conversationId);

		assertThat(result).hasSize(2);
		assertThat(result).containsExactly(new SystemMessage("System 1"), new SystemMessage("System 2"));
	}

}
