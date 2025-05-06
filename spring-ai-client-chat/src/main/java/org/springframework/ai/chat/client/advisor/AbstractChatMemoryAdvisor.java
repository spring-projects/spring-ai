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

package org.springframework.ai.chat.client.advisor;

import java.util.Map;
import java.util.function.Function;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.util.Assert;

/**
 * Abstract class that serves as a base for chat memory advisors.
 *
 * @param <T> the type of the chat memory.
 * @author Christian Tzolov
 * @author Ilayaperumal Gopinathan
 * @author Thomas Vitale
 * @since 1.0.0
 */
public abstract class AbstractChatMemoryAdvisor<T> implements CallAdvisor, StreamAdvisor {

	/**
	 * The key to retrieve the chat memory conversation id from the context.
	 */
	public static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";

	/**
	 * The key to retrieve the chat memory response size from the context.
	 */
	public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";

	/**
	 * The default chat memory retrieve size to use when no retrieve size is provided.
	 */
	public static final int DEFAULT_CHAT_MEMORY_RESPONSE_SIZE = 100;

	/**
	 * The chat memory store.
	 */
	protected final T chatMemoryStore;

	/**
	 * The default conversation id.
	 */
	protected final String defaultConversationId;

	/**
	 * The default chat memory retrieve size.
	 */
	protected final int defaultChatMemoryRetrieveSize;

	/**
	 * Whether to protect from blocking.
	 */
	private final boolean protectFromBlocking;

	/**
	 * The order of the advisor.
	 */
	private final int order;

	/**
	 * Constructor to create a new {@link AbstractChatMemoryAdvisor} instance.
	 * @param chatMemory the chat memory store
	 */
	protected AbstractChatMemoryAdvisor(T chatMemory) {
		this(chatMemory, ChatMemory.DEFAULT_CONVERSATION_ID, DEFAULT_CHAT_MEMORY_RESPONSE_SIZE, true);
	}

	/**
	 * Constructor to create a new {@link AbstractChatMemoryAdvisor} instance.
	 * @param chatMemory the chat memory store
	 * @param defaultConversationId the default conversation id
	 * @param defaultChatMemoryRetrieveSize the default chat memory retrieve size
	 * @param protectFromBlocking whether to protect from blocking
	 */
	protected AbstractChatMemoryAdvisor(T chatMemory, String defaultConversationId, int defaultChatMemoryRetrieveSize,
			boolean protectFromBlocking) {
		this(chatMemory, defaultConversationId, defaultChatMemoryRetrieveSize, protectFromBlocking,
				Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER);
	}

