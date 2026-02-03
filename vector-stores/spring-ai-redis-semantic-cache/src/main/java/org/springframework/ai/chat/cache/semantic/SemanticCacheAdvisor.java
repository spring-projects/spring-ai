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
 * @author Soby Chacko
 */
public class SemanticCacheAdvisor implements BaseChatMemoryAdvisor {

	/** The underlying semantic cache implementation. */
	private final SemanticCache cache;

	/** The order of this advisor in the chain. */
	private final int order;

	/** The scheduler for async operations. */
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
		// Extract user text for semantic similarity search
		String userText = extractUserText(request);
		// Extract context hash for isolation (different system prompts = different cache)
		String contextHash = extractContextHash(request);

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
			this.cache.set(userText, response.chatResponse(), contextHash);
		}

		return response;
	}

	/**
	 * Handles streaming chat requests by checking the cache before proceeding. If a
	 * semantically similar response is found in the cache, it is returned as a single
	 * item flux. Otherwise, the request proceeds through the chain with true streaming -
	 * tokens are returned to the user as they arrive, while the response is aggregated
	 * and cached asynchronously when the stream completes.
	 * @param request The chat client request to process
	 * @param chain The advisor chain to continue processing if needed
	 * @return A Flux of responses, either from cache or from the model
	 */
	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
		// Extract user text for semantic similarity search
		String userText = extractUserText(request);
		// Extract context hash for isolation (different system prompts = different cache)
		String contextHash = extractContextHash(request);

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
							this.cache.set(userText, aggregatedResponse.chatResponse(), contextHash);
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
	 * system prompts will produce different hashes, ensuring that cached responses are
	 * only returned for queries with matching context.
	 * @param request the chat client request containing the prompt
	 * @return the context hash if a system prompt is present, null otherwise
	 */
	private @Nullable String extractContextHash(ChatClientRequest request) {
		var systemMessage = request.prompt().getSystemMessage();
		if (systemMessage != null && systemMessage.getText() != null && !systemMessage.getText().isEmpty()) {
			return computeHash(systemMessage.getText());
		}
		return null;
	}

	/**
	 * Computes a deterministic hash for the given string. Uses the first 8 characters of
	 * the SHA-256 hash to create a compact but unique identifier.
	 * @param text the text to hash
	 * @return an 8-character hash string
	 */
	private String computeHash(String text) {
		try {
			java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder hexString = new StringBuilder();
			// Take first 4 bytes of SHA-256 hash → 8 hex characters
			// (4 billion possible values - sufficient for context differentiation)
			for (int i = 0; i < 4; i++) {
				// 0xff mask converts signed byte (-128..127) to unsigned (0..255)
				// ensuring correct hex representation (e.g., byte -1 → "ff", not
				// "ffffffff")
				String hex = Integer.toHexString(0xff & hash[i]);
				if (hex.length() == 1) {
					hexString.append('0');
				}
				hexString.append(hex);
			}
			return hexString.toString();
		}
		catch (java.security.NoSuchAlgorithmException e) {
			// SHA-256 is always available in Java, but fallback to hashCode if needed
			return Integer.toHexString(text.hashCode());
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
			Assert.notNull(this.cache, "Cache must not be null");
			return new SemanticCacheAdvisor(this.cache, this.order, this.scheduler);
		}

	}

}
