/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.chat;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.ai.model.ModelResult;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.lang.Nullable;

/**
 * Represents a response returned by the AI.
 */
public class Generation implements ModelResult<AssistantMessage> {

	private final AssistantMessage assistantMessage;

	private final ChatGenerationMetadata chatGenerationMetadata;

	private final String id;

	private final Integer index;

	private final boolean isCompleted;

	public Generation(String text) {
		this(text, new HashMap<>(), null);
	}

	public Generation(String text, @Nullable ChatGenerationMetadata chatGenerationMetadata) {
		this(text, new HashMap<>(), chatGenerationMetadata);
	}

	public Generation(String text, Map<String, Object> properties) {
		this(text, properties, null);
	}

	public Generation(String text, Map<String, Object> properties,
			@Nullable ChatGenerationMetadata chatGenerationMetadata) {
		this(UUID.randomUUID().toString(), 0, true, text, properties, chatGenerationMetadata);
	}

	public Generation(String id, Integer index, boolean isCompleted, String text, Map<String, Object> properties,
			@Nullable ChatGenerationMetadata chatGenerationMetadata) {

		this.id = id;
		this.index = index;
		this.isCompleted = isCompleted;

		Map<String, Object> newProperties = new HashMap<>(properties);
		if (chatGenerationMetadata != null) {
			this.chatGenerationMetadata = chatGenerationMetadata;
			newProperties.put("finishReason", chatGenerationMetadata.getFinishReason());
			newProperties.put("index", index);
			newProperties.put("isCompleted", isCompleted);
		}
		else {
			this.chatGenerationMetadata = ChatGenerationMetadata.NULL;
		}

		this.assistantMessage = new AssistantMessage(id, index, isCompleted, text, newProperties);
	}

	public String getId() {
		return this.id;
	}

	public Integer getIndex() {
		return this.index;
	}

	public boolean isCompleted() {
		return this.isCompleted;
	}

	@Override
	public AssistantMessage getOutput() {
		return this.assistantMessage;
	}

	@Override
	public ChatGenerationMetadata getMetadata() {
		ChatGenerationMetadata chatGenerationMetadata = this.chatGenerationMetadata;
		return chatGenerationMetadata != null ? chatGenerationMetadata : ChatGenerationMetadata.NULL;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Generation that))
			return false;
		return Objects.equals(assistantMessage, that.assistantMessage)
				&& Objects.equals(chatGenerationMetadata, that.chatGenerationMetadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(assistantMessage, chatGenerationMetadata);
	}

	@Override
	public String toString() {
		return "Generation [assistantMessage=" + assistantMessage + ", chatGenerationMetadata=" + chatGenerationMetadata
				+ ", id=" + id + ", index=" + index + ", isCompleted=" + isCompleted + "]";
	}

}
