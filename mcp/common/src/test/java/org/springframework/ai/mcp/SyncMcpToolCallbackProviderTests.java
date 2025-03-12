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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

@ExtendWith(MockitoExtension.class)
class SyncMcpToolCallbackProviderTests {

	@Mock
	private McpSyncClient mcpClient;

	@Test
	void getToolCallbacksShouldReturnEmptyArrayWhenNoTools() {

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of());
		when(mcpClient.listTools()).thenReturn(listToolsResult);

		SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(mcpClient);

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).isEmpty();
	}

	@Test
	void getToolCallbacksShouldReturnCallbacksForEachTool() {

		var clientInfo = new Implementation("testClient", "1.0.0");
		when(mcpClient.getClientInfo()).thenReturn(clientInfo);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("tool2");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1, tool2));
		when(mcpClient.listTools()).thenReturn(listToolsResult);

		SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(mcpClient);

		var callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(2);
	}

	@Test
	void getToolCallbacksShouldThrowExceptionForDuplicateToolNames() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(mcpClient.getClientInfo()).thenReturn(clientInfo);

		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("sameName");

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("sameName");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1, tool2));
		when(mcpClient.listTools()).thenReturn(listToolsResult);

		SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(mcpClient);

		assertThatThrownBy(() -> provider.getToolCallbacks()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Multiple tools with the same name");
	}

	@Test
	void getSameNameToolsButDifferntClientInfoNamesShouldProduceDifferentToolCallbackNames() {

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

}
