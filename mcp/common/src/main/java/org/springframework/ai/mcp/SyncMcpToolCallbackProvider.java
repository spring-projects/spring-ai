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

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.util.ToolUtils;
import org.springframework.util.CollectionUtils;

/**
 * Implementation of {@link ToolCallbackProvider} that discovers and provides MCP tools.
 * <p>
 * This class acts as a tool provider for Spring AI, automatically discovering tools from
 * an MCP server and making them available as Spring AI tools. It:
 * <ul>
 * <li>Connects to an MCP server through a sync client</li>
 * <li>Lists and retrieves available tools from the server</li>
 * <li>Creates {@link McpToolCallback} instances for each discovered tool</li>
 * <li>Validates tool names to prevent duplicates</li>
 * </ul>
 * <p>
 * Example usage: <pre>{@code
 * McpSyncClient mcpClient = // obtain MCP client
 * ToolCallbackProvider provider = new McpToolCallbackProvider(mcpClient);
 *
 * // Get all available tools
 * ToolCallback[] tools = provider.getToolCallbacks();
 * }</pre>
 *
 * @author Christian Tzolov
 * @since 1.0.0
 * @see ToolCallbackProvider
 * @see McpToolCallback
 * @see McpSyncClient
 */

public class SyncMcpToolCallbackProvider implements ToolCallbackProvider {

	private final McpSyncClient mcpClient;

	/**
	 * Creates a new {@code McpToolCallbackProvider} instance.
	 * @param mcpClient the MCP client to use for discovering tools
	 */
	public SyncMcpToolCallbackProvider(McpSyncClient mcpClient) {
		this.mcpClient = mcpClient;
	}

	/**
	 * Discovers and returns all available tools from the MCP server.
	 * <p>
	 * This method:
	 * <ol>
	 * <li>Retrieves the list of tools from the MCP server</li>
	 * <li>Creates a {@link McpToolCallback} for each tool</li>
	 * <li>Validates that there are no duplicate tool names</li>
	 * </ol>
	 * @return an array of tool callbacks, one for each discovered tool
	 * @throws IllegalStateException if duplicate tool names are found
	 */
	@Override
	public ToolCallback[] getToolCallbacks() {

		var toolCallbacks = this.mcpClient.listTools()
			.tools()
			.stream()
			.map(tool -> new McpToolCallback(this.mcpClient, tool))
			.toArray(ToolCallback[]::new);

		validateToolCallbacks(toolCallbacks);

		return toolCallbacks;

	}

	/**
	 * Validates that there are no duplicate tool names in the provided callbacks.
	 * <p>
	 * This method ensures that each tool has a unique name, which is required for proper
	 * tool resolution and execution.
	 * @param toolCallbacks the tool callbacks to validate
	 * @throws IllegalStateException if duplicate tool names are found
	 */
	private void validateToolCallbacks(ToolCallback[] toolCallbacks) {
		List<String> duplicateToolNames = ToolUtils.getDuplicateToolNames(toolCallbacks);
		if (!duplicateToolNames.isEmpty()) {
			throw new IllegalStateException(
					"Multiple tools with the same name (%s)".formatted(String.join(", ", duplicateToolNames)));
		}
	}

	public static List<ToolCallback> syncToolCallbacks(List<McpSyncClient> mcpClients) {

		if (CollectionUtils.isEmpty(mcpClients)) {
			return List.of();
		}
		return mcpClients.stream()
			.map(mcpClient -> List.of((new SyncMcpToolCallbackProvider(mcpClient).getToolCallbacks())))
			.flatMap(List::stream)
			.toList();
	}

}
