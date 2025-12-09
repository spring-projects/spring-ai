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
package org.springframework.ai.chat.cache.semantic;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.redis.cache.semantic.SemanticCache;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

/**
 * An advisor implementation that provides semantic caching capabilities for chat
 * responses. This advisor intercepts chat requests and checks for semantically similar
 * cached responses before allowing the request to proceed to the model.
 *
 * <p>
 * This advisor implements {@link BaseChatMemoryAdvisor} but overrides both
 * {@link #adviseCall} and {@link #adviseStream} to provide custom caching logic that
 * doesn't fit the standard before/after pattern.
 * </p>
 *
 * <p>
 * Key features:
 * <ul>
 * <li>Semantic similarity based caching of responses</li>
 * <li>Support for both synchronous and streaming chat operations</li>
 * <li>Configurable execution order in the advisor chain</li>
 * </ul>
 *
 * @author Brian Sam-Bodden
 */
public class SemanticCacheAdvisor implements BaseChatMemoryAdvisor {

	/** The underlying semantic cache implementation */
	private final SemanticCache cache;

	/** The order of this advisor in the chain */
	private final int order;

	/** The scheduler for async operations */
	private final Scheduler scheduler;

	/**
	 * Creates a new semantic cache advisor with default order and scheduler.
	 * @param cache The semantic cache implementation to use
	 */
	public SemanticCacheAdvisor(SemanticCache cache) {
		this(cache, DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER, Schedulers.boundedElastic());
	}

	/**
	 * Creates a new semantic cache advisor with specified order and default scheduler.
	 * @param cache The semantic cache implementation to use
	 * @param order The order of this advisor in the chain
	 */
	public SemanticCacheAdvisor(SemanticCache cache, int order) {
		this(cache, order, Schedulers.boundedElastic());
	}

	/**
	 * Creates a new semantic cache advisor with specified order and scheduler.
	 * @param cache The semantic cache implementation to use
	 * @param order The order of this advisor in the chain
	 * @param scheduler The scheduler for async operations
	 */
	public SemanticCacheAdvisor(SemanticCache cache, int order, Scheduler scheduler) {
		this.cache = cache;
		this.order = order;
		this.scheduler = scheduler;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public Scheduler getScheduler() {
		return this.scheduler;
	}

	/**
	 * Handles synchronous chat requests by checking the cache before proceeding. If a
	 * semantically similar response is found in the cache, it is returned immediately.
	 * Otherwise, the request proceeds through the chain and the response is cached.
	 * @param request The chat client request to process
	 * @param chain The advisor chain to continue processing if needed
	 * @return The response, either from cache or from the model
	 */
	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		// Extracting the user's text from the prompt to use as cache key
		String userText = extractUserTextFromRequest(request);

		// Check cache first
		Optional<ChatResponse> cached = cache.get(userText);

		if (cached.isPresent()) {
			// Create a new ChatClientResponse with the cached response
			return ChatClientResponse.builder().chatResponse(cached.get()).context(request.context()).build();
		}

		// Cache miss - call the model
		ChatClientResponse response = chain.nextCall(request);

		// Cache the response
		if (response.chatResponse() != null) {
			cache.set(userText, response.chatResponse());
		}

		return response;
	}

	/**
	 * Handles streaming chat requests by checking the cache before proceeding. If a
	 * semantically similar response is found in the cache, it is returned as a single
	 * item flux. Otherwise, the request proceeds through the chain and the final response
	 * is cached.
	 * @param request The chat client request to process
	 * @param chain The advisor chain to continue processing if needed
	 * @return A Flux of responses, either from cache or from the model
	 */
	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
		// Extracting the user's text from the prompt to use as cache key
		String userText = extractUserTextFromRequest(request);

		// Check cache first
		Optional<ChatResponse> cached = cache.get(userText);

		if (cached.isPresent()) {
			// Create a new ChatClientResponse with the cached response
			return Flux
				.just(ChatClientResponse.builder().chatResponse(cached.get()).context(request.context()).build());
		}

		// Cache miss - stream from model
		return chain.nextStream(request).collectList().flatMapMany(responses -> {
			// Cache the final aggregated response
			if (!responses.isEmpty()) {
				ChatClientResponse last = responses.get(responses.size() - 1);
				if (last.chatResponse() != null) {
					cache.set(userText, last.chatResponse());
				}
			}
			return Flux.fromIterable(responses);
		});
	}

	/**
	 * Not used for semantic cache advisor since we override adviseCall/adviseStream.
	 */
	@Override
	public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
		return request;
	}

	/**
	 * Not used for semantic cache advisor since we override adviseCall/adviseStream.
	 */
	@Override
	public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
		return response;
	}

	/**
	 * Utility method to extract user text from a ChatClientRequest. Extracts the content
	 * of the last user message from the prompt.
	 */
	private String extractUserTextFromRequest(ChatClientRequest request) {
		// Extract the last user message from the prompt
		return request.prompt().getUserMessage().getText();
	}

	/**
	 * Creates a new builder for constructing SemanticCacheAdvisor instances.
	 * @return A new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder class for creating SemanticCacheAdvisor instances. Provides a fluent API
	 * for configuration.
	 */
	public static class Builder {

		private SemanticCache cache;

		private int order = DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER;

		private Scheduler scheduler = Schedulers.boundedElastic();

		/**
		 * Sets the semantic cache implementation.
		 * @param cache The cache implementation to use
		 * @return This builder instance
		 */
		public Builder cache(SemanticCache cache) {
			this.cache = cache;
			return this;
		}

		/**
		 * Sets the advisor order.
		 * @param order The order value for this advisor
		 * @return This builder instance
		 */
		public Builder order(int order) {
			this.order = order;
			return this;
		}

		/**
		 * Sets the scheduler for async operations.
		 * @param scheduler The scheduler to use
		 * @return This builder instance
		 */
		public Builder scheduler(Scheduler scheduler) {
			this.scheduler = scheduler;
			return this;
		}

		/**
		 * Builds and returns a new SemanticCacheAdvisor instance.
		 * @return A new SemanticCacheAdvisor configured with this builder's settings
		 */
		public SemanticCacheAdvisor build() {
			return new SemanticCacheAdvisor(this.cache, this.order, this.scheduler);
		}

	}

}
