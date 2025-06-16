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

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.content.Content;
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
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		when(this.tool.name()).thenReturn("testTool");
		when(this.tool.description()).thenReturn("Test tool description");

		SyncMcpToolCallback callback = new SyncMcpToolCallback(this.mcpClient, this.tool);

		var toolDefinition = callback.getToolDefinition();

		assertThat(toolDefinition.name()).isEqualTo(clientInfo.name() + "_testTool");
		assertThat(toolDefinition.description()).isEqualTo("Test tool description");
	}

	@Test
	void callShouldHandleJsonInputAndOutput() {

		// when(mcpClient.getClientInfo()).thenReturn(new Implementation("testClient",
		// "1.0.0"));

		when(this.tool.name()).thenReturn("testTool");
		CallToolResult callResult = mock(CallToolResult.class);
		when(this.mcpClient.callTool(any(CallToolRequest.class))).thenReturn(callResult);

		SyncMcpToolCallback callback = new SyncMcpToolCallback(this.mcpClient, this.tool);

		String response = callback.call("{\"param\":\"value\"}");

		// Assert
		assertThat(response).isNotNull();
	}

	@Test
	void callShouldIgnoreToolContext() {
		// when(mcpClient.getClientInfo()).thenReturn(new Implementation("testClient",
		// "1.0.0"));

		when(this.tool.name()).thenReturn("testTool");
		CallToolResult callResult = mock(CallToolResult.class);
		when(this.mcpClient.callTool(any(CallToolRequest.class))).thenReturn(callResult);

		SyncMcpToolCallback callback = new SyncMcpToolCallback(this.mcpClient, this.tool);

		String response = callback.call("{\"param\":\"value\"}", new ToolContext(Map.of("foo", "bar")));

		assertThat(response).isNotNull();
	}

	@Test
	void callShouldThrowOnError() {
		when(this.tool.name()).thenReturn("testTool");
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		CallToolResult callResult = mock(CallToolResult.class);
		when(callResult.isError()).thenReturn(true);
		when(callResult.content()).thenReturn(List.of(new McpSchema.TextContent("Some error data")));
		when(this.mcpClient.callTool(any(CallToolRequest.class))).thenReturn(callResult);

		SyncMcpToolCallback callback = new SyncMcpToolCallback(this.mcpClient, this.tool);

		assertThatThrownBy(() -> callback.call("{\"param\":\"value\"}")).isInstanceOf(ToolExecutionException.class)
			.cause()
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Error calling tool: [TextContent[audience=null, priority=null, text=Some error data]]");
	}

	@Test
	void callShouldWrapExceptions() {
		when(this.tool.name()).thenReturn("testTool");
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		when(this.mcpClient.callTool(any(CallToolRequest.class))).thenThrow(new RuntimeException("Testing tool error"));

		SyncMcpToolCallback callback = new SyncMcpToolCallback(this.mcpClient, this.tool);

		assertThatThrownBy(() -> callback.call("{\"param\":\"value\"}")).isInstanceOf(ToolExecutionException.class)
			.rootCause()
			.hasMessage("Testing tool error");
	}

}
