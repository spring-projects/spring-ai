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

package org.springframework.ai.chat.memory.repository.arcadedb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ArcadeDBChatMemoryRepository}. Uses an embedded
 * ArcadeDB database — no Docker or external services required.
 *
 * @author Luca Garulli
 */
class ArcadeDBChatMemoryRepositoryIT {

	private Path tempDbPath;

	private ArcadeDBChatMemoryRepository repository;

	@BeforeEach
	void setUp() throws IOException {
		tempDbPath = Files.createTempDirectory("arcadedb-chatmemory-test");
		repository = ArcadeDBChatMemoryRepository.builder()
				.databasePath(tempDbPath.toString())
				.build();
	}

	@AfterEach
	void tearDown() throws IOException {
		if (repository != null) {
			repository.close();
		}
		if (tempDbPath != null) {
			deleteDirectory(tempDbPath);
		}
	}

	@Test
	void saveAndFindMessages() {
		List<Message> messages = List.of(
				new SystemMessage("You are a helpful assistant"),
				new UserMessage("Hello"),
				new AssistantMessage("Hi! How can I help you?"));

		repository.saveAll("conv-1", messages);

		List<Message> found = repository.findByConversationId("conv-1");
		assertThat(found).hasSize(3);
		assertThat(found.get(0).getMessageType())
				.isEqualTo(MessageType.SYSTEM);
		assertThat(found.get(0).getText())
				.isEqualTo("You are a helpful assistant");
		assertThat(found.get(1).getMessageType())
				.isEqualTo(MessageType.USER);
		assertThat(found.get(1).getText()).isEqualTo("Hello");
		assertThat(found.get(2).getMessageType())
				.isEqualTo(MessageType.ASSISTANT);
		assertThat(found.get(2).getText())
				.isEqualTo("Hi! How can I help you?");
	}

	@Test
	void findConversationIds() {
		repository.saveAll("conv-1",
				List.of(new UserMessage("Hello")));
		repository.saveAll("conv-2",
				List.of(new UserMessage("World")));

		List<String> ids = repository.findConversationIds();
		assertThat(ids).containsExactlyInAnyOrder("conv-1", "conv-2");
	}

	@Test
	void deleteByConversationId() {
		repository.saveAll("conv-1",
				List.of(new UserMessage("Hello")));
		repository.saveAll("conv-2",
				List.of(new UserMessage("World")));

		repository.deleteByConversationId("conv-1");

		assertThat(repository.findByConversationId("conv-1")).isEmpty();
		assertThat(repository.findByConversationId("conv-2")).hasSize(1);
		assertThat(repository.findConversationIds())
				.containsExactly("conv-2");
	}

	@Test
	void saveAllReplacesExistingMessages() {
		repository.saveAll("conv-1",
				List.of(new UserMessage("First message")));
		repository.saveAll("conv-1", List.of(
				new UserMessage("Replacement message"),
				new AssistantMessage("New response")));

		List<Message> found = repository.findByConversationId("conv-1");
		assertThat(found).hasSize(2);
		assertThat(found.get(0).getText())
				.isEqualTo("Replacement message");
		assertThat(found.get(1).getText()).isEqualTo("New response");
	}

	@Test
	void findByNonExistentConversationId() {
		assertThat(repository.findByConversationId("nonexistent"))
				.isEmpty();
	}

	@Test
	void deleteNonExistentConversation() {
		repository.deleteByConversationId("nonexistent");
	}

	@Test
	void messageOrderIsPreserved() {
		List<Message> messages = List.of(
				new UserMessage("First"),
				new AssistantMessage("Second"),
				new UserMessage("Third"),
				new AssistantMessage("Fourth"));

		repository.saveAll("conv-1", messages);

		List<Message> found = repository.findByConversationId("conv-1");
		assertThat(found).hasSize(4);
		assertThat(found.get(0).getText()).isEqualTo("First");
		assertThat(found.get(1).getText()).isEqualTo("Second");
		assertThat(found.get(2).getText()).isEqualTo("Third");
		assertThat(found.get(3).getText()).isEqualTo("Fourth");
	}

	@Test
	void getNativeClient() {
		assertThat(repository.getNativeClient()).isNotNull();
		assertThat(repository.getNativeClient().isOpen()).isTrue();
	}

	private static void deleteDirectory(Path path) throws IOException {
		if (Files.exists(path)) {
			Files.walk(path)
					.sorted(Comparator.reverseOrder())
					.forEach(p -> {
						try {
							Files.deleteIfExists(p);
						}
						catch (IOException ex) {
							// best effort cleanup
						}
					});
		}
	}

}
