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

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.anthropic.models.messages.CacheControlEphemeral;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.MessageType;
import org.springframework.util.Assert;

/**
 * Resolves cache eligibility for messages based on the provided
 * {@link AnthropicCacheOptions}. Returns SDK {@link CacheControlEphemeral} instances
 * instead of raw cache control records.
 *
 * @author Austin Dase
 * @author Soby Chacko
 * @since 1.1.0
 */
public class CacheEligibilityResolver {

	private static final Logger logger = LoggerFactory.getLogger(CacheEligibilityResolver.class);

	private static final MessageType TOOL_DEFINITION_MESSAGE_TYPE = MessageType.SYSTEM;

	private final CacheBreakpointTracker cacheBreakpointTracker = new CacheBreakpointTracker();

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

	public static CacheEligibilityResolver from(AnthropicCacheOptions cacheOptions) {
		AnthropicCacheStrategy strategy = cacheOptions.getStrategy();
		return new CacheEligibilityResolver(strategy, cacheOptions.getMessageTypeTtl(),
				cacheOptions.getMessageTypeMinContentLengths(), cacheOptions.getContentLengthFunction(),
				extractEligibleMessageTypes(strategy));
	}

	private static Set<MessageType> extractEligibleMessageTypes(AnthropicCacheStrategy strategy) {
		return switch (strategy) {
			case NONE -> Set.of();
			case SYSTEM_ONLY, SYSTEM_AND_TOOLS -> Set.of(MessageType.SYSTEM);
			case TOOLS_ONLY -> Set.of();
			case CONVERSATION_HISTORY -> Set.of(MessageType.values());
		};
	}

	public @Nullable CacheControlEphemeral resolve(MessageType messageType, @Nullable String content) {
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

		AnthropicCacheTtl cacheTtl = this.messageTypeTtl.get(messageType);
		Assert.state(cacheTtl != null, "The message type ttl of the message type must be defined");

		logger.debug("Caching enabled for messageType={}, ttl={}", messageType, cacheTtl);

		return CacheControlEphemeral.builder().ttl(cacheTtl.getSdkTtl()).build();
	}

	public @Nullable CacheControlEphemeral resolveToolCacheControl() {
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

		AnthropicCacheTtl cacheTtl = this.messageTypeTtl.get(TOOL_DEFINITION_MESSAGE_TYPE);
		Assert.state(cacheTtl != null, "messageTypeTtl must contain a 'system' entry");

		logger.debug("Caching enabled for tool definition, ttl={}", cacheTtl);

		return CacheControlEphemeral.builder().ttl(cacheTtl.getSdkTtl()).build();
	}

	public boolean isCachingEnabled() {
		return this.cacheStrategy != AnthropicCacheStrategy.NONE;
	}

	public void useCacheBlock() {
		this.cacheBreakpointTracker.use();
	}

}
