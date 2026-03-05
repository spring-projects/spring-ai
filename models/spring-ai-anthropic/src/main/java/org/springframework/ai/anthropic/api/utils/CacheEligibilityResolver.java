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

package org.springframework.ai.anthropic.api.utils;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicCacheOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.api.AnthropicCacheTtl;
import org.springframework.ai.anthropic.api.AnthropicCacheType;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.util.Assert;

/**
 * Resolves cache eligibility for messages based on the provided
 * {@link AnthropicCacheOptions}.
 *
 * Note: Tool definition messages are always considered for caching if the strategy
 * includes system messages. The minimum content length check is not applied to tool
 * definition messages.
 *
 * @author Austin Dase
 * @author Soby Chacko
 * @since 1.1.0
 **/
public class CacheEligibilityResolver {

	private static final Logger logger = LoggerFactory.getLogger(CacheEligibilityResolver.class);

	// Tool definition messages are always considered for caching if the strategy
	// includes system messages.
	private static final MessageType TOOL_DEFINITION_MESSAGE_TYPE = MessageType.SYSTEM;

	private final CacheBreakpointTracker cacheBreakpointTracker = new CacheBreakpointTracker();

	private final AnthropicCacheType anthropicCacheType = AnthropicCacheType.EPHEMERAL;

	private final AnthropicCacheStrategy cacheStrategy;

	private final Map<MessageType, AnthropicCacheTtl> messageTypeTtl;

	private final Map<MessageType, Integer> messageTypeMinContentLengths;

	private final Function<@Nullable String, Integer> contentLengthFunction;

	private final Set<MessageType> cacheEligibleMessageTypes;

	public CacheEligibilityResolver(AnthropicCacheStrategy cacheStrategy,
			Map<MessageType, AnthropicCacheTtl> messageTypeTtl, Map<MessageType, Integer> messageTypeMinContentLengths,
			Function<@Nullable String, Integer> contentLengthFunction, Set<MessageType> cacheEligibleMessageTypes) {
		this.cacheStrategy = cacheStrategy;
		this.messageTypeTtl = messageTypeTtl;
		this.messageTypeMinContentLengths = messageTypeMinContentLengths;
		this.contentLengthFunction = contentLengthFunction;
		this.cacheEligibleMessageTypes = cacheEligibleMessageTypes;
	}

	public static CacheEligibilityResolver from(AnthropicCacheOptions anthropicCacheOptions) {
		AnthropicCacheStrategy strategy = anthropicCacheOptions.getStrategy();
		return new CacheEligibilityResolver(strategy, anthropicCacheOptions.getMessageTypeTtl(),
				anthropicCacheOptions.getMessageTypeMinContentLengths(),
				anthropicCacheOptions.getContentLengthFunction(), extractEligibleMessageTypes(strategy));
	}

	private static Set<MessageType> extractEligibleMessageTypes(AnthropicCacheStrategy anthropicCacheStrategy) {
		return switch (anthropicCacheStrategy) {
			case NONE -> Set.of();
			case SYSTEM_ONLY, SYSTEM_AND_TOOLS -> Set.of(MessageType.SYSTEM);
			case TOOLS_ONLY -> Set.of(); // No message types cached, only tool definitions
			case CONVERSATION_HISTORY -> Set.of(MessageType.values());
		};
	}

	public AnthropicApi.ChatCompletionRequest.@Nullable CacheControl resolve(MessageType messageType,
			@Nullable String content) {
		Integer length = this.contentLengthFunction.apply(content);
		Integer minLength = this.messageTypeMinContentLengths.get(messageType);
		Assert.state(minLength != null, "The minimum content length of the message type must be defined");
		if (this.cacheStrategy == AnthropicCacheStrategy.NONE || !this.cacheEligibleMessageTypes.contains(messageType)
				|| length < minLength || this.cacheBreakpointTracker.allBreakpointsAreUsed()) {
			logger.debug(
					"Caching not enabled for messageType={}, contentLength={}, minContentLength={}, cacheStrategy={}, usedBreakpoints={}",
					messageType, length, minLength, this.cacheStrategy, this.cacheBreakpointTracker.getCount());
			return null;
		}

		AnthropicCacheTtl anthropicCacheTtl = this.messageTypeTtl.get(messageType);
		Assert.state(anthropicCacheTtl != null, "The message type ttl of the message type must be defined");

		logger.debug("Caching enabled for messageType={}, ttl={}", messageType, anthropicCacheTtl);

		return this.anthropicCacheType.cacheControl(anthropicCacheTtl.getValue());
	}

	public AnthropicApi.ChatCompletionRequest.@Nullable CacheControl resolveToolCacheControl() {
		// Tool definitions are cache-eligible for TOOLS_ONLY, SYSTEM_AND_TOOLS, and
		// CONVERSATION_HISTORY strategies. SYSTEM_ONLY caches only system messages,
		// relying on Anthropic's cache hierarchy to implicitly cache tools.
		if (this.cacheStrategy != AnthropicCacheStrategy.TOOLS_ONLY
				&& this.cacheStrategy != AnthropicCacheStrategy.SYSTEM_AND_TOOLS
				&& this.cacheStrategy != AnthropicCacheStrategy.CONVERSATION_HISTORY) {
			logger.debug("Caching not enabled for tool definition, cacheStrategy={}", this.cacheStrategy);
			return null;
		}

		if (this.cacheBreakpointTracker.allBreakpointsAreUsed()) {
			logger.debug("Caching not enabled for tool definition, usedBreakpoints={}",
					this.cacheBreakpointTracker.getCount());
			return null;
		}

		AnthropicCacheTtl anthropicCacheTtl = this.messageTypeTtl.get(TOOL_DEFINITION_MESSAGE_TYPE);
		Assert.state(anthropicCacheTtl != null, "messageTypeTtl must contain a 'system' entry");

		logger.debug("Caching enabled for tool definition, ttl={}", anthropicCacheTtl);

		return this.anthropicCacheType.cacheControl(anthropicCacheTtl.getValue());
	}

	public boolean isCachingEnabled() {
		return this.cacheStrategy != AnthropicCacheStrategy.NONE;
	}

	public void useCacheBlock() {
		this.cacheBreakpointTracker.use();
	}

}
