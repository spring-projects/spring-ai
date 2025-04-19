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
 * Unit tests for {@link InMemoryChatMemoryRepository}.
 *
 * @author Thomas Vitale
 */
public class InMemoryChatMemoryRepositoryTests {

	private final InMemoryChatMemoryRepository chatMemoryRepository = new InMemoryChatMemoryRepository();

	@Test
	void saveAndFindMultipleMessagesInConversation() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> messages = List.of(new AssistantMessage("I, Robot"), new UserMessage("Hello"));

		chatMemoryRepository.save(conversationId, messages);

		assertThat(chatMemoryRepository.findById(conversationId)).containsAll(messages);

		chatMemoryRepository.deleteById(conversationId);

		assertThat(chatMemoryRepository.findById(conversationId)).isEmpty();
	}

	@Test
	void saveAndFindSingleMessageInConversation() {
		String conversationId = UUID.randomUUID().toString();
		Message message = new UserMessage("Hello");
		List<Message> messages = List.of(message);

		chatMemoryRepository.save(conversationId, messages);

		assertThat(chatMemoryRepository.findById(conversationId)).contains(message);

		chatMemoryRepository.deleteById(conversationId);

		assertThat(chatMemoryRepository.findById(conversationId)).isEmpty();
	}

	@Test
	void findNonExistingConversation() {
		String conversationId = UUID.randomUUID().toString();

		assertThat(chatMemoryRepository.findById(conversationId)).isEmpty();
	}

	@Test
	void saveMultipleMessagesForSameConversation() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> firstMessages = List.of(new UserMessage("Hello"));
		List<Message> secondMessages = List.of(new AssistantMessage("Hi there"));

		chatMemoryRepository.save(conversationId, firstMessages);
		chatMemoryRepository.save(conversationId, secondMessages);

		List<Message> allMessages = new ArrayList<>();
		allMessages.addAll(firstMessages);
		allMessages.addAll(secondMessages);

		assertThat(chatMemoryRepository.findById(conversationId)).containsExactlyElementsOf(allMessages);
	}

	@Test
	void nullConversationIdNotAllowed() {
		assertThatThrownBy(() -> chatMemoryRepository.save(null, List.of(new UserMessage("Hello"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> chatMemoryRepository.findById(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> chatMemoryRepository.deleteById(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");
	}

	@Test
	void emptyConversationIdNotAllowed() {
		assertThatThrownBy(() -> chatMemoryRepository.save("", List.of(new UserMessage("Hello"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> chatMemoryRepository.findById("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> chatMemoryRepository.deleteById("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");
	}

	@Test
	void nullMessagesNotAllowed() {
		String conversationId = UUID.randomUUID().toString();
		assertThatThrownBy(() -> chatMemoryRepository.save(conversationId, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages cannot be null");
	}

	@Test
	void messagesWithNullElementsNotAllowed() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> messagesWithNull = new ArrayList<>();
		messagesWithNull.add(null);

		assertThatThrownBy(() -> chatMemoryRepository.save(conversationId, messagesWithNull))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages cannot contain null elements");
	}

}
