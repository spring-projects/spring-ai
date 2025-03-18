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

package org.springframework.ai.mcp;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolRegistration;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolRegistration;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @deprecated used to test backward compatbility. Replaced by the {@link ToolUtilsTests}
 * instead
 */
@Deprecated
class ToolUtilsDeprecatedTests {

	@Test
	void constructorShouldBePrivate() throws Exception {
		Constructor<McpToolUtils> constructor = McpToolUtils.class.getDeclaredConstructor();
		assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
		constructor.setAccessible(true);
		constructor.newInstance();
	}

	@Test
	void toSyncToolRegistrationShouldConvertSingleCallback() {
		// Arrange
		ToolCallback callback = createMockToolCallback("test", "success");

		// Act
		SyncToolRegistration registration = McpToolUtils.toSyncToolRegistration(callback);

		// Assert
		assertThat(registration).isNotNull();
		assertThat(registration.tool().name()).isEqualTo("test");

		CallToolResult result = registration.call().apply(Map.of());
		TextContent content = (TextContent) result.content().get(0);
		assertThat(content.text()).isEqualTo("success");
		assertThat(result.isError()).isFalse();
	}

	@Test
	void toSyncToolRegistrationShouldHandleError() {
		// Arrange
		ToolCallback callback = createMockToolCallback("test", new RuntimeException("error"));

		// Act
		SyncToolRegistration registration = McpToolUtils.toSyncToolRegistration(callback);

		// Assert
		assertThat(registration).isNotNull();
		CallToolResult result = registration.call().apply(Map.of());
		TextContent content = (TextContent) result.content().get(0);
		assertThat(content.text()).isEqualTo("error");
		assertThat(result.isError()).isTrue();
	}

	@Test
	void toSyncToolRegistrationShouldConvertMultipleCallbacks() {
		// Arrange
		ToolCallback callback1 = createMockToolCallback("test1", "success1");
		ToolCallback callback2 = createMockToolCallback("test2", "success2");

		// Act
		List<SyncToolRegistration> registrations = McpToolUtils.toSyncToolRegistrations(callback1, callback2);

		// Assert
		assertThat(registrations).hasSize(2);
		assertThat(registrations.get(0).tool().name()).isEqualTo("test1");
		assertThat(registrations.get(1).tool().name()).isEqualTo("test2");
	}

	@Test
	void toAsyncToolRegistrationShouldConvertSingleCallback() {
		// Arrange
		ToolCallback callback = createMockToolCallback("test", "success");

		// Act
		AsyncToolRegistration registration = McpToolUtils.toAsyncToolRegistration(callback);

		// Assert
		assertThat(registration).isNotNull();
		assertThat(registration.tool().name()).isEqualTo("test");

		StepVerifier.create(registration.call().apply(Map.of())).assertNext(result -> {
			TextContent content = (TextContent) result.content().get(0);
			assertThat(content.text()).isEqualTo("success");
			assertThat(result.isError()).isFalse();
		}).verifyComplete();
	}

	@Test
	void toAsyncToolRegistrationShouldHandleError() {
		// Arrange
		ToolCallback callback = createMockToolCallback("test", new RuntimeException("error"));

		// Act
		AsyncToolRegistration registration = McpToolUtils.toAsyncToolRegistration(callback);

		// Assert
		assertThat(registration).isNotNull();
		StepVerifier.create(registration.call().apply(Map.of())).assertNext(result -> {
			TextContent content = (TextContent) result.content().get(0);
			assertThat(content.text()).isEqualTo("error");
			assertThat(result.isError()).isTrue();
		}).verifyComplete();
	}

	@Test
	void toAsyncToolRegistrationShouldConvertMultipleCallbacks() {
		// Arrange
		ToolCallback callback1 = createMockToolCallback("test1", "success1");
		ToolCallback callback2 = createMockToolCallback("test2", "success2");

		// Act
		List<AsyncToolRegistration> registrations = McpToolUtils.toAsyncToolRegistration(callback1, callback2);

		// Assert
		assertThat(registrations).hasSize(2);
		assertThat(registrations.get(0).tool().name()).isEqualTo("test1");
		assertThat(registrations.get(1).tool().name()).isEqualTo("test2");
	}

	private ToolCallback createMockToolCallback(String name, String result) {
		ToolCallback callback = mock(ToolCallback.class);
		ToolDefinition definition = ToolDefinition.builder()
			.name(name)
			.description("Test tool")
			.inputSchema("{}")
			.build();
		when(callback.getToolDefinition()).thenReturn(definition);
		when(callback.call(anyString())).thenReturn(result);
		return callback;
	}

	private ToolCallback createMockToolCallback(String name, RuntimeException error) {
		ToolCallback callback = mock(ToolCallback.class);
		ToolDefinition definition = ToolDefinition.builder()
			.name(name)
			.description("Test tool")
			.inputSchema("{}")
			.build();
		when(callback.getToolDefinition()).thenReturn(definition);
		when(callback.call(anyString())).thenThrow(error);
		return callback;
	}

}
