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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
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
 * @author Mark Pollack
 * @since 1.0.0
 */
public abstract class AbstractChatMemoryAdvisor<T> implements BaseAdvisor {

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
	public int getOrder() {
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
		if (context == null || !context.containsKey(ChatMemory.CHAT_MEMORY_CONVERSATION_ID_KEY)) {
			logger.warn("No conversation ID found in context; using defaultConversationId '{}'.",
					this.defaultConversationId);
		}
		return context != null && context.containsKey(ChatMemory.CHAT_MEMORY_CONVERSATION_ID_KEY)
				? context.get(ChatMemory.CHAT_MEMORY_CONVERSATION_ID_KEY).toString() : this.defaultConversationId;
	}

	@Override
	public Scheduler getScheduler() {
		return this.protectFromBlocking ? BaseAdvisor.DEFAULT_SCHEDULER : Schedulers.immediate();
	}

	/**
	 * Abstract builder for {@link AbstractChatMemoryAdvisor}.
	 *
	 * @param <T> the type of the chat memory
	 * @param <B> the type of the builder (self-type)
	 */
	public static abstract class AbstractBuilder<T, B extends AbstractBuilder<T, B>> {

		/**
		 * The conversation id.
		 */
		protected String conversationId = ChatMemory.DEFAULT_CONVERSATION_ID;

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
		 * Returns this builder as the parameterized type.
		 * @return this builder
		 */
		@SuppressWarnings("unchecked")
		protected B self() {
			return (B) this;
		}

		/**
		 * Set the conversation id.
		 * @param conversationId the conversation id
		 * @return the builder
		 */
		public B conversationId(String conversationId) {
			this.conversationId = conversationId;
			return self();
		}

		/**
		 * Set whether to protect from blocking.
		 * @param protectFromBlocking whether to protect from blocking
		 * @return the builder
		 */
		public B protectFromBlocking(boolean protectFromBlocking) {
			this.protectFromBlocking = protectFromBlocking;
			return self();
		}

		/**
		 * Set the order.
		 * @param order the order
		 * @return the builder
		 */
		public B order(int order) {
			this.order = order;
			return self();
		}

		/**
		 * Build the advisor.
		 * @return the advisor
		 */
		abstract public AbstractChatMemoryAdvisor<T> build();

	}

}
