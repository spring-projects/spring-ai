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

package org.springframework.ai.mcp.annotation.method.changed.prompt;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.annotation.McpPromptListChanged;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AsyncMcpPromptListChangedMethodCallback}.
 *
 * @author Christian Tzolov
 */
public class AsyncMcpPromptListChangedMethodCallbackTests {

	private static final List<McpSchema.Prompt> TEST_PROMPTS = List.of(
			new McpSchema.Prompt("test-prompt-1", "Test Prompt 1", List.of()),
			new McpSchema.Prompt("test-prompt-2", "Test Prompt 2", List.of()));

	@Test
	void testValidMethodWithPromptList() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handlePromptListChanged", List.class);

		Function<List<McpSchema.Prompt>, Mono<Void>> callback = AsyncMcpPromptListChangedMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		StepVerifier.create(callback.apply(TEST_PROMPTS)).verifyComplete();

		assertThat(bean.lastUpdatedPrompts).isEqualTo(TEST_PROMPTS);
		assertThat(bean.lastUpdatedPrompts).hasSize(2);
		assertThat(bean.lastUpdatedPrompts.get(0).name()).isEqualTo("test-prompt-1");
		assertThat(bean.lastUpdatedPrompts.get(1).name()).isEqualTo("test-prompt-2");
	}

	@Test
	void testValidVoidMethod() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handlePromptListChangedVoid", List.class);

		Function<List<McpSchema.Prompt>, Mono<Void>> callback = AsyncMcpPromptListChangedMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		StepVerifier.create(callback.apply(TEST_PROMPTS)).verifyComplete();

		assertThat(bean.lastUpdatedPrompts).isEqualTo(TEST_PROMPTS);
		assertThat(bean.lastUpdatedPrompts).hasSize(2);
		assertThat(bean.lastUpdatedPrompts.get(0).name()).isEqualTo("test-prompt-1");
		assertThat(bean.lastUpdatedPrompts.get(1).name()).isEqualTo("test-prompt-2");
	}

	@Test
	void testInvalidReturnType() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidReturnType", List.class);

		assertThatThrownBy(() -> AsyncMcpPromptListChangedMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must have void or Mono<Void> return type");
	}

	@Test
	void testInvalidMonoReturnType() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidMonoReturnType", List.class);

		// This will pass validation since we can't check the generic type at runtime
		Function<List<McpSchema.Prompt>, Mono<Void>> callback = AsyncMcpPromptListChangedMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		// But it will fail at runtime when we try to cast the result
		StepVerifier.create(callback.apply(TEST_PROMPTS)).verifyError(ClassCastException.class);
	}

	@Test
	void testInvalidParameterCount() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidParameterCount", List.class, String.class);

		assertThatThrownBy(() -> AsyncMcpPromptListChangedMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must have exactly 1 parameter (List<McpSchema.Prompt>)");
	}

	@Test
	void testInvalidParameterType() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidParameterType", String.class);

		assertThatThrownBy(() -> AsyncMcpPromptListChangedMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Parameter must be of type List<McpSchema.Prompt>");
	}

	@Test
	void testNoParameters() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("noParameters");

		assertThatThrownBy(() -> AsyncMcpPromptListChangedMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must have exactly 1 parameter (List<McpSchema.Prompt>)");
	}

	@Test
	void testNullPromptList() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handlePromptListChanged", List.class);

		Function<List<McpSchema.Prompt>, Mono<Void>> callback = AsyncMcpPromptListChangedMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		StepVerifier.create(callback.apply(null))
			.verifyErrorSatisfies(e -> assertThat(e).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Updated prompts list must not be null"));
	}

	@Test
	void testEmptyPromptList() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handlePromptListChanged", List.class);

		Function<List<McpSchema.Prompt>, Mono<Void>> callback = AsyncMcpPromptListChangedMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		List<McpSchema.Prompt> emptyList = List.of();
		StepVerifier.create(callback.apply(emptyList)).verifyComplete();

		assertThat(bean.lastUpdatedPrompts).isEqualTo(emptyList);
		assertThat(bean.lastUpdatedPrompts).isEmpty();
	}

	@Test
	void testNullMethod() {
		ValidMethods bean = new ValidMethods();

		assertThatThrownBy(() -> AsyncMcpPromptListChangedMethodCallback.builder().method(null).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must not be null");
	}

	@Test
	void testNullBean() throws Exception {
		Method method = ValidMethods.class.getMethod("handlePromptListChanged", List.class);

		assertThatThrownBy(() -> AsyncMcpPromptListChangedMethodCallback.builder().method(method).bean(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Bean must not be null");
	}

	@Test
	void testMethodInvocationException() throws Exception {
		// Test class that throws an exception in the method
		class ThrowingMethod {

			@McpPromptListChanged(clients = "my-client-id")
			public Mono<Void> handlePromptListChanged(List<McpSchema.Prompt> updatedPrompts) {
				return Mono.fromRunnable(() -> {
					throw new RuntimeException("Test exception");
				});
			}

		}

		ThrowingMethod bean = new ThrowingMethod();
		Method method = ThrowingMethod.class.getMethod("handlePromptListChanged", List.class);

		Function<List<McpSchema.Prompt>, Mono<Void>> callback = AsyncMcpPromptListChangedMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		StepVerifier.create(callback.apply(TEST_PROMPTS)).verifyError(RuntimeException.class);
	}

	@Test
	void testMethodInvocationExceptionVoid() throws Exception {
		// Test class that throws an exception in a void method
		class ThrowingVoidMethod {

			@McpPromptListChanged(clients = "my-client-id")
			public void handlePromptListChanged(List<McpSchema.Prompt> updatedPrompts) {
				throw new RuntimeException("Test exception");
			}

		}

		ThrowingVoidMethod bean = new ThrowingVoidMethod();
		Method method = ThrowingVoidMethod.class.getMethod("handlePromptListChanged", List.class);

		Function<List<McpSchema.Prompt>, Mono<Void>> callback = AsyncMcpPromptListChangedMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		StepVerifier.create(callback.apply(TEST_PROMPTS))
			.verifyErrorSatisfies(e -> assertThat(e)
				.isInstanceOf(
						AbstractMcpPromptListChangedMethodCallback.McpPromptListChangedConsumerMethodException.class)
				.hasMessageContaining("Error invoking prompt list changed consumer method"));
	}

	/**
	 * Test class with valid methods.
	 */
	static class ValidMethods {

		private List<McpSchema.Prompt> lastUpdatedPrompts;

		@McpPromptListChanged(clients = "my-client-id")
		public Mono<Void> handlePromptListChanged(List<McpSchema.Prompt> updatedPrompts) {
			return Mono.fromRunnable(() -> this.lastUpdatedPrompts = updatedPrompts);
		}

		@McpPromptListChanged(clients = "my-client-id")
		public void handlePromptListChangedVoid(List<McpSchema.Prompt> updatedPrompts) {
			this.lastUpdatedPrompts = updatedPrompts;
		}

	}

	/**
	 * Test class with invalid methods.
	 */
	static class InvalidMethods {

		@McpPromptListChanged(clients = "my-client-id")
		public String invalidReturnType(List<McpSchema.Prompt> updatedPrompts) {
			return "Invalid";
		}

		@McpPromptListChanged(clients = "my-client-id")
		public Mono<String> invalidMonoReturnType(List<McpSchema.Prompt> updatedPrompts) {
			return Mono.just("Invalid");
		}

		@McpPromptListChanged(clients = "my-client-id")
		public Mono<Void> invalidParameterCount(List<McpSchema.Prompt> updatedPrompts, String extra) {
			return Mono.empty();
		}

		@McpPromptListChanged(clients = "my-client-id")
		public Mono<Void> invalidParameterType(String invalidType) {
			return Mono.empty();
		}

		@McpPromptListChanged(clients = "my-client-id")
		public Mono<Void> noParameters() {
			return Mono.empty();
		}

	}

}
