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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.file.dto.MessageDto;
import org.springframework.ai.chat.memory.repository.file.dto.MessageDtoMapper;
import org.springframework.ai.chat.messages.Message;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
/**
 * @author John Dahle
 */
public class FileChatMemoryRepository implements ChatMemoryRepository {

	private final Path baseDir;

	private final ObjectMapper objectMapper;

	public FileChatMemoryRepository(Path baseDir, ObjectMapper objectMapper) {
		this.baseDir = baseDir;
		this.objectMapper = objectMapper;
		try {
			Files.createDirectories(baseDir);
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to create base directory: " + baseDir, e);
		}
	}

	private Path fileFor(String conversationId) {
		return baseDir.resolve(conversationId + ".json");
	}

	@Override
	public List<String> findConversationIds() {
		try (var stream = Files.list(baseDir)) {
			return stream.filter(p -> p.toString().endsWith(".json"))
				.map(p -> p.getFileName().toString().replaceFirst("\\.json$", ""))
				.collect(Collectors.toList());
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to list conversation IDs", e);
		}
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		Path file = fileFor(conversationId);
		if (!Files.exists(file)) {
			return Collections.emptyList();
		}
		try {
			// 1. Read DTOs from disk
			List<MessageDto> dtos = objectMapper.readValue(file.toFile(), new TypeReference<List<MessageDto>>() {
			});
			// 2. Map them back to domain Messages
			return MessageDtoMapper.toDomainList(dtos);
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to read messages for conversation: " + conversationId, e);
		}
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		try {
			// 1. Convert domain Messages into DTOs
			List<MessageDto> dtos = MessageDtoMapper.toDtoList(messages);
			// 2. Tell Jackson they’re DTOs and write them
			objectMapper.writerFor(new TypeReference<List<MessageDto>>() {
			}).writeValue(fileFor(conversationId).toFile(), dtos);
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to write messages for conversation: " + conversationId, e);
		}
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		try {
			Files.deleteIfExists(fileFor(conversationId));
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to delete conversation: " + conversationId, e);
		}
	}

}
