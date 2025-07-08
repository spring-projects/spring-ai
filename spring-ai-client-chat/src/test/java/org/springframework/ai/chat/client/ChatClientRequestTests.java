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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ChatClientRequest}.
 *
 * @author Thomas Vitale
 */
class ChatClientRequestTests {

	@Test
	void whenPromptIsNullThenThrow() {
		assertThatThrownBy(() -> new ChatClientRequest(null, Map.of())).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("prompt cannot be null");

		assertThatThrownBy(() -> ChatClientRequest.builder().prompt(null).context(Map.of()).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("prompt cannot be null");
	}

	@Test
	void whenContextIsNullThenThrow() {
		assertThatThrownBy(() -> new ChatClientRequest(new Prompt(), null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context cannot be null");

		assertThatThrownBy(() -> ChatClientRequest.builder().prompt(new Prompt()).context(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context cannot be null");
	}

	@Test
	void whenContextHasNullKeysThenThrow() {
		Map<String, Object> context = new HashMap<>();
		context.put(null, "something");
		assertThatThrownBy(() -> new ChatClientRequest(new Prompt(), context))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context keys cannot be null");
	}

	@Test
	void whenCopyThenImmutableContext() {
		Map<String, Object> context = new HashMap<>();
		context.put("key", "value");
		ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt()).context(context).build();

		ChatClientRequest copy = request.copy();

		copy.context().put("key", "newValue");
		assertThat(request.context()).isEqualTo(Map.of("key", "value"));
	}

	@Test
	void whenMutateThenImmutableContext() {
		Map<String, Object> context = new HashMap<>();
		context.put("key", "value");
		ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt()).context(context).build();

		ChatClientRequest copy = request.mutate().context("key", "newValue").build();

		assertThat(request.context()).isEqualTo(Map.of("key", "value"));
		assertThat(copy.context()).isEqualTo(Map.of("key", "newValue"));
	}

}
