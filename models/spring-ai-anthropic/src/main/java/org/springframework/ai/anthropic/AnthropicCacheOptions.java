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
 * @since 1.1.0
 * @deprecated since 2.0.0-M5, use
 * {@link org.springframework.ai.chat.prompt.PromptCacheChatOptions} instead. All features
 * including per-message-type TTL, content length function, and multi-block system caching
 * are available on the portable API.
 */
@Deprecated(since = "2.0.0-M5", forRemoval = true)
public class AnthropicCacheOptions {

	/**
	 * Returns a new disabled cache options instance with strategy {@code NONE}. Each call
	 * returns a fresh instance to avoid shared mutable state.
	 */
	public static AnthropicCacheOptions disabled() {
		return new AnthropicCacheOptions();
	}

	private static final int DEFAULT_MIN_CONTENT_LENGTH = 1;

	private AnthropicCacheStrategy strategy = AnthropicCacheStrategy.NONE;

	private Function<@Nullable String, Integer> contentLengthFunction = s -> s != null ? s.length() : 0;

	private Map<MessageType, AnthropicCacheTtl> messageTypeTtl = Stream.of(MessageType.values())
		.collect(Collectors.toMap(mt -> mt, mt -> AnthropicCacheTtl.FIVE_MINUTES, (m1, m2) -> m1, HashMap::new));

	private Map<MessageType, Integer> messageTypeMinContentLengths = Stream.of(MessageType.values())
		.collect(Collectors.toMap(mt -> mt, mt -> DEFAULT_MIN_CONTENT_LENGTH, (m1, m2) -> m1, HashMap::new));

	private boolean multiBlockSystemCaching = false;

	public static Builder builder() {
		return new Builder();
	}

	public AnthropicCacheStrategy getStrategy() {
		return this.strategy;
	}

	public void setStrategy(AnthropicCacheStrategy strategy) {
		this.strategy = strategy;
	}

	public Function<@Nullable String, Integer> getContentLengthFunction() {
		return this.contentLengthFunction;
	}

	public void setContentLengthFunction(Function<@Nullable String, Integer> contentLengthFunction) {
		this.contentLengthFunction = contentLengthFunction;
	}

	public Map<MessageType, AnthropicCacheTtl> getMessageTypeTtl() {
		return this.messageTypeTtl;
	}

	public void setMessageTypeTtl(Map<MessageType, AnthropicCacheTtl> messageTypeTtl) {
		this.messageTypeTtl = messageTypeTtl;
	}

	public Map<MessageType, Integer> getMessageTypeMinContentLengths() {
		return this.messageTypeMinContentLengths;
	}

	public void setMessageTypeMinContentLengths(Map<MessageType, Integer> messageTypeMinContentLengths) {
		this.messageTypeMinContentLengths = messageTypeMinContentLengths;
	}

	public boolean isMultiBlockSystemCaching() {
		return this.multiBlockSystemCaching;
	}

	public void setMultiBlockSystemCaching(boolean multiBlockSystemCaching) {
		this.multiBlockSystemCaching = multiBlockSystemCaching;
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

	@Override
	public String toString() {
		return "AnthropicCacheOptions{" + "strategy=" + this.strategy + ", contentLengthFunction="
				+ this.contentLengthFunction + ", messageTypeTtl=" + this.messageTypeTtl
				+ ", messageTypeMinContentLengths=" + this.messageTypeMinContentLengths + ", multiBlockSystemCaching="
				+ this.multiBlockSystemCaching + '}';
	}

	public static final class Builder {

		private final AnthropicCacheOptions options = new AnthropicCacheOptions();

		public Builder strategy(AnthropicCacheStrategy strategy) {
			this.options.setStrategy(strategy);
			return this;
		}

		public Builder contentLengthFunction(Function<@Nullable String, Integer> contentLengthFunction) {
			this.options.setContentLengthFunction(contentLengthFunction);
			return this;
		}

		public Builder messageTypeTtl(Map<MessageType, AnthropicCacheTtl> messageTypeTtl) {
			this.options.setMessageTypeTtl(messageTypeTtl);
			return this;
		}

		public Builder messageTypeTtl(MessageType messageType, AnthropicCacheTtl ttl) {
			this.options.messageTypeTtl.put(messageType, ttl);
			return this;
		}

		public Builder messageTypeMinContentLengths(Map<MessageType, Integer> messageTypeMinContentLengths) {
			this.options.setMessageTypeMinContentLengths(messageTypeMinContentLengths);
			return this;
		}

		public Builder messageTypeMinContentLength(MessageType messageType, Integer minContentLength) {
			this.options.messageTypeMinContentLengths.put(messageType, minContentLength);
			return this;
		}

		public Builder multiBlockSystemCaching(boolean multiBlockSystemCaching) {
			this.options.setMultiBlockSystemCaching(multiBlockSystemCaching);
			return this;
		}

		public AnthropicCacheOptions build() {
			return this.options;
		}

	}

}
