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

import java.util.LinkedHashMap;
import java.util.Map;

import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.client.McpClientException;
import io.modelcontextprotocol.modern.client.McpSyncClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.util.JsonHelper;
import org.springframework.ai.util.json.schema.JsonSchemaUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

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
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see SyncModernMcpToolCallbackProvider
 */
public final class SyncModernMcpToolCallback implements ToolCallback {

	private static final Log logger = LogFactory.getLog(SyncModernMcpToolCallback.class);

	private static final JsonHelper jsonHelper = new JsonHelper();

	/**
	 * Prefix of the {@code _meta} keys the modern client sets itself and rejects when the
	 * application sets them.
	 */
	private static final String RESERVED_META_PREFIX = "io.modelcontextprotocol/";

	private final McpSyncClient.ToolSpec toolSpec;

	private final Tool tool;

	private final String prefixedToolName;

	private final ToolContextToMcpMetaConverter toolContextToMcpMetaConverter;

	private SyncModernMcpToolCallback(McpSyncClient mcpClient, Tool tool, String prefixedToolName,
			ToolContextToMcpMetaConverter toolContextToMcpMetaConverter) {
		Assert.notNull(mcpClient, "mcpClient must not be null");
		Assert.notNull(tool, "tool must not be null");
		Assert.hasText(prefixedToolName, "prefixedToolName must not be empty");
		Assert.notNull(toolContextToMcpMetaConverter, "toolContextToMcpMetaConverter must not be null");

		// Request specs are immutable: the prepared spec is shared by every call, and
		// each call derives its own spec with the arguments and the _meta entries.
		this.toolSpec = mcpClient.tool(tool);
		this.tool = tool;
		this.prefixedToolName = prefixedToolName;
		this.toolContextToMcpMetaConverter = toolContextToMcpMetaConverter;
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
		return DefaultToolDefinition.builder()
			.name(this.prefixedToolName)
			.description(this.tool.description())
			.inputSchema(JsonSchemaUtils.ensureValidInputSchema(jsonHelper.toJson(this.tool.inputSchema())))
			.build();
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

		// A streaming model may send no arguments at all.
		Map<String, Object> arguments = jsonHelper
			.fromJsonToMap(StringUtils.hasText(toolCallInput) ? toolCallInput : "{}");

		CallToolResult result;
		try {
			result = this.toolSpec.arguments(arguments).meta(this.requestMeta(toolContext)).call();
		}
		catch (McpClientException | McpException ex) {
			// The call itself failed: the server answered with a protocol error, or the
			// client could not complete the exchange. Such failures are not conveyed to
			// the model; they fail the model interaction, as the legacy callbacks do.
			logger.error("MCP failure while calling tool: " + this.tool.name(), ex);
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

		return toModelResponse(result);
	}

	/**
	 * The {@code _meta} entries of the request: the {@link ToolContext} entries without
	 * the keys the modern client sets itself.
	 */
	private Map<String, Object> requestMeta(@Nullable ToolContext toolContext) {
		if (toolContext == null) {
			return Map.of();
		}
		Map<String, Object> meta = new LinkedHashMap<>();
		this.toolContextToMcpMetaConverter.convert(toolContext).forEach((key, value) -> {
			if (!MetaKeys.PROGRESS_TOKEN.equals(key) && !key.startsWith(RESERVED_META_PREFIX)) {
				meta.put(key, value);
			}
		});
		return meta;
	}

	/**
	 * The text returned to the model. A result without structured content is returned as
	 * the JSON of its content blocks, like the legacy callbacks do. A result with
	 * structured content is returned as a JSON object that holds both.
	 */
	private static String toModelResponse(CallToolResult result) {
		if (result.structuredContent() == null) {
			return jsonHelper.toJson(result.content());
		}
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("content", result.content());
		response.put("structuredContent", result.structuredContent());
		return jsonHelper.toJson(response);
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
		 * Builds the callback.
		 * @return the callback
		 */
		public SyncModernMcpToolCallback build() {
			Assert.notNull(this.mcpClient, "mcpClient must not be null");
			Assert.notNull(this.tool, "tool must not be null");
			String name = this.prefixedToolName != null ? this.prefixedToolName : this.tool.name();
			return new SyncModernMcpToolCallback(this.mcpClient, this.tool, name, this.toolContextToMcpMetaConverter);
		}

	}

}
