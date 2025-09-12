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

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SyncMcpToolCallback.Builder}.
 *
 * @author Christian Tzolov
 * @author YunKui Lu
 */
class SyncMcpToolCallbackBuilderTest {

	@Test
	void builderShouldCreateInstanceWithRequiredFields() {
		McpSyncClient mcpClient = Mockito.mock(McpSyncClient.class);
		McpSchema.Implementation clientInfo = new McpSchema.Implementation("test-client", "1.0.0");
		when(mcpClient.getClientInfo()).thenReturn(clientInfo);

		Tool tool = Mockito.mock(Tool.class);
		when(tool.name()).thenReturn("test-tool");
		when(tool.description()).thenReturn("Test tool description");

		SyncMcpToolCallback callback = SyncMcpToolCallback.builder()
			.mcpClient(mcpClient)

			.tool(tool)
			.build();

		assertThat(callback).isNotNull();
		assertThat(callback.getOriginalToolName()).isEqualTo("test-tool");
		assertThat(callback.getToolDefinition()).isNotNull();
		assertThat(callback.getToolDefinition().name()).isEqualTo("test_tool");
		assertThat(callback.getToolDefinition().description()).isEqualTo("Test tool description");
	}

	@Test
	void builderShouldCreateInstanceWithAllFields() {
		McpSyncClient mcpClient = Mockito.mock(McpSyncClient.class);

		Tool tool = Mockito.mock(Tool.class);
		when(tool.name()).thenReturn("test-tool");
		when(tool.description()).thenReturn("Test tool description");

		String customPrefixedName = "custom_prefix_test-tool";
		ToolContextToMcpMetaConverter customConverter = ToolContextToMcpMetaConverter.defaultConverter();

		SyncMcpToolCallback callback = SyncMcpToolCallback.builder()
			.mcpClient(mcpClient)
			.tool(tool)
			.prefixedToolName(customPrefixedName)
			.toolContextToMcpMetaConverter(customConverter)
			.build();

		assertThat(callback).isNotNull();
		assertThat(callback.getOriginalToolName()).isEqualTo("test-tool");
		assertThat(callback.getToolDefinition()).isNotNull();
		assertThat(callback.getToolDefinition().name()).isEqualTo(customPrefixedName);
		assertThat(callback.getToolDefinition().description()).isEqualTo("Test tool description");
	}

	@Test
	void builderShouldThrowExceptionWhenMcpClientIsNull() {
		Tool tool = Mockito.mock(Tool.class);
		when(tool.name()).thenReturn("test-tool");
		when(tool.description()).thenReturn("Test tool description");

		assertThatThrownBy(() -> SyncMcpToolCallback.builder().tool(tool).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("MCP client must not be null");
	}

	@Test
	void builderShouldThrowExceptionWhenToolIsNull() {
		McpSyncClient mcpClient = Mockito.mock(McpSyncClient.class);

		assertThatThrownBy(() -> SyncMcpToolCallback.builder().mcpClient(mcpClient).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("MCP tool must not be null");
	}

	@Test
	void builderShouldSupportMethodChaining() {
		McpSyncClient mcpClient = Mockito.mock(McpSyncClient.class);
		McpSchema.Implementation clientInfo = new McpSchema.Implementation("test-client", "1.0.0");
		when(mcpClient.getClientInfo()).thenReturn(clientInfo);

		Tool tool = Mockito.mock(Tool.class);
		when(tool.name()).thenReturn("test-tool");
		when(tool.description()).thenReturn("Test tool description");

		SyncMcpToolCallback callback = SyncMcpToolCallback.builder()
			.mcpClient(mcpClient)
			.tool(tool)
			.prefixedToolName("chained_tool_name")
			.toolContextToMcpMetaConverter(ToolContextToMcpMetaConverter.defaultConverter())
			.build();

		assertThat(callback).isNotNull();
		assertThat(callback.getToolDefinition().name()).isEqualTo("chained_tool_name");
	}

}
