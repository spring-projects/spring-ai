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
package org.springframework.ai.chat.memory.repository.file;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
/**
 * @author John Dahle
 */
@SpringBootTest(classes = FileChatMemoryRepositoryIT.TestConfig.class)
public class FileChatMemoryRepositoryIT {

	@TempDir
	static Path tempDir;

	@Autowired
	private ChatMemoryRepository chatMemoryRepository;

	@Test
	void correctChatMemoryRepositoryInstance() {
		assertThat(chatMemoryRepository).isInstanceOf(FileChatMemoryRepository.class);
	}

	@ParameterizedTest
	@CsvSource({ "Message from assistant,ASSISTANT", "Message from user,USER", "Message from system,SYSTEM" })
	void saveMessagesSingleMessage(String content, MessageType messageType) {
		String conversationId = UUID.randomUUID().toString();
		Message message = switch (messageType) {
			case ASSISTANT -> new AssistantMessage(content + " - " + conversationId);
			case USER -> new UserMessage(content + " - " + conversationId);
			case SYSTEM -> new SystemMessage(content + " - " + conversationId);
			default -> throw new IllegalArgumentException("Unsupported type: " + messageType);
		};

		chatMemoryRepository.saveAll(conversationId, List.of(message));
		List<Message> results = chatMemoryRepository.findByConversationId(conversationId);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getText()).isEqualTo(message.getText());
		assertThat(results.get(0).getMessageType()).isEqualTo(messageType);
	}

	@Test
	void deleteMessagesByConversationId() {
		String conversationId = UUID.randomUUID().toString();
		List<Message> messages = List.of(new AssistantMessage("Assistant"), new UserMessage("User"),
				new SystemMessage("System"));

		chatMemoryRepository.saveAll(conversationId, messages);
		assertThat(chatMemoryRepository.findByConversationId(conversationId)).hasSize(3);

		chatMemoryRepository.deleteByConversationId(conversationId);
		assertThat(chatMemoryRepository.findByConversationId(conversationId)).isEmpty();
	}

	@Configuration
	static class TestConfig {

		@Bean
		public ChatMemoryRepository chatMemoryRepository() throws Exception {
			ObjectMapper mapper = new ObjectMapper();

			// Attach @class metadata only for Message types
			mapper.addMixIn(Message.class, MessageMixin.class);

			// Register concrete Message subtypes
			mapper.registerSubtypes(AssistantMessage.class, UserMessage.class, SystemMessage.class);

			// Prepare the temporary directory
			Files.createDirectories(tempDir);
			return new FileChatMemoryRepository(tempDir, mapper);
		}

		@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
		private abstract static class MessageMixin {

		}

	}

}
