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

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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

	@Test
	void whenContextHasNullValuesThenThrow() {
		Map<String, Object> context = new HashMap<>();
		context.put("key", null);

		assertThatThrownBy(() -> new ChatClientResponse(null, context)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context values cannot be null");

		assertThatThrownBy(() -> ChatClientResponse.builder().chatResponse(null).context(context).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context values cannot be null");
	}

	@Test
	void whenCopyThenImmutableContext() {
		Map<String, Object> context = new HashMap<>();
		context.put("key", "value");
		ChatClientResponse response = ChatClientResponse.builder().chatResponse(null).context(context).build();

		ChatClientResponse copy = response.copy();

		copy.context().put("key2", "value2");
		assertThat(response.context()).doesNotContainKey("key2");
		assertThat(copy.context()).containsKey("key2");

		copy.context().put("key", "newValue");
		assertThat(copy.context()).containsEntry("key", "newValue");
		assertThat(response.context()).containsEntry("key", "value");
	}

	@Test
	void whenMutateThenImmutableContext() {
		Map<String, Object> context = new HashMap<>();
		context.put("key", "value");
		ChatClientResponse response = ChatClientResponse.builder().chatResponse(null).context(context).build();

		ChatClientResponse copy = response.mutate().context(Map.of("key2", "value2")).build();

		assertThat(response.context()).doesNotContainKey("key2");
		assertThat(copy.context()).containsKey("key2");

		copy.context().put("key", "newValue");
		assertThat(copy.context()).containsEntry("key", "newValue");
		assertThat(response.context()).containsEntry("key", "value");
	}

	@Test
	void whenValidChatResponseThenCreateSuccessfully() {
		ChatResponse chatResponse = mock(ChatResponse.class);
		Map<String, Object> context = Map.of("key", "value");

		ChatClientResponse response = new ChatClientResponse(chatResponse, context);

		assertThat(response.chatResponse()).isEqualTo(chatResponse);
		assertThat(response.context()).containsExactlyInAnyOrderEntriesOf(context);
	}

	@Test
	void whenBuilderWithValidDataThenCreateSuccessfully() {
		ChatResponse chatResponse = mock(ChatResponse.class);
		Map<String, Object> context = Map.of("key1", "value1", "key2", 42);

		ChatClientResponse response = ChatClientResponse.builder().chatResponse(chatResponse).context(context).build();

		assertThat(response.chatResponse()).isEqualTo(chatResponse);
		assertThat(response.context()).containsExactlyInAnyOrderEntriesOf(context);
	}

	@Test
	void whenEmptyContextThenCreateSuccessfully() {
		ChatResponse chatResponse = mock(ChatResponse.class);
		Map<String, Object> emptyContext = Map.of();

		ChatClientResponse response = new ChatClientResponse(chatResponse, emptyContext);

		assertThat(response.chatResponse()).isEqualTo(chatResponse);
		assertThat(response.context()).isEmpty();
	}

	@Test
	void whenContextWithNullValuesThenThrow() {
		ChatResponse chatResponse = mock(ChatResponse.class);
		Map<String, Object> context = new HashMap<>();
		context.put("key1", "value1");
		context.put("key2", null);

		assertThatThrownBy(() -> new ChatClientResponse(chatResponse, context))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context values cannot be null");
	}

	@Test
	void whenCopyWithNullChatResponseThenPreserveNull() {
		Map<String, Object> context = Map.of("key", "value");
		ChatClientResponse response = new ChatClientResponse(null, context);

		ChatClientResponse copy = response.copy();

		assertThat(copy.chatResponse()).isNull();
		assertThat(copy.context()).containsExactlyInAnyOrderEntriesOf(context);
	}

	@Test
	void whenMutateWithNewChatResponseThenUpdate() {
		ChatResponse originalResponse = mock(ChatResponse.class);
		ChatResponse newResponse = mock(ChatResponse.class);
		Map<String, Object> context = Map.of("key", "value");

		ChatClientResponse response = new ChatClientResponse(originalResponse, context);
		ChatClientResponse mutated = response.mutate().chatResponse(newResponse).build();

		assertThat(response.chatResponse()).isEqualTo(originalResponse);
		assertThat(mutated.chatResponse()).isEqualTo(newResponse);
		assertThat(mutated.context()).containsExactlyInAnyOrderEntriesOf(context);
	}

	@Test
	void whenBuilderWithoutChatResponseThenCreateWithNull() {
		Map<String, Object> context = Map.of("key", "value");

		ChatClientResponse response = ChatClientResponse.builder().context(context).build();

		assertThat(response.chatResponse()).isNull();
	}

	@Test
	void whenBuilderContextMapHasNullKeyThenThrow() {
		Map<String, Object> context = new HashMap<>();
		context.put(null, "value");

		assertThatThrownBy(() -> ChatClientResponse.builder().context(context).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context keys cannot be null");
	}

	@Test
	void whenBuilderAddsNullValueThenThrow() {
		assertThatThrownBy(() -> ChatClientResponse.builder().context("key", null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("value cannot be null");
	}

	@Test
	void whenComplexObjectsInContextThenPreserveCorrectly() {
		ChatResponse chatResponse = mock(ChatResponse.class);
		Generation generation = mock(Generation.class);
		Map<String, Object> nestedMap = Map.of("nested", "value");

		Map<String, Object> context = Map.of("string", "value", "number", 1, "boolean", true, "generation", generation,
				"map", nestedMap);

		ChatClientResponse response = new ChatClientResponse(chatResponse, context);

		assertThat(response.context()).containsEntry("string", "value");
		assertThat(response.context()).containsEntry("number", 1);
		assertThat(response.context()).containsEntry("boolean", true);
		assertThat(response.context()).containsEntry("generation", generation);
		assertThat(response.context()).containsEntry("map", nestedMap);
	}

}
