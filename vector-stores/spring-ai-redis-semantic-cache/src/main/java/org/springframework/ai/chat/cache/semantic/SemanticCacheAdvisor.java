/*
 * Copyright 2023-present the original author or authors.
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

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

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
 * <p>
 * Cached responses are isolated by the scope returned by the mandatory
 * {@link SemanticCacheScopeResolver}. Requests for which the resolver returns no scope
 * bypass the cache entirely.
 * </p>
 *
 * <p>
 * Cached responses never expire unless a time-to-live is configured with
 * {@link Builder#ttl(Duration)}.
 * </p>
 *
 * @author Brian Sam-Bodden
 * @author Soby Chacko
 * @author Dimitar Proynov
 */
public class SemanticCacheAdvisor implements BaseChatMemoryAdvisor {

	/** The underlying semantic cache implementation. */
	private final SemanticCache cache;

	/** Resolves the scope that isolates cached responses. */
	private final SemanticCacheScopeResolver scopeResolver;

	/** The order of this advisor in the chain. */
	private final int order;

	/** The scheduler for async operations. */
	private final Scheduler scheduler;

	/** The time-to-live of cached responses, or null for no expiration. */
	private final @Nullable Duration ttl;

	/**
	 * Creates a new semantic cache advisor with default order and scheduler.
	 * @param cache The semantic cache implementation to use
	 * @param scopeResolver The resolver of the scope that isolates cached responses
	 * @since 2.0.2
	 */
	public SemanticCacheAdvisor(SemanticCache cache, SemanticCacheScopeResolver scopeResolver) {
		this(cache, scopeResolver, DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER, Schedulers.boundedElastic());
	}

	/**
	 * Creates a new semantic cache advisor with specified order and default scheduler.
	 * @param cache The semantic cache implementation to use
	 * @param scopeResolver The resolver of the scope that isolates cached responses
	 * @param order The order of this advisor in the chain
	 * @since 2.0.2
	 */
	public SemanticCacheAdvisor(SemanticCache cache, SemanticCacheScopeResolver scopeResolver, int order) {
		this(cache, scopeResolver, order, Schedulers.boundedElastic());
	}

	/**
	 * Creates a new semantic cache advisor with specified order and scheduler.
	 * @param cache The semantic cache implementation to use
	 * @param scopeResolver The resolver of the scope that isolates cached responses
	 * @param order The order of this advisor in the chain
	 * @param scheduler The scheduler for async operations
	 * @since 2.0.2
	 */
	public SemanticCacheAdvisor(SemanticCache cache, SemanticCacheScopeResolver scopeResolver, int order,
			Scheduler scheduler) {
		this(cache, scopeResolver, order, scheduler, null);
	}

	/**
	 * Creates a new semantic cache advisor with specified order, scheduler and
	 * time-to-live.
	 * @param cache The semantic cache implementation to use
	 * @param scopeResolver The resolver of the scope that isolates cached responses
	 * @param order The order of this advisor in the chain
	 * @param scheduler The scheduler for async operations
	 * @param ttl The time-to-live of cached responses, or null for no expiration
	 * @since 2.0.2
	 */
	public SemanticCacheAdvisor(SemanticCache cache, SemanticCacheScopeResolver scopeResolver, int order,
			Scheduler scheduler, @Nullable Duration ttl) {
		Assert.notNull(cache, "Cache must not be null");
		Assert.notNull(scopeResolver, "Scope resolver must not be null");
		Assert.isTrue(ttl == null || ttl.toMillis() > 0, "TTL must be positive");
		this.cache = cache;
		this.scopeResolver = scopeResolver;
		this.order = order;
		this.scheduler = scheduler;
		this.ttl = ttl;
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
	 * Otherwise, the request proceeds through the chain and the response is cached. If no
	 * scope is resolved for the request, the cache is bypassed.
	 * @param request The chat client request to process
	 * @param chain The advisor chain to continue processing if needed
	 * @return The response, either from cache or from the model
	 */
	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		// Extract context hash for isolation (different scopes = different cache)
		String contextHash = extractContextHash(request);
		if (contextHash == null) {
			return chain.nextCall(request);
		}
		// Extract user text for semantic similarity search
		String userText = extractUserText(request);

		// Check cache first (with context filtering)
		Optional<ChatResponse> cached = this.cache.get(userText, contextHash);

		if (cached.isPresent()) {
			// Create a new ChatClientResponse with the cached response
			return ChatClientResponse.builder().chatResponse(cached.get()).context(request.context()).build();
		}

		// Cache miss - call the model
		ChatClientResponse response = chain.nextCall(request);

		// Cache the response (with context hash for isolation)
		if (response.chatResponse() != null) {
			cacheResponse(userText, response.chatResponse(), contextHash);
		}

		return response;
	}

