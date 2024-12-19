/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.model.function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultFunctionCallingOptionsBuilder}.
 *
 */
class DefaultFunctionCallingOptionsBuilderTests {

	private DefaultFunctionCallingOptionsBuilder builder;

	@BeforeEach
	void setUp() {
		builder = new DefaultFunctionCallingOptionsBuilder();
	}

	@Test
	void shouldBuildWithFunctionCallbacksList() {
		// Given
		FunctionCallback callback1 = FunctionCallback.builder()
			.function("test1", (String input) -> "result1")
			.description("Test function 1")
			.inputType(String.class)
			.build();
		FunctionCallback callback2 = FunctionCallback.builder()
			.function("test2", (String input) -> "result2")
			.description("Test function 2")
			.inputType(String.class)
			.build();
		List<FunctionCallback> callbacks = List.of(callback1, callback2);

		// When
		FunctionCallingOptions options = builder.functionCallbacks(callbacks).build();

		// Then
		assertThat(options.getFunctionCallbacks()).hasSize(2).containsExactlyElementsOf(callbacks);
	}

	@Test
	void shouldBuildWithFunctionCallbacksVarargs() {
		// Given
		FunctionCallback callback1 = FunctionCallback.builder()
			.function("test1", (String input) -> "result1")
			.description("Test function 1")
			.inputType(String.class)
			.build();
		FunctionCallback callback2 = FunctionCallback.builder()
			.function("test2", (String input) -> "result2")
			.description("Test function 2")
			.inputType(String.class)
			.build();

		// When
		FunctionCallingOptions options = builder.functionCallbacks(callback1, callback2).build();

		// Then
		assertThat(options.getFunctionCallbacks()).hasSize(2).containsExactly(callback1, callback2);
	}

	@Test
	void shouldThrowExceptionWhenFunctionCallbacksVarargsIsNull() {
		assertThatThrownBy(() -> builder.functionCallbacks((FunctionCallback[]) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("FunctionCallbacks must not be null");
	}

	@Test
	void shouldBuildWithFunctionsSet() {
		// Given
		Set<String> functions = Set.of("function1", "function2");

		// When
		FunctionCallingOptions options = builder.functions(functions).build();

		// Then
		assertThat(options.getFunctions()).hasSize(2).containsExactlyInAnyOrderElementsOf(functions);
	}

	@Test
	void shouldBuildWithSingleFunction() {
		// When
		FunctionCallingOptions options = builder.function("function1").function("function2").build();

		// Then
		assertThat(options.getFunctions()).hasSize(2).containsExactlyInAnyOrder("function1", "function2");
	}

	@Test
	void shouldThrowExceptionWhenFunctionIsNull() {
		assertThatThrownBy(() -> builder.function(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Function must not be null");
	}

	@Test
	void shouldBuildWithProxyToolCalls() {
		// When
		FunctionCallingOptions options = builder.proxyToolCalls(true).build();

		// Then
		assertThat(options.getProxyToolCalls()).isTrue();
	}

	@Test
	void shouldBuildWithToolContextMap() {
		// Given
		Map<String, Object> context = Map.of("key1", "value1", "key2", 42);

		// When
		FunctionCallingOptions options = builder.toolContext(context).build();

		// Then
		assertThat(options.getToolContext()).hasSize(2).containsAllEntriesOf(context);
	}

	@Test
	void shouldThrowExceptionWhenToolContextMapIsNull() {
		assertThatThrownBy(() -> builder.toolContext((Map<String, Object>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Tool context must not be null");
	}

	@Test
	void shouldBuildWithToolContextKeyValue() {
		// When
		FunctionCallingOptions options = builder.toolContext("key1", "value1").toolContext("key2", 42).build();

		// Then
		assertThat(options.getToolContext()).hasSize(2).containsEntry("key1", "value1").containsEntry("key2", 42);
	}

	@Test
	void shouldThrowExceptionWhenToolContextKeyIsNull() {
		assertThatThrownBy(() -> builder.toolContext(null, "value")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Key must not be null");
	}

	@Test
	void shouldThrowExceptionWhenToolContextValueIsNull() {
		assertThatThrownBy(() -> builder.toolContext("key", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Value must not be null");
	}

	@Test
	void shouldMergeToolContextMaps() {
		// Given
		Map<String, Object> context1 = Map.of("key1", "value1", "key2", 42);
		Map<String, Object> context2 = Map.of("key2", "updated", "key3", true);

		// When
		FunctionCallingOptions options = builder.toolContext(context1).toolContext(context2).build();

		// Then
		assertThat(options.getToolContext()).hasSize(3)
			.containsEntry("key1", "value1")
			.containsEntry("key2", "updated")
			.containsEntry("key3", true);
	}

	@Test
	void shouldBuildWithAllOptions() {
		// Given
		FunctionCallback callback = FunctionCallback.builder()
			.function("test", (String input) -> "result")
			.description("Test function")
			.inputType(String.class)
			.build();
		Set<String> functions = Set.of("function1");
		Map<String, Object> context = Map.of("key1", "value1");

		// When
		FunctionCallingOptions options = builder.functionCallbacks(callback)
			.functions(functions)
			.proxyToolCalls(true)
			.toolContext(context)
			.build();

		// Then
		assertThat(options.getFunctionCallbacks()).hasSize(1).containsExactly(callback);
		assertThat(options.getFunctions()).hasSize(1).containsExactlyElementsOf(functions);
		assertThat(options.getProxyToolCalls()).isTrue();
		assertThat(options.getToolContext()).hasSize(1).containsAllEntriesOf(context);
	}

}
