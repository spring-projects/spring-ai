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

package org.springframework.ai.chat.history;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.transformer.PromptContext;
import org.springframework.ai.chat.prompt.transformer.PromptTransformer;
import org.springframework.ai.chat.prompt.transformer.TransformerContentType;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.Content;

/**
 * @author Christian Tzolov
 */
public class ChatMemoryRetriever implements PromptTransformer {

	private final ChatMemory chatHistory;

	/**
	 * Additional metadata to be assigned to the retrieved history messages.
	 */
	private final Map<String, Object> additionalMetadata;

	private final int maxHistorySize;

	public ChatMemoryRetriever(ChatMemory chatHistory) {
		this(chatHistory, Map.of());
	}

	public ChatMemoryRetriever(ChatMemory chatHistory, Map<String, Object> additionalMetadata) {
		this(chatHistory, 1000, additionalMetadata);
	}

	public ChatMemoryRetriever(ChatMemory chatHistory, int maxHistorySize, Map<String, Object> additionalMetadata) {
		this.chatHistory = chatHistory;
		this.additionalMetadata = additionalMetadata;
		this.maxHistorySize = maxHistorySize;
	}

	@Override
	public PromptContext transform(PromptContext promptContext) {

		List<Message> messageHistory = this.chatHistory.get(promptContext.getConversationId(), maxHistorySize);

		List<Content> historyContent = (messageHistory != null)
				? messageHistory.stream().filter(m -> m.getMessageType() != MessageType.SYSTEM).map(m -> {
					Content content = new Document(m.getContent(), new ArrayList<>(m.getMedia()),
							new HashMap<>(m.getMetadata()));
					content.getMetadata().putAll(this.additionalMetadata);
					content.getMetadata().put(TransformerContentType.MEMORY, true);
					return content;
				}).toList() : List.of();

		List<Content> updatedContents = new ArrayList<>(
				promptContext.getContents() != null ? promptContext.getContents() : List.of());
		updatedContents.addAll(historyContent);

		return PromptContext.from(promptContext).withContents(updatedContents).build();
	}

}
