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

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
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
		String result = McpToolUtils.prefixedToolName("prefix", "server1", "toolName");
		assertThat(result).isEqualTo("p_server1_toolName");
	}

	@Test
	void prefixedToolNameShouldReplaceSpecialCharacters() {
		String result = McpToolUtils.prefixedToolName("pre.fix", "server1", "tool@Name");
		assertThat(result).isEqualTo("p_server1_toolName");
	}

	@Test
	void prefixedToolNameShouldReplaceHyphensWithUnderscores() {
		String result = McpToolUtils.prefixedToolName("p", "tool-name");
		assertThat(result).isEqualTo("p_tool_name");
	}

	@Test
	void prefixedToolNameShouldTruncateLongStrings() {
		String longPrefix = "a".repeat(40);
		String longToolName = "b".repeat(62);
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
	void prefixedToolNameShouldSupportChineseCharacters() {
		String result = McpToolUtils.prefixedToolName("前缀", "工具名称");
		assertThat(result).isEqualTo("前_工具名称");
	}

	@Test
	void prefixedToolNameShouldSupportMixedChineseAndEnglish() {
		String result = McpToolUtils.prefixedToolName("prefix前缀", "tool工具Name");
		assertThat(result).isEqualTo("p_tool工具Name");
	}

	@Test
	void prefixedToolNameShouldRemoveSpecialCharactersButKeepChinese() {
		String result = McpToolUtils.prefixedToolName("pre@fix前缀", "tool#工具$name");
		assertThat(result).isEqualTo("p_tool工具name");
	}

	@Test
	void prefixedToolNameShouldHandleChineseWithHyphens() {
		String result = McpToolUtils.prefixedToolName("前缀-test", "工具-name");
		assertThat(result).isEqualTo("前_t_工具_name");
	}

	@Test
	void prefixedToolNameShouldTruncateLongChineseStrings() {
		// Create a string with Chinese characters that exceeds 64 characters
		String longPrefix = "前缀".repeat(20); // 40 Chinese characters
		String longToolName = "工具".repeat(20); // 40 Chinese characters
		String result = McpToolUtils.prefixedToolName(longPrefix, longToolName);
		assertThat(result).hasSize(42);
		assertThat(result).endsWith("_" + "工具".repeat(20));
	}

	@Test
	void prefixedToolNameShouldHandleChinesePunctuation() {
		String result = McpToolUtils.prefixedToolName("前缀，测试", "工具。名称！");
		assertThat(result).isEqualTo("前_工具名称");
	}

	@Test
	void prefixedToolNameShouldHandleUnicodeBoundaries() {
		// Test characters at the boundaries of the Chinese Unicode range
		String result1 = McpToolUtils.prefixedToolName("prefix", "tool\u4e00"); // First
																				// Chinese
																				// character
		assertThat(result1).isEqualTo("p_tool\u4e00");

		String result2 = McpToolUtils.prefixedToolName("prefix", "tool\u9fa5"); // Last
																				// Chinese
																				// character
		assertThat(result2).isEqualTo("p_tool\u9fa5");
	}

	@Test
	void prefixedToolNameShouldExcludeNonChineseUnicodeCharacters() {
		// Test with Japanese Hiragana (outside Chinese range)
		String result1 = McpToolUtils.prefixedToolName("prefix", "toolあ"); // Japanese
																			// Hiragana
		assertThat(result1).isEqualTo("p_tool");

		// Test with Korean characters (outside Chinese range)
		String result2 = McpToolUtils.prefixedToolName("prefix", "tool한"); // Korean
																			// character
		assertThat(result2).isEqualTo("p_tool");

		// Test with Arabic characters (outside Chinese range)
		String result3 = McpToolUtils.prefixedToolName("prefix", "toolع"); // Arabic
																			// character
		assertThat(result3).isEqualTo("p_tool");
	}

	@Test
	void prefixedToolNameShouldHandleEmojisAndSymbols() {
		// Emojis and symbols should be removed
		String result = McpToolUtils.prefixedToolName("prefix🚀", "tool工具😀name");
		assertThat(result).isEqualTo("p_tool工具name");
	}

	@Test
	void prefixedToolNameShouldPreserveNumbersWithChinese() {
		String result = McpToolUtils.prefixedToolName("前缀123", "工具456名称");
		assertThat(result).isEqualTo("前_工具456名称");
	}

	@Test
	void prefixedToolNameShouldSupportExtendedHanCharacters() {
		// Test boundary character at end of CJK Unified Ideographs block
		String result1 = McpToolUtils.prefixedToolName("prefix", "tool\u9fff"); // CJK
																				// block
																				// boundary
		assertThat(result1).isEqualTo("p_tool\u9fff");

		// Test CJK Extension A characters
		String result2 = McpToolUtils.prefixedToolName("prefix", "tool\u3400"); // CJK Ext
																				// A
		assertThat(result2).isEqualTo("p_tool\u3400");
	}

	@Test
	void prefixedToolNameShouldSupportCompatibilityIdeographs() {
		// Test CJK Compatibility Ideographs
		String result = McpToolUtils.prefixedToolName("prefix", "tool\uf900"); // Compatibility
																				// ideograph
		assertThat(result).isEqualTo("p_tool\uf900");
	}

	@Test
	void prefixedToolNameShouldHandleAllHanScriptCharacters() {
		// Mix of different Han character blocks: Extension A + CJK Unified +
		// Compatibility
		String result = McpToolUtils.prefixedToolName("前缀\u3400", "缀\\u3400", "工具\u9fff名称\uf900");
		assertThat(result).isEqualTo("前_缀u3400_工具鿿名称豈");
	}

	@Test
	void constructorShouldBePrivate() throws Exception {
		Constructor<McpToolUtils> constructor = McpToolUtils.class.getDeclaredConstructor();
		assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
		constructor.setAccessible(true);
		constructor.newInstance();
	}

	@Test
	void toSyncToolSpecificationShouldConvertSingleCallback() {

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
	void toAsyncToolSpecificationShouldConvertSingleCallback() {
		ToolCallback callback = createMockToolCallback("test", "success");

		AsyncToolSpecification toolSpecification = McpToolUtils.toAsyncToolSpecification(callback);

		// Assert
		assertThat(toolSpecification).isNotNull();
		assertThat(toolSpecification.tool().name()).isEqualTo("test");

		StepVerifier
			.create(toolSpecification.callHandler()
				.apply(mock(McpAsyncServerExchange.class), mock(McpSchema.CallToolRequest.class)))
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

		AsyncToolSpecification toolSpecification = McpToolUtils.toAsyncToolSpecification(callback);

		assertThat(toolSpecification).isNotNull();
		StepVerifier
			.create(toolSpecification.callHandler()
				.apply(mock(McpAsyncServerExchange.class), mock(McpSchema.CallToolRequest.class)))
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
		ToolDefinition definition = DefaultToolDefinition.builder()
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
		ToolDefinition definition = DefaultToolDefinition.builder()
			.name(name)
			.description("Test tool")
			.inputSchema("{}")
			.build();
		when(callback.getToolDefinition()).thenReturn(definition);
		when(callback.call(anyString(), any())).thenThrow(error);
		return callback;
	}

	@Test
	void getToolCallbacksFromSyncClientsWithEmptyListShouldReturnEmptyList() {
		List<ToolCallback> result = McpToolUtils.getToolCallbacksFromSyncClients(List.of());
		assertThat(result).isEmpty();
	}

	@Test
	void getToolCallbacksFromSyncClientsWithSingleClientShouldReturnToolCallbacks() {
		McpSyncClient mockClient = mock(McpSyncClient.class);
		Implementation clientInfo = new Implementation("test-client", "1.0.0");
		ClientCapabilities clientCapabilities = new ClientCapabilities(null, null, null, null);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");
		when(tool1.description()).thenReturn("Test Tool 1");

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("tool2");
		when(tool2.description()).thenReturn("Test Tool 2");

		when(mockClient.getClientInfo()).thenReturn(clientInfo);
		when(mockClient.getClientCapabilities()).thenReturn(clientCapabilities);

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1, tool2));
		when(mockClient.listTools()).thenReturn(listToolsResult);

		List<ToolCallback> result = McpToolUtils.getToolCallbacksFromSyncClients(mockClient);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).getToolDefinition().name()).isEqualTo("tool1");
		assertThat(result.get(1).getToolDefinition().name()).isEqualTo("tool2");

		List<ToolCallback> result2 = McpToolUtils.getToolCallbacksFromSyncClients(List.of(mockClient));

		assertThat(result2).hasSize(2);
		assertThat(result2.get(0).getToolDefinition().name()).isEqualTo("tool1");
		assertThat(result2.get(1).getToolDefinition().name()).isEqualTo("tool2");
	}

	@Test
	void getToolCallbacksFromSyncClientsWithMultipleClientsShouldReturnCombinedToolCallbacks() {

		McpSyncClient mockClient1 = mock(McpSyncClient.class);
		Implementation clientInfo1 = new Implementation("client1", "1.0.0");
		ClientCapabilities clientCapabilities1 = new ClientCapabilities(null, null, null, null);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");
		when(tool1.description()).thenReturn("Test Tool 1");

		McpSyncClient mockClient2 = mock(McpSyncClient.class);
		Implementation clientInfo2 = new Implementation("client2", "1.0.0");
		ClientCapabilities clientCapabilities2 = new ClientCapabilities(null, null, null, null);

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("tool2");
		when(tool2.description()).thenReturn("Test Tool 2");

		when(mockClient1.getClientInfo()).thenReturn(clientInfo1);
		when(mockClient1.getClientCapabilities()).thenReturn(clientCapabilities1);

		ListToolsResult listToolsResult1 = mock(ListToolsResult.class);
		when(listToolsResult1.tools()).thenReturn(List.of(tool1));
		when(mockClient1.listTools()).thenReturn(listToolsResult1);

		when(mockClient2.getClientInfo()).thenReturn(clientInfo2);
		when(mockClient2.getClientCapabilities()).thenReturn(clientCapabilities2);

		ListToolsResult listToolsResult2 = mock(ListToolsResult.class);
		when(listToolsResult2.tools()).thenReturn(List.of(tool2));
		when(mockClient2.listTools()).thenReturn(listToolsResult2);

		List<ToolCallback> result = McpToolUtils.getToolCallbacksFromSyncClients(mockClient1, mockClient2);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).getToolDefinition().name()).isEqualTo("tool1");
		assertThat(result.get(1).getToolDefinition().name()).isEqualTo("tool2");

		List<ToolCallback> result2 = McpToolUtils.getToolCallbacksFromSyncClients(List.of(mockClient1, mockClient2));

		assertThat(result2).hasSize(2);
		assertThat(result2.get(0).getToolDefinition().name()).isEqualTo("tool1");
		assertThat(result2.get(1).getToolDefinition().name()).isEqualTo("tool2");
	}

	@Test
	void getToolCallbacksFromSyncClientsShouldHandleDuplicateToolNames() {

		McpSyncClient mockClient1 = mock(McpSyncClient.class);
		Implementation clientInfo1 = new Implementation("client", "1.0.0");
		ClientCapabilities clientCapabilities1 = new ClientCapabilities(null, null, null, null);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool");
		when(tool1.description()).thenReturn("Test Tool 1");

		McpSyncClient mockClient2 = mock(McpSyncClient.class);
		Implementation clientInfo2 = new Implementation("client", "1.0.0");
		ClientCapabilities clientCapabilities2 = new ClientCapabilities(null, null, null, null);

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("tool");
		when(tool2.description()).thenReturn("Test Tool 2");

		when(mockClient1.getClientInfo()).thenReturn(clientInfo1);
		when(mockClient1.getClientCapabilities()).thenReturn(clientCapabilities1);

		ListToolsResult listToolsResult1 = mock(ListToolsResult.class);
		when(listToolsResult1.tools()).thenReturn(List.of(tool1));
		when(mockClient1.listTools()).thenReturn(listToolsResult1);

		when(mockClient2.getClientInfo()).thenReturn(clientInfo2);
		when(mockClient2.getClientCapabilities()).thenReturn(clientCapabilities2);

		ListToolsResult listToolsResult2 = mock(ListToolsResult.class);
		when(listToolsResult2.tools()).thenReturn(List.of(tool2));
		when(mockClient2.listTools()).thenReturn(listToolsResult2);

		assertThatThrownBy(() -> McpToolUtils.getToolCallbacksFromSyncClients(mockClient1, mockClient2))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Multiple tools with the same name");
	}

}
