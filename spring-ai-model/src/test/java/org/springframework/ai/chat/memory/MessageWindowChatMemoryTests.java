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
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
	void handleMultipleMessagesInConversation() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> messages = List.of(new AssistantMessage("I, Robot"), new UserMessage("Hello"));

		chatMemory.add(conversationId, messages);

		assertThat(chatMemory.get(conversationId)).containsAll(messages);

		chatMemory.clear(conversationId);

		assertThat(chatMemory.get(conversationId)).isEmpty();
	}

	@Test
	void handleSingleMessageInConversation() {
		String conversationId = UUID.randomUUID().toString();
		Message message = new UserMessage("Hello");

		chatMemory.add(conversationId, message);

		assertThat(chatMemory.get(conversationId)).contains(message);

		chatMemory.clear(conversationId);

		assertThat(chatMemory.get(conversationId)).isEmpty();
	}

	@Test
	void nullConversationIdNotAllowed() {
		assertThatThrownBy(() -> chatMemory.add(null, List.of(new UserMessage("Hello"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> chatMemory.add(null, new UserMessage("Hello")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> chatMemory.get(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> chatMemory.clear(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");
	}

	@Test
	void emptyConversationIdNotAllowed() {
		assertThatThrownBy(() -> chatMemory.add("", List.of(new UserMessage("Hello"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> chatMemory.add(null, new UserMessage("Hello")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> chatMemory.get("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> chatMemory.clear("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");
	}

	@Test
	void nullMessagesNotAllowed() {
		String conversationId = UUID.randomUUID().toString();
		assertThatThrownBy(() -> chatMemory.add(conversationId, (List<Message>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages cannot be null");
	}

	@Test
	void nullMessageNotAllowed() {
		String conversationId = UUID.randomUUID().toString();
		assertThatThrownBy(() -> chatMemory.add(conversationId, (Message) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages cannot contain null elements");
	}

	@Test
	void messagesWithNullElementsNotAllowed() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> messagesWithNull = new ArrayList<>();
		messagesWithNull.add(null);

		assertThatThrownBy(() -> chatMemory.add(conversationId, messagesWithNull))
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

}
