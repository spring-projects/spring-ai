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
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Adapts MCP tools to Spring AI's {@link ToolCallback} interface with asynchronous
 * execution.
 * <p>
 * Bridges Model Context Protocol (MCP) tools with Spring AI's tool system, enabling
 * seamless integration of MCP tools in Spring AI applications.
 *
 * @author Christian Tzolov
 * @author YunKui Lu
 * @author Ilayaperumal Gopinathan
 */
public class AsyncMcpToolCallback implements ToolCallback {

	private static final Logger logger = LoggerFactory.getLogger(AsyncMcpToolCallback.class);

	private final McpAsyncClient mcpClient;

	private final Tool tool;

	private final String prefixedToolName;

	private final ToolContextToMcpMetaConverter toolContextToMcpMetaConverter;

	/**
	 * Creates an AsyncMcpToolCallback with default prefixed tool name.
	 * @param mcpClient the MCP client for tool execution
	 * @param tool the MCP tool to adapt
	 * @deprecated use {@link Builder} instead
	 */
	@Deprecated
	public AsyncMcpToolCallback(McpAsyncClient mcpClient, Tool tool) {
		this(mcpClient, tool, McpToolUtils.prefixedToolName(mcpClient.getClientInfo().name(),
				mcpClient.getClientInfo().title(), tool.name()), ToolContextToMcpMetaConverter.defaultConverter());
	}

	/**
	 * Creates an AsyncMcpToolCallback with specified parameters.
	 * @param mcpClient the MCP client for tool execution
	 * @param tool the MCP tool to adapt
	 * @param prefixedToolName the prefixed tool name for the tool definition
	 * @param toolContextToMcpMetaConverter converter for tool context to MCP metadata
	 */
	private AsyncMcpToolCallback(McpAsyncClient mcpClient, Tool tool, String prefixedToolName,
			ToolContextToMcpMetaConverter toolContextToMcpMetaConverter) {
		Assert.notNull(mcpClient, "MCP client must not be null");
		Assert.notNull(tool, "MCP tool must not be null");
		Assert.hasText(prefixedToolName, "Prefixed tool name must not be empty");
		Assert.notNull(toolContextToMcpMetaConverter, "ToolContextToMcpMetaConverter must not be null");

		this.mcpClient = mcpClient;
		this.tool = tool;
		this.prefixedToolName = prefixedToolName;
		this.toolContextToMcpMetaConverter = toolContextToMcpMetaConverter;
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return McpToolUtils.createToolDefinition(this.prefixedToolName, this.tool);
	}

	public String getOriginalToolName() {
		return this.tool.name();
	}

	@Override
	public String call(String toolCallInput) {
		return this.call(toolCallInput, null);
	}

	@Override
	public String call(String toolCallInput, @Nullable ToolContext toolContext) {

		// Handle the possible null parameter situation in streaming mode.
		if (!StringUtils.hasText(toolCallInput)) {
			logger.warn("Tool call arguments are null or empty for MCP tool: {}. Using empty JSON object as default.",
					this.tool.name());
			toolCallInput = "{}";
		}

		Map<String, Object> arguments = ModelOptionsUtils.jsonToMap(toolCallInput);

		CallToolResult response;
		try {
			var mcpMeta = toolContext != null ? this.toolContextToMcpMetaConverter.convert(toolContext) : null;

			var request = CallToolRequest.builder()
				// Use the original tool name, not the prefixed one from getToolDefinition
				.name(this.tool.name())
				.arguments(arguments)
				.meta(mcpMeta)
				.build();

			response = this.mcpClient.callTool(request).onErrorMap(exception -> {
				logger.error("Exception while tool calling: ", exception);
				return new ToolExecutionException(this.getToolDefinition(), exception);
			}).contextWrite(ctx -> ctx.putAll(ToolCallReactiveContextHolder.getContext())).block();
		}
		catch (Exception ex) {
			logger.error("Exception while tool calling: ", ex);
			throw new ToolExecutionException(this.getToolDefinition(), ex);
		}
		Assert.notNull(response, "response was null");

		if (response.isError() != null && response.isError()) {
			logger.error("Error calling tool: {}", response.content());
			throw new ToolExecutionException(this.getToolDefinition(),
					new IllegalStateException("Error calling tool: " + response.content()));
		}
		return ModelOptionsUtils.toJsonString(response.content());
	}

	/**
	 * Creates a builder for constructing AsyncMcpToolCallback instances.
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for constructing AsyncMcpToolCallback instances.
	 */
	public static final class Builder {

		private @Nullable McpAsyncClient mcpClient;

		private @Nullable Tool tool;

		private @Nullable String prefixedToolName;

		private ToolContextToMcpMetaConverter toolContextToMcpMetaConverter = ToolContextToMcpMetaConverter
			.defaultConverter();

		/**
		 * Sets the MCP client for tool execution.
		 * @param mcpClient the MCP client (required)
		 * @return this builder
		 */
		public Builder mcpClient(McpAsyncClient mcpClient) {
			this.mcpClient = mcpClient;
			return this;
		}

		/**
		 * Sets the MCP tool to adapt.
		 * @param tool the MCP tool (required)
		 * @return this builder
		 */
		public Builder tool(Tool tool) {
			this.tool = tool;
			return this;
		}

		/**
		 * Sets the prefixed tool name for the tool definition.
		 * <p>
		 * Defaults to a generated name using the client and tool names.
		 * @param prefixedToolName the prefixed tool name
		 * @return this builder
		 */
		public Builder prefixedToolName(String prefixedToolName) {
			this.prefixedToolName = prefixedToolName;
			return this;
		}

		/**
		 * Sets the converter for tool context to MCP metadata transformation.
		 * <p>
		 * Defaults to {@link ToolContextToMcpMetaConverter#defaultConverter()}.
		 * @param toolContextToMcpMetaConverter the converter
		 * @return this builder
		 */
		public Builder toolContextToMcpMetaConverter(ToolContextToMcpMetaConverter toolContextToMcpMetaConverter) {
			Assert.notNull(toolContextToMcpMetaConverter, "ToolContextToMcpMetaConverter must not be null");
			this.toolContextToMcpMetaConverter = toolContextToMcpMetaConverter;
			return this;
		}

		/**
		 * Builds an AsyncMcpToolCallback with the configured parameters.
		 * @return a new AsyncMcpToolCallback
		 * @throws IllegalArgumentException if required parameters are missing
		 */
		public AsyncMcpToolCallback build() {
			Assert.notNull(this.mcpClient, "MCP client must not be null");
			Assert.notNull(this.tool, "MCP tool must not be null");

			// Apply defaults if not specified
			if (this.prefixedToolName == null) {
				this.prefixedToolName = McpToolUtils.format(this.tool.name());
			}

			return new AsyncMcpToolCallback(this.mcpClient, this.tool, this.prefixedToolName,
					this.toolContextToMcpMetaConverter);
		}

	}

}
