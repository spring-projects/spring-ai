/*
 * Copyright 2024-2024 the original author or authors.
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

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.transformer.AbstractPromptTransformer;
import org.springframework.ai.chat.prompt.transformer.ChatServiceContext;
import org.springframework.ai.chat.prompt.transformer.TransformerContentType;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.Content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Christian Tzolov
 */
public class ChatMemoryRetriever extends AbstractPromptTransformer {

	private final ChatMemory chatHistory;

	/**
	 * Additional metadata to be assigned to the retrieved history messages.
	 */
	private final Map<String, Object> metadata;

	private final int maxHistorySize;

	public ChatMemoryRetriever(ChatMemory chatHistory) {
		this(chatHistory, 1000, Map.of(), "ChatMemoryRetriever");
	}

	public ChatMemoryRetriever(ChatMemory chatHistory, int maxHistorySize, Map<String, Object> metadata, String name) {
		this.chatHistory = chatHistory;
		this.metadata = metadata;
		this.maxHistorySize = maxHistorySize;
		this.setName(name);
	}

	@Override
	public ChatServiceContext transform(ChatServiceContext chatServiceContext) {

		List<Message> messageHistory = this.chatHistory.get(chatServiceContext.getConversationId(), maxHistorySize);

		List<Content> historyContent = (messageHistory != null)
				? messageHistory.stream().filter(m -> m.getMessageType() != MessageType.SYSTEM).map(m -> {
					Content content = new Document(m.getContent(), new ArrayList<>(m.getMedia()),
							new HashMap<>(m.getMetadata()));
					content.getMetadata().putAll(this.metadata);
					content.getMetadata().put(TransformerContentType.MEMORY, true);
					return content;
				}).toList() : List.of();

		List<Content> updatedContents = new ArrayList<>(
				chatServiceContext.getContents() != null ? chatServiceContext.getContents() : List.of());
		updatedContents.addAll(historyContent);

		return ChatServiceContext.from(chatServiceContext).withContents(updatedContents).build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private ChatMemory chatHistory;

		private Map<String, Object> metadata = Map.of();

		private int maxHistorySize = 1000;

		private String name = "ChatMemoryRetriever";

		public Builder withChatHistory(ChatMemory chatHistory) {
			this.chatHistory = chatHistory;
			return this;
		}

		public Builder withMetadata(Map<String, Object> metadata) {
			this.metadata = metadata;
			return this;
		}

		public Builder withMaxHistorySize(int maxHistorySize) {
			this.maxHistorySize = maxHistorySize;
			return this;
		}

		public Builder withName(String name) {
			this.name = name;
			return this;
		}

		public ChatMemoryRetriever build() {
			return new ChatMemoryRetriever(this.chatHistory, this.maxHistorySize, this.metadata, this.name);
		}

	}

}
