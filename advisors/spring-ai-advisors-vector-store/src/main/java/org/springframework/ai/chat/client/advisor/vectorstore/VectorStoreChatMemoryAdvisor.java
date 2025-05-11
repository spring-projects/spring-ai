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

package org.springframework.ai.chat.client.advisor.vectorstore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Memory is retrieved from a VectorStore added into the prompt's system text.
 *
 * This only works for text based exchanges with the models, not multi-modal exchanges.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Oganes Bozoyan
 * @since 1.0.0
 */
public class VectorStoreChatMemoryAdvisor extends AbstractChatMemoryAdvisor<VectorStore> {

	public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";

	private static final String DOCUMENT_METADATA_CONVERSATION_ID = "conversationId";

	private static final String DOCUMENT_METADATA_MESSAGE_TYPE = "messageType";

	/**
	 * The default chat memory retrieve size to use when no retrieve size is provided.
	 */
	public static final int DEFAULT_CHAT_MEMORY_RESPONSE_SIZE = 100;

	private static final PromptTemplate DEFAULT_SYSTEM_PROMPT_TEMPLATE = new PromptTemplate("""
			{instructions}

			Use the long term conversation memory from the LONG_TERM_MEMORY section to provide accurate answers.

			---------------------
			LONG_TERM_MEMORY:
			{long_term_memory}
			---------------------
			""");

	private final PromptTemplate systemPromptTemplate;

	protected final int defaultChatMemoryRetrieveSize;

	public VectorStoreChatMemoryAdvisor(VectorStore chatMemory, String defaultConversationId,
			int defaultChatMemoryRetrieveSize, boolean protectFromBlocking, PromptTemplate systemPromptTemplate,
			int order) {
		super(chatMemory, defaultConversationId, protectFromBlocking, order);
		this.systemPromptTemplate = systemPromptTemplate;
		this.defaultChatMemoryRetrieveSize = defaultChatMemoryRetrieveSize;
	}

	public static Builder builder(VectorStore chatMemory) {
		return new Builder(chatMemory);
	}

	protected int doGetChatMemoryRetrieveSize(Map<String, Object> context) {
		return context.containsKey(CHAT_MEMORY_RETRIEVE_SIZE_KEY)
				? Integer.parseInt(context.get(CHAT_MEMORY_RETRIEVE_SIZE_KEY).toString())
				: this.defaultChatMemoryRetrieveSize;
	}

	@Override
	protected ChatClientRequest before(ChatClientRequest request, String conversationId) {
		String query = request.prompt().getUserMessage() != null ? request.prompt().getUserMessage().getText() : "";
		int topK = doGetChatMemoryRetrieveSize(request.context());
		String filter = DOCUMENT_METADATA_CONVERSATION_ID + "=='" + conversationId + "'";
		var searchRequest = org.springframework.ai.vectorstore.SearchRequest.builder()
			.query(query)
			.topK(topK)
			.filterExpression(filter)
			.build();
		java.util.List<org.springframework.ai.document.Document> documents = this.getChatMemoryStore()
			.similaritySearch(searchRequest);

		String longTermMemory = documents == null ? ""
				: documents.stream()
					.map(org.springframework.ai.document.Document::getText)
					.collect(java.util.stream.Collectors.joining(System.lineSeparator()));

		org.springframework.ai.chat.messages.SystemMessage systemMessage = request.prompt().getSystemMessage();
		String augmentedSystemText = this.systemPromptTemplate
			.render(java.util.Map.of("instructions", systemMessage.getText(), "long_term_memory", longTermMemory));

		ChatClientRequest processedChatClientRequest = request.mutate()
			.prompt(request.prompt().augmentSystemMessage(augmentedSystemText))
			.build();

		org.springframework.ai.chat.messages.UserMessage userMessage = processedChatClientRequest.prompt()
			.getUserMessage();
		if (userMessage != null) {
			this.getChatMemoryStore().write(toDocuments(java.util.List.of(userMessage), conversationId));
		}

		return processedChatClientRequest;
	}

