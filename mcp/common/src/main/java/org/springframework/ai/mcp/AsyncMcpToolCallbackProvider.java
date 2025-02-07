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

import io.modelcontextprotocol.client.McpAsyncClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.util.ToolUtils;
import org.springframework.util.CollectionUtils;

/**
 * Implementation of {@link ToolCallbackProvider} that discovers and provides MCP tools
 * asynchronously.
 * <p>
 * This class acts as a tool provider for Spring AI, automatically discovering tools from
 * an MCP server and making them available as Spring AI tools. It:
 * <ul>
 * <li>Connects to an MCP server through an async client</li>
 * <li>Lists and retrieves available tools from the server</li>
 * <li>Creates {@link AsyncMcpToolCallback} instances for each discovered tool</li>
 * <li>Validates tool names to prevent duplicates</li>
 * </ul>
 * <p>
 * Example usage: <pre>{@code
 * McpAsyncClient mcpClient = // obtain MCP client
 * ToolCallbackProvider provider = new AsyncMcpToolCallbackProvider(mcpClient);
 *
 * // Get all available tools
 * ToolCallback[] tools = provider.getToolCallbacks();
 * }</pre>
 *
 * @author Christian Tzolov
 * @since 1.0.0
 * @see ToolCallbackProvider
 * @see AsyncMcpToolCallback
 * @see McpAsyncClient
 */
public class AsyncMcpToolCallbackProvider implements ToolCallbackProvider {

	private final McpAsyncClient mcpClient;

	/**
	 * Creates a new {@code AsyncMcpToolCallbackProvider} instance.
	 * @param mcpClient the MCP client to use for discovering tools
	 */
	public AsyncMcpToolCallbackProvider(McpAsyncClient mcpClient) {
		this.mcpClient = mcpClient;
	}

	/**
	 * Discovers and returns all available tools from the MCP server asynchronously.
	 * <p>
	 * This method:
	 * <ol>
	 * <li>Retrieves the list of tools from the MCP server</li>
	 * <li>Creates a {@link AsyncMcpToolCallback} for each tool</li>
	 * <li>Validates that there are no duplicate tool names</li>
	 * </ol>
	 * @return an array of tool callbacks, one for each discovered tool
	 * @throws IllegalStateException if duplicate tool names are found
	 */
	@Override
	public ToolCallback[] getToolCallbacks() {
		var toolCallbacks = this.mcpClient.listTools()
			.map(response -> response.tools()
				.stream()
				.map(tool -> new AsyncMcpToolCallback(this.mcpClient, tool))
				.toArray(ToolCallback[]::new))
			.block();

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

	/**
	 * Creates a reactive stream of tool callbacks from multiple MCP clients.
	 * <p>
	 * This utility method:
	 * <ol>
	 * <li>Takes a list of MCP clients</li>
	 * <li>Creates a provider for each client</li>
	 * <li>Retrieves and flattens all tool callbacks into a single stream</li>
	 * </ol>
	 * @param mcpClients the list of MCP clients to create callbacks from
	 * @return a Flux of tool callbacks from all provided clients
	 */
	public static Flux<ToolCallback> asyncToolCallbacks(List<McpAsyncClient> mcpClients) {
		if (CollectionUtils.isEmpty(mcpClients)) {
			return Flux.empty();
		}

		return Flux.fromIterable(mcpClients)
			.flatMap(mcpClient -> Mono.just(new AsyncMcpToolCallbackProvider(mcpClient).getToolCallbacks()))
			.flatMap(callbacks -> Flux.fromArray(callbacks));
	}

}
