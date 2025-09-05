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

import java.util.Map;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

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
 * @see ToolCallback
 * @see McpAsyncClient
 * @see Tool
 */
public class AsyncMcpToolCallback implements ToolCallback {

	private static final Logger logger = LoggerFactory.getLogger(AsyncMcpToolCallback.class);

	private final McpAsyncClient asyncMcpClient;

	private final Tool tool;

	private final String prefixedToolName;

	/**
	 * Creates a new {@code AsyncMcpToolCallback} instance.
	 * @param mcpClient the MCP client to use for tool execution
	 * @param tool the MCP tool definition to adapt
	 * @deprecated use {@link #AsyncMcpToolCallback(McpAsyncClient, Tool, String)}
	 */
	@Deprecated
	public AsyncMcpToolCallback(McpAsyncClient mcpClient, Tool tool) {
		this(mcpClient, tool, McpToolUtils.prefixedToolName(mcpClient.getClientInfo().name(), tool.name()));
	}

	/**
	 * Creates a new {@code AsyncMcpToolCallback} instance.
	 * @param mcpClient the MCP client to use for tool execution
	 * @param tool the MCP tool definition to adapt
	 * @param prefixedToolName the prefixed tool name to use in the tool definition.
	 */
	public AsyncMcpToolCallback(McpAsyncClient mcpClient, Tool tool, String prefixedToolName) {
		Assert.notNull(mcpClient, "MCP client must not be null");
		Assert.notNull(tool, "MCP tool must not be null");
		Assert.hasText(prefixedToolName, "Prefixed tool name must not be empty");

		this.asyncMcpClient = mcpClient;
		this.tool = tool;
		this.prefixedToolName = prefixedToolName;
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
			.name(this.prefixedToolName)
			.description(this.tool.description())
			.inputSchema(ModelOptionsUtils.toJsonString(this.tool.inputSchema()))
			.build();
	}

	public String getOriginalToolName() {
		return this.tool.name();
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
	 * @param toolCallInput the tool input as a JSON string
	 * @return the tool's response as a JSON string
	 */
	@Override
	public String call(String toolCallInput) {
		// Handle the possible null parameter situation in streaming mode.
		if (!StringUtils.hasText(toolCallInput)) {
			logger.warn("Tool call arguments are null or empty for MCP tool: {}. Using empty JSON object as default.",
					this.tool.name());
			toolCallInput = "{}";
		}

		Map<String, Object> arguments = ModelOptionsUtils.jsonToMap(toolCallInput);
		// Note that we use the original tool name here, not the adapted one from
		// getToolDefinition
		return this.asyncMcpClient.callTool(new CallToolRequest(this.tool.name(), arguments)).onErrorMap(exception -> {
			// If the tool throws an error during execution
			throw new ToolExecutionException(this.getToolDefinition(), exception);
		}).map(response -> {
			if (response.isError() != null && response.isError()) {
				throw new ToolExecutionException(this.getToolDefinition(),
						new IllegalStateException("Error calling tool: " + response.content()));
			}
			return ModelOptionsUtils.toJsonString(response.content());
		}).contextWrite(ctx -> ctx.putAll(ToolCallReactiveContextHolder.getContext())).block();
	}

	@Override
	public String call(String toolArguments, ToolContext toolContext) {
		// ToolContext is not supported by the MCP tools
		return this.call(toolArguments);
	}

}
