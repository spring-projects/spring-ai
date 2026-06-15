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

package org.springframework.ai.bedrock.converse.api;

import org.jspecify.annotations.Nullable;

/**
 * AWS Bedrock cache options for configuring prompt caching behavior.
 *
 * <p>
 * Prompt caching allows you to reduce latency and costs by reusing previously processed
 * prompt content. Cached content has a fixed 5-minute Time To Live (TTL) that resets with
 * each cache hit.
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * BedrockCacheOptions cacheOptions = BedrockCacheOptions.builder()
 *     .strategy(BedrockCacheStrategy.SYSTEM_ONLY)
 *     .build();
 *
 * ChatResponse response = chatModel.call(new Prompt(
 *     List.of(new SystemMessage(largeSystemPrompt), new UserMessage("Question")),
 *     BedrockChatOptions.builder()
 *         .cacheOptions(cacheOptions)
 *         .build()
 * ));
 * }</pre>
 *
 * @author Soby Chacko
 * @author Sebastien Deleuze
 * @since 1.1.0
 * @see BedrockCacheStrategy
 * @see <a href=
 * "https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html">AWS Bedrock
 * Prompt Caching</a>
 */
public class BedrockCacheOptions {

	private final BedrockCacheStrategy strategy;

	private final boolean multiBlockSystemCaching;

	protected BedrockCacheOptions(@Nullable BedrockCacheStrategy strategy, boolean multiBlockSystemCaching) {
		this.strategy = (strategy != null ? strategy : BedrockCacheStrategy.NONE);
		this.multiBlockSystemCaching = multiBlockSystemCaching;
	}

	/**
	 * Creates a new builder for constructing BedrockCacheOptions.
	 * @return a new Builder instance
	 */
	public static BedrockCacheOptions.Builder builder() {
		return new Builder();
	}

	/**
	 * Gets the caching strategy.
	 * @return the configured BedrockCacheStrategy
	 */
	public BedrockCacheStrategy getStrategy() {
		return this.strategy;
	}

	/**
	 * Returns whether multi-block system message caching is enabled. When enabled, each
	 * {@link org.springframework.ai.chat.messages.SystemMessage} is emitted as a separate
	 * {@code SystemContentBlock} in the Bedrock Converse request, with a
	 * {@code CachePoint} placed after the second-to-last text block. This allows a static
	 * system prompt prefix to be cached while dynamic content (e.g., advisor-injected RAG
	 * context) in the last block can change freely without invalidating the cache. When
	 * disabled (default), the cache point is placed after the last system block, so any
	 * change to the last block invalidates the cache.
	 * @return {@code true} if each system message is emitted as a separate content block
	 * with the cache point placed before the last block; {@code false} otherwise
	 */
	public boolean isMultiBlockSystemCaching() {
		return this.multiBlockSystemCaching;
	}

	/**
	 * Builder for constructing BedrockCacheOptions instances.
	 */
	public static class Builder {

		private @Nullable BedrockCacheStrategy strategy;

		private boolean multiBlockSystemCaching = false;

		/**
		 * Sets the caching strategy.
		 * @param strategy the BedrockCacheStrategy to use
		 * @return this Builder instance
		 */
		public Builder strategy(@Nullable BedrockCacheStrategy strategy) {
			this.strategy = strategy;
			return this;
		}

		/**
		 * Sets whether multi-block system message caching is enabled. When enabled, each
		 * {@link org.springframework.ai.chat.messages.SystemMessage} is emitted as a
		 * separate {@code SystemContentBlock} and the cache point is placed after the
		 * second-to-last block, allowing a static prefix to be cached while the last
		 * (dynamic) block can change freely.
		 * @param multiBlockSystemCaching {@code true} to enable multi-block system
		 * caching; defaults to {@code false}
		 * @return this Builder instance
		 */
		public Builder multiBlockSystemCaching(boolean multiBlockSystemCaching) {
			this.multiBlockSystemCaching = multiBlockSystemCaching;
			return this;
		}

		/**
		 * Builds the BedrockCacheOptions instance.
		 * @return the configured BedrockCacheOptions
		 */
		public BedrockCacheOptions build() {
			return new BedrockCacheOptions(this.strategy, this.multiBlockSystemCaching);
		}

	}

}
