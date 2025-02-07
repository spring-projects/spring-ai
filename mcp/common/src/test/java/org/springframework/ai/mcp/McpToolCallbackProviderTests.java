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
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

@ExtendWith(MockitoExtension.class)
class McpToolCallbackProviderTests {

	@Mock
	private McpSyncClient mcpClient;

	@Test
	void getToolCallbacksShouldReturnEmptyArrayWhenNoTools() {
		// Arrange
		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of());
		when(mcpClient.listTools()).thenReturn(listToolsResult);

		SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(mcpClient);

		// Act
		var callbacks = provider.getToolCallbacks();

		// Assert
		assertThat(callbacks).isEmpty();
	}

	@Test
	void getToolCallbacksShouldReturnCallbacksForEachTool() {
		// Arrange
		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("tool1");

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("tool2");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1, tool2));
		when(mcpClient.listTools()).thenReturn(listToolsResult);

		SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(mcpClient);

		// Act
		var callbacks = provider.getToolCallbacks();

		// Assert
		assertThat(callbacks).hasSize(2);
	}

	@Test
	void getToolCallbacksShouldThrowExceptionForDuplicateToolNames() {
		// Arrange
		Tool tool1 = mock(Tool.class);
		when(tool1.name()).thenReturn("sameName");

		Tool tool2 = mock(Tool.class);
		when(tool2.name()).thenReturn("sameName");

		ListToolsResult listToolsResult = mock(ListToolsResult.class);
		when(listToolsResult.tools()).thenReturn(List.of(tool1, tool2));
		when(mcpClient.listTools()).thenReturn(listToolsResult);

		SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(mcpClient);

		// Act & Assert
		assertThatThrownBy(() -> provider.getToolCallbacks()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Multiple tools with the same name");
	}

}
