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

import com.anthropic.models.messages.CacheControlEphemeral;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.MessageType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CacheEligibilityResolver}.
 *
 * @author Soby Chacko
 */
class CacheEligibilityResolverTests {

	@Test
	void noCachingWhenStrategyNone() {
		AnthropicCacheOptions options = AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.NONE).build();
		CacheEligibilityResolver resolver = CacheEligibilityResolver.from(options);
		assertThat(resolver.isCachingEnabled()).isFalse();
		assertThat(resolver.resolve(MessageType.SYSTEM, "some text")).isNull();
		assertThat(resolver.resolveToolCacheControl()).isNull();
	}

	@Test
	void systemCachingRespectsMinLength() {
		AnthropicCacheOptions options = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
			.messageTypeMinContentLength(MessageType.SYSTEM, 10)
			.build();
		CacheEligibilityResolver resolver = CacheEligibilityResolver.from(options);

		// Below min length -> no cache
		assertThat(resolver.resolve(MessageType.SYSTEM, "short")).isNull();

		// Above min length -> cache control with default TTL
		CacheControlEphemeral cc = resolver.resolve(MessageType.SYSTEM, "01234567890");
		assertThat(cc).isNotNull();
		assertThat(cc.ttl()).isPresent();
		assertThat(cc.ttl().get()).isEqualTo(CacheControlEphemeral.Ttl.TTL_5M);
	}

	@Test
	void emptyTextShouldNotBeCachedEvenIfMinIsZero() {
		AnthropicCacheOptions options = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
			.build();
		CacheEligibilityResolver resolver = CacheEligibilityResolver.from(options);
		assertThat(resolver.resolve(MessageType.SYSTEM, "")).isNull();
		assertThat(resolver.resolve(MessageType.SYSTEM, null)).isNull();
	}

	@Test
	void toolCacheControlRespectsStrategy() {
		// NONE -> no tool caching
		CacheEligibilityResolver none = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.NONE).build());
		assertThat(none.resolveToolCacheControl()).isNull();

		// SYSTEM_ONLY -> no explicit tool caching
		CacheEligibilityResolver sys = CacheEligibilityResolver.from(AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
			.messageTypeTtl(MessageType.SYSTEM, AnthropicCacheTtl.ONE_HOUR)
			.build());
		assertThat(sys.resolveToolCacheControl()).isNull();

		// TOOLS_ONLY -> tool caching enabled, system messages NOT cached
		CacheEligibilityResolver toolsOnly = CacheEligibilityResolver.from(AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.TOOLS_ONLY)
			.messageTypeTtl(MessageType.SYSTEM, AnthropicCacheTtl.ONE_HOUR)
			.build());
		assertThat(toolsOnly.resolveToolCacheControl()).isNotNull();
		assertThat(toolsOnly.resolve(MessageType.SYSTEM, "Large system prompt text")).isNull();

		// SYSTEM_AND_TOOLS -> tool caching enabled (uses SYSTEM TTL)
		CacheEligibilityResolver sysAndTools = CacheEligibilityResolver.from(AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS)
			.messageTypeTtl(MessageType.SYSTEM, AnthropicCacheTtl.ONE_HOUR)
			.build());
		CacheControlEphemeral cc = sysAndTools.resolveToolCacheControl();
		assertThat(cc).isNotNull();
		assertThat(cc.ttl()).isPresent();
		assertThat(cc.ttl().get()).isEqualTo(CacheControlEphemeral.Ttl.TTL_1H);

		// CONVERSATION_HISTORY -> tool caching enabled
		CacheEligibilityResolver history = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY).build());
		assertThat(history.resolveToolCacheControl()).isNotNull();
	}

	@Test
	void toolsOnlyStrategyBehavior() {
		AnthropicCacheOptions options = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.TOOLS_ONLY)
			.messageTypeMinContentLength(MessageType.SYSTEM, 100)
			.build();
		CacheEligibilityResolver resolver = CacheEligibilityResolver.from(options);

		assertThat(resolver.isCachingEnabled()).isTrue();
		assertThat(resolver.resolve(MessageType.SYSTEM, "Large system prompt with plenty of content")).isNull();
		assertThat(resolver.resolve(MessageType.USER, "User message content")).isNull();
		assertThat(resolver.resolve(MessageType.ASSISTANT, "Assistant message content")).isNull();
		assertThat(resolver.resolve(MessageType.TOOL, "Tool result content")).isNull();

		CacheControlEphemeral toolCache = resolver.resolveToolCacheControl();
		assertThat(toolCache).isNotNull();
	}

	@Test
	void breakpointCountForEachStrategy() {
		// NONE: 0 breakpoints
		CacheEligibilityResolver none = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.NONE).build());
		assertThat(none.resolveToolCacheControl()).isNull();
		assertThat(none.resolve(MessageType.SYSTEM, "content")).isNull();

		// SYSTEM_ONLY: system cached, tools not explicitly cached
		CacheEligibilityResolver systemOnly = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_ONLY).build());
		assertThat(systemOnly.resolveToolCacheControl()).isNull();
		assertThat(systemOnly.resolve(MessageType.SYSTEM, "content")).isNotNull();

		// TOOLS_ONLY: tools cached, system not cached
		CacheEligibilityResolver toolsOnly = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.TOOLS_ONLY).build());
		assertThat(toolsOnly.resolveToolCacheControl()).isNotNull();
		assertThat(toolsOnly.resolve(MessageType.SYSTEM, "content")).isNull();

		// SYSTEM_AND_TOOLS: both cached
		CacheEligibilityResolver systemAndTools = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS).build());
		assertThat(systemAndTools.resolveToolCacheControl()).isNotNull();
		assertThat(systemAndTools.resolve(MessageType.SYSTEM, "content")).isNotNull();
	}

	@Test
	void messageTypeEligibilityPerStrategy() {
		// NONE: No message types eligible
		CacheEligibilityResolver none = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.NONE).build());
		assertThat(none.resolve(MessageType.SYSTEM, "content")).isNull();
		assertThat(none.resolve(MessageType.USER, "content")).isNull();
		assertThat(none.resolve(MessageType.ASSISTANT, "content")).isNull();
		assertThat(none.resolve(MessageType.TOOL, "content")).isNull();

		// SYSTEM_ONLY: Only SYSTEM eligible
		CacheEligibilityResolver systemOnly = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_ONLY).build());
		assertThat(systemOnly.resolve(MessageType.SYSTEM, "content")).isNotNull();
		assertThat(systemOnly.resolve(MessageType.USER, "content")).isNull();
		assertThat(systemOnly.resolve(MessageType.ASSISTANT, "content")).isNull();
		assertThat(systemOnly.resolve(MessageType.TOOL, "content")).isNull();

		// TOOLS_ONLY: No message types eligible
		CacheEligibilityResolver toolsOnly = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.TOOLS_ONLY).build());
		assertThat(toolsOnly.resolve(MessageType.SYSTEM, "content")).isNull();
		assertThat(toolsOnly.resolve(MessageType.USER, "content")).isNull();
		assertThat(toolsOnly.resolve(MessageType.ASSISTANT, "content")).isNull();
		assertThat(toolsOnly.resolve(MessageType.TOOL, "content")).isNull();

		// SYSTEM_AND_TOOLS: Only SYSTEM eligible
		CacheEligibilityResolver systemAndTools = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS).build());
		assertThat(systemAndTools.resolve(MessageType.SYSTEM, "content")).isNotNull();
		assertThat(systemAndTools.resolve(MessageType.USER, "content")).isNull();
		assertThat(systemAndTools.resolve(MessageType.ASSISTANT, "content")).isNull();
		assertThat(systemAndTools.resolve(MessageType.TOOL, "content")).isNull();

		// CONVERSATION_HISTORY: All message types eligible
		CacheEligibilityResolver history = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY).build());
		assertThat(history.resolve(MessageType.SYSTEM, "content")).isNotNull();
		assertThat(history.resolve(MessageType.USER, "content")).isNotNull();
		assertThat(history.resolve(MessageType.ASSISTANT, "content")).isNotNull();
		assertThat(history.resolve(MessageType.TOOL, "content")).isNotNull();
	}

	@Test
	void systemAndToolsIndependentBreakpoints() {
		CacheEligibilityResolver resolver = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS).build());

		CacheControlEphemeral toolCache = resolver.resolveToolCacheControl();
		CacheControlEphemeral systemCache = resolver.resolve(MessageType.SYSTEM, "content");

		assertThat(toolCache).isNotNull();
		assertThat(systemCache).isNotNull();
		assertThat(toolCache.ttl()).isEqualTo(systemCache.ttl());
	}

	@Test
	void breakpointLimitEnforced() {
		AnthropicCacheOptions options = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
			.build();
		CacheEligibilityResolver resolver = CacheEligibilityResolver.from(options);

		// Use up breakpoints
		resolver.resolve(MessageType.SYSTEM, "content");
		resolver.useCacheBlock();
		resolver.resolve(MessageType.USER, "content");
		resolver.useCacheBlock();
		resolver.resolve(MessageType.ASSISTANT, "content");
		resolver.useCacheBlock();
		resolver.resolve(MessageType.TOOL, "content");
		resolver.useCacheBlock();

		// 5th attempt should return null
		assertThat(resolver.resolve(MessageType.USER, "more content"))
			.as("Should return null when all 4 breakpoints are used")
			.isNull();
	}

	@Test
	void emptyAndNullContentHandling() {
		CacheEligibilityResolver resolver = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY).build());

		assertThat(resolver.resolve(MessageType.SYSTEM, "")).as("Empty string should not be cached").isNull();
		assertThat(resolver.resolve(MessageType.SYSTEM, null)).as("Null content should not be cached").isNull();
		assertThat(resolver.resolve(MessageType.SYSTEM, "   "))
			.as("Whitespace-only content meeting length requirements should be cacheable")
			.isNotNull();
	}

	@Test
	void oneHourTtlReturnedForConfiguredMessageType() {
		AnthropicCacheOptions options = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
			.messageTypeTtl(MessageType.SYSTEM, AnthropicCacheTtl.ONE_HOUR)
			.build();
		CacheEligibilityResolver resolver = CacheEligibilityResolver.from(options);

		CacheControlEphemeral cc = resolver.resolve(MessageType.SYSTEM, "enough content");
		assertThat(cc).isNotNull();
		assertThat(cc.ttl()).isPresent();
		assertThat(cc.ttl().get()).isEqualTo(CacheControlEphemeral.Ttl.TTL_1H);
	}

}
