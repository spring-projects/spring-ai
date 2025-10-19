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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpElicitation;
import org.springaicommunity.mcp.annotation.McpLogging;
import org.springaicommunity.mcp.annotation.McpProgress;
import org.springaicommunity.mcp.annotation.McpPromptListChanged;
import org.springaicommunity.mcp.annotation.McpResourceListChanged;
import org.springaicommunity.mcp.annotation.McpSampling;
import org.springaicommunity.mcp.annotation.McpToolListChanged;
import org.springaicommunity.mcp.method.changed.prompt.AsyncPromptListChangedSpecification;
import org.springaicommunity.mcp.method.changed.prompt.SyncPromptListChangedSpecification;
import org.springaicommunity.mcp.method.changed.resource.AsyncResourceListChangedSpecification;
import org.springaicommunity.mcp.method.changed.resource.SyncResourceListChangedSpecification;
import org.springaicommunity.mcp.method.changed.tool.AsyncToolListChangedSpecification;
import org.springaicommunity.mcp.method.changed.tool.SyncToolListChangedSpecification;
import org.springaicommunity.mcp.method.elicitation.AsyncElicitationSpecification;
import org.springaicommunity.mcp.method.elicitation.SyncElicitationSpecification;
import org.springaicommunity.mcp.method.logging.AsyncLoggingSpecification;
import org.springaicommunity.mcp.method.logging.SyncLoggingSpecification;
import org.springaicommunity.mcp.method.progress.AsyncProgressSpecification;
import org.springaicommunity.mcp.method.progress.SyncProgressSpecification;
import org.springaicommunity.mcp.method.sampling.AsyncSamplingSpecification;
import org.springaicommunity.mcp.method.sampling.SyncSamplingSpecification;

import org.springframework.ai.mcp.annotation.spring.AsyncMcpAnnotationProviders;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.ai.mcp.client.common.autoconfigure.annotations.McpAsyncAnnotationCustomizer;
import org.springframework.ai.mcp.client.common.autoconfigure.annotations.McpClientAnnotationScannerAutoConfiguration.ClientMcpAnnotatedBeans;
import org.springframework.ai.mcp.client.common.autoconfigure.annotations.McpSyncAnnotationCustomizer;
import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpAsyncClientConfigurer;
import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpSyncClientConfigurer;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.customizer.McpAsyncClientCustomizer;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
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
 */
@AutoConfiguration(afterName = {
		"org.springframework.ai.mcp.client.common.autoconfigure.StdioTransportAutoConfiguration",
		"org.springframework.ai.mcp.client.httpclient.autoconfigure.SseHttpClientTransportAutoConfiguration",
		"org.springframework.ai.mcp.client.httpclient.autoconfigure.StreamableHttpHttpClientTransportAutoConfiguration",
		"org.springframework.ai.mcp.client.webflux.autoconfigure.SseWebFluxTransportAutoConfiguration",
		"org.springframework.ai.mcp.client.webflux.autoconfigure.StreamableHttpWebFluxTransportAutoConfiguration" })
