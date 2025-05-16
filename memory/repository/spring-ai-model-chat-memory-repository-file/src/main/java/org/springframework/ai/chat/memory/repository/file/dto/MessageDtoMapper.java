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
package org.springframework.ai.chat.memory.repository.file.dto;

import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author John Dahle
 */
public final class MessageDtoMapper {

	private MessageDtoMapper() {
		/* no-op */ }

	public static MessageDto toDto(Message msg) {
		return switch (msg.getMessageType()) {
			case ASSISTANT -> new AssistantMessageDto(msg.getText());
			case USER -> new UserMessageDto(msg.getText());
			case SYSTEM -> new SystemMessageDto(msg.getText());
			default -> throw new IllegalArgumentException("Unsupported message type: " + msg.getMessageType());
		};
	}

	public static Message toDomain(MessageDto dto) {
		return dto.toDomain();
	}

	public static List<MessageDto> toDtoList(List<Message> messages) {
		return messages.stream().map(MessageDtoMapper::toDto).collect(Collectors.toList());
	}

	public static List<Message> toDomainList(List<MessageDto> dtos) {
		return dtos.stream().map(MessageDto::toDomain).collect(Collectors.toList());
	}

}
