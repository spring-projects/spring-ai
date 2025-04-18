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

package org.springframework.ai.chat.client;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ChatClientResponse}.
 *
 * @author Thomas Vitale
 */
class ChatClientResponseTests {

	@Test
	void whenContextIsNullThenThrow() {
		assertThatThrownBy(() -> new ChatClientResponse(null, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context cannot be null");

		assertThatThrownBy(() -> ChatClientResponse.builder().chatResponse(null).context(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context cannot be null");
	}

	@Test
	void whenContextHasNullKeysThenThrow() {
		Map<String, Object> context = new HashMap<>();
		context.put(null, "something");
		assertThatThrownBy(() -> new ChatClientResponse(null, context)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context keys cannot be null");
	}

}
