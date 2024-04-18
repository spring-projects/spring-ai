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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.chatbot.ChatBotResponse;
import org.springframework.ai.chat.chatbot.ChatAgentListener;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.transformer.TransformerContentType;
import org.springframework.ai.chat.prompt.transformer.PromptContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.util.CollectionUtils;

/**
 * @author Christian Tzolov
 */
public class VectorStoreChatMemoryAgentListener implements ChatAgentListener {

	private final VectorStore vectorStore;

	private final Map<String, Object> additionalMetadata;

	public VectorStoreChatMemoryAgentListener(VectorStore vectorStore) {
		this(vectorStore, new HashMap<>());
	}

	public VectorStoreChatMemoryAgentListener(VectorStore vectorStore, Map<String, Object> additionalMetadata) {
		this.vectorStore = vectorStore;
		this.additionalMetadata = additionalMetadata;
	}

	@Override
	public void onStart(PromptContext promptContext) {

		if (!CollectionUtils.isEmpty(promptContext.getPrompt().getInstructions())) {
			List<Document> docs = toDocuments(promptContext.getPrompt().getInstructions(),
					promptContext.getConversationId());

			this.vectorStore.add(docs);
		}
	}

	@Override
	public void onComplete(ChatBotResponse chatBotResponse) {
		if (!CollectionUtils.isEmpty(chatBotResponse.getChatResponse().getResults())) {
			List<Message> assistantMessages = chatBotResponse.getChatResponse()
				.getResults()
				.stream()
				.map(g -> (org.springframework.ai.chat.messages.Message) g.getOutput())
				.toList();

			List<Document> docs = toDocuments(assistantMessages,
					chatBotResponse.getPromptContext().getConversationId());

			this.vectorStore.add(docs);
		}
	}

	private List<Document> toDocuments(List<Message> messages, String conversationId) {

		List<Document> docs = messages.stream()
			.filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
			.map(message -> {
				var metadata = new HashMap<>(message.getMetadata() != null ? message.getMetadata() : new HashMap<>());
				metadata.putAll(this.additionalMetadata);
				metadata.put(TransformerContentType.CONVERSATION_ID, conversationId);
				metadata.put("messageType", message.getMessageType().name());
				metadata.put(TransformerContentType.MEMORY, true);
				metadata.put(TransformerContentType.LONG_TERM_MEMORY, true);
				var doc = new Document(message.getContent(), metadata);
				return doc;
			})
			.toList();

		return docs;

	}

}
