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

package org.springframework.ai.chat.client.advisor;

import java.util.Map;
import java.util.function.Function;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.util.Assert;
import org.stringtemplate.v4.compiler.CodeGenerator.includeExpr_return;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Abstract class that serves as a base for chat memory advisors.
 *
 * @param <T> the type of the chat memory.
 * @author Christian Tzolov
 * @since 1.0.0
 */
public abstract class AbstractChatMemoryAdvisor<T> implements CallAroundAdvisor, StreamAroundAdvisor {

	public static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";

	public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";

	public static final String DEFAULT_CHAT_MEMORY_CONVERSATION_ID = "default";

	public static final int DEFAULT_CHAT_MEMORY_RESPONSE_SIZE = 100;

	protected final T chatMemoryStore;

	protected final String defaultConversationId;

	protected final int defaultChatMemoryRetrieveSize;

	private final boolean protectFromBlocking;

	private final int order;

	protected AbstractChatMemoryAdvisor(T chatMemory) {
		this(chatMemory, DEFAULT_CHAT_MEMORY_CONVERSATION_ID, DEFAULT_CHAT_MEMORY_RESPONSE_SIZE, true);
	}

	protected AbstractChatMemoryAdvisor(T chatMemory, String defaultConversationId, int defaultChatMemoryRetrieveSize,
			boolean protectFromBlocking) {
		this(chatMemory, defaultConversationId, defaultChatMemoryRetrieveSize, protectFromBlocking,
				Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER);
	}

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

	protected T getChatMemoryStore() {
		return this.chatMemoryStore;
	}

	protected String doGetConversationId(Map<String, Object> context) {

		return context.containsKey(CHAT_MEMORY_CONVERSATION_ID_KEY)
				? context.get(CHAT_MEMORY_CONVERSATION_ID_KEY).toString() : this.defaultConversationId;
	}

	protected int doGetChatMemoryRetrieveSize(Map<String, Object> context) {
		return context.containsKey(CHAT_MEMORY_RETRIEVE_SIZE_KEY)
				? Integer.parseInt(context.get(CHAT_MEMORY_RETRIEVE_SIZE_KEY).toString())
				: this.defaultChatMemoryRetrieveSize;
	}

	protected Flux<AdvisedResponse> doNextWithProtectFromBlockingBefore(AdvisedRequest advisedRequest,
			StreamAroundAdvisorChain chain, Function<AdvisedRequest, AdvisedRequest> beforeAdvise) {

		// This can be executed by both blocking and non-blocking Threads
		// E.g. a command line or Tomcat blocking Thread implementation
		// or by a WebFlux dispatch in a non-blocking manner.
		return (this.protectFromBlocking) ?
		// @formatter:off
			Mono.just(advisedRequest)
				.publishOn(Schedulers.boundedElastic())
				.map(beforeAdvise)
				.flatMapMany(request -> chain.nextAroundStream(request))
			: chain.nextAroundStream(beforeAdvise.apply(advisedRequest));
	}

	public static abstract class AbstractBuilder<T> {
		
		protected String conversationId = DEFAULT_CHAT_MEMORY_CONVERSATION_ID;

		protected int chatMemoryRetrieveSize = DEFAULT_CHAT_MEMORY_RESPONSE_SIZE;

		protected boolean protectFromBlocking = true;

		protected int order = Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER;

		protected T chatMemory;

		protected AbstractBuilder(T chatMemory) {
			this.chatMemory = chatMemory;
		}

		public AbstractBuilder withConversationId(String conversationId) {
			this.conversationId = conversationId;
			return this;
		}

		public AbstractBuilder withChatMemoryRetrieveSize(int chatMemoryRetrieveSize) {
			this.chatMemoryRetrieveSize = chatMemoryRetrieveSize;
			return this;
		}

		public AbstractBuilder withProtectFromBlocking(boolean protectFromBlocking) {
			this.protectFromBlocking = protectFromBlocking;
			return this;
		}

		public AbstractBuilder withOrder(int order) {
			this.order = order;
			return this;
		}

		abstract public <T> AbstractChatMemoryAdvisor<T> build();
	}

}
