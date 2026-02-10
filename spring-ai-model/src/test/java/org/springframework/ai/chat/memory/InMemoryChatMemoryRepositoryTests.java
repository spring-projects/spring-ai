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
import org.springframework.ai.chat.messages.UserMessage;

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

		this.chatMemoryRepository.saveAll(conversationId1, messages1);
		this.chatMemoryRepository.saveAll(conversationId2, messages2);

		assertThat(this.chatMemoryRepository.findConversationIds()).containsExactlyInAnyOrder(conversationId1,
				conversationId2);

		this.chatMemoryRepository.deleteByConversationId(conversationId1);
		assertThat(this.chatMemoryRepository.findConversationIds()).containsExactlyInAnyOrder(conversationId2);
	}

	@Test
	void saveMessagesAndFindMultipleMessagesInConversation() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> messages = List.of(new AssistantMessage("I, Robot"), new UserMessage("Hello"));

		this.chatMemoryRepository.saveAll(conversationId, messages);

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).containsAll(messages);

		this.chatMemoryRepository.deleteByConversationId(conversationId);

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).isEmpty();
	}

	@Test
	void saveMessagesAndFindSingleMessageInConversation() {
		String conversationId = UUID.randomUUID().toString();
		Message message = new UserMessage("Hello");
		List<Message> messages = List.of(message);

		this.chatMemoryRepository.saveAll(conversationId, messages);

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).contains(message);

		this.chatMemoryRepository.deleteByConversationId(conversationId);

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).isEmpty();
	}

	@Test
	void findNonExistingConversation() {
		String conversationId = UUID.randomUUID().toString();

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).isEmpty();
	}

	@Test
	void subsequentSaveOverwritesPreviousVersion() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> firstMessages = List.of(new UserMessage("Hello"));
		List<Message> secondMessages = List.of(new AssistantMessage("Hi there"));

		this.chatMemoryRepository.saveAll(conversationId, firstMessages);
		this.chatMemoryRepository.saveAll(conversationId, secondMessages);

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId))
			.containsExactlyElementsOf(secondMessages);
	}

	@Test
	void nullConversationIdNotAllowed() {
		assertThatThrownBy(() -> this.chatMemoryRepository.saveAll(null, List.of(new UserMessage("Hello"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> this.chatMemoryRepository.findByConversationId(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> this.chatMemoryRepository.deleteByConversationId(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");
	}

	@Test
	void emptyConversationIdNotAllowed() {
		assertThatThrownBy(() -> this.chatMemoryRepository.saveAll("", List.of(new UserMessage("Hello"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> this.chatMemoryRepository.findByConversationId(""))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");

		assertThatThrownBy(() -> this.chatMemoryRepository.deleteByConversationId(""))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("conversationId cannot be null or empty");
	}

	@Test
	void nullMessagesNotAllowed() {
		String conversationId = UUID.randomUUID().toString();
		assertThatThrownBy(() -> this.chatMemoryRepository.saveAll(conversationId, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages cannot be null");
	}

	@Test
	void messagesWithNullElementsNotAllowed() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> messagesWithNull = new ArrayList<>();
		messagesWithNull.add(null);

		assertThatThrownBy(() -> this.chatMemoryRepository.saveAll(conversationId, messagesWithNull))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages cannot contain null elements");
	}

	@Test
	void refreshConversation() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> initialMessages = List.of(new UserMessage("Hello"), new AssistantMessage("Hi"));
		this.chatMemoryRepository.saveAll(conversationId, initialMessages);

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).hasSize(2);

		List<Message> toDelete = List.of(new UserMessage("Hello"));
		List<Message> toAdd = List.of(new UserMessage("How are you?"), new AssistantMessage("I'm fine, thanks!"));

		this.chatMemoryRepository.refresh(conversationId, toDelete, toAdd);

		List<Message> updatedMessages = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(updatedMessages).hasSize(3);
		assertThat(updatedMessages).contains(new AssistantMessage("Hi"));
		assertThat(updatedMessages).contains(new UserMessage("How are you?"));
		assertThat(updatedMessages).contains(new AssistantMessage("I'm fine, thanks!"));
		assertThat(updatedMessages).doesNotContain(new UserMessage("Hello"));
	}

}
