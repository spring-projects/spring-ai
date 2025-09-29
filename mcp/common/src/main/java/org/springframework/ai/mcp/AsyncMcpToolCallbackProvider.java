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

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.util.Assert;
import reactor.core.publisher.Flux;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.util.CollectionUtils;

/**
 * Provides MCP tools asynchronously from multiple MCP servers as Spring AI tool
 * callbacks.
 * <p>
 * Discovers and exposes tools from configured MCP servers, enabling their use within
 * Spring AI applications. Supports filtering and custom naming strategies for tools.
 *
 * @author Christian Tzolov
 * @author YunKui Lu
 * @since 1.0.0
 */
public class AsyncMcpToolCallbackProvider implements ToolCallbackProvider {

	private final McpToolFilter toolFilter;

	private final List<McpAsyncClient> mcpClients;

	private final McpToolNamePrefixGenerator toolNamePrefixGenerator;

	private final ToolContextToMcpMetaConverter toolContextToMcpMetaConverter;

	/**
	 * Creates a provider with tool filtering.
	 * @param toolFilter filter to apply to discovered tools
	 * @param mcpClients MCP clients for tool discovery
	 * @deprecated use {@link #builder()} instead
	 */
	@Deprecated
	public AsyncMcpToolCallbackProvider(McpToolFilter toolFilter, List<McpAsyncClient> mcpClients) {
		this(toolFilter, McpToolNamePrefixGenerator.noPrefix(), ToolContextToMcpMetaConverter.defaultConverter(),
				mcpClients);
	}

	/**
	 * Creates a provider with full configuration.
	 * @param toolFilter filter for discovered tools
	 * @param toolNamePrefixGenerator generates prefixes for tool names
	 * @param toolContextToMcpMetaConverter converts tool context to MCP metadata
	 * @param mcpClients MCP clients for tool discovery
	 */
	private AsyncMcpToolCallbackProvider(McpToolFilter toolFilter, McpToolNamePrefixGenerator toolNamePrefixGenerator,
			ToolContextToMcpMetaConverter toolContextToMcpMetaConverter, List<McpAsyncClient> mcpClients) {
		Assert.notNull(mcpClients, "MCP clients must not be null");
		Assert.notNull(toolFilter, "Tool filter must not be null");
		Assert.notNull(toolNamePrefixGenerator, "Tool name prefix generator must not be null");
		Assert.notNull(toolContextToMcpMetaConverter, "Tool context to MCP meta converter must not be null");
		this.toolFilter = toolFilter;
		this.mcpClients = mcpClients;
		this.toolNamePrefixGenerator = toolNamePrefixGenerator;
		this.toolContextToMcpMetaConverter = toolContextToMcpMetaConverter;
	}

	/**
	 * Creates a provider with default configuration.
	 * @param mcpClients MCP clients for tool discovery
	 * @throws IllegalArgumentException if mcpClients is null
	 * @deprecated use {@link #builder()} instead
	 */
	@Deprecated
	public AsyncMcpToolCallbackProvider(List<McpAsyncClient> mcpClients) {
		this((mcpClient, tool) -> true, mcpClients);
	}

	/**
	 * Creates a provider with tool filtering.
	 * @param toolFilter filter for discovered tools
	 * @param mcpClients MCP clients for tool discovery
	 * @deprecated use {@link #builder()} instead
	 */
	@Deprecated
	public AsyncMcpToolCallbackProvider(McpToolFilter toolFilter, McpAsyncClient... mcpClients) {
		this(toolFilter, List.of(mcpClients));
	}

	/**
	 * Creates a provider with default configuration.
	 * @param mcpClients MCP clients for tool discovery
	 * @deprecated use {@link #builder()} instead
	 */
	@Deprecated
	public AsyncMcpToolCallbackProvider(McpAsyncClient... mcpClients) {
		this(List.of(mcpClients));
	}

	/**
	 * Discovers and returns all available tools from configured MCP servers.
	 * <p>
	 * Retrieves tools asynchronously from each server, creates callbacks, and validates
	 * uniqueness. Blocks until all tools are discovered.
	 * @return array of tool callbacks for discovered tools
	 * @throws IllegalStateException if duplicate tool names exist
	 */
	@Override
	public ToolCallback[] getToolCallbacks() {

		List<ToolCallback> toolCallbackList = new ArrayList<>();

		for (McpAsyncClient mcpClient : this.mcpClients) {

			ToolCallback[] toolCallbacks = mcpClient.listTools()
				.map(response -> response.tools()
					.stream()
					.filter(tool -> this.toolFilter.test(connectionInfo(mcpClient), tool))
					.map(tool -> AsyncMcpToolCallback.builder()
						.mcpClient(mcpClient)
						.tool(tool)
						.prefixedToolName(
								this.toolNamePrefixGenerator.prefixedToolName(connectionInfo(mcpClient), tool))
						.toolContextToMcpMetaConverter(this.toolContextToMcpMetaConverter)
						.build())
					.toArray(ToolCallback[]::new))
				.block();

			validateToolCallbacks(toolCallbacks);

			toolCallbackList.addAll(List.of(toolCallbacks));
		}

		return toolCallbackList.toArray(new ToolCallback[0]);
	}

