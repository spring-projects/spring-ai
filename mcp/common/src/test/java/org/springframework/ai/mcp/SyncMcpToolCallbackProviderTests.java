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
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncMcpToolCallbackProviderTests {

	@Mock
	private McpSyncClient mcpClient;

	@Test
	void getToolCallbacksShouldReturnEmptyArrayWhenNoTools() {

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of());
		when(this.mcpClient.listTools()).thenReturn(listToolsResult);

		SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(this.mcpClient);

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).isEmpty();
	}

	@Test
	void getToolCallbacksShouldReturnCallbacksForEachTool() {

		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("tool2");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1, tool2));
		when(this.mcpClient.listTools()).thenReturn(listToolsResult);

		SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(this.mcpClient);

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(2);
	}

	@Test
	void getToolCallbacksShouldThrowExceptionForDuplicateToolNames() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("sameName");

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("sameName");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1, tool2));
		when(this.mcpClient.listTools()).thenReturn(listToolsResult);

		SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(this.mcpClient);

		assertThatThrownBy(() -> provider.getToolCallbacks()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Multiple tools with the same name");
	}

	@Test
	void getSameNameToolsButDifferentClientInfoNamesShouldProduceDifferentToolCallbackNames() {

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("sameName");

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("sameName");

		McpSyncClient mcpClient1 = mock(McpSyncClient.class);
		ListToolsResult listToolsResult1 = mock(ListToolsResult.class);
		when(listToolsResult1.tools()).thenReturn(List.of(tool1));
		when(mcpClient1.listTools()).thenReturn(listToolsResult1);

		var clientInfo1 = new Implementation("testClient1", "1.0.0");
		when(mcpClient1.getClientInfo()).thenReturn(clientInfo1);

		McpSyncClient mcpClient2 = mock(McpSyncClient.class);
		ListToolsResult listToolsResult2 = mock(ListToolsResult.class);
		when(listToolsResult2.tools()).thenReturn(List.of(tool2));
		when(mcpClient2.listTools()).thenReturn(listToolsResult2);

		var clientInfo2 = new Implementation("testClient2", "1.0.0");
		when(mcpClient2.getClientInfo()).thenReturn(clientInfo2);

		SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(mcpClient1, mcpClient2);

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(2);
	}

	@Test
	void toolFilterShouldAcceptAllToolsByDefault() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("tool2");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1, tool2));
		when(this.mcpClient.listTools()).thenReturn(listToolsResult);

		// Using the constructor without explicit filter (should use default filter that
		// accepts all)
		SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(this.mcpClient);

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(2);
	}

	@Test
	void toolFilterShouldRejectAllToolsWhenConfigured() {

		Tool tool1 = mock(Tool.class);
		Tool tool2 = mock(Tool.class);

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1, tool2));
		when(this.mcpClient.listTools()).thenReturn(listToolsResult);

		// Create a filter that rejects all tools
		McpToolFilter rejectAllFilter = (client, tool) -> false;

		SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(rejectAllFilter,
				McpToolNamePrefixGenerator.defaultGenerator(), this.mcpClient);

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).isEmpty();
	}

	@Test
	void toolFilterShouldFilterToolsByNameWhenConfigured() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("tool2");

		Tool tool3 = mock(Tool.class);
		when(tool3.name()).thenReturn("tool3");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1, tool2, tool3));
		when(this.mcpClient.listTools()).thenReturn(listToolsResult);

		// Create a filter that only accepts tools with names containing "2" or "3"
		McpToolFilter nameFilter = (client, tool) -> tool.name().contains("2") || tool.name().contains("3");

		SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(nameFilter,
				McpToolNamePrefixGenerator.defaultGenerator(), this.mcpClient);

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(2);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("testClient_tool2");
		assertThat(callbacks[1].getToolDefinition().name()).isEqualTo("testClient_tool3");
	}

	@Test
	void toolFilterShouldFilterToolsByClientWhenConfigured() {
		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		Tool tool2 = mock(Tool.class);

		McpSyncClient mcpClient1 = mock(McpSyncClient.class);
		ListToolsResult listToolsResult1 = mock(ListToolsResult.class);
		when(listToolsResult1.tools()).thenReturn(List.of(tool1));
		when(mcpClient1.listTools()).thenReturn(listToolsResult1);

		var clientInfo1 = new Implementation("testClient1", "1.0.0");
		when(mcpClient1.getClientInfo()).thenReturn(clientInfo1);

		McpSyncClient mcpClient2 = mock(McpSyncClient.class);
		ListToolsResult listToolsResult2 = mock(ListToolsResult.class);
		when(listToolsResult2.tools()).thenReturn(List.of(tool2));
		when(mcpClient2.listTools()).thenReturn(listToolsResult2);

		var clientInfo2 = new Implementation("testClient2", "1.0.0");
		when(mcpClient2.getClientInfo()).thenReturn(clientInfo2);

		// Create a filter that only accepts tools from client1
		McpToolFilter clientFilter = (mcpConnectionInfo,
				tool) -> mcpConnectionInfo.clientInfo().name().equals("testClient1");

		SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(clientFilter,
				McpToolNamePrefixGenerator.defaultGenerator(), mcpClient1, mcpClient2);

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(1);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("testClient1_tool1");
	}

	@Test
	void toolFilterShouldCombineClientAndToolCriteriaWhenConfigured() {
		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("weather");

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("calculator");

		McpSyncClient weatherClient = mock(McpSyncClient.class);
		ListToolsResult weatherResult = mock(ListToolsResult.class);
		when(weatherResult.tools()).thenReturn(List.of(tool1, tool2));
		when(weatherClient.listTools()).thenReturn(weatherResult);

		var weatherClientInfo = new Implementation("weather-service", "1.0.0");
		when(weatherClient.getClientInfo()).thenReturn(weatherClientInfo);

		// Create a filter that only accepts weather tools from the weather service
		McpToolFilter complexFilter = (mcpConnectionInfo,
				tool) -> mcpConnectionInfo.clientInfo().name().equals("weather-service")
						&& tool.name().equals("weather");

		SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(complexFilter,
				McpToolNamePrefixGenerator.defaultGenerator(), weatherClient);

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(1);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("weather_service_weather");
	}

}
