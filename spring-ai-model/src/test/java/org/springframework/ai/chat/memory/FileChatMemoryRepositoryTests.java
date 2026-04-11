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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link FileChatMemoryRepository}.
 *
 * @author Ranjit Muniyappa
 */
class FileChatMemoryRepositoryTests {

	@TempDir
	Path tempDir;

	private FileChatMemoryRepository chatMemoryRepository;

	@BeforeEach
	void setUp() {
		this.chatMemoryRepository = FileChatMemoryRepository.builder().directory(this.tempDir).build();
	}

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

		List<Message> retrieved = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(retrieved).hasSize(2);
		assertThat(retrieved.get(0).getText()).isEqualTo("I, Robot");
		assertThat(retrieved.get(1).getText()).isEqualTo("Hello");

		this.chatMemoryRepository.deleteByConversationId(conversationId);

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).isEmpty();
	}

	@Test
	void saveMessagesAndFindSingleMessageInConversation() {
		String conversationId = UUID.randomUUID().toString();
		Message message = new UserMessage("Hello");
		List<Message> messages = List.of(message);

		this.chatMemoryRepository.saveAll(conversationId, messages);

		List<Message> retrieved = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(retrieved).hasSize(1);
		assertThat(retrieved.get(0).getText()).isEqualTo("Hello");

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

		List<Message> retrieved = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(retrieved).hasSize(1);
		assertThat(retrieved.get(0).getText()).isEqualTo("Hi there");
	}

	@Test
	void saveAllMessageTypes() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> messages = List.of(new SystemMessage("You are a helpful assistant"),
				new UserMessage("What is Java?"), new AssistantMessage("Java is a programming language"));

		this.chatMemoryRepository.saveAll(conversationId, messages);

		List<Message> retrieved = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(retrieved).hasSize(3);
		assertThat(retrieved.get(0)).isInstanceOf(SystemMessage.class);
		assertThat(retrieved.get(1)).isInstanceOf(UserMessage.class);
		assertThat(retrieved.get(2)).isInstanceOf(AssistantMessage.class);
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
	void builderWithStringPath() {
		Path customDir = this.tempDir.resolve("custom-memory");
		FileChatMemoryRepository repo = FileChatMemoryRepository.builder().directory(customDir.toString()).build();

		String conversationId = UUID.randomUUID().toString();
		repo.saveAll(conversationId, List.of(new UserMessage("Test")));

		assertThat(Files.exists(customDir.resolve(conversationId + ".json"))).isTrue();
	}

}
