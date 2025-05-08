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
import java.util.function.BiPredicate;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Implementation of {@link ToolCallbackProvider} that discovers and provides MCP tools
 * from one or more MCP servers.
 * <p>
 * This class acts as a tool provider for Spring AI, automatically discovering tools from
 * multiple MCP servers and making them available as Spring AI tools. It:
 * <ul>
 * <li>Connects to one or more MCP servers through sync clients</li>
 * <li>Lists and retrieves available tools from all connected servers</li>
 * <li>Creates {@link SyncMcpToolCallback} instances for each discovered tool</li>
 * <li>Validates tool names to prevent duplicates across all servers</li>
 * </ul>
 * <p>
 * Example usage with a single client:
 *
 * <pre>{@code
 * McpSyncClient mcpClient = // obtain MCP client
 * ToolCallbackProvider provider = new SyncMcpToolCallbackProvider(mcpClient);
 *
 * // Get all available tools
 * ToolCallback[] tools = provider.getToolCallbacks();
 * }</pre>
 * <p>
 * Example usage with multiple clients:
 *
 * <pre>{@code
 * List<McpSyncClient> mcpClients = // obtain multiple MCP clients
 * ToolCallbackProvider provider = new SyncMcpToolCallbackProvider(mcpClients);
 *
 * // Get tools from all clients
 * ToolCallback[] tools = provider.getToolCallbacks();
 * }</pre>
 *
 * @author Christian Tzolov
 * @see ToolCallbackProvider
 * @see SyncMcpToolCallback
 * @see McpSyncClient
 * @since 1.0.0
 */

public class SyncMcpToolCallbackProvider implements ToolCallbackProvider {

	private final List<McpSyncClient> mcpClients;

	private final BiPredicate<McpSyncClient, Tool> toolFilter;

	/**
	 * Creates a new {@code SyncMcpToolCallbackProvider} instance with a list of MCP
	 * clients.
	 * @param mcpClients the list of MCP clients to use for discovering tools
	 * @param toolFilter a filter to apply to each discovered tool
	 */
	public SyncMcpToolCallbackProvider(BiPredicate<McpSyncClient, Tool> toolFilter, List<McpSyncClient> mcpClients) {
		Assert.notNull(mcpClients, "MCP clients must not be null");
		Assert.notNull(toolFilter, "Tool filter must not be null");
		this.mcpClients = mcpClients;
		this.toolFilter = toolFilter;
	}

	/**
	 * Creates a new {@code SyncMcpToolCallbackProvider} instance with a list of MCP
	 * clients.
	 * @param mcpClients the list of MCP clients to use for discovering tools
	 */
	public SyncMcpToolCallbackProvider(List<McpSyncClient> mcpClients) {
		this((mcpClient, tool) -> true, mcpClients);
	}

	/**
	 * Creates a new {@code SyncMcpToolCallbackProvider} instance with one or more MCP
	 * clients.
	 * @param mcpClients the MCP clients to use for discovering tools
	 * @param toolFilter a filter to apply to each discovered tool
	 */
	public SyncMcpToolCallbackProvider(BiPredicate<McpSyncClient, Tool> toolFilter, McpSyncClient... mcpClients) {
		this(toolFilter, List.of(mcpClients));
	}

	/**
	 * Creates a new {@code SyncMcpToolCallbackProvider} instance with one or more MCP
	 * clients.
	 * @param mcpClients the MCP clients to use for discovering tools
	 */
	public SyncMcpToolCallbackProvider(McpSyncClient... mcpClients) {
		this(List.of(mcpClients));
	}

	/**
	 * Discovers and returns all available tools from all connected MCP servers.
	 * <p>
	 * This method:
	 * <ol>
	 * <li>Retrieves the list of tools from each connected MCP server</li>
	 * <li>Creates a {@link SyncMcpToolCallback} for each discovered tool</li>
	 * <li>Validates that there are no duplicate tool names across all servers</li>
	 * </ol>
	 * @return an array of tool callbacks, one for each discovered tool
	 * @throws IllegalStateException if duplicate tool names are found
	 */
	@Override
	public ToolCallback[] getToolCallbacks() {
		var array = this.mcpClients.stream()
			.flatMap(mcpClient -> mcpClient.listTools()
				.tools()
				.stream()
				.filter(tool -> this.toolFilter.test(mcpClient, tool))
				.map(tool -> new SyncMcpToolCallback(mcpClient, tool)))
			.toArray(ToolCallback[]::new);
		validateToolCallbacks(array);
		return array;
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

	/**
	 * Creates a consolidated list of tool callbacks from multiple MCP clients.
	 * <p>
	 * This utility method provides a convenient way to create tool callbacks from
	 * multiple MCP clients in a single operation. It:
	 * <ol>
	 * <li>Takes a list of MCP clients as input</li>
	 * <li>Creates a provider instance to manage all clients</li>
	 * <li>Retrieves tools from all clients and combines them into a single list</li>
	 * <li>Ensures there are no naming conflicts between tools from different clients</li>
	 * </ol>
	 * @param mcpClients the list of MCP clients to create callbacks from
	 * @return a list of tool callbacks from all provided clients
	 */
	public static List<ToolCallback> syncToolCallbacks(List<McpSyncClient> mcpClients) {

		if (CollectionUtils.isEmpty(mcpClients)) {
			return List.of();
		}
		return List.of((new SyncMcpToolCallbackProvider(mcpClients).getToolCallbacks()));
	}

}
