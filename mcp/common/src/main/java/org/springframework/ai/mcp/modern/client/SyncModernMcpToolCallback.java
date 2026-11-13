/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.mcp.modern.client;

import java.util.Map;

import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.client.McpSyncClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.util.Assert;

/**
 * Adapts a tool of a modern MCP server to Spring AI's {@link ToolCallback}.
 * <p>
 * The callback is bound to the {@link McpSyncClient} it was created with and to the
 * {@link Tool} definition that client listed. It never selects a client itself, so it
 * calls the tool with the same client, and on behalf of the same caller, that listed it.
 * Passing the listed definition also lets the client add the {@code Mcp-Param-*} headers
 * without looking the tool up again.
 * <p>
 * A callback is only as long-lived as its client. Register the
 * {@link SyncModernMcpToolCallbackProvider} with the {@code ChatClient}, not the
 * callbacks it returns, so that each request gets callbacks for its own client.
 * <p>
 * A call fails in one of two ways, as a call of a legacy tool does. A protocol error that
 * the server answers with ({@code McpException}) is thrown as it is and fails the model
 * interaction. Every other failure is thrown as a {@link ToolExecutionException}, which
 * the application's tool error handling decides about: a tool result that is marked as an
 * error, and a failure in the client or on the way to the server, such as a timeout
 * ({@code McpTimeoutException}), a lost response or an HTTP error status. After a timeout
 * or a lost response it is unknown whether the server ran the tool.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see SyncModernMcpToolCallbackProvider
 */
public final class SyncModernMcpToolCallback implements ToolCallback {

	private static final Log logger = LogFactory.getLog(SyncModernMcpToolCallback.class);

	private final McpSyncClient.ToolSpec toolSpec;

	private final Tool tool;

	/** Built once: it is asked for several times for each request. */
	private final ToolDefinition toolDefinition;

	private final ToolContextToMcpMetaConverter toolContextToMcpMetaConverter;

	private final @Nullable McpModernConnectionInfo connectionInfo;

	private final @Nullable SyncModernMcpToolCallCustomizer toolCallCustomizer;

	private SyncModernMcpToolCallback(McpSyncClient mcpClient, Tool tool, String prefixedToolName,
			ToolContextToMcpMetaConverter toolContextToMcpMetaConverter,
			@Nullable McpModernConnectionInfo connectionInfo,
			@Nullable SyncModernMcpToolCallCustomizer toolCallCustomizer) {
		Assert.notNull(mcpClient, "mcpClient must not be null");
		Assert.notNull(tool, "tool must not be null");
		Assert.hasText(prefixedToolName, "prefixedToolName must not be empty");
		Assert.notNull(toolContextToMcpMetaConverter, "toolContextToMcpMetaConverter must not be null");
		Assert.isTrue(toolCallCustomizer == null || connectionInfo != null,
				"connectionInfo must not be null when a toolCallCustomizer is set");

		// Request specs are immutable: the prepared spec is shared by every call, and
		// each call derives its own spec with the arguments and the _meta entries.
		this.toolSpec = mcpClient.tool(tool);
		this.tool = tool;
		this.toolDefinition = ModernToolCalls.toolDefinition(prefixedToolName, tool);
		this.toolContextToMcpMetaConverter = toolContextToMcpMetaConverter;
		this.connectionInfo = connectionInfo;
		this.toolCallCustomizer = toolCallCustomizer;
	}

	/**
	 * Creates a builder for a {@link SyncModernMcpToolCallback}.
	 * @return the builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return this.toolDefinition;
	}

	/**
	 * Returns the name of the tool on the MCP server, without the prefix.
	 * @return the original tool name
	 */
	public String getOriginalToolName() {
		return this.tool.name();
	}

	@Override
	public String call(String toolCallInput) {
		return this.call(toolCallInput, null);
	}

