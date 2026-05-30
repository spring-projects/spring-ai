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

	protected AnthropicCacheOptions(@Nullable AnthropicCacheStrategy strategy,
			@Nullable Function<@Nullable String, Integer> contentLengthFunction,
			@Nullable Map<MessageType, AnthropicCacheTtl> messageTypeTtl,
			@Nullable Map<MessageType, Integer> messageTypeMinContentLengths,
			@Nullable Boolean multiBlockSystemCaching) {
		this.strategy = (strategy != null ? strategy : AnthropicCacheStrategy.NONE);
		this.contentLengthFunction = (contentLengthFunction != null ? contentLengthFunction
				: s -> s != null ? s.length() : 0);
		this.messageTypeTtl = (messageTypeTtl != null ? Map.copyOf(messageTypeTtl) : Stream.of(MessageType.values())
			.collect(Collectors.toUnmodifiableMap(mt -> mt, mt -> AnthropicCacheTtl.FIVE_MINUTES)));
		this.messageTypeMinContentLengths = (messageTypeMinContentLengths != null
				? Map.copyOf(messageTypeMinContentLengths) : Stream.of(MessageType.values())
					.collect(Collectors.toUnmodifiableMap(mt -> mt, mt -> DEFAULT_MIN_CONTENT_LENGTH)));
		this.multiBlockSystemCaching = (multiBlockSystemCaching != null ? multiBlockSystemCaching : false);
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

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AnthropicCacheOptions that)) {
			return false;
		}
		return this.multiBlockSystemCaching == that.multiBlockSystemCaching && this.strategy == that.strategy
				&& Objects.equals(this.messageTypeTtl, that.messageTypeTtl)
				&& Objects.equals(this.messageTypeMinContentLengths, that.messageTypeMinContentLengths);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.strategy, this.messageTypeTtl, this.messageTypeMinContentLengths,
				this.multiBlockSystemCaching);
	}

	public static final class Builder {

		private AnthropicCacheStrategy strategy = AnthropicCacheStrategy.NONE;

		private Function<@Nullable String, Integer> contentLengthFunction = s -> s != null ? s.length() : 0;

		private Map<MessageType, AnthropicCacheTtl> messageTypeTtl = Stream.of(MessageType.values())
			.collect(Collectors.toMap(mt -> mt, mt -> AnthropicCacheTtl.FIVE_MINUTES, (m1, m2) -> m1, HashMap::new));

		private Map<MessageType, Integer> messageTypeMinContentLengths = Stream.of(MessageType.values())
			.collect(Collectors.toMap(mt -> mt, mt -> DEFAULT_MIN_CONTENT_LENGTH, (m1, m2) -> m1, HashMap::new));

		private boolean multiBlockSystemCaching = false;

		public Builder strategy(AnthropicCacheStrategy strategy) {
			this.strategy = strategy;
			return this;
		}

		public Builder contentLengthFunction(Function<@Nullable String, Integer> contentLengthFunction) {
			this.contentLengthFunction = contentLengthFunction;
			return this;
		}

		public Builder messageTypeTtl(Map<MessageType, AnthropicCacheTtl> messageTypeTtl) {
			this.messageTypeTtl = messageTypeTtl;
			return this;
		}

		public Builder messageTypeTtl(MessageType messageType, AnthropicCacheTtl ttl) {
			this.messageTypeTtl.put(messageType, ttl);
			return this;
		}

		public Builder messageTypeMinContentLengths(Map<MessageType, Integer> messageTypeMinContentLengths) {
			this.messageTypeMinContentLengths = messageTypeMinContentLengths;
			return this;
		}

		public Builder messageTypeMinContentLength(MessageType messageType, Integer minContentLength) {
			this.messageTypeMinContentLengths.put(messageType, minContentLength);
			return this;
		}

		public Builder multiBlockSystemCaching(boolean multiBlockSystemCaching) {
			this.multiBlockSystemCaching = multiBlockSystemCaching;
			return this;
		}

		public AnthropicCacheOptions build() {
			return new AnthropicCacheOptions(this.strategy, this.contentLengthFunction, this.messageTypeTtl,
					this.messageTypeMinContentLengths, this.multiBlockSystemCaching);
		}

	}

}
