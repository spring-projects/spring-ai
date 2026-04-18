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

package org.springframework.ai.chat.prompt;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.MessageType;

/**
 * Interface for {@link ChatOptions} implementations that support prompt caching. Provides
 * a unified way to configure portable prompt cache settings.
 *
 * <p>
 * Providers that support explicit prompt caching (e.g., Anthropic, Bedrock) implement
 * this interface on their options class. The framework can then use
 * {@code instanceof PromptCacheChatOptions} to detect caching support and apply
 * configuration without provider-specific knowledge.
 *
 * @author Soby Chacko
 * @since 2.0.0
 * @see PromptCacheStrategy
 */
public interface PromptCacheChatOptions extends ChatOptions {

	@Nullable PromptCacheStrategy getPromptCacheStrategy();

	void setPromptCacheStrategy(@Nullable PromptCacheStrategy strategy);

	@Nullable Duration getPromptCacheTtl();

	void setPromptCacheTtl(@Nullable Duration ttl);

	@Nullable Integer getPromptCacheMinContentLength();

	void setPromptCacheMinContentLength(@Nullable Integer minContentLength);

	@Nullable Boolean getPromptCacheMultiBlockSystemCaching();

	void setPromptCacheMultiBlockSystemCaching(@Nullable Boolean multiBlockSystemCaching);

	@Nullable Map<MessageType, Duration> getPromptCacheMessageTypeTtl();

	void setPromptCacheMessageTypeTtl(@Nullable Map<MessageType, Duration> messageTypeTtl);

	@Nullable Map<MessageType, Integer> getPromptCacheMessageTypeMinContentLengths();

	void setPromptCacheMessageTypeMinContentLengths(@Nullable Map<MessageType, Integer> messageTypeMinContentLengths);

	@Nullable Function<@Nullable String, Integer> getPromptCacheContentLengthFunction();

	void setPromptCacheContentLengthFunction(@Nullable Function<@Nullable String, Integer> contentLengthFunction);

	interface Builder<B extends Builder<B>> {

		B promptCacheStrategy(@Nullable PromptCacheStrategy strategy);

		B promptCacheTtl(@Nullable Duration ttl);

		B promptCacheMinContentLength(@Nullable Integer minContentLength);

		B promptCacheMultiBlockSystemCaching(@Nullable Boolean multiBlockSystemCaching);

		B promptCacheMessageTypeTtl(@Nullable Map<MessageType, Duration> messageTypeTtl);

		B promptCacheMessageTypeMinContentLengths(@Nullable Map<MessageType, Integer> messageTypeMinContentLengths);

		B promptCacheContentLengthFunction(@Nullable Function<@Nullable String, Integer> contentLengthFunction);

	}

}
