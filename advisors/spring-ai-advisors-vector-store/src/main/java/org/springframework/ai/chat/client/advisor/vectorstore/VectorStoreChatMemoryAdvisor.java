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
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
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

	private static final String DOCUMENT_METADATA_CONVERSATION_ID = "conversationId";

	private static final String DOCUMENT_METADATA_MESSAGE_TYPE = "messageType";

	private static final PromptTemplate DEFAULT_SYSTEM_PROMPT_TEMPLATE = new PromptTemplate("""
			{instructions}

			Use the long term conversation memory from the LONG_TERM_MEMORY section to provide accurate answers.

			---------------------
			LONG_TERM_MEMORY:
			{long_term_memory}
			---------------------
			""");

	private final PromptTemplate systemPromptTemplate;

	private VectorStoreChatMemoryAdvisor(VectorStore vectorStore, String defaultConversationId,
			int chatHistoryWindowSize, boolean protectFromBlocking, PromptTemplate systemPromptTemplate, int order) {
		super(vectorStore, defaultConversationId, chatHistoryWindowSize, protectFromBlocking, order);
		this.systemPromptTemplate = systemPromptTemplate;
	}

	public static Builder builder(VectorStore chatMemory) {
		return new Builder(chatMemory);
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		chatClientRequest = this.before(chatClientRequest);

		ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);

		this.after(chatClientResponse);

		return chatClientResponse;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		Flux<ChatClientResponse> chatClientResponses = this.doNextWithProtectFromBlockingBefore(chatClientRequest,
				streamAdvisorChain, this::before);

		return new MessageAggregator().aggregateChatClientResponse(chatClientResponses, this::after);
	}

	private ChatClientRequest before(ChatClientRequest chatClientRequest) {
		String conversationId = this.doGetConversationId(chatClientRequest.context());
		int chatMemoryRetrieveSize = this.doGetChatMemoryRetrieveSize(chatClientRequest.context());

		// 1. Retrieve the chat memory for the current conversation.
		var searchRequest = SearchRequest.builder()
			.query(chatClientRequest.prompt().getUserMessage().getText())
			.topK(chatMemoryRetrieveSize)
			.filterExpression(DOCUMENT_METADATA_CONVERSATION_ID + "=='" + conversationId + "'")
			.build();

		List<Document> documents = this.getChatMemoryStore().similaritySearch(searchRequest);

		// 2. Processed memory messages as a string.
		String longTermMemory = documents == null ? ""
				: documents.stream().map(Document::getText).collect(Collectors.joining(System.lineSeparator()));

		// 2. Augment the system message.
		SystemMessage systemMessage = chatClientRequest.prompt().getSystemMessage();
		String augmentedSystemText = this.systemPromptTemplate
			.render(Map.of("instructions", systemMessage.getText(), "long_term_memory", longTermMemory));

		// 3. Create a new request with the augmented system message.
		ChatClientRequest processedChatClientRequest = chatClientRequest.mutate()
			.prompt(chatClientRequest.prompt().augmentSystemMessage(augmentedSystemText))
			.build();

		// 4. Add the new user message to the conversation memory.
		UserMessage userMessage = processedChatClientRequest.prompt().getUserMessage();
		this.getChatMemoryStore().write(toDocuments(List.of(userMessage), conversationId));

		return processedChatClientRequest;
	}

	private void after(ChatClientResponse chatClientResponse) {
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

	public static class Builder extends AbstractChatMemoryAdvisor.AbstractBuilder<VectorStore> {

		private PromptTemplate systemPromptTemplate = DEFAULT_SYSTEM_PROMPT_TEMPLATE;

		protected Builder(VectorStore chatMemory) {
			super(chatMemory);
		}

		public Builder systemTextAdvise(String systemTextAdvise) {
			this.systemPromptTemplate = new PromptTemplate(systemTextAdvise);
			return this;
		}

		public Builder systemPromptTemplate(PromptTemplate systemPromptTemplate) {
			this.systemPromptTemplate = systemPromptTemplate;
			return this;
		}

		@Override
		public VectorStoreChatMemoryAdvisor build() {
			return new VectorStoreChatMemoryAdvisor(this.chatMemory, this.conversationId, this.chatMemoryRetrieveSize,
					this.protectFromBlocking, this.systemPromptTemplate, this.order);
		}

	}

}
