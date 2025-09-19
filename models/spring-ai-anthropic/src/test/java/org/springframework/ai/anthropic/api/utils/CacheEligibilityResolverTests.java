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

		// SYSTEM_ONLY -> tool caching enabled (uses SYSTEM TTL)
		CacheEligibilityResolver sys = CacheEligibilityResolver.from(AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
			.messageTypeTtl(MessageType.SYSTEM, AnthropicCacheTtl.ONE_HOUR)
			.build());
		var cc = sys.resolveToolCacheControl();
		assertThat(cc).isNotNull();
		assertThat(cc.ttl()).isEqualTo(AnthropicCacheTtl.ONE_HOUR.getValue());
	}

}
