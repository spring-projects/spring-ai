/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.chat.messages;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AssistantMessage}.
 *
 * @author Thomas Vitale
 */
class AssistantMessageTests {

	@Test
	void whenMediaIsNullThenThrow() {
		assertThatThrownBy(() -> AssistantMessage.builder().media(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Media must not be null");
	}

	@Test
	void whenMetadataIsNullThenThrow() {
		assertThatThrownBy(() -> AssistantMessage.builder().properties(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Metadata must not be null");
	}

	@Test
	void whenToolCallsIsNullThenThrow() {
		assertThatThrownBy(() -> AssistantMessage.builder().toolCalls(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Tool calls must not be null");
	}

}
