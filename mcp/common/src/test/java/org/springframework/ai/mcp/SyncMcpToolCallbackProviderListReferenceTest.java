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

import java.util.ArrayList;
import java.util.List;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that {@link SyncMcpToolCallbackProvider} correctly maintains list references when
 * using {@code mcpClientsReference()}.
 *
 * @author Christian Tzolov
 */
class SyncMcpToolCallbackProviderListReferenceTest {

	@Test
	void testMcpClientsReferenceSharesList() {
		// Create an empty list that will be populated later
		List<McpSyncClient> clientsList = new ArrayList<>();

		// Create provider with reference to empty list
		SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
			.mcpClientsReference(clientsList)
			.build();

		// Initially, no tool callbacks should be available
		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).isEmpty();

		// Now simulate SmartInitializingSingleton populating the list
		McpSyncClient mockClient = mock(McpSyncClient.class);
		McpSchema.Tool mockTool = mock(McpSchema.Tool.class);
		when(mockTool.name()).thenReturn("test_tool");
		when(mockTool.description()).thenReturn("Test tool");

		McpSchema.ListToolsResult toolsResult = mock(McpSchema.ListToolsResult.class);
		when(toolsResult.tools()).thenReturn(List.of(mockTool));
		when(mockClient.listTools()).thenReturn(toolsResult);

		// Mock connection info
		when(mockClient.getClientCapabilities()).thenReturn(mock(McpSchema.ClientCapabilities.class));
		when(mockClient.getClientInfo()).thenReturn(mock(McpSchema.Implementation.class));
		when(mockClient.getCurrentInitializationResult()).thenReturn(mock(McpSchema.InitializeResult.class));

		clientsList.add(mockClient);

		// Now the provider should see the client and return tool callbacks
		callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(1);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("test_tool");
	}

	@Test
	void testMcpClientsCreatesCopy() {
		// Create a list with a client
		McpSyncClient mockClient = mock(McpSyncClient.class);
		McpSchema.Tool mockTool = mock(McpSchema.Tool.class);
		when(mockTool.name()).thenReturn("test_tool");
		when(mockTool.description()).thenReturn("Test tool");

		McpSchema.ListToolsResult toolsResult = mock(McpSchema.ListToolsResult.class);
		when(toolsResult.tools()).thenReturn(List.of(mockTool));
		when(mockClient.listTools()).thenReturn(toolsResult);

		// Mock connection info
		when(mockClient.getClientCapabilities()).thenReturn(mock(McpSchema.ClientCapabilities.class));
		when(mockClient.getClientInfo()).thenReturn(mock(McpSchema.Implementation.class));
		when(mockClient.getCurrentInitializationResult()).thenReturn(mock(McpSchema.InitializeResult.class));

		List<McpSyncClient> clientsList = new ArrayList<>();
		clientsList.add(mockClient);

		// Create provider with defensive copy
		SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder().mcpClients(clientsList).build();

		// Provider should see the initial client
		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(1);

		// Clear the original list
		clientsList.clear();

		// Provider still has its copy, so should still return the tool
		callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(1);
	}

}
