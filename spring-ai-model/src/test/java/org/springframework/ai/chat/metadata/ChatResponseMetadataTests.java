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

package org.springframework.ai.chat.metadata;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChatResponseMetadata}.
 *
 * @author Christian Tzolov
 */
class ChatResponseMetadataTests {

	@Test
	void metadataWithDefaultUsageIsEqual() {
		// EmptyRateLimit and PromptMetadata.empty() compare by identity, so both are
		// shared
		// to show that the default usage alone no longer breaks equality
		RateLimit rateLimit = new EmptyRateLimit();
		PromptMetadata promptMetadata = PromptMetadata.empty();
		var metadata = ChatResponseMetadata.builder()
			.id("chat_1")
			.model("m")
			.rateLimit(rateLimit)
			.promptMetadata(promptMetadata)
			.build();
		var same = ChatResponseMetadata.builder()
			.id("chat_1")
			.model("m")
			.rateLimit(rateLimit)
			.promptMetadata(promptMetadata)
			.build();

		assertThat(metadata).isEqualTo(same).hasSameHashCodeAs(same);
	}

	@Test
	void metadataWithDifferentUsageIsNotEqual() {
		var metadata = ChatResponseMetadata.builder().id("chat_1").build();

		assertThat(metadata)
			.isNotEqualTo(ChatResponseMetadata.builder().id("chat_1").usage(new DefaultUsage(1, 1)).build());
	}

}