@ConditionalOnClass({ McpSchema.class })
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

	/**
	 * Creates a {@link McpSyncClientInitializer} that defers client creation until all
	 * singleton beans have been initialized.
	 *
	 * <p>
	 * This ensures that all beans with MCP annotations have been scanned and registered
	 * before the clients are created, preventing a timing issue where late-initialized
	 * beans might miss registration.
	 * @param mcpSyncClientConfigurer the configurer for customizing client creation
	 * @param commonProperties common MCP client properties
	 * @param transportsProvider provider of named MCP transports
	 * @param annotatedBeans registry of beans with MCP annotations
	 * @return the client initializer that creates clients after singleton instantiation
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public McpSyncClientInitializer mcpSyncClientInitializer(McpSyncClientConfigurer mcpSyncClientConfigurer,
			McpClientCommonProperties commonProperties,
			ObjectProvider<List<NamedClientMcpTransport>> transportsProvider,
			ObjectProvider<ClientMcpAnnotatedBeans> annotatedBeansProvider) {
		return new McpSyncClientInitializer(this, mcpSyncClientConfigurer, commonProperties, transportsProvider,
				annotatedBeansProvider);
	}

	/**
	 * Provides the list of {@link McpSyncClient} instances created by the initializer.
	 *
	 * <p>
	 * This bean is available after all singleton beans have been initialized.
	 * @param initializer the client initializer
	 * @return list of configured MCP sync clients
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<McpSyncClient> mcpSyncClients(McpSyncClientInitializer initializer) {
		// Return the client list directly - it will be populated by
		// SmartInitializingSingleton
		return initializer.getClients();
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

	/**
	 * Creates a {@link McpAsyncClientInitializer} that defers client creation until all
	 * singleton beans have been initialized.
	 *
	 * <p>
	 * This ensures that all beans with MCP annotations have been scanned and registered
	 * before the clients are created, preventing a timing issue where late-initialized
	 * beans might miss registration.
	 * @param mcpAsyncClientConfigurer the configurer for customizing client creation
	 * @param commonProperties common MCP client properties
	 * @param transportsProvider provider of named MCP transports
	 * @param annotatedBeans registry of beans with MCP annotations
	 * @return the client initializer that creates clients after singleton instantiation
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public McpAsyncClientInitializer mcpAsyncClientInitializer(McpAsyncClientConfigurer mcpAsyncClientConfigurer,
			McpClientCommonProperties commonProperties,
			ObjectProvider<List<NamedClientMcpTransport>> transportsProvider,
			ObjectProvider<ClientMcpAnnotatedBeans> annotatedBeansProvider) {
		return new McpAsyncClientInitializer(this, mcpAsyncClientConfigurer, commonProperties, transportsProvider,
				annotatedBeansProvider);
	}

	/**
	 * Provides the list of {@link McpAsyncClient} instances created by the initializer.
	 *
	 * <p>
	 * This bean is available after all singleton beans have been initialized.
	 * @param initializer the client initializer
	 * @return list of configured MCP async clients
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public List<McpAsyncClient> mcpAsyncClients(McpAsyncClientInitializer initializer) {
		// Return the client list directly - it will be populated by
		// SmartInitializingSingleton
		return initializer.getClients();
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
	 * Initializer for MCP synchronous clients that implements
	 * {@link SmartInitializingSingleton}.
	 *
	 * <p>
	 * This class defers the creation of MCP sync clients until after all singleton beans
	 * have been initialized. This ensures that all beans with MCP-annotated methods have
	 * been scanned and registered in the {@link ClientMcpAnnotatedBeans} registry before
	 * the specifications are created and clients are configured.
	 *
	 * <p>
	 * The initialization process:
	 * <ol>
	 * <li>Wait for all singleton beans to be instantiated
	 * <li>Re-evaluate specifications from the complete registry
	 * <li>Create and configure MCP clients with all registered specifications
	 * <li>Initialize clients if configured to do so
	 * </ol>
	 */
	public static class McpSyncClientInitializer implements SmartInitializingSingleton {

		private static final Logger logger = LoggerFactory.getLogger(McpSyncClientInitializer.class);

		private final McpClientAutoConfiguration configuration;

		private final McpSyncClientConfigurer configurer;

		private final McpClientCommonProperties properties;

		private final ObjectProvider<List<NamedClientMcpTransport>> transportsProvider;

		private final ObjectProvider<ClientMcpAnnotatedBeans> annotatedBeansProvider;

		final List<McpSyncClient> clients = new ArrayList<>();

		private long initializationTimestamp = -1;

		public McpSyncClientInitializer(McpClientAutoConfiguration configuration, McpSyncClientConfigurer configurer,
				McpClientCommonProperties properties, ObjectProvider<List<NamedClientMcpTransport>> transportsProvider,
				ObjectProvider<ClientMcpAnnotatedBeans> annotatedBeansProvider) {
			this.configuration = configuration;
			this.configurer = configurer;
			this.properties = properties;
			this.transportsProvider = transportsProvider;
			this.annotatedBeansProvider = annotatedBeansProvider;
		}

		@Override
		public void afterSingletonsInstantiated() {
			// Record when initialization starts
			this.initializationTimestamp = System.nanoTime();

			logger.debug("Creating MCP sync clients after all singleton beans have been instantiated");

			McpSyncClientCustomizer annotationCustomizer = null;

			// Only create annotation customizer if annotated beans registry is available
			ClientMcpAnnotatedBeans annotatedBeans = this.annotatedBeansProvider.getIfAvailable();
			if (annotatedBeans != null) {
				// Re-create specifications from the now-complete registry
				List<SyncLoggingSpecification> loggingSpecs = SyncMcpAnnotationProviders
					.loggingSpecifications(annotatedBeans.getBeansByAnnotation(McpLogging.class));

				List<SyncSamplingSpecification> samplingSpecs = SyncMcpAnnotationProviders
					.samplingSpecifications(annotatedBeans.getBeansByAnnotation(McpSampling.class));

				List<SyncElicitationSpecification> elicitationSpecs = SyncMcpAnnotationProviders
					.elicitationSpecifications(annotatedBeans.getBeansByAnnotation(McpElicitation.class));

				List<SyncProgressSpecification> progressSpecs = SyncMcpAnnotationProviders
					.progressSpecifications(annotatedBeans.getBeansByAnnotation(McpProgress.class));

				List<SyncToolListChangedSpecification> toolListChangedSpecs = SyncMcpAnnotationProviders
					.toolListChangedSpecifications(annotatedBeans.getBeansByAnnotation(McpToolListChanged.class));

				List<SyncResourceListChangedSpecification> resourceListChangedSpecs = SyncMcpAnnotationProviders
					.resourceListChangedSpecifications(
							annotatedBeans.getBeansByAnnotation(McpResourceListChanged.class));

				List<SyncPromptListChangedSpecification> promptListChangedSpecs = SyncMcpAnnotationProviders
					.promptListChangedSpecifications(annotatedBeans.getBeansByAnnotation(McpPromptListChanged.class));

				// Create the annotation customizer with fresh specifications
				annotationCustomizer = new McpSyncAnnotationCustomizer(samplingSpecs, loggingSpecs, elicitationSpecs,
						progressSpecs, toolListChangedSpecs, resourceListChangedSpecs, promptListChangedSpecs);
			}

			// Create the clients using the base configurer and annotation customizer (if
			// available)
			List<McpSyncClient> createdClients = createClients(this.configurer, annotationCustomizer);
			this.clients.addAll(createdClients);

			logger.info("Created {} MCP sync client(s)", this.clients.size());
		}

		private List<McpSyncClient> createClients(McpSyncClientConfigurer configurer,
				McpSyncClientCustomizer annotationCustomizer) {
			List<McpSyncClient> mcpSyncClients = new ArrayList<>();

			List<NamedClientMcpTransport> namedTransports = this.transportsProvider.stream()
				.flatMap(List::stream)
				.toList();

			if (!CollectionUtils.isEmpty(namedTransports)) {
				for (NamedClientMcpTransport namedTransport : namedTransports) {

					McpSchema.Implementation clientInfo = new McpSchema.Implementation(
							this.configuration.connectedClientName(this.properties.getName(), namedTransport.name()),
							namedTransport.name(), this.properties.getVersion());

					McpClient.SyncSpec spec = McpClient.sync(namedTransport.transport())
						.clientInfo(clientInfo)
						.requestTimeout(this.properties.getRequestTimeout());

					spec = configurer.configure(namedTransport.name(), spec);

					// Apply annotation customizer after other customizers (if available)
					if (annotationCustomizer != null) {
						annotationCustomizer.customize(namedTransport.name(), spec);
					}

					var client = spec.build();

					if (this.properties.isInitialized()) {
						client.initialize();
					}

					mcpSyncClients.add(client);
				}
			}

			return mcpSyncClients;
		}

		public List<McpSyncClient> getClients() {
			if (this.clients == null) {
				throw new IllegalStateException(
						"MCP sync clients not yet initialized. They are created after all singleton beans are instantiated.");
			}
			return this.clients;
		}

		/**
		 * Returns the timestamp (in nanoseconds) when afterSingletonsInstantiated() was
		 * called. This can be used in tests to verify SmartInitializingSingleton timing.
		 * @return the initialization timestamp, or -1 if not yet initialized
		 */
		public long getInitializationTimestamp() {
			return this.initializationTimestamp;
		}

	}

	/**
	 * Initializer for MCP asynchronous clients that implements
	 * {@link SmartInitializingSingleton}.
	 *
	 * <p>
	 * This class defers the creation of MCP async clients until after all singleton beans
	 * have been initialized. This ensures that all beans with MCP-annotated methods have
	 * been scanned and registered in the {@link ClientMcpAnnotatedBeans} registry before
	 * the specifications are created and clients are configured.
	 */
	public static class McpAsyncClientInitializer implements SmartInitializingSingleton {

		private static final Logger logger = LoggerFactory.getLogger(McpAsyncClientInitializer.class);

		private final McpClientAutoConfiguration configuration;

		private final McpAsyncClientConfigurer configurer;

		private final McpClientCommonProperties properties;

		private final ObjectProvider<List<NamedClientMcpTransport>> transportsProvider;

		private final ObjectProvider<ClientMcpAnnotatedBeans> annotatedBeansProvider;

		final List<McpAsyncClient> clients = new ArrayList<>();

		public McpAsyncClientInitializer(McpClientAutoConfiguration configuration, McpAsyncClientConfigurer configurer,
				McpClientCommonProperties properties, ObjectProvider<List<NamedClientMcpTransport>> transportsProvider,
				ObjectProvider<ClientMcpAnnotatedBeans> annotatedBeansProvider) {
			this.configuration = configuration;
			this.configurer = configurer;
			this.properties = properties;
			this.transportsProvider = transportsProvider;
			this.annotatedBeansProvider = annotatedBeansProvider;
		}

		@Override
		public void afterSingletonsInstantiated() {
			logger.debug("Creating MCP async clients after all singleton beans have been instantiated");

			McpAsyncClientCustomizer annotationCustomizer = null;

			// Only create annotation customizer if annotated beans registry is available
			ClientMcpAnnotatedBeans annotatedBeans = this.annotatedBeansProvider.getIfAvailable();
			if (annotatedBeans != null) {
				// Re-create specifications from the now-complete registry
				List<AsyncLoggingSpecification> loggingSpecs = AsyncMcpAnnotationProviders
					.loggingSpecifications(annotatedBeans.getAllAnnotatedBeans());

				List<AsyncSamplingSpecification> samplingSpecs = AsyncMcpAnnotationProviders
					.samplingSpecifications(annotatedBeans.getAllAnnotatedBeans());

				List<AsyncElicitationSpecification> elicitationSpecs = AsyncMcpAnnotationProviders
					.elicitationSpecifications(annotatedBeans.getAllAnnotatedBeans());

				List<AsyncProgressSpecification> progressSpecs = AsyncMcpAnnotationProviders
					.progressSpecifications(annotatedBeans.getAllAnnotatedBeans());

				List<AsyncToolListChangedSpecification> toolListChangedSpecs = AsyncMcpAnnotationProviders
					.toolListChangedSpecifications(annotatedBeans.getAllAnnotatedBeans());

				List<AsyncResourceListChangedSpecification> resourceListChangedSpecs = AsyncMcpAnnotationProviders
					.resourceListChangedSpecifications(annotatedBeans.getAllAnnotatedBeans());

				List<AsyncPromptListChangedSpecification> promptListChangedSpecs = AsyncMcpAnnotationProviders
					.promptListChangedSpecifications(annotatedBeans.getAllAnnotatedBeans());

				// Create the annotation customizer with fresh specifications
				annotationCustomizer = new McpAsyncAnnotationCustomizer(samplingSpecs, loggingSpecs, elicitationSpecs,
						progressSpecs, toolListChangedSpecs, resourceListChangedSpecs, promptListChangedSpecs);
			}

			// Create the clients using the base configurer and annotation customizer (if
			// available)
			List<McpAsyncClient> createdClients = createClients(this.configurer, annotationCustomizer);
			this.clients.addAll(createdClients);

			logger.info("Created {} MCP async client(s)", this.clients.size());
		}

		private List<McpAsyncClient> createClients(McpAsyncClientConfigurer configurer,
				McpAsyncClientCustomizer annotationCustomizer) {
			List<McpAsyncClient> mcpAsyncClients = new ArrayList<>();

			List<NamedClientMcpTransport> namedTransports = this.transportsProvider.stream()
				.flatMap(List::stream)
				.toList();

			if (!CollectionUtils.isEmpty(namedTransports)) {
				for (NamedClientMcpTransport namedTransport : namedTransports) {

					McpSchema.Implementation clientInfo = new McpSchema.Implementation(
							this.configuration.connectedClientName(this.properties.getName(), namedTransport.name()),
							this.properties.getVersion());

					McpClient.AsyncSpec spec = McpClient.async(namedTransport.transport())
						.clientInfo(clientInfo)
						.requestTimeout(this.properties.getRequestTimeout());

					spec = configurer.configure(namedTransport.name(), spec);

					// Apply annotation customizer after other customizers (if available)
					if (annotationCustomizer != null) {
						annotationCustomizer.customize(namedTransport.name(), spec);
					}

					var client = spec.build();

					if (this.properties.isInitialized()) {
						client.initialize().block();
					}

					mcpAsyncClients.add(client);
				}
			}

			return mcpAsyncClients;
		}

		public List<McpAsyncClient> getClients() {
			if (this.clients == null) {
				throw new IllegalStateException(
						"MCP async clients not yet initialized. They are created after all singleton beans are instantiated.");
			}
			return this.clients;
		}

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
	 * Record class that implements {@link AutoCloseable} to ensure proper cleanup of MCP
	 * async clients.
	 *
	 * <p>
	 * This class is responsible for closing all MCP async clients when the application
	 * context is closed, preventing resource leaks.
	 */
	public record CloseableMcpAsyncClients(List<McpAsyncClient> clients) implements AutoCloseable {

		@Override
		public void close() {
			this.clients.forEach(McpAsyncClient::close);
		}

	}

}
