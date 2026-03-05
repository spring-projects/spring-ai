/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.anthropic.api;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.MessageType;

/**
 * Anthropic cache options for configuring prompt caching behavior.
 *
 * @author Austin Dase
 * @since 1.1.0
 **/
public class AnthropicCacheOptions {

	public static AnthropicCacheOptions DISABLED = new AnthropicCacheOptions();

	private static final int DEFAULT_MIN_CONTENT_LENGTH = 1;

	private AnthropicCacheStrategy strategy = AnthropicCacheStrategy.NONE;

	/**
	 * Function to determine the content length of a message. Defaults to the length of
	 * the string, or {@code 0} if the string is {@code null}. This is used as a proxy for
	 * number of tokens because Anthropic does document that messages with too few tokens
	 * are not eligible for caching - see <a href=
	 * "https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching#cache-limitations">Anthropic
	 * Caching Limitations</a>. Further, the function can be customized to use a more
	 * accurate token count if desired.
	 */
	private Function<@Nullable String, Integer> contentLengthFunction = s -> s != null ? s.length() : 0;

	/**
	 * Configure on a per {@link MessageType} basis the TTL (time-to-live) for cached
	 * prompts. Defaults to {@link AnthropicCacheTtl#FIVE_MINUTES}. Note that different
	 * caches have different write costs, see <a href=
	 * "https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching#understanding-cache-breakpoint-costs">Anthropic
	 * Cache Breakpoint Costs</a>
	 */
	private Map<MessageType, AnthropicCacheTtl> messageTypeTtl = Stream.of(MessageType.values())
		.collect(Collectors.toMap(mt -> mt, mt -> AnthropicCacheTtl.FIVE_MINUTES, (m1, m2) -> m1, HashMap::new));

	/**
	 * Configure on a per {@link MessageType} basis the minimum content length required to
	 * consider a message for caching. Defaults to {@code 1}. This is used in conjunction
	 * with the {@link #contentLengthFunction} to determine if a message is eligible for
	 * caching based on its content length. Helping to optimize the usage of the limited
	 * cache breakpoints (4 max) allowed by Anthropic.
	 */
	private Map<MessageType, Integer> messageTypeMinContentLengths = Stream.of(MessageType.values())
		.collect(Collectors.toMap(mt -> mt, mt -> DEFAULT_MIN_CONTENT_LENGTH, (m1, m2) -> m1, HashMap::new));

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

	@Override
	public String toString() {
		return "AnthropicCacheOptions{" + "strategy=" + this.strategy + ", contentLengthFunction="
				+ this.contentLengthFunction + ", messageTypeTtl=" + this.messageTypeTtl
				+ ", messageTypeMinContentLengths=" + this.messageTypeMinContentLengths + '}';
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

		public AnthropicCacheOptions build() {
			return this.options;
		}

	}

}
