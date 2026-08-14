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

package org.springframework.ai.mcp.annotation.method.changed.resource;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpResourceListChanged;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SyncMcpResourceListChangedMethodCallback}.
 *
 * @author Christian Tzolov
 */
public class SyncMcpResourceListChangedMethodCallbackTests {

	private static final List<McpSchema.Resource> TEST_RESOURCES = List.of(
			McpSchema.Resource.builder("file:///test1.txt", "test-resource-1")
				.description("Test Resource 1")
				.mimeType("text/plain")
				.build(),
			McpSchema.Resource.builder("file:///test2.txt", "test-resource-2")
				.description("Test Resource 2")
				.mimeType("text/plain")
				.build());

	@Test
	void testValidMethodWithResourceList() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handleResourceListChanged", List.class);

		Consumer<List<McpSchema.Resource>> callback = SyncMcpResourceListChangedMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		callback.accept(TEST_RESOURCES);

		assertThat(bean.lastUpdatedResources).isEqualTo(TEST_RESOURCES);
		assertThat(bean.lastUpdatedResources).hasSize(2);
		assertThat(bean.lastUpdatedResources.get(0).name()).isEqualTo("test-resource-1");
		assertThat(bean.lastUpdatedResources.get(1).name()).isEqualTo("test-resource-2");
	}

	@Test
	void testInvalidReturnType() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidReturnType", List.class);

		assertThatThrownBy(() -> SyncMcpResourceListChangedMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must have void return type");
	}

	@Test
	void testInvalidParameterCount() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidParameterCount", List.class, String.class);

		assertThatThrownBy(() -> SyncMcpResourceListChangedMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must have exactly 1 parameter (List<McpSchema.Resource>)");
	}

	@Test
	void testInvalidParameterType() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("invalidParameterType", String.class);

		assertThatThrownBy(() -> SyncMcpResourceListChangedMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Parameter must be of type List<McpSchema.Resource>");
	}

	@Test
	void testNoParameters() throws Exception {
		InvalidMethods bean = new InvalidMethods();
		Method method = InvalidMethods.class.getMethod("noParameters");

		assertThatThrownBy(() -> SyncMcpResourceListChangedMethodCallback.builder().method(method).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must have exactly 1 parameter (List<McpSchema.Resource>)");
	}

	@Test
	void testNullResourceList() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handleResourceListChanged", List.class);

		Consumer<List<McpSchema.Resource>> callback = SyncMcpResourceListChangedMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		assertThatThrownBy(() -> callback.accept(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Updated resources list must not be null");
	}

	@Test
	void testEmptyResourceList() throws Exception {
		ValidMethods bean = new ValidMethods();
		Method method = ValidMethods.class.getMethod("handleResourceListChanged", List.class);

		Consumer<List<McpSchema.Resource>> callback = SyncMcpResourceListChangedMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		List<McpSchema.Resource> emptyList = List.of();
		callback.accept(emptyList);

		assertThat(bean.lastUpdatedResources).isEqualTo(emptyList);
		assertThat(bean.lastUpdatedResources).isEmpty();
	}

	@Test
	void testNullMethod() {
		ValidMethods bean = new ValidMethods();

		assertThatThrownBy(() -> SyncMcpResourceListChangedMethodCallback.builder().method(null).bean(bean).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must not be null");
	}

	@Test
	void testNullBean() throws Exception {
		Method method = ValidMethods.class.getMethod("handleResourceListChanged", List.class);

		assertThatThrownBy(() -> SyncMcpResourceListChangedMethodCallback.builder().method(method).bean(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Bean must not be null");
	}

	@Test
	void testMethodInvocationException() throws Exception {
		// Test class that throws an exception in the method
		class ThrowingMethod {

			@McpResourceListChanged(clients = "client1")
			public void handleResourceListChanged(List<McpSchema.Resource> updatedResources) {
				throw new RuntimeException("Test exception");
			}

		}

		ThrowingMethod bean = new ThrowingMethod();
		Method method = ThrowingMethod.class.getMethod("handleResourceListChanged", List.class);

		Consumer<List<McpSchema.Resource>> callback = SyncMcpResourceListChangedMethodCallback.builder()
			.method(method)
			.bean(bean)
			.build();

		assertThatThrownBy(() -> callback.accept(TEST_RESOURCES))
			.isInstanceOf(
					AbstractMcpResourceListChangedMethodCallback.McpResourceListChangedConsumerMethodException.class)
			.hasMessageContaining("Error invoking resource list changed consumer method");
	}

	/**
	 * Test class with valid methods.
	 */
	static class ValidMethods {

		private List<McpSchema.Resource> lastUpdatedResources;

		@McpResourceListChanged(clients = "client1")
		public void handleResourceListChanged(List<McpSchema.Resource> updatedResources) {
			this.lastUpdatedResources = updatedResources;
		}

	}

	/**
	 * Test class with invalid methods.
	 */
	static class InvalidMethods {

		@McpResourceListChanged(clients = "client1")
		public String invalidReturnType(List<McpSchema.Resource> updatedResources) {
			return "Invalid";
		}

		@McpResourceListChanged(clients = "client1")
		public void invalidParameterCount(List<McpSchema.Resource> updatedResources, String extra) {
			// Invalid parameter count
		}

		@McpResourceListChanged(clients = "client1")
		public void invalidParameterType(String invalidType) {
			// Invalid parameter type
		}

		@McpResourceListChanged(clients = "client1")
		public void noParameters() {
			// No parameters
		}

	}

}