	/**
	 * Constructor to create a new {@link AbstractChatMemoryAdvisor} instance.
	 * @param chatMemory the chat memory store
	 * @param defaultConversationId the default conversation id
	 * @param defaultChatMemoryRetrieveSize the default chat memory retrieve size
	 * @param protectFromBlocking whether to protect from blocking
	 * @param order the order
	 */
	protected AbstractChatMemoryAdvisor(T chatMemory, String defaultConversationId, int defaultChatMemoryRetrieveSize,
			boolean protectFromBlocking, int order) {

		Assert.notNull(chatMemory, "The chatMemory must not be null!");
		Assert.hasText(defaultConversationId, "The conversationId must not be empty!");
		Assert.isTrue(defaultChatMemoryRetrieveSize > 0, "The defaultChatMemoryRetrieveSize must be greater than 0!");

		this.chatMemoryStore = chatMemory;
		this.defaultConversationId = defaultConversationId;
		this.defaultChatMemoryRetrieveSize = defaultChatMemoryRetrieveSize;
		this.protectFromBlocking = protectFromBlocking;
		this.order = order;
	}

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		// by default the (Ordered.HIGHEST_PRECEDENCE + 1000) value ensures this order has
		// lower priority (e.g. precedences) than the internal Spring AI advisors. It
		// leaves room (1000 slots) for the user to plug in their own advisors with higher
		// priority.
		return this.order;
	}

	/**
	 * Get the chat memory store.
	 * @return the chat memory store
	 */
	protected T getChatMemoryStore() {
		return this.chatMemoryStore;
	}

	/**
	 * Get the default conversation id.
	 * @param context the context
	 * @return the default conversation id
	 */
	protected String doGetConversationId(Map<String, Object> context) {

		return context.containsKey(CHAT_MEMORY_CONVERSATION_ID_KEY)
				? context.get(CHAT_MEMORY_CONVERSATION_ID_KEY).toString() : this.defaultConversationId;
	}

	/**
	 * Get the default chat memory retrieve size.
	 * @param context the context
	 * @return the default chat memory retrieve size
	 */
	protected int doGetChatMemoryRetrieveSize(Map<String, Object> context) {
		return context.containsKey(CHAT_MEMORY_RETRIEVE_SIZE_KEY)
				? Integer.parseInt(context.get(CHAT_MEMORY_RETRIEVE_SIZE_KEY).toString())
				: this.defaultChatMemoryRetrieveSize;
	}

	protected Flux<ChatClientResponse> doNextWithProtectFromBlockingBefore(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain, Function<ChatClientRequest, ChatClientRequest> before) {
		// This can be executed by both blocking and non-blocking Threads
		// E.g. a command line or Tomcat blocking Thread implementation
		// or by a WebFlux dispatch in a non-blocking manner.
		return (this.protectFromBlocking) ?
		// @formatter:off
				Mono.just(chatClientRequest)
						.publishOn(Schedulers.boundedElastic())
						.map(before)
						.flatMapMany(streamAdvisorChain::nextStream)
				: streamAdvisorChain.nextStream(before.apply(chatClientRequest));
	}

	/**
	 * Abstract builder for {@link AbstractChatMemoryAdvisor}.
	 * @param <T> the type of the chat memory
	 */
	public static abstract class AbstractBuilder<T> {

		/**
		 * The conversation id.
		 */
		protected String conversationId = ChatMemory.DEFAULT_CONVERSATION_ID;

		/**
		 * The chat memory retrieve size.
		 */
		protected int chatMemoryRetrieveSize = DEFAULT_CHAT_MEMORY_RESPONSE_SIZE;

		/**
		 * Whether to protect from blocking.
		 */
		protected boolean protectFromBlocking = true;

		/**
		 * The order of the advisor.
		 */
		protected int order = Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER;

		/**
		 * The chat memory.
		 */
		protected T chatMemory;

		/**
		 * Constructor to create a new {@link AbstractBuilder} instance.
		 * @param chatMemory the chat memory
		 */
		protected AbstractBuilder(T chatMemory) {
			this.chatMemory = chatMemory;
		}

		/**
		 * Set the conversation id.
		 * @param conversationId the conversation id
		 * @return the builder
		 */
		public AbstractBuilder<T> conversationId(String conversationId) {
			this.conversationId = conversationId;
			return this;
		}

		/**
		 * Set the chat memory retrieve size.
		 * @param chatMemoryRetrieveSize the chat memory retrieve size
		 * @return the builder
		 */
		public AbstractBuilder<T> chatMemoryRetrieveSize(int chatMemoryRetrieveSize) {
			this.chatMemoryRetrieveSize = chatMemoryRetrieveSize;
			return this;
		}

		/**
		 * Set whether to protect from blocking.
		 * @param protectFromBlocking whether to protect from blocking
		 * @return the builder
		 */
		public AbstractBuilder<T> protectFromBlocking(boolean protectFromBlocking) {
			this.protectFromBlocking = protectFromBlocking;
			return this;
		}

		/**
		 * Set the order.
		 * @param order the order
		 * @return the builder
		 */
		public AbstractBuilder<T> order(int order) {
			this.order = order;
			return this;
		}

		/**
		 * Build the advisor.
		 * @return the advisor
		 */
		abstract public AbstractChatMemoryAdvisor<T> build();
	}

}
