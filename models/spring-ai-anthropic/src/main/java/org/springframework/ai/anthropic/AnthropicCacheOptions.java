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

package org.springframework.ai.anthropic;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.MessageType;

/**
 * Anthropic cache options for configuring prompt caching behavior with the Anthropic Java
 * SDK.
 *
 * @author Austin Dase
 * @author Soby Chacko
 * @author Sebastien Deleuze
 * @since 1.1.0
 */
public class AnthropicCacheOptions {

	/**
	 * Returns a new disabled cache options instance with strategy {@code NONE}. Each call
	 * returns a fresh instance to avoid shared mutable state.
	 */
	public static AnthropicCacheOptions disabled() {
		return builder().strategy(AnthropicCacheStrategy.NONE).build();
	}

	private static final int DEFAULT_MIN_CONTENT_LENGTH = 1;

	private final AnthropicCacheStrategy strategy;

	private final Function<@Nullable String, Integer> contentLengthFunction;

	private final Map<MessageType, AnthropicCacheTtl> messageTypeTtl;

	private final Map<MessageType, Integer> messageTypeMinContentLengths;

	private final boolean multiBlockSystemCaching;

	private final boolean cacheToolResults;

	protected AnthropicCacheOptions(@Nullable AnthropicCacheStrategy strategy,
			@Nullable Function<@Nullable String, Integer> contentLengthFunction,
			@Nullable Map<MessageType, AnthropicCacheTtl> messageTypeTtl,
			@Nullable Map<MessageType, Integer> messageTypeMinContentLengths, @Nullable Boolean multiBlockSystemCaching,
			@Nullable Boolean cacheToolResults) {
		this.strategy = (strategy != null ? strategy : AnthropicCacheStrategy.NONE);
		this.contentLengthFunction = (contentLengthFunction != null ? contentLengthFunction
				: s -> s != null ? s.length() : 0);
		this.messageTypeTtl = Stream.of(MessageType.values())
			.collect(Collectors.toUnmodifiableMap(mt -> mt,
					mt -> (messageTypeTtl != null && messageTypeTtl.containsKey(mt)) ? messageTypeTtl.get(mt)
							: AnthropicCacheTtl.FIVE_MINUTES));
		this.messageTypeMinContentLengths = Stream.of(MessageType.values())
			.collect(Collectors.toUnmodifiableMap(mt -> mt,
					mt -> (messageTypeMinContentLengths != null && messageTypeMinContentLengths.containsKey(mt))
							? messageTypeMinContentLengths.get(mt) : DEFAULT_MIN_CONTENT_LENGTH));
		this.multiBlockSystemCaching = (multiBlockSystemCaching != null ? multiBlockSystemCaching : false);
		this.cacheToolResults = (cacheToolResults != null ? cacheToolResults : false);
	}

	public static AnthropicCacheOptions.Builder builder() {
		return new Builder();
	}

	public AnthropicCacheStrategy getStrategy() {
		return this.strategy;
	}

	public Function<@Nullable String, Integer> getContentLengthFunction() {
		return this.contentLengthFunction;
	}

	public Map<MessageType, AnthropicCacheTtl> getMessageTypeTtl() {
		return this.messageTypeTtl;
	}

	public Map<MessageType, Integer> getMessageTypeMinContentLengths() {
		return this.messageTypeMinContentLengths;
	}

	public boolean isMultiBlockSystemCaching() {
		return this.multiBlockSystemCaching;
	}

	/**
	 * Whether a {@code cache_control} breakpoint is placed on the last tool result
	 * message of a request, so that tool outputs are written to (and read from) the cache
	 * across tool-calling rounds. Takes effect with strategies whose eligible message
	 * types include tool results, i.e.
	 * {@link AnthropicCacheStrategy#CONVERSATION_HISTORY}; it is a no-op for strategies
	 * that only target tool definitions or system messages.
	 * @return {@code true} if tool result caching is enabled
	 * @since 2.0.0
	 */
	public boolean isCacheToolResults() {
		return this.cacheToolResults;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AnthropicCacheOptions that)) {
			return false;
		}
		return this.multiBlockSystemCaching == that.multiBlockSystemCaching
				&& this.cacheToolResults == that.cacheToolResults && this.strategy == that.strategy
				&& Objects.equals(this.messageTypeTtl, that.messageTypeTtl)
				&& Objects.equals(this.messageTypeMinContentLengths, that.messageTypeMinContentLengths);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.strategy, this.messageTypeTtl, this.messageTypeMinContentLengths,
				this.multiBlockSystemCaching, this.cacheToolResults);
	}

	public static final class Builder {

		private @Nullable AnthropicCacheStrategy strategy;

		private @Nullable Function<@Nullable String, Integer> contentLengthFunction;

		private @Nullable Map<MessageType, AnthropicCacheTtl> messageTypeTtl;

		private @Nullable Map<MessageType, Integer> messageTypeMinContentLengths;

		private @Nullable Boolean multiBlockSystemCaching;

		private @Nullable Boolean cacheToolResults;

		public Builder strategy(@Nullable AnthropicCacheStrategy strategy) {
			this.strategy = strategy;
			return this;
		}

		public Builder contentLengthFunction(@Nullable Function<@Nullable String, Integer> contentLengthFunction) {
			this.contentLengthFunction = contentLengthFunction;
			return this;
		}

		public Builder messageTypeTtl(@Nullable Map<MessageType, AnthropicCacheTtl> messageTypeTtl) {
			this.messageTypeTtl = messageTypeTtl;
			return this;
		}

		public Builder messageTypeTtl(MessageType messageType, AnthropicCacheTtl ttl) {
			if (this.messageTypeTtl == null) {
				this.messageTypeTtl = new HashMap<>();
			}
			this.messageTypeTtl.put(messageType, ttl);
			return this;
		}

		public Builder messageTypeMinContentLengths(@Nullable Map<MessageType, Integer> messageTypeMinContentLengths) {
			this.messageTypeMinContentLengths = messageTypeMinContentLengths;
			return this;
		}

		public Builder messageTypeMinContentLength(MessageType messageType, Integer minContentLength) {
			if (this.messageTypeMinContentLengths == null) {
				this.messageTypeMinContentLengths = new HashMap<>();
			}
			this.messageTypeMinContentLengths.put(messageType, minContentLength);
			return this;
		}

		public Builder multiBlockSystemCaching(@Nullable Boolean multiBlockSystemCaching) {
			this.multiBlockSystemCaching = multiBlockSystemCaching;
			return this;
		}

		/**
		 * Place a {@code cache_control} breakpoint on the last tool result message of the
		 * request so that tool outputs are cached across tool-calling rounds. Takes
		 * effect with {@link AnthropicCacheStrategy#CONVERSATION_HISTORY}.
		 * @param cacheToolResults whether to cache tool result messages
		 * @return this builder
		 * @since 2.0.0
		 */
		public Builder cacheToolResults(@Nullable Boolean cacheToolResults) {
			this.cacheToolResults = cacheToolResults;
			return this;
		}

		public AnthropicCacheOptions build() {
			return new AnthropicCacheOptions(this.strategy, this.contentLengthFunction, this.messageTypeTtl,
					this.messageTypeMinContentLengths, this.multiBlockSystemCaching, this.cacheToolResults);
		}

	}

}
