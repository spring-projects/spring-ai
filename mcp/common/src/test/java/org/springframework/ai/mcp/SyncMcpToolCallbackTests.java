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

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.execution.ToolCallResult;
import org.springframework.ai.tool.execution.ToolExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncMcpToolCallbackTests {

	@Mock
	private McpSyncClient mcpClient;

	@Mock
	private Tool tool;

	@Test
	void getToolDefinitionShouldReturnCorrectDefinition() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.tool.name()).thenReturn("testTool");
		when(this.tool.description()).thenReturn("Test tool description");

		SyncMcpToolCallback callback = SyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName(McpToolUtils.prefixedToolName(clientInfo.name(), clientInfo.title(), this.tool.name()))
			.toolContextToMcpMetaConverter(ToolContextToMcpMetaConverter.defaultConverter())
			.build();

		var toolDefinition = callback.getToolDefinition();

		assertThat(toolDefinition.name()).isEqualTo("t_testTool");
		assertThat(toolDefinition.description()).isEqualTo("Test tool description");
	}

	@Test
	void getOriginalToolNameShouldReturnCorrectName() {
		when(this.tool.name()).thenReturn("originalToolName");

		SyncMcpToolCallback callback = SyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName("prefix_originalToolName")
			.build();

		assertThat(callback.getOriginalToolName()).isEqualTo("originalToolName");
	}

	@Test
	void callShouldHandleJsonInputAndOutput() {
		when(this.tool.name()).thenReturn("testTool");
		CallToolResult callResult = mock(CallToolResult.class);
		when(this.mcpClient.callTool(any(CallToolRequest.class))).thenReturn(callResult);

		SyncMcpToolCallback callback = SyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName(McpToolUtils.prefixedToolName("testClient", "server1", this.tool.name()))
			.toolContextToMcpMetaConverter(ToolContextToMcpMetaConverter.defaultConverter())
			.build();

		ToolCallResult response = callback.call("{\"param\":\"value\"}");

		assertThat(response).isNotNull();
	}

	@Test
	void callShouldHandleToolContext() {
		when(this.tool.name()).thenReturn("testTool");
		CallToolResult callResult = mock(CallToolResult.class);
		when(this.mcpClient.callTool(any(CallToolRequest.class))).thenReturn(callResult);

		SyncMcpToolCallback callback = SyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName(McpToolUtils.prefixedToolName("testClient", "server1", this.tool.name()))
			.toolContextToMcpMetaConverter(ToolContextToMcpMetaConverter.defaultConverter())
			.build();

		ToolCallResult response = callback.call("{\"param\":\"value\"}", new ToolContext(Map.of("foo", "bar")));

		assertThat(response).isNotNull();
	}

	@Test
	void callShouldHandleNullOrEmptyInput() {
		when(this.tool.name()).thenReturn("testTool");
		CallToolResult callResult = mock(CallToolResult.class);
		when(callResult.content()).thenReturn(List.of());
		when(this.mcpClient.callTool(any(CallToolRequest.class))).thenReturn(callResult);

		SyncMcpToolCallback callback = SyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName("testClient_testTool")
			.build();

		// Test with null input
		ToolCallResult responseNull = callback.call(null);
		assertThat(responseNull.content()).isEqualTo("[]");

		// Test with empty string input
		ToolCallResult responseEmpty = callback.call("");
		assertThat(responseEmpty.content()).isEqualTo("[]");

		// Test with whitespace-only input
		ToolCallResult responseWhitespace = callback.call("   ");
		assertThat(responseWhitespace.content()).isEqualTo("[]");
	}

	@Test
	void callShouldThrowOnError() {
		when(this.tool.name()).thenReturn("testTool");
		var clientInfo = new Implementation("testClient", "server1", "1.0.0");
		CallToolResult callResult = mock(CallToolResult.class);
		when(callResult.isError()).thenReturn(true);
		when(callResult.content()).thenReturn(List.of(new McpSchema.TextContent("Some error data")));
		when(this.mcpClient.callTool(any(CallToolRequest.class))).thenReturn(callResult);

		SyncMcpToolCallback callback = SyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName(McpToolUtils.prefixedToolName(clientInfo.name(), clientInfo.title(), this.tool.name()))
			.toolContextToMcpMetaConverter(ToolContextToMcpMetaConverter.defaultConverter())
			.build();

		assertThatThrownBy(() -> callback.call("{\"param\":\"value\"}")).isInstanceOf(ToolExecutionException.class)
			.cause()
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Error calling tool: [TextContent[annotations=null, text=Some error data, meta=null]]");
	}

	@Test
	void callShouldWrapExceptions() {
		when(this.tool.name()).thenReturn("testTool");
		var clientInfo = new Implementation("testClient", "server1", "1.0.0");
		when(this.mcpClient.callTool(any(CallToolRequest.class))).thenThrow(new RuntimeException("Testing tool error"));

		SyncMcpToolCallback callback = SyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName(McpToolUtils.prefixedToolName(clientInfo.name(), clientInfo.title(), this.tool.name()))
			.toolContextToMcpMetaConverter(ToolContextToMcpMetaConverter.defaultConverter())
			.build();

		assertThatThrownBy(() -> callback.call("{\"param\":\"value\"}")).isInstanceOf(ToolExecutionException.class)
			.rootCause()
			.hasMessage("Testing tool error");
	}

	@Test
	void callShouldHandleEmptyResponse() {
		when(this.tool.name()).thenReturn("testTool");
		CallToolResult callResult = mock(CallToolResult.class);
		when(callResult.isError()).thenReturn(false);
		when(callResult.content()).thenReturn(List.of());
		when(this.mcpClient.callTool(any(CallToolRequest.class))).thenReturn(callResult);

		SyncMcpToolCallback callback = SyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName(McpToolUtils.prefixedToolName("testClient", "server1", this.tool.name()))
			.toolContextToMcpMetaConverter(ToolContextToMcpMetaConverter.defaultConverter())
			.build();

		ToolCallResult response = callback.call("{\"param\":\"value\"}");

		assertThat(response.content()).isEqualTo("[]");
	}

	@Test
	void callShouldHandleMultipleContentItems() {
		when(this.tool.name()).thenReturn("testTool");
		CallToolResult callResult = mock(CallToolResult.class);
		when(callResult.isError()).thenReturn(false);
		when(callResult.content()).thenReturn(
				List.of(new McpSchema.TextContent("First content"), new McpSchema.TextContent("Second content")));
		when(this.mcpClient.callTool(any(CallToolRequest.class))).thenReturn(callResult);

		SyncMcpToolCallback callback = SyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName(McpToolUtils.prefixedToolName("testClient", "server1", this.tool.name()))
			.toolContextToMcpMetaConverter(ToolContextToMcpMetaConverter.defaultConverter())
			.build();

		ToolCallResult response = callback.call("{\"param\":\"value\"}");

		assertThat(response).isNotNull();
		assertThat(response.content()).isEqualTo("[{\"text\":\"First content\"},{\"text\":\"Second content\"}]");
	}

	@Test
	void callShouldHandleNonTextContent() {
		when(this.tool.name()).thenReturn("testTool");
		CallToolResult callResult = mock(CallToolResult.class);
		when(callResult.isError()).thenReturn(false);
		when(callResult.content()).thenReturn(List.of(new McpSchema.ImageContent(null, "base64data", "image/png")));
		when(this.mcpClient.callTool(any(CallToolRequest.class))).thenReturn(callResult);

		SyncMcpToolCallback callback = SyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName(McpToolUtils.prefixedToolName("testClient", "server1", this.tool.name()))
			.toolContextToMcpMetaConverter(ToolContextToMcpMetaConverter.defaultConverter())
			.build();

		ToolCallResult response = callback.call("{\"param\":\"value\"}");

		assertThat(response).isNotNull();
		assertThat(response.content()).isEqualTo("[{\"data\":\"base64data\",\"mimeType\":\"image/png\"}]");
	}

	@Test
	void builderShouldUseDefaultPrefixWhenNotSpecified() {
		when(this.tool.name()).thenReturn("testTool");

		SyncMcpToolCallback callback = SyncMcpToolCallback.builder().mcpClient(this.mcpClient).tool(this.tool).build();

		// The default prefix generator should create a prefixed name
		var toolDefinition = callback.getToolDefinition();
		assertThat(toolDefinition.name()).contains("testTool");
	}

	@Test
	void builderShouldValidateRequiredParameters() {
		// Test missing mcpClient
		assertThatThrownBy(() -> SyncMcpToolCallback.builder().tool(this.tool).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("MCP client must not be null");

		// Test missing tool
		assertThatThrownBy(() -> SyncMcpToolCallback.builder().mcpClient(this.mcpClient).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("MCP tool must not be null");
	}

}
