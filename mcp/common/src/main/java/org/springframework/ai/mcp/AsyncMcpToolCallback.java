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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Implementation of {@link ToolCallback} that adapts MCP tools to Spring AI's tool
 * interface with asynchronous execution support.
 * <p>
 * This class acts as a bridge between the Model Context Protocol (MCP) and Spring AI's
 * tool system, allowing MCP tools to be used seamlessly within Spring AI applications.
 * It:
 * <ul>
 * <li>Converts MCP tool definitions to Spring AI tool definitions</li>
 * <li>Handles the asynchronous execution of tool calls through the MCP client</li>
 * <li>Manages JSON serialization/deserialization of tool inputs and outputs</li>
 * </ul>
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * McpAsyncClient mcpClient = // obtain MCP client
 * Tool mcpTool = // obtain MCP tool definition
 * ToolCallback callback = new AsyncMcpToolCallback(mcpClient, mcpTool);
 *
 * // Use the tool through Spring AI's interfaces
 * ToolDefinition definition = callback.getToolDefinition();
 * String result = callback.call("{\"param\": \"value\"}");
 * }</pre>
 *
 * @author Christian Tzolov
 * @author YunKui Lu
 * @see ToolCallback
 * @see McpAsyncClient
 * @see Tool
 */
public class AsyncMcpToolCallback extends AbstractMcpToolCallback {

	private final McpAsyncClient asyncMcpClient;

	/**
	 * Creates a new {@code AsyncMcpToolCallback} instance.
	 * @param mcpClient the MCP client to use for tool execution
	 * @param tool the MCP tool definition to adapt
	 */
	public AsyncMcpToolCallback(McpAsyncClient mcpClient, Tool tool) {
		this(mcpClient, tool, Set.of());
	}

	/**
	 * Creates a new {@code AsyncMcpToolCallback} instance.
	 * @param mcpClient the MCP client to use for tool execution
	 * @param tool the MCP tool definition to adapt
	 * @param excludedToolContextKeys the keys that will not be sent to the MCP Server
	 * inside the `_meta` field of
	 * {@link io.modelcontextprotocol.spec.McpSchema.CallToolRequest}
	 */
	private AsyncMcpToolCallback(McpAsyncClient mcpClient, Tool tool, Set<String> excludedToolContextKeys) {
		super(tool, excludedToolContextKeys);

		Assert.notNull(mcpClient, "mcpClient cannot be null");

		this.asyncMcpClient = mcpClient;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Returns a Spring AI tool definition adapted from the MCP tool.
	 * <p>
	 * The tool definition includes:
	 * <ul>
	 * <li>The tool's name from the MCP definition</li>
	 * <li>The tool's description from the MCP definition</li>
	 * <li>The input schema converted to JSON format</li>
	 * </ul>
	 * @return the Spring AI tool definition
	 */
	@Override
	public ToolDefinition getToolDefinition() {
		return DefaultToolDefinition.builder()
			.name(McpToolUtils.prefixedToolName(this.asyncMcpClient.getClientInfo().name(), this.tool.name()))
			.description(this.tool.description())
			.inputSchema(ModelOptionsUtils.toJsonString(this.tool.inputSchema()))
			.build();
	}

	/**
	 * Executes the tool with the provided input asynchronously.
	 * <p>
	 * This method:
	 * <ol>
	 * <li>Converts the JSON input string to a map of arguments</li>
	 * <li>Calls the tool through the MCP client asynchronously</li>
	 * <li>Converts the tool's response content to a JSON string</li>
	 * </ol>
	 * @param functionInput the tool input as a JSON string
	 * @param toolContext the context for tool execution in a function calling scenario
	 * @return the tool's response as a JSON string
	 */
	@Override
	public String call(String functionInput, @Nullable ToolContext toolContext) {
		Map<String, Object> arguments = ModelOptionsUtils.jsonToMap(functionInput);

		Map<String, Object> meta = new HashMap<>();
		if (toolContext != null && !toolContext.getContext().isEmpty()) {
			meta.put(DEFAULT_MCP_META_TOOL_CONTEXT_KEY, super.getAdditionalToolContextToMeta(toolContext));
		}

		return Objects
			// Note that we use the original tool name here, not the adapted one from
			// getToolDefinition
			.requireNonNull(this.asyncMcpClient.callTool(new CallToolRequest(this.tool.name(), arguments, meta))
				.onErrorMap(exception -> {
					// If the tool throws an error during execution
					throw new ToolExecutionException(this.getToolDefinition(), exception);
				})
				.map(response -> {
					if (response.isError() != null && response.isError()) {
						throw new ToolExecutionException(this.getToolDefinition(),
								new IllegalStateException("Error calling tool: " + response.content()));
					}
					return ModelOptionsUtils.toJsonString(response.content());
				})
				.contextWrite(ctx -> ctx.putAll(ToolCallReactiveContextHolder.getContext()))
				.block());
	}

	public static class Builder {

		private McpAsyncClient asyncMcpClient;

		private Tool tool;

		private Set<String> excludedToolContextKeys = new HashSet<>();

		private Builder() {
		}

		public Builder asyncMcpClient(McpAsyncClient asyncMcpClient) {
			this.asyncMcpClient = asyncMcpClient;
			return this;
		}

		public Builder tool(Tool tool) {
			this.tool = tool;
			return this;
		}

		public Builder addExcludedToolContextKeys(Set<String> excludedToolContextKeys) {
			this.excludedToolContextKeys.addAll(excludedToolContextKeys);
			return this;
		}

		public Builder addExcludedToolContextKey(String excludedToolContextKey) {
			this.excludedToolContextKeys.add(excludedToolContextKey);
			return this;
		}

		public AsyncMcpToolCallback build() {
			return new AsyncMcpToolCallback(this.asyncMcpClient, this.tool, this.excludedToolContextKeys);
		}

	}

}
