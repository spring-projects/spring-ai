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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;

/**
 * A file-based implementation of {@link ChatMemoryRepository} that stores chat messages
 * as JSON files on the local filesystem. Each conversation is stored in a separate file
 * named {@code {conversationId}.json}.
 *
 * <p>
 * This implementation is useful for local development, testing, and simple applications
 * that require persistent chat memory without setting up a database.
 *
 * @author Ranjit Muniyappa
 * @since 1.0.0
 */
public final class FileChatMemoryRepository implements ChatMemoryRepository {

	private static final Logger logger = LoggerFactory.getLogger(FileChatMemoryRepository.class);

	private static final String FILE_EXTENSION = ".json";

	private static final Path DEFAULT_DIRECTORY = Path.of("chat-memory");

	private final Path directory;

	private final ObjectMapper objectMapper;

	/**
	 * Creates a new {@link FileChatMemoryRepository} with the default directory
	 * {@code chat-memory}.
	 */
	public FileChatMemoryRepository() {
		this(DEFAULT_DIRECTORY);
	}

	/**
	 * Creates a new {@link FileChatMemoryRepository} with the specified directory.
	 * @param directory the directory where conversation files will be stored
	 */
	public FileChatMemoryRepository(Path directory) {
		Assert.notNull(directory, "directory cannot be null");
		this.directory = directory;
		this.objectMapper = new ObjectMapper();
		ensureDirectoryExists();
	}

	private void ensureDirectoryExists() {
		try {
			Files.createDirectories(this.directory);
		}
		catch (IOException ex) {
			throw new RuntimeException("Failed to create chat memory directory: " + this.directory, ex);
		}
	}

	@Override
	public List<String> findConversationIds() {
		try (Stream<Path> files = Files.list(this.directory)) {
			return files.filter(path -> path.toString().endsWith(FILE_EXTENSION))
				.map(path -> path.getFileName().toString().replace(FILE_EXTENSION, ""))
				.toList();
		}
		catch (IOException ex) {
			logger.warn("Failed to list conversation files in directory: {}", this.directory, ex);
			return List.of();
		}
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Path filePath = getFilePath(conversationId);
		if (!Files.exists(filePath)) {
			return List.of();
		}
		try {
			String json = Files.readString(filePath);
			List<StoredMessage> storedMessages = this.objectMapper.readValue(json,
					new TypeReference<List<StoredMessage>>() {
					});
			return storedMessages.stream().map(this::toMessage).toList();
		}
		catch (IOException ex) {
			logger.warn("Failed to read conversation file: {}", filePath, ex);
			return List.of();
		}
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Assert.notNull(messages, "messages cannot be null");
		Assert.noNullElements(messages, "messages cannot contain null elements");
		Path filePath = getFilePath(conversationId);
		try {
			List<StoredMessage> storedMessages = messages.stream().map(this::toStoredMessage).toList();
			String json = this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(storedMessages);
			Files.writeString(filePath, json);
			logger.debug("Saved {} messages to conversation: {}", messages.size(), conversationId);
		}
		catch (IOException ex) {
			throw new RuntimeException("Failed to save conversation: " + conversationId, ex);
		}
	}

	private StoredMessage toStoredMessage(Message message) {
		return new StoredMessage(message.getMessageType().name(), message.getText(), message.getMetadata());
	}

	private Message toMessage(StoredMessage stored) {
		MessageType type = MessageType.valueOf(stored.type());
		return switch (type) {
			case USER -> UserMessage.builder().text(stored.content()).metadata(stored.metadata()).build();
			case ASSISTANT ->
				AssistantMessage.builder().content(stored.content()).properties(stored.metadata()).build();
			case SYSTEM -> SystemMessage.builder().text(stored.content()).metadata(stored.metadata()).build();
			case TOOL -> ToolResponseMessage.builder().build();
		};
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		Assert.hasText(conversationId, "conversationId cannot be null or empty");
		Path filePath = getFilePath(conversationId);
		try {
			Files.deleteIfExists(filePath);
			logger.debug("Deleted conversation: {}", conversationId);
		}
		catch (IOException ex) {
			logger.warn("Failed to delete conversation file: {}", filePath, ex);
		}
	}

	private Path getFilePath(String conversationId) {
		return this.directory.resolve(conversationId + FILE_EXTENSION);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private Path directory = DEFAULT_DIRECTORY;

		private Builder() {
		}

		public Builder directory(Path directory) {
			this.directory = directory;
			return this;
		}

		public Builder directory(String directory) {
			this.directory = Path.of(directory);
			return this;
		}

		public FileChatMemoryRepository build() {
			return new FileChatMemoryRepository(this.directory);
		}

	}

	/**
	 * Internal record for JSON serialization of messages.
	 */
	private record StoredMessage(String type, String content, Map<String, Object> metadata) {

	}

}