	/**
	 * Handles streaming chat requests by checking the cache before proceeding. If a
	 * semantically similar response is found in the cache, it is returned as a single
	 * item flux. Otherwise, the request proceeds through the chain with true streaming -
	 * tokens are returned to the user as they arrive, while the response is aggregated
	 * and cached asynchronously when the stream completes. If no scope is resolved for
	 * the request, the cache is bypassed.
	 * @param request The chat client request to process
	 * @param chain The advisor chain to continue processing if needed
	 * @return A Flux of responses, either from cache or from the model
	 */
	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
		// Extract context hash for isolation (different scopes = different cache)
		String contextHash = extractContextHash(request);
		if (contextHash == null) {
			return chain.nextStream(request);
		}
		// Extract user text for semantic similarity search
		String userText = extractUserText(request);

		// Check cache first (with context filtering)
		Optional<ChatResponse> cached = this.cache.get(userText, contextHash);

		if (cached.isPresent()) {
			// Create a new ChatClientResponse with the cached response
			return Flux
				.just(ChatClientResponse.builder().chatResponse(cached.get()).context(request.context()).build());
		}

		// Cache miss - stream from model with true streaming behavior.
		// Tokens are returned to the user immediately as they arrive.
		// The response is aggregated and cached asynchronously when the stream completes.
		return chain.nextStream(request)
			.transform(
					flux -> new ChatClientMessageAggregator().aggregateChatClientResponse(flux, aggregatedResponse -> {
						// Cache the aggregated response when the stream completes
						if (aggregatedResponse.chatResponse() != null) {
							cacheResponse(userText, aggregatedResponse.chatResponse(), contextHash);
						}
					}));
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
	 * Stores the response in the cache, with the time-to-live if configured.
	 * @param userText the user message text
	 * @param response the chat response to cache
	 * @param contextHash the context hash for isolation
	 */
	private void cacheResponse(String userText, ChatResponse response, String contextHash) {
		if (this.ttl != null) {
			this.cache.set(userText, response, contextHash, this.ttl);
		}
		else {
			this.cache.set(userText, response, contextHash);
		}
	}

	/**
	 * Extracts the user message text from the ChatClientRequest to use for semantic
	 * similarity search.
	 * @param request the chat client request containing the prompt
	 * @return the user message text, or empty string if not present
	 */
	private String extractUserText(ChatClientRequest request) {
		return Objects.requireNonNullElse(request.prompt().getUserMessage().getText(), "");
	}

	/**
	 * Extracts a context hash from the ChatClientRequest for cache isolation. Different
	 * scopes will produce different hashes, ensuring that cached responses are only
	 * returned for queries with matching scope.
	 * @param request the chat client request containing the prompt
	 * @return the context hash, or null if no scope is resolved for the request
	 */
	private @Nullable String extractContextHash(ChatClientRequest request) {
		String scope = this.scopeResolver.resolveScope(request);
		if (!StringUtils.hasText(scope)) {
			return null;
		}
		return computeHash(scope);
	}

	/**
	 * Computes a deterministic hash for the given string. Uses a SHA-256 hash to create a
	 * unique identifier.
	 * @param text the text to hash
	 * @return a 64-character hash string
	 */
	private String computeHash(String text) {
		try {
			java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(hash);
		}
		catch (java.security.NoSuchAlgorithmException e) {
			// possible only on a broken/tampered with java.security provider
			// configuration
			throw new IllegalStateException(e);
		}
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

		private @Nullable SemanticCache cache;

		private @Nullable SemanticCacheScopeResolver scopeResolver;

		private int order = DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER;

		private Scheduler scheduler = Schedulers.boundedElastic();

		private @Nullable Duration ttl;

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
		 * Sets the resolver of the scope that isolates cached responses. Mandatory.
		 * @param scopeResolver The scope resolver to use
		 * @return This builder instance
		 * @since 2.0.2
		 */
		public Builder scopeResolver(SemanticCacheScopeResolver scopeResolver) {
			this.scopeResolver = scopeResolver;
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
		 * Sets the time-to-live of cached responses. By default, cached responses never
		 * expire.
		 * @param ttl The time-to-live of cached responses
		 * @return This builder instance
		 * @since 2.0.2
		 */
		public Builder ttl(Duration ttl) {
			this.ttl = ttl;
			return this;
		}

		/**
		 * Builds and returns a new SemanticCacheAdvisor instance.
		 * @return A new SemanticCacheAdvisor configured with this builder's settings
		 */
		public SemanticCacheAdvisor build() {
			Assert.notNull(this.cache, "Cache must not be null");
			Assert.notNull(this.scopeResolver, "Scope resolver must not be null");
			return new SemanticCacheAdvisor(this.cache, this.scopeResolver, this.order, this.scheduler, this.ttl);
		}

	}

}
