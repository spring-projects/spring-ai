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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.anthropic.models.messages.CacheControlEphemeral;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.PromptCacheChatOptions;
import org.springframework.ai.chat.prompt.PromptCacheStrategy;
import org.springframework.util.Assert;

/**
 * Resolves cache eligibility for messages based on {@link PromptCacheChatOptions} fields.
 * Returns SDK {@link CacheControlEphemeral} instances for cache-eligible content.
 *
 * @author Austin Dase
 * @author Soby Chacko
 * @since 1.1.0
 */
public class AnthropicCacheEligibilityResolver {

	private static final Logger logger = LoggerFactory.getLogger(AnthropicCacheEligibilityResolver.class);

	private static final Function<@Nullable String, Integer> DEFAULT_CONTENT_LENGTH_FUNCTION = s -> s != null
			? s.length() : 0;

	private static final int DEFAULT_MIN_CONTENT_LENGTH = 1;

	private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

	private final CacheBreakpointTracker cacheBreakpointTracker = new CacheBreakpointTracker();

	private final PromptCacheStrategy strategy;

	private final Map<MessageType, Duration> messageTypeTtl;

	private final Map<MessageType, Integer> messageTypeMinContentLengths;

	private final Function<@Nullable String, Integer> contentLengthFunction;

	private final Set<MessageType> cacheEligibleMessageTypes;

	private final boolean multiBlockSystemCaching;

	AnthropicCacheEligibilityResolver(PromptCacheStrategy strategy, Map<MessageType, Duration> messageTypeTtl,
			Map<MessageType, Integer> messageTypeMinContentLengths,
			Function<@Nullable String, Integer> contentLengthFunction, Set<MessageType> cacheEligibleMessageTypes,
			boolean multiBlockSystemCaching) {
		this.strategy = strategy;
		this.messageTypeTtl = messageTypeTtl;
		this.messageTypeMinContentLengths = messageTypeMinContentLengths;
		this.contentLengthFunction = contentLengthFunction;
		this.cacheEligibleMessageTypes = cacheEligibleMessageTypes;
		this.multiBlockSystemCaching = multiBlockSystemCaching;
	}

	/**
	 * Creates a resolver from portable prompt cache options.
	 */
	public static AnthropicCacheEligibilityResolver from(PromptCacheChatOptions options) {
		PromptCacheStrategy strategy = options.getPromptCacheStrategy() != null ? options.getPromptCacheStrategy()
				: PromptCacheStrategy.NONE;

		Map<MessageType, Duration> ttlMap = buildTtlMap(options);
		Map<MessageType, Integer> minLengthMap = buildMinContentLengthMap(options);

		Function<@Nullable String, Integer> lengthFn = options.getPromptCacheContentLengthFunction() != null
				? options.getPromptCacheContentLengthFunction() : DEFAULT_CONTENT_LENGTH_FUNCTION;

		boolean multiBlock = options.getPromptCacheMultiBlockSystemCaching() != null
				&& options.getPromptCacheMultiBlockSystemCaching();

		return new AnthropicCacheEligibilityResolver(strategy, ttlMap, minLengthMap, lengthFn,
				extractEligibleMessageTypes(strategy), multiBlock);
	}

	private static Map<MessageType, Duration> buildTtlMap(PromptCacheChatOptions options) {
		Duration globalTtl = options.getPromptCacheTtl() != null ? options.getPromptCacheTtl() : DEFAULT_TTL;
		Map<MessageType, Duration> map = new HashMap<>();
		for (MessageType mt : MessageType.values()) {
			map.put(mt, globalTtl);
		}
		if (options.getPromptCacheMessageTypeTtl() != null) {
			map.putAll(options.getPromptCacheMessageTypeTtl());
		}
		return map;
	}

	private static Map<MessageType, Integer> buildMinContentLengthMap(PromptCacheChatOptions options) {
		int globalMin = options.getPromptCacheMinContentLength() != null ? options.getPromptCacheMinContentLength()
				: DEFAULT_MIN_CONTENT_LENGTH;
		Map<MessageType, Integer> map = new HashMap<>();
		for (MessageType mt : MessageType.values()) {
			map.put(mt, globalMin);
		}
		if (options.getPromptCacheMessageTypeMinContentLengths() != null) {
			map.putAll(options.getPromptCacheMessageTypeMinContentLengths());
		}
		return map;
	}

	private static Set<MessageType> extractEligibleMessageTypes(PromptCacheStrategy strategy) {
		return switch (strategy) {
			case NONE -> Set.of();
			case SYSTEM_ONLY, SYSTEM_AND_TOOLS -> Set.of(MessageType.SYSTEM);
			case TOOLS_ONLY -> Set.of();
			case CONVERSATION_HISTORY -> Set.of(MessageType.values());
		};
	}

	private static CacheControlEphemeral.Ttl toSdkTtl(Duration duration) {
		return duration.toMinutes() > 5 ? CacheControlEphemeral.Ttl.TTL_1H : CacheControlEphemeral.Ttl.TTL_5M;
	}

	public @Nullable CacheControlEphemeral resolve(MessageType messageType, @Nullable String content) {
		Integer length = this.contentLengthFunction.apply(content);
		Integer minLength = this.messageTypeMinContentLengths.get(messageType);
		Assert.state(minLength != null, "The minimum content length of the message type must be defined");
		if (this.strategy == PromptCacheStrategy.NONE || !this.cacheEligibleMessageTypes.contains(messageType)
				|| length < minLength || this.cacheBreakpointTracker.allBreakpointsAreUsed()) {
			logger.debug(
					"Caching not enabled for messageType={}, contentLength={}, minContentLength={}, strategy={}, usedBreakpoints={}",
					messageType, length, minLength, this.strategy, this.cacheBreakpointTracker.getCount());
			return null;
		}

		Duration ttl = this.messageTypeTtl.get(messageType);
		Assert.state(ttl != null, "The TTL for the message type must be defined");

		logger.debug("Caching enabled for messageType={}, ttl={}", messageType, ttl);

		return CacheControlEphemeral.builder().ttl(toSdkTtl(ttl)).build();
	}

	public @Nullable CacheControlEphemeral resolveToolCacheControl() {
		if (this.strategy != PromptCacheStrategy.TOOLS_ONLY && this.strategy != PromptCacheStrategy.SYSTEM_AND_TOOLS
				&& this.strategy != PromptCacheStrategy.CONVERSATION_HISTORY) {
			logger.debug("Caching not enabled for tool definition, strategy={}", this.strategy);
			return null;
		}

		if (this.cacheBreakpointTracker.allBreakpointsAreUsed()) {
			logger.debug("Caching not enabled for tool definition, usedBreakpoints={}",
					this.cacheBreakpointTracker.getCount());
			return null;
		}

		Duration ttl = this.messageTypeTtl.get(MessageType.SYSTEM);
		Assert.state(ttl != null, "messageTypeTtl must contain a SYSTEM entry for tool caching");

		logger.debug("Caching enabled for tool definition, ttl={}", ttl);

		return CacheControlEphemeral.builder().ttl(toSdkTtl(ttl)).build();
	}

	public boolean isCachingEnabled() {
		return this.strategy != PromptCacheStrategy.NONE;
	}

	public boolean isMultiBlockSystemCaching() {
		return this.multiBlockSystemCaching;
	}

	public void useCacheBlock() {
		this.cacheBreakpointTracker.use();
	}

}
