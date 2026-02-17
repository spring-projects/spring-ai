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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MessageWindowChatMemory}.
 *
 * @author Thomas Vitale
 */
public class MessageWindowChatMemoryTests {

	private final MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder().build();

	@Test
	void zeroMaxMessagesNotAllowed() {
		assertThatThrownBy(() -> MessageWindowChatMemory.builder().maxMessages(0).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("maxMessages must be greater than 0");
	}

	@Test
	void negativeMaxMessagesNotAllowed() {
		assertThatThrownBy(() -> MessageWindowChatMemory.builder().maxMessages(-1).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("maxMessages must be greater than 0");
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
	void customMaxMessages() {
		String conversationId = UUID.randomUUID().toString();
		int customMaxMessages = 2;

		MessageWindowChatMemory customChatMemory = MessageWindowChatMemory.builder()
			.maxMessages(customMaxMessages)
			.build();

		List<Message> messages = List.of(new UserMessage("Message 1"), new AssistantMessage("Response 1"),
				new UserMessage("Message 2"), new AssistantMessage("Response 2"), new UserMessage("Message 3"));

		customChatMemory.add(conversationId, messages);
		List<Message> result = customChatMemory.get(conversationId);

		assertThat(result).hasSize(2);
	}

	@Test
	void noEvictionWhenMessagesWithinLimit() {
		int limit = 3;
		MessageWindowChatMemory customChatMemory = MessageWindowChatMemory.builder().maxMessages(limit).build();

		String conversationId = UUID.randomUUID().toString();
		List<Message> memoryMessages = new ArrayList<>(
				List.of(new UserMessage("Hello"), new AssistantMessage("Hi there")));
		customChatMemory.add(conversationId, memoryMessages);

		List<Message> newMessages = new ArrayList<>(List.of(new UserMessage("How are you?")));
		customChatMemory.add(conversationId, newMessages);

		List<Message> result = customChatMemory.get(conversationId);

		assertThat(result).hasSize(limit);
		assertThat(result).containsExactly(new UserMessage("Hello"), new AssistantMessage("Hi there"),
				new UserMessage("How are you?"));
	}

	@Test
	void evictionWhenMessagesExceedLimit() {
		int limit = 2;
		MessageWindowChatMemory customChatMemory = MessageWindowChatMemory.builder().maxMessages(limit).build();

		String conversationId = UUID.randomUUID().toString();
		List<Message> memoryMessages = new ArrayList<>(
				List.of(new UserMessage("Message 1"), new AssistantMessage("Response 1")));
		customChatMemory.add(conversationId, memoryMessages);

		List<Message> newMessages = new ArrayList<>(
				List.of(new UserMessage("Message 2"), new AssistantMessage("Response 2")));
		customChatMemory.add(conversationId, newMessages);

		List<Message> result = customChatMemory.get(conversationId);

		assertThat(result).hasSize(limit);
		assertThat(result).containsExactly(new UserMessage("Message 2"), new AssistantMessage("Response 2"));
	}

	@Test
	void systemMessageIsPreservedDuringEviction() {
		int limit = 3;
		MessageWindowChatMemory customChatMemory = MessageWindowChatMemory.builder().maxMessages(limit).build();

		String conversationId = UUID.randomUUID().toString();
		List<Message> memoryMessages = new ArrayList<>(List.of(new SystemMessage("System instruction"),
				new UserMessage("Message 1"), new AssistantMessage("Response 1")));
		customChatMemory.add(conversationId, memoryMessages);

		List<Message> newMessages = new ArrayList<>(
				List.of(new UserMessage("Message 2"), new AssistantMessage("Response 2")));
		customChatMemory.add(conversationId, newMessages);

		List<Message> result = customChatMemory.get(conversationId);

		assertThat(result).hasSize(limit);
		assertThat(result).containsExactly(new SystemMessage("System instruction"), new UserMessage("Message 2"),
				new AssistantMessage("Response 2"));
	}

	@Test
	void multipleSystemMessagesArePreservedDuringEviction() {
		int limit = 3;
		MessageWindowChatMemory customChatMemory = MessageWindowChatMemory.builder().maxMessages(limit).build();

		String conversationId = UUID.randomUUID().toString();
		List<Message> memoryMessages = new ArrayList<>(
				List.of(new SystemMessage("System instruction 1"), new SystemMessage("System instruction 2"),
						new UserMessage("Message 1"), new AssistantMessage("Response 1")));
		customChatMemory.add(conversationId, memoryMessages);

		List<Message> newMessages = new ArrayList<>(
				List.of(new UserMessage("Message 2"), new AssistantMessage("Response 2")));
		customChatMemory.add(conversationId, newMessages);

		List<Message> result = customChatMemory.get(conversationId);

		assertThat(result).hasSize(limit);
		assertThat(result).containsExactly(new SystemMessage("System instruction 1"),
				new SystemMessage("System instruction 2"), new AssistantMessage("Response 2"));
	}

	@Test
	void emptyMessageList() {
		String conversationId = UUID.randomUUID().toString();

		List<Message> result = this.chatMemory.get(conversationId);

		assertThat(result).isEmpty();
	}

	@Test
	void oldSystemMessagesAreRemovedWhenNewOneAdded() {
		int limit = 2;
		MessageWindowChatMemory customChatMemory = MessageWindowChatMemory.builder().maxMessages(limit).build();

		String conversationId = UUID.randomUUID().toString();
		List<Message> memoryMessages = new ArrayList<>(
				List.of(new SystemMessage("System instruction 1"), new SystemMessage("System instruction 2")));
		customChatMemory.add(conversationId, memoryMessages);

		List<Message> newMessages = new ArrayList<>(List.of(new SystemMessage("System instruction 3")));
		customChatMemory.add(conversationId, newMessages);

		List<Message> result = customChatMemory.get(conversationId);

		assertThat(result).hasSize(1);
		assertThat(result).containsExactly(new SystemMessage("System instruction 3"));
	}

	@Test
	void mixedMessagesWithLimitEqualToSystemMessageCount() {
		int limit = 2;
		MessageWindowChatMemory customChatMemory = MessageWindowChatMemory.builder().maxMessages(limit).build();

		String conversationId = UUID.randomUUID().toString();
		List<Message> memoryMessages = new ArrayList<>(
				List.of(new SystemMessage("System instruction 1"), new SystemMessage("System instruction 2")));
		customChatMemory.add(conversationId, memoryMessages);

		List<Message> newMessages = new ArrayList<>(
				List.of(new UserMessage("Message 1"), new AssistantMessage("Response 1")));
		customChatMemory.add(conversationId, newMessages);

		List<Message> result = customChatMemory.get(conversationId);

		assertThat(result).hasSize(2);
		assertThat(result).containsExactly(new SystemMessage("System instruction 1"),
				new SystemMessage("System instruction 2"));
	}

	@Test
	void getConversationsReturnsAllConversationIds() {
		MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder().build();

		String conversationId1 = UUID.randomUUID().toString();
		String conversationId2 = UUID.randomUUID().toString();
		String conversationId3 = UUID.randomUUID().toString();

		chatMemory.add(conversationId1, new UserMessage("Hello from conversation 1"));
		chatMemory.add(conversationId2, new UserMessage("Hello from conversation 2"));
		chatMemory.add(conversationId3, new UserMessage("Hello from conversation 3"));

		List<String> conversations = chatMemory.getConversations();

		assertThat(conversations).contains(conversationId1, conversationId2, conversationId3);
		assertThat(conversations).hasSize(3);
	}

	@Test
	void getConversationsWithCustomRepository() {
		InMemoryChatMemoryRepository customRepository = new InMemoryChatMemoryRepository();
		MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
			.chatMemoryRepository(customRepository)
			.build();

		String conversationId1 = UUID.randomUUID().toString();
		String conversationId2 = UUID.randomUUID().toString();

		chatMemory.add(conversationId1, new UserMessage("Message in conversation 1"));
		chatMemory.add(conversationId2, new UserMessage("Message in conversation 2"));

		List<String> conversations = chatMemory.getConversations();

		assertThat(conversations).contains(conversationId1, conversationId2);
		assertThat(conversations).hasSize(2);
	}

	@Test
	void getConversationsReturnsEmptyListWhenNoConversations() {
		MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder().build();

		List<String> conversations = chatMemory.getConversations();

		assertThat(conversations).isEmpty();
	}

}
