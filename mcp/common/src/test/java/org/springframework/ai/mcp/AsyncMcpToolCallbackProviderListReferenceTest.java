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

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that {@link AsyncMcpToolCallbackProvider} correctly maintains list references
 * when using {@code mcpClientsReference()}.
 *
 * @author Christian Tzolov
 */
class AsyncMcpToolCallbackProviderListReferenceTest {

	@Test
	void testMcpClientsReferenceSharesList() {
		// Create an empty list that will be populated later
		List<McpAsyncClient> clientsList = new ArrayList<>();

		// Create provider with reference to empty list
		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder()
			.mcpClientsReference(clientsList)
			.build();

		// Initially, no tool callbacks should be available
		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).isEmpty();

		// Now simulate SmartInitializingSingleton populating the list
		McpAsyncClient mockClient = mock(McpAsyncClient.class);
		McpSchema.Tool mockTool = mock(McpSchema.Tool.class);
		when(mockTool.name()).thenReturn("test_tool");
		when(mockTool.description()).thenReturn("Test tool");

		McpSchema.ListToolsResult toolsResult = mock(McpSchema.ListToolsResult.class);
		when(toolsResult.tools()).thenReturn(List.of(mockTool));
		when(mockClient.listTools()).thenReturn(Mono.just(toolsResult));

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
	void testMcpClientsStoresReference() {
		// Create a list with a client
		McpAsyncClient mockClient = mock(McpAsyncClient.class);
		McpSchema.Tool mockTool = mock(McpSchema.Tool.class);
		when(mockTool.name()).thenReturn("test_tool");
		when(mockTool.description()).thenReturn("Test tool");

		McpSchema.ListToolsResult toolsResult = mock(McpSchema.ListToolsResult.class);
		when(toolsResult.tools()).thenReturn(List.of(mockTool));
		when(mockClient.listTools()).thenReturn(Mono.just(toolsResult));

		// Mock connection info
		when(mockClient.getClientCapabilities()).thenReturn(mock(McpSchema.ClientCapabilities.class));
		when(mockClient.getClientInfo()).thenReturn(mock(McpSchema.Implementation.class));
		when(mockClient.getCurrentInitializationResult()).thenReturn(mock(McpSchema.InitializeResult.class));

		List<McpAsyncClient> clientsList = new ArrayList<>();
		clientsList.add(mockClient);

		// Create provider - async version stores reference (no defensive copy)
		AsyncMcpToolCallbackProvider provider = AsyncMcpToolCallbackProvider.builder().mcpClients(clientsList).build();

		// Provider should see the initial client
		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(1);

		// Add another client to the original list
		McpAsyncClient mockClient2 = mock(McpAsyncClient.class);
		McpSchema.Tool mockTool2 = mock(McpSchema.Tool.class);
		when(mockTool2.name()).thenReturn("test_tool_2");
		when(mockTool2.description()).thenReturn("Test tool 2");

		McpSchema.ListToolsResult toolsResult2 = mock(McpSchema.ListToolsResult.class);
		when(toolsResult2.tools()).thenReturn(List.of(mockTool2));
		when(mockClient2.listTools()).thenReturn(Mono.just(toolsResult2));

		when(mockClient2.getClientCapabilities()).thenReturn(mock(McpSchema.ClientCapabilities.class));
		when(mockClient2.getClientInfo()).thenReturn(mock(McpSchema.Implementation.class));
		when(mockClient2.getCurrentInitializationResult()).thenReturn(mock(McpSchema.InitializeResult.class));

		clientsList.add(mockClient2);

		// Provider shares the reference, so should see both clients
		callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(2);
	}

}
