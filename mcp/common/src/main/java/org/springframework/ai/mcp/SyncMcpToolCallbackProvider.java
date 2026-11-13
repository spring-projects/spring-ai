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

package org.springframework.ai.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.context.ApplicationListener;
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
public class SyncMcpToolCallbackProvider implements ToolCallbackProvider, ApplicationListener<McpToolsChangedEvent> {

	private static final Log logger = LogFactory.getLog(SyncMcpToolCallbackProvider.class);

	private final List<McpSyncClient> mcpClients;

	private final McpToolFilter toolFilter;

	private final McpToolNamePrefixGenerator toolNamePrefixGenerator;

	private final ToolContextToMcpMetaConverter toolContextToMcpMetaConverter;

	private final boolean failFast;

	private volatile boolean invalidateCache = true;

	private volatile List<ToolCallback> cachedToolCallbacks = List.of();

	private final Map<Integer, List<ToolCallback>> toolCallbacksByClient = new HashMap<>();

	private final Set<Integer> failedClients = new HashSet<>();

	private volatile boolean retryFailedClients;

	private final Lock lock = new ReentrantLock();

	/**
	 * Creates a provider with MCP clients and tool filter.
	 * @param mcpClients MCP clients for tool discovery
	 * @param toolFilter filter for discovered tools
	 * @deprecated use {@link #builder()} instead
	 */
	@Deprecated
	public SyncMcpToolCallbackProvider(McpToolFilter toolFilter, List<McpSyncClient> mcpClients) {
		this(toolFilter, McpToolNamePrefixGenerator.noPrefix(), mcpClients,
				ToolContextToMcpMetaConverter.defaultConverter(), true);
	}

	/**
	 * Creates a provider with all configuration options.
	 * @param mcpClients MCP clients for tool discovery
	 * @param toolNamePrefixGenerator generates prefixes for tool names
	 * @param toolFilter filter for discovered tools
	 * @param toolContextToMcpMetaConverter converts tool context to MCP metadata
	 * @param failFast whether tool discovery failures are fatal
	 */
	private SyncMcpToolCallbackProvider(McpToolFilter toolFilter, McpToolNamePrefixGenerator toolNamePrefixGenerator,
			List<McpSyncClient> mcpClients, ToolContextToMcpMetaConverter toolContextToMcpMetaConverter,
			boolean failFast) {
		Assert.notNull(mcpClients, "MCP clients must not be null");
		Assert.notNull(toolFilter, "Tool filter must not be null");
		Assert.notNull(toolNamePrefixGenerator, "Tool name prefix generator must not be null");
		Assert.notNull(toolContextToMcpMetaConverter, "Tool context to MCP meta converter must not be null");
		this.mcpClients = mcpClients;
		this.toolFilter = toolFilter;
		this.toolNamePrefixGenerator = toolNamePrefixGenerator;
		this.toolContextToMcpMetaConverter = toolContextToMcpMetaConverter;
		this.failFast = failFast;
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
		this(toolFilter, toolNamePrefixGenerator, List.of(mcpClients), ToolContextToMcpMetaConverter.defaultConverter(),
				true);
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

		if (this.invalidateCache || this.retryFailedClients) {
			this.lock.lock();
			try {
				if (this.invalidateCache || this.retryFailedClients) {
					boolean refreshAll = this.invalidateCache;
					Set<Integer> clientsToRetry = new HashSet<>(this.failedClients);

					for (int i = 0; i < this.mcpClients.size(); i++) {
						if (!refreshAll && !clientsToRetry.contains(i)) {
							continue;
						}

						McpSyncClient mcpClient = this.mcpClients.get(i);
						McpSchema.ListToolsResult listToolsResult;
						try {
							listToolsResult = mcpClient.listTools();
						}
						catch (RuntimeException ex) {
							if (this.failFast) {
								throw ex;
							}
							boolean firstFailure = this.failedClients.add(i);
							this.toolCallbacksByClient.remove(i);
							if (firstFailure) {
								logger.warn("Failed to discover tools from MCP client '" + clientName(mcpClient)
										+ "'. The client will be retried on the next discovery attempt", ex);
							}
							continue;
						}

						List<ToolCallback> clientToolCallbacks = listToolsResult.tools()
							.stream()
							.filter(tool -> this.toolFilter.test(connectionInfo(mcpClient), tool))
							.<ToolCallback>map(tool -> SyncMcpToolCallback.builder()
								.mcpClient(mcpClient)
								.tool(tool)
								.prefixedToolName(
										this.toolNamePrefixGenerator.prefixedToolName(connectionInfo(mcpClient), tool))
								.toolContextToMcpMetaConverter(this.toolContextToMcpMetaConverter)
								.build())
							.toList();

						this.toolCallbacksByClient.put(i, clientToolCallbacks);
						this.failedClients.remove(i);
					}

					List<ToolCallback> toolCallbacks = new ArrayList<>();
					for (int i = 0; i < this.mcpClients.size(); i++) {
						toolCallbacks.addAll(this.toolCallbacksByClient.getOrDefault(i, List.of()));
					}
					this.cachedToolCallbacks = toolCallbacks;

					this.validateToolCallbacks(this.cachedToolCallbacks);
					this.invalidateCache = false;
					this.retryFailedClients = !this.failedClients.isEmpty();
				}
			}
			finally {
				this.lock.unlock();
			}
		}

		return this.cachedToolCallbacks.toArray(new ToolCallback[0]);
	}

	/**
	 * Invalidates the cached tool callbacks, forcing re-discovery on next request.
	 */
	public void invalidateCache() {
		this.invalidateCache = true;
	}

	@Override
	public void onApplicationEvent(McpToolsChangedEvent event) {
		this.invalidateCache();
	}

	private static McpConnectionInfo connectionInfo(McpSyncClient mcpClient) {
		return McpConnectionInfo.builder()
			.clientCapabilities(mcpClient.getClientCapabilities())
			.clientInfo(mcpClient.getClientInfo())
			.initializeResult(mcpClient.getCurrentInitializationResult())
			.build();
	}

	private static String clientName(McpSyncClient mcpClient) {
		var clientInfo = mcpClient.getClientInfo();
		return clientInfo != null ? clientInfo.name() : "unknown";
	}

	/**
	 * Validates tool callbacks for duplicate names.
	 * @param toolCallbacks callbacks to validate
	 * @throws IllegalStateException if duplicate names exist
	 */
	private void validateToolCallbacks(List<ToolCallback> toolCallbacks) {
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
	public static final class Builder {

		private List<McpSyncClient> mcpClients = new ArrayList<>();

		private McpToolFilter toolFilter = (mcpClient, tool) -> true;

		private McpToolNamePrefixGenerator toolNamePrefixGenerator = new DefaultMcpToolNamePrefixGenerator();

		private ToolContextToMcpMetaConverter toolContextToMcpMetaConverter = ToolContextToMcpMetaConverter
			.defaultConverter();

		private boolean failFast = true;

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
		 * Configures whether a tool discovery failure from one MCP client should abort
		 * discovery for all clients. Defaults to {@code true}.
		 * @param failFast whether to fail immediately on a client error
		 * @return this builder
		 * @since 2.0.2
		 */
		public Builder failFast(boolean failFast) {
			this.failFast = failFast;
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
					this.toolContextToMcpMetaConverter, this.failFast);
		}

	}

}
