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
	void findConversationIds() {
		String conversationId1 = UUID.randomUUID().toString();
		String conversationId2 = UUID.randomUUID().toString();
		List<Message> messages1 = List.of(new UserMessage("Hello"));
		List<Message> messages2 = List.of(new AssistantMessage("Hi there"));

		chatMemoryRepository.saveAll(conversationId1, messages1);
		chatMemoryRepository.saveAll(conversationId2, messages2);

		assertThat(chatMemoryRepository.findConversationIds()).containsExactlyInAnyOrder(conversationId1,
				conversationId2);

		chatMemoryRepository.deleteByConversationId(conversationId1);
		assertThat(chatMemoryRepository.findConversationIds()).containsExactlyInAnyOrder(conversationId2);
	}

	@Test
	void saveMessagesAndFindMultipleMessagesInConversation() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> messages = List.of(new AssistantMessage("I, Robot"), new UserMessage("Hello"));

		chatMemoryRepository.saveAll(conversationId, messages);

		assertThat(chatMemoryRepository.findByConversationId(conversationId)).containsAll(messages);

		chatMemoryRepository.deleteByConversationId(conversationId);

		assertThat(chatMemoryRepository.findByConversationId(conversationId)).isEmpty();
	}

	@Test
	void saveMessagesAndFindSingleMessageInConversation() {
		String conversationId = UUID.randomUUID().toString();
		Message message = new UserMessage("Hello");
		List<Message> messages = List.of(message);

		chatMemoryRepository.saveAll(conversationId, messages);

		assertThat(chatMemoryRepository.findByConversationId(conversationId)).contains(message);

		chatMemoryRepository.deleteByConversationId(conversationId);

		assertThat(chatMemoryRepository.findByConversationId(conversationId)).isEmpty();
	}

	@Test
	void findNonExistingConversation() {
		String conversationId = UUID.randomUUID().toString();

		assertThat(chatMemoryRepository.findByConversationId(conversationId)).isEmpty();
	}

	@Test
	void subsequentSaveOverwritesPreviousVersion() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> firstMessages = List.of(new UserMessage("Hello"));
		List<Message> secondMessages = List.of(new AssistantMessage("Hi there"));

		chatMemoryRepository.saveAll(conversationId, firstMessages);
		chatMemoryRepository.saveAll(conversationId, secondMessages);

		assertThat(chatMemoryRepository.findByConversationId(conversationId)).containsExactlyElementsOf(secondMessages);
	}

	@Test
	void nullConversationIdNotAllowed() {
		assertThatThrownBy(() -> chatMemoryRepository.saveAll(null, List.of(new UserMessage("Hello"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> chatMemoryRepository.findByConversationId(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> chatMemoryRepository.deleteByConversationId(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");
	}

	@Test
	void emptyConversationIdNotAllowed() {
		assertThatThrownBy(() -> chatMemoryRepository.saveAll("", List.of(new UserMessage("Hello"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> chatMemoryRepository.findByConversationId(""))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> chatMemoryRepository.deleteByConversationId(""))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");
	}

	@Test
	void nullMessagesNotAllowed() {
		String conversationId = UUID.randomUUID().toString();
		assertThatThrownBy(() -> chatMemoryRepository.saveAll(conversationId, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages cannot be null");
	}

	@Test
	void messagesWithNullElementsNotAllowed() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> messagesWithNull = new ArrayList<>();
		messagesWithNull.add(null);

		assertThatThrownBy(() -> chatMemoryRepository.saveAll(conversationId, messagesWithNull))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages cannot contain null elements");
	}

}
