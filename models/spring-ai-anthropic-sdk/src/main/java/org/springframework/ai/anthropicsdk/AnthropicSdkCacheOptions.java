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

package org.springframework.ai.anthropicsdk;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.MessageType;

/**
 * Anthropic cache options for configuring prompt caching behavior with the Anthropic Java
 * SDK.
 *
 * @author Soby Chacko
 * @since 2.0.0
 */
public class AnthropicSdkCacheOptions {

	public static AnthropicSdkCacheOptions DISABLED = new AnthropicSdkCacheOptions();

	private static final int DEFAULT_MIN_CONTENT_LENGTH = 1;

	private AnthropicSdkCacheStrategy strategy = AnthropicSdkCacheStrategy.NONE;

	private Function<@Nullable String, Integer> contentLengthFunction = s -> s != null ? s.length() : 0;

	private Map<MessageType, AnthropicSdkCacheTtl> messageTypeTtl = Stream.of(MessageType.values())
		.collect(Collectors.toMap(mt -> mt, mt -> AnthropicSdkCacheTtl.FIVE_MINUTES, (m1, m2) -> m1, HashMap::new));

	private Map<MessageType, Integer> messageTypeMinContentLengths = Stream.of(MessageType.values())
		.collect(Collectors.toMap(mt -> mt, mt -> DEFAULT_MIN_CONTENT_LENGTH, (m1, m2) -> m1, HashMap::new));

	private boolean multiBlockSystemCaching = false;

	public static Builder builder() {
		return new Builder();
	}

	public AnthropicSdkCacheStrategy getStrategy() {
		return this.strategy;
	}

	public void setStrategy(AnthropicSdkCacheStrategy strategy) {
		this.strategy = strategy;
	}

	public Function<@Nullable String, Integer> getContentLengthFunction() {
		return this.contentLengthFunction;
	}

	public void setContentLengthFunction(Function<@Nullable String, Integer> contentLengthFunction) {
		this.contentLengthFunction = contentLengthFunction;
	}

	public Map<MessageType, AnthropicSdkCacheTtl> getMessageTypeTtl() {
		return this.messageTypeTtl;
	}

	public void setMessageTypeTtl(Map<MessageType, AnthropicSdkCacheTtl> messageTypeTtl) {
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
	public String toString() {
		return "AnthropicSdkCacheOptions{" + "strategy=" + this.strategy + ", contentLengthFunction="
				+ this.contentLengthFunction + ", messageTypeTtl=" + this.messageTypeTtl
				+ ", messageTypeMinContentLengths=" + this.messageTypeMinContentLengths + ", multiBlockSystemCaching="
				+ this.multiBlockSystemCaching + '}';
	}

	public static final class Builder {

		private final AnthropicSdkCacheOptions options = new AnthropicSdkCacheOptions();

		public Builder strategy(AnthropicSdkCacheStrategy strategy) {
			this.options.setStrategy(strategy);
			return this;
		}

		public Builder contentLengthFunction(Function<@Nullable String, Integer> contentLengthFunction) {
			this.options.setContentLengthFunction(contentLengthFunction);
			return this;
		}

		public Builder messageTypeTtl(Map<MessageType, AnthropicSdkCacheTtl> messageTypeTtl) {
			this.options.setMessageTypeTtl(messageTypeTtl);
			return this;
		}

		public Builder messageTypeTtl(MessageType messageType, AnthropicSdkCacheTtl ttl) {
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

		public AnthropicSdkCacheOptions build() {
			return this.options;
		}

	}

}
