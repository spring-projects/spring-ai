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

import java.util.ArrayList;
import java.util.List;

import io.modelcontextprotocol.client.McpSyncClient;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Provides Spring AI tool callbacks by discovering tools from MCP servers.
 * <p>
 * Automatically discovers and exposes tools from multiple MCP servers as Spring AI
 * {@link ToolCallback} instances.
 *
 * @author Christian Tzolov
 * @author YunKui Lu
 * @since 1.0.0
 */
public class SyncMcpToolCallbackProvider implements ToolCallbackProvider {

	private final List<McpSyncClient> mcpClients;

	private final McpToolFilter toolFilter;

	private McpToolNamePrefixGenerator toolNamePrefixGenerator;

	private final ToolContextToMcpMetaConverter toolContextToMcpMetaConverter;

	/**
	 * Creates a provider with MCP clients and tool filter.
	 * @param mcpClients MCP clients for tool discovery
	 * @param toolFilter filter for discovered tools
	 * @deprecated use {@link #builder()} instead
	 */
	@Deprecated
	public SyncMcpToolCallbackProvider(McpToolFilter toolFilter, List<McpSyncClient> mcpClients) {
		this(toolFilter, McpToolNamePrefixGenerator.noPrefix(), mcpClients,
				ToolContextToMcpMetaConverter.defaultConverter());
	}

	/**
	 * Creates a provider with all configuration options.
	 * @param mcpClients MCP clients for tool discovery
	 * @param toolNamePrefixGenerator generates prefixes for tool names
	 * @param toolFilter filter for discovered tools
	 * @param toolContextToMcpMetaConverter converts tool context to MCP metadata
	 */
	private SyncMcpToolCallbackProvider(McpToolFilter toolFilter, McpToolNamePrefixGenerator toolNamePrefixGenerator,
			List<McpSyncClient> mcpClients, ToolContextToMcpMetaConverter toolContextToMcpMetaConverter) {
		Assert.notNull(mcpClients, "MCP clients must not be null");
		Assert.notNull(toolFilter, "Tool filter must not be null");
		Assert.notNull(toolNamePrefixGenerator, "Tool name prefix generator must not be null");
		Assert.notNull(toolContextToMcpMetaConverter, "Tool context to MCP meta converter must not be null");
		this.mcpClients = mcpClients;
		this.toolFilter = toolFilter;
		this.toolNamePrefixGenerator = toolNamePrefixGenerator;
		this.toolContextToMcpMetaConverter = toolContextToMcpMetaConverter;
	}

	/**
	 * Creates a provider with MCP clients using default filter.
	 * @param mcpClients MCP clients for tool discovery
	 * @deprecated use {@link #builder()} instead
	 */
	@Deprecated
	public SyncMcpToolCallbackProvider(List<McpSyncClient> mcpClients) {
		this((mcpClient, tool) -> true, mcpClients);
	}

	/**
	 * Creates a provider with MCP clients, filter, and prefix generator.
	 * @param mcpClients MCP clients for tool discovery
	 * @param toolNamePrefixGenerator generates prefixes for tool names
	 * @param toolFilter filter for discovered tools
	 * @deprecated use {@link #builder()} instead
	 */
	@Deprecated
	public SyncMcpToolCallbackProvider(McpToolFilter toolFilter, McpToolNamePrefixGenerator toolNamePrefixGenerator,
			McpSyncClient... mcpClients) {
		this(toolFilter, toolNamePrefixGenerator, List.of(mcpClients),
				ToolContextToMcpMetaConverter.defaultConverter());
	}

	/**
	 * Creates a provider with MCP clients using default filter.
	 * @param mcpClients MCP clients for tool discovery
	 * @deprecated use {@link #builder()} instead
	 */
	@Deprecated
	public SyncMcpToolCallbackProvider(McpSyncClient... mcpClients) {
		this(List.of(mcpClients));
	}

	@Override
	public ToolCallback[] getToolCallbacks() {

		var array = this.mcpClients.stream()
			.flatMap(mcpClient -> mcpClient.listTools()
				.tools()
				.stream()
				.filter(tool -> this.toolFilter.test(connectionInfo(mcpClient), tool))
				.map(tool -> SyncMcpToolCallback.builder()
					.mcpClient(mcpClient)
					.tool(tool)
					.prefixedToolName(this.toolNamePrefixGenerator.prefixedToolName(connectionInfo(mcpClient), tool))
					.toolContextToMcpMetaConverter(this.toolContextToMcpMetaConverter)
					.build()))
			.toArray(ToolCallback[]::new);
		validateToolCallbacks(array);
		return array;
	}

	private static McpConnectionInfo connectionInfo(McpSyncClient mcpClient) {
		return McpConnectionInfo.builder()
			.clientCapabilities(mcpClient.getClientCapabilities())
			.clientInfo(mcpClient.getClientInfo())
			.initializeResult(mcpClient.getCurrentInitializationResult())
			.build();
	}

