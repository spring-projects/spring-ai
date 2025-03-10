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

package org.springframework.ai.autoconfigure.mcp.client;

import java.util.ArrayList;
import java.util.List;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.ai.autoconfigure.mcp.client.configurer.McpAsyncClientConfigurer;
import org.springframework.ai.autoconfigure.mcp.client.configurer.McpSyncClientConfigurer;
import org.springframework.ai.autoconfigure.mcp.client.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.customizer.McpAsyncClientCustomizer;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.CollectionUtils;

/**
 * Auto-configuration for Model Context Protocol (MCP) client support.
 *
 * <p>
 * This configuration class sets up the necessary beans for MCP client functionality,
 * including both synchronous and asynchronous clients along with their respective tool
 * callbacks. It is automatically enabled when the required classes are present on the
 * classpath and can be explicitly disabled through properties.
 *
 * <p>
 * Configuration Properties:
 * <ul>
 * <li>{@code spring.ai.mcp.client.enabled} - Enable/disable MCP client support (default:
 * true)
 * <li>{@code spring.ai.mcp.client.type} - Client type: SYNC or ASYNC (default: SYNC)
 * <li>{@code spring.ai.mcp.client.name} - Client implementation name
 * <li>{@code spring.ai.mcp.client.version} - Client implementation version
 * <li>{@code spring.ai.mcp.client.request-timeout} - Request timeout duration
 * <li>{@code spring.ai.mcp.client.initialized} - Whether to initialize clients on
 * creation
 * </ul>
 *
 * <p>
 * The configuration is activated after the transport-specific auto-configurations (Stdio,
 * SSE HTTP, and SSE WebFlux) to ensure proper initialization order. At least one
 * transport must be available for the clients to be created.
 *
 * <p>
 * Key features:
 * <ul>
 * <li>Synchronous and Asynchronous Client Support:
 * <ul>
 * <li>Creates and configures MCP clients based on available transports
 * <li>Supports both blocking (sync) and non-blocking (async) operations
 * <li>Automatic client initialization if enabled
 * </ul>
 * <li>Integration Support:
 * <ul>
 * <li>Sets up tool callbacks for Spring AI integration
 * <li>Supports multiple named transports
 * <li>Proper lifecycle management with automatic cleanup
 * </ul>
 * <li>Customization Options:
 * <ul>
 * <li>Extensible through {@link McpSyncClientCustomizer} and
 * {@link McpAsyncClientCustomizer}
 * <li>Configurable timeouts and client information
 * <li>Support for custom transport implementations
 * </ul>
 * </ul>
 *
 * @see McpSyncClient
 * @see McpAsyncClient
 * @see McpClientCommonProperties
 * @see McpSyncClientCustomizer
 * @see McpAsyncClientCustomizer
 * @see StdioTransportAutoConfiguration
 * @see SseHttpClientTransportAutoConfiguration
 * @see SseWebFluxTransportAutoConfiguration
 */
@AutoConfiguration(after = { StdioTransportAutoConfiguration.class, SseHttpClientTransportAutoConfiguration.class,
		SseWebFluxTransportAutoConfiguration.class })
