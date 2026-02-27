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
import org.springframework.core.env.Environment;
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
	 * Creates the {@link McpConnectionBeanRegistrar} that registers individual MCP client
	 * beans for each configured connection, enabling selective injection via
	 * {@link org.springframework.ai.mcp.McpClient @McpClient}.
	 * <p>
	 * This bean must be static to ensure early registration as a
	 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor}.
	 * @param environment the Spring environment for reading connection configuration
	 * @return the MCP connection bean registrar
	 */
	@Bean
	public static McpConnectionBeanRegistrar mcpConnectionBeanRegistrar(Environment environment) {
		return new McpConnectionBeanRegistrar(environment);
	}

	/**
	 * Creates a list of {@link McpSyncClient} instances based on the available
	 * transports.
	 *
	 * <p>
	 * Each client is created using the {@link McpSyncClientFactory} to ensure consistent
	 * configuration across all client creation paths.
	 *
	 * <p>
	 * If initialization is enabled in properties, the clients are automatically
	 * initialized.
	 * @param mcpSyncClientFactory the factory for creating MCP sync clients
	 * @param transportsProvider provider of named MCP transports
	 * @return list of configured MCP sync clients
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<McpSyncClient> mcpSyncClients(McpSyncClientFactory mcpSyncClientFactory,
			ObjectProvider<List<NamedClientMcpTransport>> transportsProvider) {

		List<McpSyncClient> mcpSyncClients = new ArrayList<>();

		List<NamedClientMcpTransport> namedTransports = transportsProvider.stream().flatMap(List::stream).toList();

		if (!CollectionUtils.isEmpty(namedTransports)) {
			for (NamedClientMcpTransport namedTransport : namedTransports) {
				McpSyncClient client = mcpSyncClientFactory.createClient(namedTransport);
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

	/**
	 * Creates the {@link McpSyncClientFactory} for building MCP sync clients.
	 *
	 * <p>
	 * This factory encapsulates the common client creation logic used by both the bulk
	 * client list and individual named client beans.
	 * @param commonProperties common MCP client properties
	 * @param configurer the configurer for customizing clients
	 * @param clientMcpSyncHandlersRegistry registry for client event handlers
	 * @return the MCP sync client factory
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	McpSyncClientFactory mcpSyncClientFactory(McpClientCommonProperties commonProperties,
			McpSyncClientConfigurer configurer, ClientMcpSyncHandlersRegistry clientMcpSyncHandlersRegistry) {
		return new McpSyncClientFactory(commonProperties, configurer, clientMcpSyncHandlersRegistry);
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
	public List<McpAsyncClient> mcpAsyncClients(McpAsyncClientFactory mcpAsyncClientFactory,
			ObjectProvider<List<NamedClientMcpTransport>> transportsProvider) {

		List<McpAsyncClient> mcpAsyncClients = new ArrayList<>();

		List<NamedClientMcpTransport> namedTransports = transportsProvider.stream().flatMap(List::stream).toList();

		if (!CollectionUtils.isEmpty(namedTransports)) {
			for (NamedClientMcpTransport namedTransport : namedTransports) {
				McpAsyncClient client = mcpAsyncClientFactory.createClient(namedTransport);
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
	 * Creates the {@link McpAsyncClientFactory} for building MCP async clients.
	 *
	 * <p>
	 * This factory encapsulates the common client creation logic used by both the bulk
	 * client list and individual named client beans.
	 * @param commonProperties common MCP client properties
	 * @param configurer the configurer for customizing clients
	 * @param clientMcpAsyncHandlersRegistry registry for client event handlers
	 * @return the MCP async client factory
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	McpAsyncClientFactory mcpAsyncClientFactory(McpClientCommonProperties commonProperties,
			McpAsyncClientConfigurer configurer, ClientMcpAsyncHandlersRegistry clientMcpAsyncHandlersRegistry) {
		return new McpAsyncClientFactory(commonProperties, configurer, clientMcpAsyncHandlersRegistry);
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
