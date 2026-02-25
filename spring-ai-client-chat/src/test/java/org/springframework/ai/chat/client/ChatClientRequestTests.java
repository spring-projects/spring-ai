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
	void whenContextHasNullValuesThenThrow() {
		Map<String, Object> context = new HashMap<>();
		context.put("key", null);

		assertThatThrownBy(() -> new ChatClientRequest(new Prompt(), context))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context values cannot be null");

		assertThatThrownBy(() -> ChatClientRequest.builder().prompt(new Prompt()).context(context).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context values cannot be null");
	}

	@Test
	void whenBuilderContextMapHasNullKeyThenThrow() {
		Map<String, Object> context = new HashMap<>();
		context.put(null, "value");

		assertThatThrownBy(() -> ChatClientRequest.builder().prompt(new Prompt()).context(context).build())
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

	@Test
	void whenBuilderAddsNullValueThenThrow() {
		assertThatThrownBy(() -> ChatClientRequest.builder().prompt(new Prompt()).context("key", null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("value cannot be null");
	}

	@Test
	void whenBuilderWithMultipleContextEntriesThenSuccess() {
		Prompt prompt = new Prompt("test message");
		Map<String, Object> context = Map.of("key1", "value1", "key2", 42, "key3", true, "key4",
				Map.of("nested", "value"));

		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).context(context).build();

		assertThat(request.context()).hasSize(4);
		assertThat(request.context().get("key1")).isEqualTo("value1");
		assertThat(request.context().get("key2")).isEqualTo(42);
		assertThat(request.context().get("key3")).isEqualTo(true);
		assertThat(request.context().get("key4")).isEqualTo(Map.of("nested", "value"));
	}

	@Test
	void whenMutateWithNewContextKeysThenMerged() {
		Prompt prompt = new Prompt("test message");
		ChatClientRequest original = ChatClientRequest.builder()
			.prompt(prompt)
			.context(Map.of("existing", "value"))
			.build();

		ChatClientRequest mutated = original.mutate().context("new1", "newValue1").context("new2", "newValue2").build();

		assertThat(original.context()).hasSize(1);
		assertThat(mutated.context()).hasSize(3);
		assertThat(mutated.context().get("existing")).isEqualTo("value");
		assertThat(mutated.context().get("new1")).isEqualTo("newValue1");
		assertThat(mutated.context().get("new2")).isEqualTo("newValue2");
	}

	@Test
	void whenMutateWithOverridingContextKeysThenOverridden() {
		Prompt prompt = new Prompt("test message");
		ChatClientRequest original = ChatClientRequest.builder()
			.prompt(prompt)
			.context(Map.of("key", "originalValue", "other", "untouched"))
			.build();

		ChatClientRequest mutated = original.mutate().context("key", "newValue").build();

		assertThat(original.context().get("key")).isEqualTo("originalValue");
		assertThat(mutated.context().get("key")).isEqualTo("newValue");
		assertThat(mutated.context().get("other")).isEqualTo("untouched");
	}

	@Test
	void whenMutatePromptThenPromptChanged() {
		Prompt originalPrompt = new Prompt("original message");
		Prompt newPrompt = new Prompt("new message");

		ChatClientRequest original = ChatClientRequest.builder()
			.prompt(originalPrompt)
			.context(Map.of("key", "value"))
			.build();

		ChatClientRequest mutated = original.mutate().prompt(newPrompt).build();

		assertThat(original.prompt()).isEqualTo(originalPrompt);
		assertThat(mutated.prompt()).isEqualTo(newPrompt);
		assertThat(mutated.context()).isEqualTo(original.context());
	}

	@Test
	void whenMutateContextWithMapThenMerged() {
		Prompt prompt = new Prompt("test message");
		ChatClientRequest original = ChatClientRequest.builder()
			.prompt(prompt)
			.context(Map.of("existing", "value"))
			.build();

		Map<String, Object> newContext = Map.of("new1", "value1", "new2", "value2");
		ChatClientRequest mutated = original.mutate().context(newContext).build();

		assertThat(mutated.context()).hasSize(3);
		assertThat(mutated.context().get("existing")).isEqualTo("value");
		assertThat(mutated.context().get("new1")).isEqualTo("value1");
		assertThat(mutated.context().get("new2")).isEqualTo("value2");
	}

	@Test
	void whenContextContainsComplexObjectsThenPreserved() {
		Prompt prompt = new Prompt("test message");

		// Test with various object types
		Map<String, Object> nestedMap = Map.of("nested", "value");
		java.util.List<String> list = java.util.List.of("item1", "item2");

		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(prompt)
			.context(Map.of("map", nestedMap, "list", list, "string", "value", "number", 123, "boolean", true))
			.build();

		assertThat(request.context().get("map")).isEqualTo(nestedMap);
		assertThat(request.context().get("list")).isEqualTo(list);
		assertThat(request.context().get("string")).isEqualTo("value");
		assertThat(request.context().get("number")).isEqualTo(123);
		assertThat(request.context().get("boolean")).isEqualTo(true);
	}

}