	protected void after(ChatClientResponse chatClientResponse) {
		List<Message> assistantMessages = new ArrayList<>();
		if (chatClientResponse.chatResponse() != null) {
			assistantMessages = chatClientResponse.chatResponse()
				.getResults()
				.stream()
				.map(g -> (Message) g.getOutput())
				.toList();
		}
		this.getChatMemoryStore()
			.write(toDocuments(assistantMessages, this.doGetConversationId(chatClientResponse.context())));
	}

	private List<Document> toDocuments(List<Message> messages, String conversationId) {
		List<Document> docs = messages.stream()
			.filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
			.map(message -> {
				var metadata = new HashMap<>(message.getMetadata() != null ? message.getMetadata() : new HashMap<>());
				metadata.put(DOCUMENT_METADATA_CONVERSATION_ID, conversationId);
				metadata.put(DOCUMENT_METADATA_MESSAGE_TYPE, message.getMessageType().name());
				if (message instanceof UserMessage userMessage) {
					return Document.builder()
						.text(userMessage.getText())
						// userMessage.getMedia().get(0).getId()
						// TODO vector store for memory would not store this into the
						// vector store, could store an 'id' instead
						// .media(userMessage.getMedia())
						.metadata(metadata)
						.build();
				}
				else if (message instanceof AssistantMessage assistantMessage) {
					return Document.builder().text(assistantMessage.getText()).metadata(metadata).build();
				}
				throw new RuntimeException("Unknown message type: " + message.getMessageType());
			})
			.toList();

		return docs;
	}

	/**
	 * Builder for VectorStoreChatMemoryAdvisor.
	 */
	public static class Builder extends AbstractChatMemoryAdvisor.AbstractBuilder<VectorStore, Builder> {

		private PromptTemplate systemPromptTemplate = DEFAULT_SYSTEM_PROMPT_TEMPLATE;

		private Integer defaultChatMemoryRetrieveSize = null;

		/**
		 * Creates a new builder instance.
		 * @param vectorStore the vector store to use
		 */
		protected Builder(VectorStore vectorStore) {
			super(vectorStore);
		}

		/**
		 * Set the system prompt template.
		 * @param systemPromptTemplate the system prompt template
		 * @return this builder
		 */
		public Builder systemPromptTemplate(PromptTemplate systemPromptTemplate) {
			this.systemPromptTemplate = systemPromptTemplate;
			return this;
		}

		/**
		 * Set the system prompt template using a text template.
		 * @param systemTextAdvise the system prompt text template
		 * @return this builder
		 */
		public Builder systemTextAdvise(String systemTextAdvise) {
			this.systemPromptTemplate = new PromptTemplate(systemTextAdvise);
			return this;
		}

		/**
		 * Set the default chat memory retrieve size.
		 * @param defaultChatMemoryRetrieveSize the default chat memory retrieve size
		 * @return this builder
		 */
		public Builder defaultChatMemoryRetrieveSize(int defaultChatMemoryRetrieveSize) {
			this.defaultChatMemoryRetrieveSize = defaultChatMemoryRetrieveSize;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		@Override
		public VectorStoreChatMemoryAdvisor build() {
			if (defaultChatMemoryRetrieveSize == null) {
				// Default to legacy mode for backward compatibility
				return new VectorStoreChatMemoryAdvisor(this.chatMemory, this.conversationId,
						DEFAULT_CHAT_MEMORY_RESPONSE_SIZE, this.protectFromBlocking, this.systemPromptTemplate,
						this.order);
			}
			else {
				return new VectorStoreChatMemoryAdvisor(this.chatMemory, this.conversationId,
						this.defaultChatMemoryRetrieveSize, this.protectFromBlocking, this.systemPromptTemplate,
						this.order);
			}
		}

	}

}
