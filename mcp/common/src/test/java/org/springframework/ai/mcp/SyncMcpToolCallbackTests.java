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

import java.util.Map;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.model.ToolContext;

import static org.assertj.core.api.Assertions.assertThat;
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
		// Arrange
		when(tool.name()).thenReturn("testTool");
		when(tool.description()).thenReturn("Test tool description");

		SyncMcpToolCallback callback = new SyncMcpToolCallback(mcpClient, tool);

		// Act
		var toolDefinition = callback.getToolDefinition();

		// Assert
		assertThat(toolDefinition.name()).isEqualTo("testTool");
		assertThat(toolDefinition.description()).isEqualTo("Test tool description");
	}

	@Test
	void callShouldHandleJsonInputAndOutput() {
		// Arrange
		when(tool.name()).thenReturn("testTool");
		CallToolResult callResult = mock(CallToolResult.class);
		when(mcpClient.callTool(any(CallToolRequest.class))).thenReturn(callResult);

		SyncMcpToolCallback callback = new SyncMcpToolCallback(mcpClient, tool);

		// Act
		String response = callback.call("{\"param\":\"value\"}");

		// Assert
		assertThat(response).isNotNull();
	}

	@Test
	void callShoulIngroeToolContext() {
		// Arrange
		when(tool.name()).thenReturn("testTool");
		CallToolResult callResult = mock(CallToolResult.class);
		when(mcpClient.callTool(any(CallToolRequest.class))).thenReturn(callResult);

		SyncMcpToolCallback callback = new SyncMcpToolCallback(mcpClient, tool);

		// Act
		String response = callback.call("{\"param\":\"value\"}", new ToolContext(Map.of("foo", "bar")));

		// Assert
		assertThat(response).isNotNull();
	}

}
