/*
 * Copyright 2025-2025 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicCacheOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.api.AnthropicCacheTtl;
import org.springframework.ai.chat.messages.MessageType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CacheEligibilityResolver}.
 *
 * @author Austin Dase
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
		AnthropicApi.ChatCompletionRequest.CacheControl cc = resolver.resolve(MessageType.SYSTEM, "01234567890");
		assertThat(cc).isNotNull();
		assertThat(cc.type()).isEqualTo("ephemeral");
		assertThat(cc.ttl()).isEqualTo(AnthropicCacheTtl.FIVE_MINUTES.getValue());
	}

	@Test
	void emptyTextShouldNotBeCachedEvenIfMinIsZero() {
		AnthropicCacheOptions options = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
			// default min content length is 0
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

		// SYSTEM_ONLY -> no explicit tool caching (tools cached implicitly via hierarchy)
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
		var cc = sysAndTools.resolveToolCacheControl();
		assertThat(cc).isNotNull();
		assertThat(cc.ttl()).isEqualTo(AnthropicCacheTtl.ONE_HOUR.getValue());

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

		// Caching is enabled
		assertThat(resolver.isCachingEnabled()).isTrue();

		// System messages should NOT be cached
		assertThat(resolver.resolve(MessageType.SYSTEM, "Large system prompt with plenty of content"))
			.as("System messages should not be cached with TOOLS_ONLY strategy")
			.isNull();

		// User messages should NOT be cached
		assertThat(resolver.resolve(MessageType.USER, "User message content")).isNull();

		// Assistant messages should NOT be cached
		assertThat(resolver.resolve(MessageType.ASSISTANT, "Assistant message content")).isNull();

		// Tool messages should NOT be cached
		assertThat(resolver.resolve(MessageType.TOOL, "Tool result content")).isNull();

		// Tool definitions SHOULD be cached
		AnthropicApi.ChatCompletionRequest.CacheControl toolCache = resolver.resolveToolCacheControl();
		assertThat(toolCache).as("Tool definitions should be cached with TOOLS_ONLY strategy").isNotNull();
		assertThat(toolCache.type()).isEqualTo("ephemeral");
	}

	@Test
	void breakpointCountForEachStrategy() {
		// NONE: 0 breakpoints
		CacheEligibilityResolver none = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.NONE).build());
		assertThat(none.resolveToolCacheControl()).isNull();
		assertThat(none.resolve(MessageType.SYSTEM, "content")).isNull();

		// SYSTEM_ONLY: 1 breakpoint (system only, tools implicit)
		CacheEligibilityResolver systemOnly = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_ONLY).build());
		assertThat(systemOnly.resolveToolCacheControl()).as("SYSTEM_ONLY should not explicitly cache tools").isNull();
		assertThat(systemOnly.resolve(MessageType.SYSTEM, "content")).isNotNull();

		// TOOLS_ONLY: 1 breakpoint (tools only)
		CacheEligibilityResolver toolsOnly = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.TOOLS_ONLY).build());
		assertThat(toolsOnly.resolveToolCacheControl()).as("TOOLS_ONLY should cache tools").isNotNull();
		assertThat(toolsOnly.resolve(MessageType.SYSTEM, "content")).as("TOOLS_ONLY should not cache system").isNull();

		// SYSTEM_AND_TOOLS: 2 breakpoints (tools + system)
		CacheEligibilityResolver systemAndTools = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS).build());
		assertThat(systemAndTools.resolveToolCacheControl()).as("SYSTEM_AND_TOOLS should cache tools").isNotNull();
		assertThat(systemAndTools.resolve(MessageType.SYSTEM, "content")).as("SYSTEM_AND_TOOLS should cache system")
			.isNotNull();
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

		// TOOLS_ONLY: No message types eligible (only tool definitions)
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
	void toolsOnlyIsolationFromSystemChanges() {
		// Validates that TOOLS_ONLY resolver behavior is consistent
		// regardless of system message content (simulating different system prompts)
		CacheEligibilityResolver resolver = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.TOOLS_ONLY).build());

		// Different system prompts should all be ineligible for caching
		assertThat(resolver.resolve(MessageType.SYSTEM, "You are a helpful assistant"))
			.as("System prompt 1 should not be cached")
			.isNull();
		assertThat(resolver.resolve(MessageType.SYSTEM, "You are a STRICT validator"))
			.as("System prompt 2 should not be cached")
			.isNull();
		assertThat(resolver.resolve(MessageType.SYSTEM, "You are a creative writer"))
			.as("System prompt 3 should not be cached")
			.isNull();

		// Tool cache eligibility should remain consistent
		assertThat(resolver.resolveToolCacheControl()).as("Tools should always be cacheable").isNotNull();
	}

	@Test
	void systemAndToolsIndependentBreakpoints() {
		// Validates that SYSTEM_AND_TOOLS creates two independent eligibility checks
		CacheEligibilityResolver resolver = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS).build());

		// Both tools and system should be independently eligible
		AnthropicApi.ChatCompletionRequest.CacheControl toolCache = resolver.resolveToolCacheControl();
		AnthropicApi.ChatCompletionRequest.CacheControl systemCache = resolver.resolve(MessageType.SYSTEM, "content");

		assertThat(toolCache).as("Tools should be cacheable").isNotNull();
		assertThat(systemCache).as("System should be cacheable").isNotNull();

		// They should use the same TTL (both use SYSTEM message type TTL)
		assertThat(toolCache.ttl()).isEqualTo(systemCache.ttl());
	}

	@Test
	void breakpointLimitEnforced() {
		AnthropicCacheOptions options = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
			.build();
		CacheEligibilityResolver resolver = CacheEligibilityResolver.from(options);

		// Use up breakpoints by resolving multiple times
		resolver.resolve(MessageType.SYSTEM, "content"); // Uses breakpoint 1
		resolver.useCacheBlock();
		resolver.resolve(MessageType.USER, "content"); // Uses breakpoint 2
		resolver.useCacheBlock();
		resolver.resolve(MessageType.ASSISTANT, "content"); // Uses breakpoint 3
		resolver.useCacheBlock();
		resolver.resolve(MessageType.TOOL, "content"); // Uses breakpoint 4
		resolver.useCacheBlock();

		// 5th attempt should return null (all 4 breakpoints used)
		assertThat(resolver.resolve(MessageType.USER, "more content"))
			.as("Should return null when all 4 breakpoints are used")
			.isNull();
	}

	@Test
	void emptyAndNullContentHandling() {
		CacheEligibilityResolver resolver = CacheEligibilityResolver
			.from(AnthropicCacheOptions.builder().strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY).build());

		// Empty string should not be cached
		assertThat(resolver.resolve(MessageType.SYSTEM, "")).as("Empty string should not be cached").isNull();

		// Null should not be cached
		assertThat(resolver.resolve(MessageType.SYSTEM, null)).as("Null content should not be cached").isNull();

		// Whitespace-only should be cached if it meets length requirement
		assertThat(resolver.resolve(MessageType.SYSTEM, "   "))
			.as("Whitespace-only content meeting length requirements should be cacheable")
			.isNotNull();
	}

}
