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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
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
 * @author Mark Pollack
 * @since 1.0.0
 */
public class VectorStoreChatMemoryAdvisor implements BaseChatMemoryAdvisor {

	public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";

	private static final String DOCUMENT_METADATA_CONVERSATION_ID = "conversationId";

	private static final String DOCUMENT_METADATA_MESSAGE_TYPE = "messageType";

	/**
	 * The default chat memory retrieve size to use when no retrieve size is provided.
	 */
	public static final int DEFAULT_TOP_K = 20;

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

	private final String defaultConversationId;

	private final int order;

	private final Scheduler scheduler;

	private VectorStore vectorStore;

	public VectorStoreChatMemoryAdvisor(PromptTemplate systemPromptTemplate, int defaultChatMemoryRetrieveSize,
			String defaultConversationId, int order, Scheduler scheduler, VectorStore vectorStore) {
		this.systemPromptTemplate = systemPromptTemplate;
		this.defaultChatMemoryRetrieveSize = defaultChatMemoryRetrieveSize;
		this.defaultConversationId = defaultConversationId;
		this.order = order;
		this.scheduler = scheduler;
		this.vectorStore = vectorStore;
	}

	public static Builder builder(VectorStore chatMemory) {
		return new Builder(chatMemory);
	}

	@Override
	public int getOrder() {
		return order;
	}

	@Override
	public Scheduler getScheduler() {
		return this.scheduler;
	}

	@Override
	public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
		String conversationId = getConversationId(request.context());
		String query = request.prompt().getUserMessage() != null ? request.prompt().getUserMessage().getText() : "";
		int topK = getChatMemoryTopK(request.context());
		String filter = DOCUMENT_METADATA_CONVERSATION_ID + "=='" + conversationId + "'";
		var searchRequest = org.springframework.ai.vectorstore.SearchRequest.builder()
			.query(query)
			.topK(topK)
			.filterExpression(filter)
			.build();
		java.util.List<org.springframework.ai.document.Document> documents = this.vectorStore
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
			this.vectorStore.write(toDocuments(java.util.List.of(userMessage), conversationId));
		}

		return processedChatClientRequest;
	}

	private int getChatMemoryTopK(Map<String, Object> context) {
		return context.containsKey(CHAT_MEMORY_RETRIEVE_SIZE_KEY)
				? Integer.parseInt(context.get(CHAT_MEMORY_RETRIEVE_SIZE_KEY).toString())
				: this.defaultChatMemoryRetrieveSize;
	}

	@Override
	public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
		List<Message> assistantMessages = new ArrayList<>();
		if (chatClientResponse.chatResponse() != null) {
			assistantMessages = chatClientResponse.chatResponse()
				.getResults()
				.stream()
				.map(g -> (Message) g.getOutput())
				.toList();
		}
		this.vectorStore.write(toDocuments(assistantMessages, this.getConversationId(chatClientResponse.context())));
		return chatClientResponse;
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
	public static class Builder {

		private PromptTemplate systemPromptTemplate = DEFAULT_SYSTEM_PROMPT_TEMPLATE;

		private Integer topK = DEFAULT_TOP_K;

		private String conversationId = ChatMemory.DEFAULT_CONVERSATION_ID;

		private Scheduler scheduler;

		private int order = Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER;

		private VectorStore vectorStore;

		/**
		 * Creates a new builder instance.
		 * @param vectorStore the vector store to use
		 */
		protected Builder(VectorStore vectorStore) {
			this.vectorStore = vectorStore;
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
		 * Set the chat memory retrieve size.
		 * @param topK the chat memory retrieve size
		 * @return this builder
		 */
		public Builder topK(int topK) {
			this.topK = topK;
			return this;
		}

		/**
		 * Set the conversation id.
		 * @param conversationId the conversation id
		 * @return the builder
		 */
		public Builder conversationId(String conversationId) {
			this.conversationId = conversationId;
			return this;
		}

		/**
		 * Set whether to protect from blocking.
		 * @param protectFromBlocking whether to protect from blocking
		 * @return the builder
		 */
		public Builder protectFromBlocking(boolean protectFromBlocking) {
			this.scheduler = protectFromBlocking ? BaseAdvisor.DEFAULT_SCHEDULER : Schedulers.immediate();
			return this;
		}

		public Builder scheduler(Scheduler scheduler) {
			this.scheduler = scheduler;
			return this;
		}

		/**
		 * Set the order.
		 * @param order the order
		 * @return the builder
		 */
		public Builder order(int order) {
			this.order = order;
			return this;
		}

		/**
		 * Build the advisor.
		 * @return the advisor
		 */
		public VectorStoreChatMemoryAdvisor build() {
			return new VectorStoreChatMemoryAdvisor(this.systemPromptTemplate, this.topK, this.conversationId,
					this.order, this.scheduler, this.vectorStore);
		}

	}

}