@ConditionalOnClass({ McpSchema.class })
@EnableConfigurationProperties(McpClientCommonProperties.class)
@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class McpClientAutoConfiguration {

	/**
	 * Creates a list of {@link McpSyncClient} instances based on the available
	 * transports.
	 *
	 * <p>
	 * Each client is configured with:
	 * <ul>
	 * <li>Client information (name and version) from common properties
	 * <li>Request timeout settings
	 * <li>Custom configurations through {@link McpSyncClientConfigurer}
	 * </ul>
	 *
	 * <p>
	 * If initialization is enabled in properties, the clients are automatically
	 * initialized.
	 * @param mcpSyncClientConfigurer the configurer for customizing client creation
	 * @param commonProperties common MCP client properties
	 * @param transportsProvider provider of named MCP transports
	 * @return list of configured MCP sync clients
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<McpSyncClient> mcpSyncClients(McpSyncClientConfigurer mcpSyncClientConfigurer,
			McpClientCommonProperties commonProperties,
			ObjectProvider<List<NamedClientMcpTransport>> transportsProvider) {

		List<McpSyncClient> mcpSyncClients = new ArrayList<>();

		List<NamedClientMcpTransport> namedTransports = transportsProvider.stream().flatMap(List::stream).toList();

		if (!CollectionUtils.isEmpty(namedTransports)) {
			for (NamedClientMcpTransport namedTransport : namedTransports) {

				McpSchema.Implementation clientInfo = new McpSchema.Implementation(commonProperties.getName(),
						commonProperties.getVersion());

				McpClient.SyncSpec syncSpec = McpClient.sync(namedTransport.transport())
					.clientInfo(clientInfo)
					.requestTimeout(commonProperties.getRequestTimeout());

				syncSpec = mcpSyncClientConfigurer.configure(namedTransport.name(), syncSpec);

				var syncClient = syncSpec.build();

				if (commonProperties.isInitialized()) {
					syncClient.initialize();
				}

				mcpSyncClients.add(syncClient);
			}
		}

		return mcpSyncClients;
	}

	/**
	 * Creates tool callbacks for all configured MCP clients.
	 *
	 * <p>
	 * These callbacks enable integration with Spring AI's tool execution framework,
	 * allowing MCP tools to be used as part of AI interactions.
	 * @param mcpClientsProvider provider of MCP sync clients
	 * @return list of tool callbacks for MCP integration
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public ToolCallbackProvider toolCallbacks(ObjectProvider<List<McpSyncClient>> mcpClientsProvider) {
		List<McpSyncClient> mcpClients = mcpClientsProvider.stream().flatMap(List::stream).toList();
		return new SyncMcpToolCallbackProvider(mcpClients);
	}

	/**
	 * @deprecated replaced by {@link #toolCallbacks(ObjectProvider)} that returns a
	 * {@link ToolCallbackProvider} instead of a list of {@link ToolCallback}
	 */
	@Deprecated
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<ToolCallback> toolCallbacksDeprecated(ObjectProvider<List<McpSyncClient>> mcpClientsProvider) {
		List<McpSyncClient> mcpClients = mcpClientsProvider.stream().flatMap(List::stream).toList();
		return List.of(new SyncMcpToolCallbackProvider(mcpClients).getToolCallbacks());
	}

	/**
	 * Record class that implements {@link AutoCloseable} to ensure proper cleanup of MCP
	 * clients.
	 *
	 * <p>
	 * This class is responsible for closing all MCP sync clients when the application
	 * context is closed, preventing resource leaks.
	 */
	public record CloseableMcpSyncClients(List<McpSyncClient> clients) implements AutoCloseable {

		@Override
		public void close() {
			this.clients.forEach(McpSyncClient::close);
		}
	}

	/**
	 * Creates a closeable wrapper for MCP sync clients to ensure proper resource cleanup.
	 * @param clients the list of MCP sync clients to manage
	 * @return a closeable wrapper for the clients
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public CloseableMcpSyncClients makeSyncClientsClosable(List<McpSyncClient> clients) {
		return new CloseableMcpSyncClients(clients);
	}

	/**
	 * Creates the default {@link McpSyncClientConfigurer} if none is provided.
	 *
	 * <p>
	 * This configurer aggregates all available {@link McpSyncClientCustomizer} instances
	 * to allow for customization of MCP sync client creation.
	 * @param customizerProvider provider of MCP sync client customizers
	 * @return the configured MCP sync client configurer
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	McpSyncClientConfigurer mcpSyncClientConfigurer(ObjectProvider<McpSyncClientCustomizer> customizerProvider) {
		return new McpSyncClientConfigurer(customizerProvider.orderedStream().toList());
	}

	// Async client configuration

	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public List<McpAsyncClient> mcpAsyncClients(McpAsyncClientConfigurer mcpSyncClientConfigurer,
			McpClientCommonProperties commonProperties,
			ObjectProvider<List<NamedClientMcpTransport>> transportsProvider) {

		List<McpAsyncClient> mcpSyncClients = new ArrayList<>();

		List<NamedClientMcpTransport> namedTransports = transportsProvider.stream().flatMap(List::stream).toList();

		if (!CollectionUtils.isEmpty(namedTransports)) {
			for (NamedClientMcpTransport namedTransport : namedTransports) {

				McpSchema.Implementation clientInfo = new McpSchema.Implementation(commonProperties.getName(),
						commonProperties.getVersion());

				McpClient.AsyncSpec syncSpec = McpClient.async(namedTransport.transport())
					.clientInfo(clientInfo)
					.requestTimeout(commonProperties.getRequestTimeout());

				syncSpec = mcpSyncClientConfigurer.configure(namedTransport.name(), syncSpec);

				var syncClient = syncSpec.build();

				if (commonProperties.isInitialized()) {
					syncClient.initialize().block();
				}

				mcpSyncClients.add(syncClient);
			}
		}

		return mcpSyncClients;
	}

	/**
	 * @deprecated replaced by {@link #asyncToolCallbacks(ObjectProvider)} that returns a
	 * {@link ToolCallbackProvider} instead of a list of {@link ToolCallback}
	 */
	@Deprecated
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public List<ToolCallback> asyncToolCallbacksDeprecated(ObjectProvider<List<McpAsyncClient>> mcpClientsProvider) {
		List<McpAsyncClient> mcpClients = mcpClientsProvider.stream().flatMap(List::stream).toList();
		return List.of(new AsyncMcpToolCallbackProvider(mcpClients).getToolCallbacks());
	}

	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public ToolCallbackProvider asyncToolCallbacks(ObjectProvider<List<McpAsyncClient>> mcpClientsProvider) {
		List<McpAsyncClient> mcpClients = mcpClientsProvider.stream().flatMap(List::stream).toList();
		return new AsyncMcpToolCallbackProvider(mcpClients);
	}

	public record CloseableMcpAsyncClients(List<McpAsyncClient> clients) implements AutoCloseable {
		@Override
		public void close() {
			this.clients.forEach(McpAsyncClient::close);
		}
	}

	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public CloseableMcpAsyncClients makeAsynClientsClosable(List<McpAsyncClient> clients) {
		return new CloseableMcpAsyncClients(clients);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	McpAsyncClientConfigurer mcpAsyncClientConfigurer(ObjectProvider<McpAsyncClientCustomizer> customizerProvider) {
		return new McpAsyncClientConfigurer(customizerProvider.orderedStream().toList());
	}

}