	/**
	 * Validates tool callbacks for duplicate names.
	 * @param toolCallbacks callbacks to validate
	 * @throws IllegalStateException if duplicate names exist
	 */
	private void validateToolCallbacks(ToolCallback[] toolCallbacks) {
		List<String> duplicateToolNames = ToolUtils.getDuplicateToolNames(toolCallbacks);
		if (!duplicateToolNames.isEmpty()) {
			throw new IllegalStateException(
					"Multiple tools with the same name (%s)".formatted(String.join(", ", duplicateToolNames)));
		}
	}

	/**
	 * Creates tool callbacks from multiple MCP clients.
	 * <p>
	 * Discovers and consolidates tools from all provided clients into a single list,
	 * ensuring no naming conflicts.
	 * @param mcpClients MCP clients to discover tools from
	 * @return consolidated list of tool callbacks
	 */
	public static List<ToolCallback> syncToolCallbacks(List<McpSyncClient> mcpClients) {

		if (CollectionUtils.isEmpty(mcpClients)) {
			return List.of();
		}
		return List.of((new SyncMcpToolCallbackProvider(mcpClients).getToolCallbacks()));
	}

	/**
	 * Creates a builder for constructing provider instances.
	 * @return new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@code SyncMcpToolCallbackProvider}.
	 */
	public static class Builder {

		private List<McpSyncClient> mcpClients = new ArrayList<>();

		private McpToolFilter toolFilter = (mcpClient, tool) -> true;

		private McpToolNamePrefixGenerator toolNamePrefixGenerator = new DefaultMcpToolNamePrefixGenerator();

		private ToolContextToMcpMetaConverter toolContextToMcpMetaConverter = ToolContextToMcpMetaConverter
			.defaultConverter();

		/**
		 * Sets MCP clients for tool discovery (replaces existing).
		 * @param mcpClients list of MCP clients
		 * @return this builder
		 */
		public Builder mcpClients(List<McpSyncClient> mcpClients) {
			Assert.notNull(mcpClients, "MCP clients list must not be null");
			this.mcpClients = new ArrayList<>(mcpClients);
			return this;
		}

		/**
		 * Sets MCP clients for tool discovery (replaces existing).
		 * @param mcpClients MCP clients array
		 * @return this builder
		 */
		public Builder mcpClients(McpSyncClient... mcpClients) {
			Assert.notNull(mcpClients, "MCP clients array must not be null");
			this.mcpClients = new java.util.ArrayList<>(List.of(mcpClients));
			return this;
		}

		/**
		 * Adds an MCP client to the existing list.
		 * @param mcpClient MCP client to add
		 * @return this builder
		 */
		public Builder addMcpClient(McpSyncClient mcpClient) {
			Assert.notNull(mcpClient, "MCP client must not be null");
			this.mcpClients.add(mcpClient);
			return this;
		}

		/**
		 * Sets tool filter. Defaults to accepting all tools.
		 * @param toolFilter filter for discovered tools
		 * @return this builder
		 */
		public Builder toolFilter(McpToolFilter toolFilter) {
			Assert.notNull(toolFilter, "Tool filter must not be null");
			this.toolFilter = toolFilter;
			return this;
		}

		/**
		 * Sets tool name prefix generator.
		 * @param toolNamePrefixGenerator generates prefixes for tool names
		 * @return this builder
		 */
		public Builder toolNamePrefixGenerator(McpToolNamePrefixGenerator toolNamePrefixGenerator) {
			Assert.notNull(toolNamePrefixGenerator, "Tool name prefix generator must not be null");
			this.toolNamePrefixGenerator = toolNamePrefixGenerator;
			return this;
		}

		/**
		 * Sets tool context to MCP metadata converter. Defaults to
		 * {@link ToolContextToMcpMetaConverter#defaultConverter()}.
		 * @param toolContextToMcpMetaConverter converts tool context to MCP metadata
		 * @return this builder
		 */
		public Builder toolContextToMcpMetaConverter(ToolContextToMcpMetaConverter toolContextToMcpMetaConverter) {
			Assert.notNull(toolContextToMcpMetaConverter, "Tool context to MCP meta converter must not be null");
			this.toolContextToMcpMetaConverter = toolContextToMcpMetaConverter;
			return this;
		}

		/**
		 * Builds the provider with configured parameters.
		 * @return configured {@code SyncMcpToolCallbackProvider}
		 */
		public SyncMcpToolCallbackProvider build() {
			// Assert.notEmpty(this.mcpClients, "At least one MCP client must be
			// provided");
			return new SyncMcpToolCallbackProvider(this.toolFilter, this.toolNamePrefixGenerator, this.mcpClients,
					this.toolContextToMcpMetaConverter);
		}

	}

}
