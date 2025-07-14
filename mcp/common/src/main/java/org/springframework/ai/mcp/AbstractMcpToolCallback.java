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
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import static io.modelcontextprotocol.spec.McpSchema.Tool;

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
 *
 * @author YunKui Lu
 * @see ToolCallback
 * @see AsyncMcpToolCallback
 * @see SyncMcpToolCallback
 * @see Tool
 */
public abstract class AbstractMcpToolCallback implements ToolCallback {

	public static final String DEFAULT_MCP_META_TOOL_CONTEXT_KEY = McpToolUtils.DEFAULT_MCP_META_TOOL_CONTEXT_KEY;

	protected final Tool tool;

	/**
	 * the keys that will not be sent to the MCP Server inside the `_meta` field of
	 * {@link io.modelcontextprotocol.spec.McpSchema.CallToolRequest}
	 */
	protected final Set<String> excludedToolContextKeys;

	/**
	 * Creates a new {@code AbstractMcpToolCallback} instance.
	 * @param tool the MCP tool definition to adapt
	 * @param excludedToolContextKeys the keys that will not be sent to the MCP Server
	 * inside the `_meta` field of
	 * {@link io.modelcontextprotocol.spec.McpSchema.CallToolRequest}
	 */
	protected AbstractMcpToolCallback(Tool tool, Set<String> excludedToolContextKeys) {
		Assert.notNull(tool, "tool cannot be null");
		Assert.notNull(excludedToolContextKeys, "excludedToolContextKeys cannot be null");

		this.tool = tool;
		this.excludedToolContextKeys = excludedToolContextKeys;
	}

	/**
	 * Executes the tool with the provided input.
	 * <p>
	 * This method:
	 * <ol>
	 * <li>Converts the JSON input string to a map of arguments</li>
	 * <li>Calls the tool through the MCP client</li>
	 * <li>Converts the tool's response content to a JSON string</li>
	 * </ol>
	 * @param functionInput the tool input as a JSON string
	 * @return the tool's response as a JSON string
	 */
	@Override
	public String call(String functionInput) {
		return this.call(functionInput, null);
	}

	/**
	 * Converts the tool context to a mcp meta map
	 * @param toolContext the context for tool execution in a function calling scenario
	 * @return the mcp meta map
	 */
	protected Map<String, Object> getAdditionalToolContextToMeta(@Nullable ToolContext toolContext) {
		if (toolContext == null || toolContext.getContext().isEmpty()) {
			return Map.of();
		}

		Map<String, Object> meta = new HashMap<>(toolContext.getContext().size() - excludedToolContextKeys.size());
		for (var toolContextEntry : toolContext.getContext().entrySet()) {
			if (excludedToolContextKeys.contains(toolContextEntry.getKey())) {
				continue;
			}
			meta.put(toolContextEntry.getKey(), toolContextEntry.getValue());
		}
		return meta;
	}

}
