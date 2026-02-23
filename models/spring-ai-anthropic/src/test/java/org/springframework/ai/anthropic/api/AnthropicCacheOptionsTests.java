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

package org.springframework.ai.anthropic.api;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.MessageType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AnthropicCacheOptions}.
 *
 * @author Austin Dase
 */
class AnthropicCacheOptionsTests {

	@Test
	void defaultsAreSane() {
		AnthropicCacheOptions options = new AnthropicCacheOptions();
		assertThat(options.getStrategy()).isEqualTo(AnthropicCacheStrategy.NONE);
		// All message types default to FIVE_MINUTES and min content length 1
		for (MessageType mt : MessageType.values()) {
			assertThat(options.getMessageTypeTtl().get(mt)).isEqualTo(AnthropicCacheTtl.FIVE_MINUTES);
			assertThat(options.getMessageTypeMinContentLengths().get(mt)).isEqualTo(1);
		}
		// Default content length function returns string length (null -> 0)
		assertThat(options.getContentLengthFunction().apply("abc")).isEqualTo(3);
		assertThat(options.getContentLengthFunction().apply(null)).isEqualTo(0);
	}

	@Test
	void multiBlockSystemCachingDefaultsToFalse() {
		AnthropicCacheOptions options = new AnthropicCacheOptions();
		assertThat(options.isMultiBlockSystemCaching()).isFalse();
	}

	@Test
	void multiBlockSystemCachingBuilderOverride() {
		AnthropicCacheOptions options = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
			.multiBlockSystemCaching(true)
			.build();
		assertThat(options.isMultiBlockSystemCaching()).isTrue();
	}

	@Test
	void builderOverrides() {
		Function<String, Integer> clf = s -> 123;
		AnthropicCacheOptions options = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS)
			.messageTypeTtl(MessageType.SYSTEM, AnthropicCacheTtl.ONE_HOUR)
			.messageTypeMinContentLength(MessageType.SYSTEM, 100)
			.contentLengthFunction(clf)
			.build();

		assertThat(options.getStrategy()).isEqualTo(AnthropicCacheStrategy.SYSTEM_AND_TOOLS);
		assertThat(options.getMessageTypeTtl().get(MessageType.SYSTEM)).isEqualTo(AnthropicCacheTtl.ONE_HOUR);
		assertThat(options.getMessageTypeMinContentLengths().get(MessageType.SYSTEM)).isEqualTo(100);
		assertThat(options.getContentLengthFunction()).isSameAs(clf);
	}

}
