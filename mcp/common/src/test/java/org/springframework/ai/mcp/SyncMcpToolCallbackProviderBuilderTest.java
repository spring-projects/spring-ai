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

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SyncMcpToolCallbackProvider.Builder}.
 *
 * @author Christian Tzolov
 */
class SyncMcpToolCallbackProviderBuilderTest {

	@Test
	void builderShouldCreateInstanceWithSingleClient() {

		McpSyncClient mcpClient = createMockClient("test-client", "test-tool");

		SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder().addMcpClient(mcpClient).build();

		assertThat(provider).isNotNull();
		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(1);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("test_tool");
	}

	@Test
	void builderShouldCreateInstanceWithMultipleClients() {

		McpSyncClient client1 = createMockClient("client1", "tool1");
		McpSyncClient client2 = createMockClient("client2", "tool2");

		SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
			.addMcpClient(client1)
			.addMcpClient(client2)
			.build();

		assertThat(provider).isNotNull();
		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(2);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("tool1");
		assertThat(callbacks[1].getToolDefinition().name()).isEqualTo("tool2");
	}

	@Test
	void builderShouldCreateInstanceWithClientList() {

		McpSyncClient client1 = createMockClient("client1", "tool1");
		McpSyncClient client2 = createMockClient("client2", "tool2");
		List<McpSyncClient> clients = List.of(client1, client2);

		SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder().mcpClients(clients).build();

		assertThat(provider).isNotNull();
		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(2);
	}

	@Test
	void builderShouldCreateInstanceWithClientArray() {

		McpSyncClient client1 = createMockClient("client1", "tool1");
		McpSyncClient client2 = createMockClient("client2", "tool2");

		SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
			.mcpClients(client1, client2)
			.build();

		assertThat(provider).isNotNull();
		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(2);
	}

	@Test
	void builderShouldCreateInstanceWithCustomToolFilter() {

		McpSyncClient client = createMockClient("client", "filtered-tool");
		McpToolFilter customFilter = (connectionInfo, tool) -> tool.name().startsWith("filtered");

		SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
			.addMcpClient(client)
			.toolFilter(customFilter)
			.build();

		assertThat(provider).isNotNull();
		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(1);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("filtered_tool");
	}

	@Test
	void builderShouldCreateInstanceWithCustomToolNamePrefixGenerator() {

		McpSyncClient client = createMockClient("client", "tool");
		McpToolNamePrefixGenerator customGenerator = (connectionInfo, tool) -> "custom_" + tool.name();

		SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
			.addMcpClient(client)
			.toolNamePrefixGenerator(customGenerator)
			.build();

		assertThat(provider).isNotNull();
		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(1);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("custom_tool");
	}

	@Test
	void builderShouldCreateInstanceWithAllCustomParameters() {

		McpSyncClient client = createMockClient("client", "custom-tool");
		McpToolFilter customFilter = (connectionInfo, tool) -> tool.name().contains("custom");
		McpToolNamePrefixGenerator customGenerator = (connectionInfo, tool) -> "prefix_" + tool.name();

		SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
			.addMcpClient(client)
			.toolFilter(customFilter)
			.toolNamePrefixGenerator(customGenerator)
			.build();

		assertThat(provider).isNotNull();
		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(1);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("prefix_custom-tool");
	}

	@Test
	void builderShouldThrowExceptionWhenClientListIsNull() {

		assertThatThrownBy(() -> SyncMcpToolCallbackProvider.builder().mcpClients((List<McpSyncClient>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("MCP clients list must not be null");
	}

	@Test
	void builderShouldThrowExceptionWhenClientArrayIsNull() {

		assertThatThrownBy(() -> SyncMcpToolCallbackProvider.builder().mcpClients((McpSyncClient[]) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("MCP clients array must not be null");
	}

	@Test
	void builderShouldThrowExceptionWhenAddingNullClient() {

		assertThatThrownBy(() -> SyncMcpToolCallbackProvider.builder().addMcpClient(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("MCP client must not be null");
	}

	@Test
	void builderShouldThrowExceptionWhenToolFilterIsNull() {

		McpSyncClient client = createMockClient("client", "tool");

		assertThatThrownBy(() -> SyncMcpToolCallbackProvider.builder().addMcpClient(client).toolFilter(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Tool filter must not be null");
	}

	@Test
	void builderShouldThrowExceptionWhenToolNamePrefixGeneratorIsNull() {

		McpSyncClient client = createMockClient("client", "tool");

		assertThatThrownBy(
				() -> SyncMcpToolCallbackProvider.builder().addMcpClient(client).toolNamePrefixGenerator(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Tool name prefix generator must not be null");
	}

	@Test
	void builderShouldSupportMethodChaining() {

		McpSyncClient client1 = createMockClient("client1", "tool1");
		McpSyncClient client2 = createMockClient("client2", "tool2");
		McpToolFilter filter = (connectionInfo, tool) -> true;
		McpToolNamePrefixGenerator generator = new DefaultMcpToolNamePrefixGenerator();

		SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
			.addMcpClient(client1)
			.addMcpClient(client2)
			.toolFilter(filter)
			.toolNamePrefixGenerator(generator)
			.build();

		assertThat(provider).isNotNull();
		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(2);
	}

	@Test
	void builderShouldReplaceClientsWhenSettingNewList() {

		McpSyncClient client1 = createMockClient("client1", "tool1");
		McpSyncClient client2 = createMockClient("client2", "tool2");
		McpSyncClient client3 = createMockClient("client3", "tool3");

		SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
			.addMcpClient(client1)
			.mcpClients(List.of(client2, client3)) // This should replace client1
			.build();

		assertThat(provider).isNotNull();
		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(2);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("tool2");
		assertThat(callbacks[1].getToolDefinition().name()).isEqualTo("tool3");
	}

	private McpSyncClient createMockClient(String clientName, String toolName) {
		McpSyncClient mcpClient = Mockito.mock(McpSyncClient.class);

		// Mock client info
		McpSchema.Implementation clientInfo = new McpSchema.Implementation(clientName, "1.0.0");
		when(mcpClient.getClientInfo()).thenReturn(clientInfo);

		// Mock client capabilities
		McpSchema.ClientCapabilities capabilities = Mockito.mock(McpSchema.ClientCapabilities.class);
		when(mcpClient.getClientCapabilities()).thenReturn(capabilities);

		// Mock initialization result
		McpSchema.InitializeResult initResult = Mockito.mock(McpSchema.InitializeResult.class);
		when(mcpClient.getCurrentInitializationResult()).thenReturn(initResult);

		// Mock tool
		Tool tool = Mockito.mock(Tool.class);
		when(tool.name()).thenReturn(toolName);
		when(tool.description()).thenReturn("Test tool description");
		when(tool.inputSchema()).thenReturn(Mockito.mock(McpSchema.JsonSchema.class));

		// Mock list tools response
		McpSchema.ListToolsResult listToolsResult = Mockito.mock(McpSchema.ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool));
		when(mcpClient.listTools()).thenReturn(listToolsResult);

		return mcpClient;
	}

}
