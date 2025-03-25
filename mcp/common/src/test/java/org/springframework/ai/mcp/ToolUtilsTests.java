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

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolUtilsTests {

	@Test
	void prefixedToolNameShouldConcatenateWithUnderscore() {
		String result = McpToolUtils.prefixedToolName("prefix", "toolName");
		assertThat(result).isEqualTo("prefix_toolName");
	}

	@Test
	void prefixedToolNameShouldReplaceSpecialCharacters() {
		String result = McpToolUtils.prefixedToolName("pre.fix", "tool@Name");
		assertThat(result).isEqualTo("prefix_toolName");
	}

	@Test
	void prefixedToolNameShouldReplaceHyphensWithUnderscores() {
		String result = McpToolUtils.prefixedToolName("pre-fix", "tool-name");
		assertThat(result).isEqualTo("pre_fix_tool_name");
	}

	@Test
	void prefixedToolNameShouldTruncateLongStrings() {
		String longPrefix = "a".repeat(40);
		String longToolName = "b".repeat(40);
		String result = McpToolUtils.prefixedToolName(longPrefix, longToolName);
		assertThat(result).hasSize(64);
		assertThat(result).endsWith("_" + longToolName);
	}

	@Test
	void prefixedToolNameShouldThrowExceptionForNullOrEmptyInputs() {
		assertThatThrownBy(() -> McpToolUtils.prefixedToolName(null, "toolName"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Prefix or toolName cannot be null or empty");

		assertThatThrownBy(() -> McpToolUtils.prefixedToolName("", "toolName"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Prefix or toolName cannot be null or empty");

		assertThatThrownBy(() -> McpToolUtils.prefixedToolName("prefix", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Prefix or toolName cannot be null or empty");

		assertThatThrownBy(() -> McpToolUtils.prefixedToolName("prefix", ""))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Prefix or toolName cannot be null or empty");
	}

	@Test
	void constructorShouldBePrivate() throws Exception {
		Constructor<McpToolUtils> constructor = McpToolUtils.class.getDeclaredConstructor();
		assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
		constructor.setAccessible(true);
		constructor.newInstance();
	}

	@Test
	void toSyncToolSpecificaitonShouldConvertSingleCallback() {

		ToolCallback callback = createMockToolCallback("test", "success");

		SyncToolSpecification toolSpecification = McpToolUtils.toSyncToolSpecification(callback);

		assertThat(toolSpecification).isNotNull();
		assertThat(toolSpecification.tool().name()).isEqualTo("test");

		CallToolResult result = toolSpecification.call().apply(mock(McpSyncServerExchange.class), Map.of());
		TextContent content = (TextContent) result.content().get(0);
		assertThat(content.text()).isEqualTo("success");
		assertThat(result.isError()).isFalse();
	}

	@Test
	void toSyncToolSpecificationShouldHandleError() {
		ToolCallback callback = createMockToolCallback("test", new RuntimeException("error"));

		SyncToolSpecification toolSpecification = McpToolUtils.toSyncToolSpecification(callback);

		assertThat(toolSpecification).isNotNull();
		CallToolResult result = toolSpecification.call().apply(mock(McpSyncServerExchange.class), Map.of());
		TextContent content = (TextContent) result.content().get(0);
		assertThat(content.text()).isEqualTo("error");
		assertThat(result.isError()).isTrue();
	}

	@Test
	void toSyncToolSpecificationShouldConvertMultipleCallbacks() {
		ToolCallback callback1 = createMockToolCallback("test1", "success1");
		ToolCallback callback2 = createMockToolCallback("test2", "success2");

		List<SyncToolSpecification> toolSpecification = McpToolUtils.toSyncToolSpecifications(callback1, callback2);

		assertThat(toolSpecification).hasSize(2);
		assertThat(toolSpecification.get(0).tool().name()).isEqualTo("test1");
		assertThat(toolSpecification.get(1).tool().name()).isEqualTo("test2");
	}

	@Test
	void toAsyncToolSpecificaitonShouldConvertSingleCallback() {
		ToolCallback callback = createMockToolCallback("test", "success");

		AsyncToolSpecification toolSpecification = McpToolUtils.toAsyncToolSpecification(callback);

		// Assert
		assertThat(toolSpecification).isNotNull();
		assertThat(toolSpecification.tool().name()).isEqualTo("test");

		StepVerifier.create(toolSpecification.call().apply(mock(McpAsyncServerExchange.class), Map.of()))
			.assertNext(result -> {
				TextContent content = (TextContent) result.content().get(0);
				assertThat(content.text()).isEqualTo("success");
				assertThat(result.isError()).isFalse();
			})
			.verifyComplete();
	}

	@Test
	void toAsyncToolSpecificationShouldHandleError() {
		ToolCallback callback = createMockToolCallback("test", new RuntimeException("error"));

		AsyncToolSpecification toolSpecificaiton = McpToolUtils.toAsyncToolSpecification(callback);

		assertThat(toolSpecificaiton).isNotNull();
		StepVerifier.create(toolSpecificaiton.call().apply(mock(McpAsyncServerExchange.class), Map.of()))
			.assertNext(result -> {
				TextContent content = (TextContent) result.content().get(0);
				assertThat(content.text()).isEqualTo("error");
				assertThat(result.isError()).isTrue();
			})
			.verifyComplete();
	}

	@Test
	void toAsyncToolSpecificationShouldConvertMultipleCallbacks() {
		// Arrange
		ToolCallback callback1 = createMockToolCallback("test1", "success1");
		ToolCallback callback2 = createMockToolCallback("test2", "success2");

		// Act
		List<AsyncToolSpecification> toolSpecifications = McpToolUtils.toAsyncToolSpecifications(callback1, callback2);

		// Assert
		assertThat(toolSpecifications).hasSize(2);
		assertThat(toolSpecifications.get(0).tool().name()).isEqualTo("test1");
		assertThat(toolSpecifications.get(1).tool().name()).isEqualTo("test2");
	}

	private ToolCallback createMockToolCallback(String name, String result) {
		ToolCallback callback = mock(ToolCallback.class);
		ToolDefinition definition = ToolDefinition.builder()
			.name(name)
			.description("Test tool")
			.inputSchema("{}")
			.build();
		when(callback.getToolDefinition()).thenReturn(definition);
		when(callback.call(anyString(), any())).thenReturn(result);
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
		when(callback.call(anyString(), any())).thenThrow(error);
		return callback;
	}

}
