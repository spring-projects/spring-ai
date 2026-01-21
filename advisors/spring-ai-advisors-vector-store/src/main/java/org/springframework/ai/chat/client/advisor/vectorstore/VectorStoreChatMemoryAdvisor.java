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
import java.util.Objects;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.util.Assert;

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
public final class VectorStoreChatMemoryAdvisor implements BaseChatMemoryAdvisor {

	public static final String TOP_K = "chat_memory_vector_store_top_k";

	private static final String DOCUMENT_METADATA_CONVERSATION_ID = "conversationId";

	private static final String DOCUMENT_METADATA_MESSAGE_TYPE = "messageType";

	private static final int DEFAULT_TOP_K = 20;

	private static final PromptTemplate DEFAULT_SYSTEM_PROMPT_TEMPLATE = new PromptTemplate("""
			{instructions}

			Use the long term conversation memory from the LONG_TERM_MEMORY section to provide accurate answers.

			---------------------
			LONG_TERM_MEMORY:
			{long_term_memory}
			---------------------
			""");

	private final PromptTemplate systemPromptTemplate;

	private final int defaultTopK;

	private final String defaultConversationId;

	private final int order;

	private final Scheduler scheduler;

	private final VectorStore vectorStore;

	private VectorStoreChatMemoryAdvisor(PromptTemplate systemPromptTemplate, int defaultTopK,
			String defaultConversationId, int order, Scheduler scheduler, VectorStore vectorStore) {
		Assert.notNull(systemPromptTemplate, "systemPromptTemplate cannot be null");
		Assert.isTrue(defaultTopK > 0, "topK must be greater than 0");
		Assert.hasText(defaultConversationId, "defaultConversationId cannot be null or empty");
		Assert.notNull(scheduler, "scheduler cannot be null");
		Assert.notNull(vectorStore, "vectorStore cannot be null");
		this.systemPromptTemplate = systemPromptTemplate;
		this.defaultTopK = defaultTopK;
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
		return this.order;
	}

	@Override
	public Scheduler getScheduler() {
		return this.scheduler;
	}

	@Override
	public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
		String conversationId = getConversationId(request.context(), this.defaultConversationId);
		String query = Objects.requireNonNullElse(request.prompt().getUserMessage().getText(), "");
		int topK = getChatMemoryTopK(request.context());
		String filter = DOCUMENT_METADATA_CONVERSATION_ID + "=='" + conversationId + "'";
		SearchRequest searchRequest = SearchRequest.builder().query(query).topK(topK).filterExpression(filter).build();
		List<Document> documents = this.vectorStore.similaritySearch(searchRequest);

		String longTermMemory = documents == null ? ""
				: documents.stream().map(Document::getText).collect(Collectors.joining(System.lineSeparator()));

		SystemMessage systemMessage = request.prompt().getSystemMessage();
		String augmentedSystemText = this.systemPromptTemplate
			.render(Map.of("instructions", systemMessage.getText(), "long_term_memory", longTermMemory));

		ChatClientRequest processedChatClientRequest = request.mutate()
			.prompt(request.prompt().augmentSystemMessage(augmentedSystemText))
			.build();

		UserMessage userMessage = processedChatClientRequest.prompt().getUserMessage();
		if (userMessage != null) {
			this.vectorStore.write(toDocuments(List.of(userMessage), conversationId));
		}

		return processedChatClientRequest;
	}

	private int getChatMemoryTopK(Map<String, @Nullable Object> context) {
		Object fromCtx = context.get(TOP_K);
		if (fromCtx != null) {
			return Integer.parseInt(fromCtx.toString());
		}
		else {
			return this.defaultTopK;
		}
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
		this.vectorStore.write(toDocuments(assistantMessages,
				this.getConversationId(chatClientResponse.context(), this.defaultConversationId)));
		return chatClientResponse;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		// Get the scheduler from BaseAdvisor
		Scheduler scheduler = this.getScheduler();
		// Process the request with the before method
		return Mono.just(chatClientRequest)
			.publishOn(scheduler)
			.map(request -> this.before(request, streamAdvisorChain))
			.flatMapMany(streamAdvisorChain::nextStream)
			.transform(flux -> new ChatClientMessageAggregator().aggregateChatClientResponse(flux,
					response -> this.after(response, streamAdvisorChain)));
	}

	private List<Document> toDocuments(List<Message> messages, String conversationId) {
		return messages.stream()
			.filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
			.map(message -> {
				Map<String, Object> metadata = new HashMap<>(
						message.getMetadata() != null ? message.getMetadata() : new HashMap<>());
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
	}

	/**
	 * Builder for VectorStoreChatMemoryAdvisor.
	 */
	public static final class Builder {

		private PromptTemplate systemPromptTemplate = DEFAULT_SYSTEM_PROMPT_TEMPLATE;

		private Integer defaultTopK = DEFAULT_TOP_K;

		private String conversationId = ChatMemory.DEFAULT_CONVERSATION_ID;

		private Scheduler scheduler = BaseAdvisor.DEFAULT_SCHEDULER;

		private int order = Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER;

		private final VectorStore vectorStore;

		/**
		 * Creates a new builder instance.
		 * @param vectorStore the vector store to use
		 */
		Builder(VectorStore vectorStore) {
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
		 * @param defaultTopK the chat memory retrieve size
		 * @return this builder
		 */
		public Builder defaultTopK(int defaultTopK) {
			this.defaultTopK = defaultTopK;
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
			return new VectorStoreChatMemoryAdvisor(this.systemPromptTemplate, this.defaultTopK, this.conversationId,
					this.order, this.scheduler, this.vectorStore);
		}

	}

}
