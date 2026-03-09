/*
 * Copyright 2025-2025 the original author or authors.
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
import java.util.function.Consumer;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpPromptListChanged;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SyncMcpPromptListChangedMethodCallback}.
 *
 * @author Christian Tzolov
 */
public class SyncMcpPromptListChangedMethodCallbackTests {

	private static final List<McpSchema.Prompt> TEST_PROMPTS = List.of(
			new McpSchema.Prompt("test-prompt-1", "Test Prompt 1", List.of()),
			new McpSchema.Prompt("test-prompt-2", "Test Prompt 2", List.of()));

	@Test
	void testValidMethodWithPromptList() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handlePromptListChanged", List.class);

		Consumer<List<McpSchema.Prompt>> callback = SyncMcpPromptListChangedMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		callback.accept(TEST_PROMPTS);

		assertThat(bean.lastUpdatedPrompts).isEqualTo(TEST_PROMPTS);
		assertThat(bean.lastUpdatedPrompts).hasSize(2);
		assertThat(bean.lastUpdatedPrompts.get(0).name()).isEqualTo("test-prompt-1");
		assertThat(bean.lastUpdatedPrompts.get(1).name()).isEqualTo("test-prompt-2");
	}

	@Test
	void testInvalidReturnType() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidReturnType", List.class);

		assertThatThrownBy(() -> SyncMcpPromptListChangedMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must have void return type");
	}

	@Test
	void testInvalidParameterCount() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidParameterCount", List.class, String.class);

		assertThatThrownBy(() -> SyncMcpPromptListChangedMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must have exactly 1 parameter (List<McpSchema.Prompt>)");
	}

	@Test
	void testInvalidParameterType() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidParameterType", String.class);

		assertThatThrownBy(() -> SyncMcpPromptListChangedMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Parameter must be of type List<McpSchema.Prompt>");
	}

	@Test
	void testNoParameters() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("noParameters");

		assertThatThrownBy(() -> SyncMcpPromptListChangedMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must have exactly 1 parameter (List<McpSchema.Prompt>)");
	}

	@Test
	void testNullPromptList() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handlePromptListChanged", List.class);

		Consumer<List<McpSchema.Prompt>> callback = SyncMcpPromptListChangedMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		assertThatThrownBy(() -> callback.accept(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Updated prompts list must not be null");
	}

	@Test
	void testEmptyPromptList() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handlePromptListChanged", List.class);

		Consumer<List<McpSchema.Prompt>> callback = SyncMcpPromptListChangedMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		List<McpSchema.Prompt> emptyList = List.of();
		callback.accept(emptyList);

		assertThat(bean.lastUpdatedPrompts).isEqualTo(emptyList);
		assertThat(bean.lastUpdatedPrompts).isEmpty();
	}

	@Test
	void testNullMethod() {
		ValidMethods bean = new ValidMethods();

		assertThatThrownBy(() -> SyncMcpPromptListChangedMethodCallback.builder().method(null).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must not be null");
	}

	@Test
	void testNullBean() throws Exception {
		Method method = ValidMethods.class.getMethod("handlePromptListChanged", List.class);

		assertThatThrownBy(() -> SyncMcpPromptListChangedMethodCallback.builder().method(method).bean(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Bean must not be null");
	}

	@Test
	void testMethodInvocationException() throws Exception {
		// Test class that throws an exception in the method
		class ThrowingMethod {

			@McpPromptListChanged(clients = "my-client-id")
			public void handlePromptListChanged(List<McpSchema.Prompt> updatedPrompts) {
				throw new RuntimeException("Test exception");
			}

		}

		ThrowingMethod bean = new ThrowingMethod();
		Method method = ThrowingMethod.class.getMethod("handlePromptListChanged", List.class);

		Consumer<List<McpSchema.Prompt>> callback = SyncMcpPromptListChangedMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		assertThatThrownBy(() -> callback.accept(TEST_PROMPTS))
			.isInstanceOf(AbstractMcpPromptListChangedMethodCallback.McpPromptListChangedConsumerMethodException.class)
			.hasMessageContaining("Error invoking prompt list changed consumer method");
	}

	/**
	 * Test class with valid methods.
	 */
	static class ValidMethods {

		private List<McpSchema.Prompt> lastUpdatedPrompts;

		@McpPromptListChanged(clients = "my-client-id")
		public void handlePromptListChanged(List<McpSchema.Prompt> updatedPrompts) {
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
		public void invalidParameterCount(List<McpSchema.Prompt> updatedPrompts, String extra) {
			// Invalid parameter count
		}

		@McpPromptListChanged(clients = "my-client-id")
		public void invalidParameterType(String invalidType) {
			// Invalid parameter type
		}

		@McpPromptListChanged(clients = "my-client-id")
		public void noParameters() {
			// No parameters
		}

	}

}
