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

package org.springframework.ai.bedrock.converse.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BedrockCacheOptions}.
 *
 * @author Soby Chacko
 */
class BedrockCacheOptionsTests {

	@Test
	void defaultsAreSane() {
		BedrockCacheOptions options = BedrockCacheOptions.builder().build();
		assertThat(options.getStrategy()).isEqualTo(BedrockCacheStrategy.NONE);
		assertThat(options.isMultiBlockSystemCaching()).isFalse();
	}

	@Test
	void builderSetsStrategy() {
		BedrockCacheOptions options = BedrockCacheOptions.builder().strategy(BedrockCacheStrategy.SYSTEM_ONLY).build();
		assertThat(options.getStrategy()).isEqualTo(BedrockCacheStrategy.SYSTEM_ONLY);
	}

	@Test
	void multiBlockSystemCachingDefaultsToFalse() {
		BedrockCacheOptions options = BedrockCacheOptions.builder().build();
		assertThat(options.isMultiBlockSystemCaching()).isFalse();
	}

	@Test
	void builderEnablesMultiBlockSystemCaching() {
		BedrockCacheOptions options = BedrockCacheOptions.builder()
			.strategy(BedrockCacheStrategy.SYSTEM_ONLY)
			.multiBlockSystemCaching(true)
			.build();
		assertThat(options.isMultiBlockSystemCaching()).isTrue();
	}

}
