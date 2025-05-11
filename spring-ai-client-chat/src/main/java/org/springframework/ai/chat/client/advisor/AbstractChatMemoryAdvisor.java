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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.util.Assert;

/**
 * Abstract class that serves as a base for chat memory advisors.
 * <p>
 * <b>WARNING:</b> If you rely on the {@code defaultConversationId} (i.e., do not provide
 * a conversation ID in the context), all chat memory will be shared across all users and
 * sessions. This means you will <b>NOT</b> be able to support multiple independent user
 * sessions or conversations. Always provide a unique conversation ID in the context to
 * ensure proper session isolation.
 * </p>
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
	 * The chat memory store.
	 */
	protected final T chatMemoryStore;

	/**
	 * The default conversation id.
	 */
	protected final String defaultConversationId;

	/**
	 * Whether to protect from blocking.
	 */
	private final boolean protectFromBlocking;

	/**
	 * The order of the advisor.
	 */
	private final int order;

	private static final Logger logger = LoggerFactory.getLogger(AbstractChatMemoryAdvisor.class);

	/**
	 * Constructor to create a new {@link AbstractChatMemoryAdvisor} instance.
	 * @param chatMemory the chat memory store
	 */
	protected AbstractChatMemoryAdvisor(T chatMemory) {
		this(chatMemory, ChatMemory.DEFAULT_CONVERSATION_ID, true);
	}

	/**
	 * Constructor to create a new {@link AbstractChatMemoryAdvisor} instance.
	 * @param chatMemory the chat memory store
	 * @param defaultConversationId the default conversation id
	 * @param protectFromBlocking whether to protect from blocking
	 */
	protected AbstractChatMemoryAdvisor(T chatMemory, String defaultConversationId, boolean protectFromBlocking) {
		this(chatMemory, defaultConversationId, protectFromBlocking, Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER);
	}

	/**
	 * Constructor to create a new {@link AbstractChatMemoryAdvisor} instance.
	 * @param chatMemory the chat memory store
	 * @param defaultConversationId the default conversation id
	 * @param protectFromBlocking whether to protect from blocking
	 * @param order the order
	 */
	protected AbstractChatMemoryAdvisor(T chatMemory, String defaultConversationId, boolean protectFromBlocking,
			int order) {

		Assert.notNull(chatMemory, "The chatMemory must not be null!");
		Assert.hasText(defaultConversationId, "The conversationId must not be empty!");
		this.chatMemoryStore = chatMemory;
		this.defaultConversationId = defaultConversationId;
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
	 * Get the conversation id for the current context.
	 * @param context the context
	 * @return the conversation id
	 */
	protected String doGetConversationId(Map<String, Object> context) {
		if (context == null || !context.containsKey(CHAT_MEMORY_CONVERSATION_ID_KEY)) {
			logger.warn("No conversation ID found in context; using defaultConversationId '{}'.",
					this.defaultConversationId);
		}
		return context != null && context.containsKey(CHAT_MEMORY_CONVERSATION_ID_KEY)
				? context.get(CHAT_MEMORY_CONVERSATION_ID_KEY).toString() : this.defaultConversationId;
	}

	protected Flux<ChatClientResponse> doNextWithProtectFromBlockingBefore(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain, Function<ChatClientRequest, ChatClientRequest> before) {
		// This can be executed by both blocking and non-blocking Threads
		// E.g. a command line or Tomcat blocking Thread implementation
		// or by a WebFlux dispatch in a non-blocking manner.
		return (this.protectFromBlocking)
				? Mono.just(chatClientRequest)
					.publishOn(Schedulers.boundedElastic())
					.map(before)
					.flatMapMany(streamAdvisorChain::nextStream)
				: streamAdvisorChain.nextStream(before.apply(chatClientRequest));
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		// Apply memory to the request
		ChatClientRequest modifiedRequest = before(chatClientRequest);

		// Call the next advisor in the chain
		ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(modifiedRequest);

		// Process the response (save to memory, etc.)
		after(chatClientResponse);

		return chatClientResponse;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		return this.doNextWithProtectFromBlockingBefore(chatClientRequest, streamAdvisorChain, this::before);
	}

	/**
	 * Hook for subclasses to modify the request before passing to the chain. Default
	 * implementation returns the request as-is.
	 */
	protected ChatClientRequest before(ChatClientRequest chatClientRequest) {
		String conversationId = doGetConversationId(chatClientRequest.context());
		return before(chatClientRequest, conversationId);
	}

	/**
	 * Hook for subclasses to modify the request before passing to the chain.
	 * @param chatClientRequest the request
	 * @param conversationId the conversation id
	 * @return the modified request
	 */
	protected abstract ChatClientRequest before(ChatClientRequest chatClientRequest, String conversationId);

	/**
	 * Utility to build the context options map for downstream advisor implementations.
	 * Adds the request itself under the key "request".
	 */
	protected Map<String, Object> buildContextMap(ChatClientRequest request) {
		Map<String, Object> options = new HashMap<>(request.context());
		options.put("request", request);
		return options;
	}

	/**
	 * Hook for subclasses to handle the response after the chain. Default implementation
	 * does nothing.
	 */
	protected void after(ChatClientResponse chatClientResponse) {
		// No-op by default
	}

	/**
	 * Abstract builder for {@link AbstractChatMemoryAdvisor}.
	 *
	 * @param <T> the type of the chat memory
	 * @param <B> the type of the builder
	 */
	public static abstract class AbstractBuilder<T, B extends AbstractBuilder<T, B>> {

		protected final T chatMemory;

		protected String conversationId = ChatMemory.DEFAULT_CONVERSATION_ID;

		protected boolean protectFromBlocking = true;

		protected int order = Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER;

		protected AbstractBuilder(T chatMemory) {
			this.chatMemory = chatMemory;
		}

		public B conversationId(String conversationId) {
			this.conversationId = conversationId;
			return self();
		}

		public B protectFromBlocking(boolean protectFromBlocking) {
			this.protectFromBlocking = protectFromBlocking;
			return self();
		}

		public B order(int order) {
			this.order = order;
			return self();
		}

		protected abstract B self();

		public abstract AbstractChatMemoryAdvisor<T> build();

	}

}