	private static McpConnectionInfo connectionInfo(McpAsyncClient mcpClient) {
		return McpConnectionInfo.builder()
			.clientCapabilities(mcpClient.getClientCapabilities())
			.clientInfo(mcpClient.getClientInfo())
			.initializeResult(mcpClient.getCurrentInitializationResult())
			.build();
	}

	/**
	 * Validates tool name uniqueness.
	 * @param toolCallbacks callbacks to validate
	 * @throws IllegalStateException if duplicate names found
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
	 * Provides fully reactive tool discovery suitable for non-blocking applications.
	 * Combines tools from all clients into a single stream with name conflict validation.
	 * @param mcpClients MCP clients for tool discovery
	 * @return Flux of tool callbacks from all clients
	 */
	public static Flux<ToolCallback> asyncToolCallbacks(List<McpAsyncClient> mcpClients) {
		if (CollectionUtils.isEmpty(mcpClients)) {
			return Flux.empty();
		}

		return Flux.fromArray(new AsyncMcpToolCallbackProvider(mcpClients).getToolCallbacks());
	}

	/**
	 * Creates a builder for constructing provider instances.
	 * @return new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@code AsyncMcpToolCallbackProvider} configuration.
	 */
	public final static class Builder {

		private McpToolFilter toolFilter = (mcpClient, tool) -> true;

		private List<McpAsyncClient> mcpClients;

		private McpToolNamePrefixGenerator toolNamePrefixGenerator = new DefaultMcpToolNamePrefixGenerator();

		private ToolContextToMcpMetaConverter toolContextToMcpMetaConverter = ToolContextToMcpMetaConverter
			.defaultConverter();

		private Builder() {
		}

		/**
		 * Sets tool filter.
		 * @param toolFilter filter for discovered tools
		 * @return this builder
		 */
		public Builder toolFilter(McpToolFilter toolFilter) {
			Assert.notNull(toolFilter, "Tool filter must not be null");
			this.toolFilter = toolFilter;
			return this;
		}

		/**
		 * Sets MCP clients.
		 * @param mcpClients list of MCP clients
		 * @return this builder
		 */
		public Builder mcpClients(List<McpAsyncClient> mcpClients) {
			Assert.notNull(mcpClients, "MCP clients list must not be null");
			this.mcpClients = mcpClients;
			return this;
		}

		/**
		 * Sets MCP clients.
		 * @param mcpClients MCP clients as varargs
		 * @return this builder
		 */
		public Builder mcpClients(McpAsyncClient... mcpClients) {
			Assert.notNull(mcpClients, "MCP clients must not be null");
			this.mcpClients = List.of(mcpClients);
			return this;
		}

		/**
		 * Sets tool name prefix generator.
		 * @param toolNamePrefixGenerator generator for tool name prefixes
		 * @return this builder
		 */
		public Builder toolNamePrefixGenerator(McpToolNamePrefixGenerator toolNamePrefixGenerator) {
			Assert.notNull(toolNamePrefixGenerator, "Tool name prefix generator must not be null");
			this.toolNamePrefixGenerator = toolNamePrefixGenerator;
			return this;
		}

		/**
		 * Sets tool context to MCP metadata converter.
		 * @param toolContextToMcpMetaConverter converter for tool context
		 * @return this builder
		 */
		public Builder toolContextToMcpMetaConverter(ToolContextToMcpMetaConverter toolContextToMcpMetaConverter) {
			Assert.notNull(toolContextToMcpMetaConverter, "Tool context to MCP meta converter must not be null");
			this.toolContextToMcpMetaConverter = toolContextToMcpMetaConverter;
			return this;
		}

		public AsyncMcpToolCallbackProvider build() {
			return new AsyncMcpToolCallbackProvider(this.toolFilter, this.toolNamePrefixGenerator,
					this.toolContextToMcpMetaConverter, this.mcpClients);
		}

	}

}
