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

package org.springframework.ai.mcp.client.common.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.ai.mcp.annotation.spring.ClientMcpAsyncHandlersRegistry;
import org.springframework.ai.mcp.annotation.spring.ClientMcpSyncHandlersRegistry;
import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpAsyncClientConfigurer;
import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpSyncClientConfigurer;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.customizer.McpAsyncClientCustomizer;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
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
 */
@AutoConfiguration(afterName = {
		"org.springframework.ai.mcp.client.common.autoconfigure.StdioTransportAutoConfiguration",
		"org.springframework.ai.mcp.client.httpclient.autoconfigure.SseHttpClientTransportAutoConfiguration",
		"org.springframework.ai.mcp.client.httpclient.autoconfigure.StreamableHttpHttpClientTransportAutoConfiguration",
		"org.springframework.ai.mcp.client.webflux.autoconfigure.SseWebFluxTransportAutoConfiguration",
		"org.springframework.ai.mcp.client.webflux.autoconfigure.StreamableHttpWebFluxTransportAutoConfiguration" })
@ConditionalOnClass(McpSchema.class)
@EnableConfigurationProperties(McpClientCommonProperties.class)
@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class McpClientAutoConfiguration {

	/**
	 * Create a dynamic client name based on the client name and the name of the server
	 * connection.
	 * @param clientName the client name as defined by the configuration
	 * @param serverConnectionName the name of the server connection being used by the
	 * client
	 * @return the connected client name
	 */
	private String connectedClientName(String clientName, String serverConnectionName) {
		return clientName + " - " + serverConnectionName;
	}

	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public McpSyncToolsChangeEventEmmiter mcpSyncToolChangeEventEmmiter(
			ApplicationEventPublisher applicationEventPublisher) {
		return new McpSyncToolsChangeEventEmmiter(applicationEventPublisher);
	}

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
			ObjectProvider<List<NamedClientMcpTransport>> transportsProvider,
			ObjectProvider<ClientMcpSyncHandlersRegistry> clientMcpSyncHandlersRegistry) {

		List<McpSyncClient> mcpSyncClients = new ArrayList<>();

		List<NamedClientMcpTransport> namedTransports = transportsProvider.stream().flatMap(List::stream).toList();

		if (!CollectionUtils.isEmpty(namedTransports)) {
			for (NamedClientMcpTransport namedTransport : namedTransports) {

				McpSchema.Implementation clientInfo = new McpSchema.Implementation(
						this.connectedClientName(commonProperties.getName(), namedTransport.name()),
						namedTransport.name(), commonProperties.getVersion());

				McpClient.SyncSpec spec = McpClient.sync(namedTransport.transport())
					.clientInfo(clientInfo)
					.requestTimeout(commonProperties.getRequestTimeout());

				clientMcpSyncHandlersRegistry.ifAvailable(registry -> spec
					.sampling(samplingRequest -> registry.handleSampling(namedTransport.name(), samplingRequest))
					.elicitation(
							elicitationRequest -> registry.handleElicitation(namedTransport.name(), elicitationRequest))
					.loggingConsumer(loggingMessageNotification -> registry.handleLogging(namedTransport.name(),
							loggingMessageNotification))
					.progressConsumer(progressNotification -> registry.handleProgress(namedTransport.name(),
							progressNotification))
					.toolsChangeConsumer(newTools -> registry.handleToolListChanged(namedTransport.name(), newTools))
					.promptsChangeConsumer(
							newPrompts -> registry.handlePromptListChanged(namedTransport.name(), newPrompts))
					.resourcesChangeConsumer(
							newResources -> registry.handleResourceListChanged(namedTransport.name(), newResources))
					.capabilities(registry.getCapabilities(namedTransport.name())));

				McpClient.SyncSpec customizedSpec = mcpSyncClientConfigurer.configure(namedTransport.name(), spec);

				var client = customizedSpec.build();

				if (commonProperties.isInitialized()) {
					client.initialize();
				}

				mcpSyncClients.add(client);
			}
		}

		return mcpSyncClients;
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
	public McpAsyncToolsChangeEventEmmiter mcpAsyncToolChangeEventEmmiter(
			ApplicationEventPublisher applicationEventPublisher) {
		return new McpAsyncToolsChangeEventEmmiter(applicationEventPublisher);
	}

	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public List<McpAsyncClient> mcpAsyncClients(McpAsyncClientConfigurer mcpAsyncClientConfigurer,
			McpClientCommonProperties commonProperties,
			ObjectProvider<List<NamedClientMcpTransport>> transportsProvider,
			ObjectProvider<ClientMcpAsyncHandlersRegistry> clientMcpAsyncHandlersRegistry) {

		List<McpAsyncClient> mcpAsyncClients = new ArrayList<>();

		List<NamedClientMcpTransport> namedTransports = transportsProvider.stream().flatMap(List::stream).toList();

		if (!CollectionUtils.isEmpty(namedTransports)) {
			for (NamedClientMcpTransport namedTransport : namedTransports) {

				McpSchema.Implementation clientInfo = new McpSchema.Implementation(
						this.connectedClientName(commonProperties.getName(), namedTransport.name()),
						commonProperties.getVersion());
				McpClient.AsyncSpec spec = McpClient.async(namedTransport.transport())
					.clientInfo(clientInfo)
					.requestTimeout(commonProperties.getRequestTimeout());
				clientMcpAsyncHandlersRegistry.ifAvailable(registry -> spec
					.sampling(samplingRequest -> registry.handleSampling(namedTransport.name(), samplingRequest))
					.elicitation(
							elicitationRequest -> registry.handleElicitation(namedTransport.name(), elicitationRequest))
					.loggingConsumer(loggingMessageNotification -> registry.handleLogging(namedTransport.name(),
							loggingMessageNotification))
					.progressConsumer(progressNotification -> registry.handleProgress(namedTransport.name(),
							progressNotification))
					.toolsChangeConsumer(newTools -> registry.handleToolListChanged(namedTransport.name(), newTools))
					.promptsChangeConsumer(
							newPrompts -> registry.handlePromptListChanged(namedTransport.name(), newPrompts))
					.resourcesChangeConsumer(
							newResources -> registry.handleResourceListChanged(namedTransport.name(), newResources))
					.capabilities(registry.getCapabilities(namedTransport.name())));

				McpClient.AsyncSpec customizedSpec = mcpAsyncClientConfigurer.configure(namedTransport.name(), spec);

				var client = customizedSpec.build();

				if (commonProperties.isInitialized()) {
					client.initialize().block();
				}

				mcpAsyncClients.add(client);
			}
		}

		return mcpAsyncClients;
	}

	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public CloseableMcpAsyncClients makeAsyncClientsClosable(List<McpAsyncClient> clients) {
		return new CloseableMcpAsyncClients(clients);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	McpAsyncClientConfigurer mcpAsyncClientConfigurer(ObjectProvider<McpAsyncClientCustomizer> customizerProvider) {
		return new McpAsyncClientConfigurer(customizerProvider.orderedStream().toList());
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

	public record CloseableMcpAsyncClients(List<McpAsyncClient> clients) implements AutoCloseable {
		@Override
		public void close() {
			this.clients.forEach(McpAsyncClient::close);
		}
	}

}