	@Override
	public String call(String toolCallInput, @Nullable ToolContext toolContext) {

		Map<String, Object> arguments = ModernToolCalls.arguments(toolCallInput);

		CallToolResult result;
		try {
			McpSyncClient.ToolSpec callSpec = this.toolSpec.arguments(arguments)
				.meta(ModernToolCalls.requestMeta(this.toolContextToMcpMetaConverter, toolContext));
			if (this.toolCallCustomizer != null && this.connectionInfo != null) {
				callSpec = this.toolCallCustomizer.customize(this.connectionInfo, callSpec, toolContext);
				Assert.state(callSpec != null, "toolCallCustomizer must not return null");
			}
			result = callSpec.call();
		}
		catch (McpException ex) {
			// A protocol-level error of the server indicates the tool invocation itself
			// failed and must not be conveyed to the model. Rethrow it as a hard failure,
			// as the legacy callbacks do with their protocol errors. Since the tool
			// calling manager only handles ToolExecutionException, this bubbles up and
			// fails the model interaction.
			logger.error("Protocol error while calling tool: " + this.tool.name(), ex);
			throw ex;
		}
		catch (Exception ex) {
			logger.error("Exception while calling tool: " + this.tool.name(), ex);
			throw new ToolExecutionException(this.getToolDefinition(), ex);
		}

		if (Boolean.TRUE.equals(result.isError())) {
			throw new ToolExecutionException(this.getToolDefinition(),
					new IllegalStateException("Error calling tool: " + result.content()));
		}

		return ModernToolCalls.toModelResponse(result);
	}

	/**
	 * Builder for {@link SyncModernMcpToolCallback}.
	 */
	public static final class Builder {

		private @Nullable McpSyncClient mcpClient;

		private @Nullable Tool tool;

		private @Nullable String prefixedToolName;

		private ToolContextToMcpMetaConverter toolContextToMcpMetaConverter = ToolContextToMcpMetaConverter
			.defaultConverter();

		private @Nullable McpModernConnectionInfo connectionInfo;

		private @Nullable SyncModernMcpToolCallCustomizer toolCallCustomizer;

		private Builder() {
		}

		/**
		 * Sets the client that calls the tool.
		 * @param mcpClient the client the tool was listed with
		 * @return this builder
		 */
		public Builder mcpClient(McpSyncClient mcpClient) {
			this.mcpClient = mcpClient;
			return this;
		}

		/**
		 * Sets the tool to adapt.
		 * @param tool the tool, as listed by the server
		 * @return this builder
		 */
		public Builder tool(Tool tool) {
			this.tool = tool;
			return this;
		}

		/**
		 * Sets the name under which the model sees the tool. Defaults to the tool's own
		 * name.
		 * @param prefixedToolName the tool name presented to the model
		 * @return this builder
		 */
		public Builder prefixedToolName(String prefixedToolName) {
			this.prefixedToolName = prefixedToolName;
			return this;
		}

		/**
		 * Sets the converter that turns {@link ToolContext} entries into the request's
		 * {@code _meta} entries.
		 * @param toolContextToMcpMetaConverter the converter
		 * @return this builder
		 */
		public Builder toolContextToMcpMetaConverter(ToolContextToMcpMetaConverter toolContextToMcpMetaConverter) {
			Assert.notNull(toolContextToMcpMetaConverter, "toolContextToMcpMetaConverter must not be null");
			this.toolContextToMcpMetaConverter = toolContextToMcpMetaConverter;
			return this;
		}

		/**
		 * Sets the connection the tool belongs to. Required when a
		 * {@link #toolCallCustomizer} is set, which receives it.
		 * @param connectionInfo the connection of the tool
		 * @return this builder
		 */
		public Builder connectionInfo(McpModernConnectionInfo connectionInfo) {
			Assert.notNull(connectionInfo, "connectionInfo must not be null");
			this.connectionInfo = connectionInfo;
			return this;
		}

		/**
		 * Sets the customizer that is applied to the request spec of every call, with the
		 * {@link ToolContext} of that call.
		 * @param toolCallCustomizer the customizer
		 * @return this builder
		 */
		public Builder toolCallCustomizer(SyncModernMcpToolCallCustomizer toolCallCustomizer) {
			Assert.notNull(toolCallCustomizer, "toolCallCustomizer must not be null");
			this.toolCallCustomizer = toolCallCustomizer;
			return this;
		}

		/**
		 * Builds the callback.
		 * @return the callback
		 */
		public SyncModernMcpToolCallback build() {
			Assert.notNull(this.mcpClient, "mcpClient must not be null");
			Assert.notNull(this.tool, "tool must not be null");
			String name = this.prefixedToolName != null ? this.prefixedToolName : this.tool.name();
			return new SyncModernMcpToolCallback(this.mcpClient, this.tool, name, this.toolContextToMcpMetaConverter,
					this.connectionInfo, this.toolCallCustomizer);
		}

	}

}
