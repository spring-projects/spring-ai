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

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.MessageType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AnthropicCacheOptions}.
 *
 * @author Soby Chacko
 */
@SuppressWarnings("deprecation")
class AnthropicCacheOptionsTests {

	@Test
	void defaultsAreSane() {
		AnthropicCacheOptions options = AnthropicCacheOptions.builder().build();

		assertThat(options.getStrategy()).isEqualTo(AnthropicCacheStrategy.NONE);
		assertThat(options.getMessageTypeTtl().get(MessageType.SYSTEM)).isEqualTo(AnthropicCacheTtl.FIVE_MINUTES);
		assertThat(options.getMessageTypeMinContentLengths().get(MessageType.SYSTEM)).isEqualTo(1);
		assertThat(options.getContentLengthFunction().apply("hello")).isEqualTo(5);
		assertThat(options.getContentLengthFunction().apply(null)).isEqualTo(0);
	}

	@Test
	void builderOverrides() {
		AnthropicCacheOptions options = AnthropicCacheOptions.builder()
			.strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS)
			.messageTypeTtl(MessageType.SYSTEM, AnthropicCacheTtl.ONE_HOUR)
			.messageTypeMinContentLength(MessageType.SYSTEM, 100)
			.contentLengthFunction(s -> s != null ? s.length() * 2 : 0)
			.build();

		assertThat(options.getStrategy()).isEqualTo(AnthropicCacheStrategy.SYSTEM_AND_TOOLS);
		assertThat(options.getMessageTypeTtl().get(MessageType.SYSTEM)).isEqualTo(AnthropicCacheTtl.ONE_HOUR);
		assertThat(options.getMessageTypeMinContentLengths().get(MessageType.SYSTEM)).isEqualTo(100);
		assertThat(options.getContentLengthFunction().apply("test")).isEqualTo(8);
	}

	@Test
	void multiBlockSystemCachingDefaultsToFalse() {
		AnthropicCacheOptions options = AnthropicCacheOptions.builder().build();
		assertThat(options.isMultiBlockSystemCaching()).isFalse();
	}

	@Test
	void multiBlockSystemCachingBuilderOverride() {
		AnthropicCacheOptions options = AnthropicCacheOptions.builder().multiBlockSystemCaching(true).build();
		assertThat(options.isMultiBlockSystemCaching()).isTrue();
	}

	@Test
	void disabledSingletonHasNoneStrategy() {
		assertThat(AnthropicCacheOptions.disabled().getStrategy()).isEqualTo(AnthropicCacheStrategy.NONE);
	}

}
