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

package org.springframework.ai.mcp.client.common.autoconfigure;

import java.util.List;
import java.util.stream.Stream;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpSyncClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.DefaultMcpToolNamePrefixGenerator;
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.mcp.annotation.spring.ClientMcpAsyncHandlersRegistry;
import org.springframework.ai.mcp.annotation.spring.ClientMcpSyncHandlersRegistry;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientModernProperties;
import org.springframework.ai.mcp.modern.client.AsyncModernMcpChangeSubscriber;
import org.springframework.ai.mcp.modern.client.AsyncModernMcpConnection;
import org.springframework.ai.mcp.modern.client.AsyncModernMcpToolCallCustomizer;
import org.springframework.ai.mcp.modern.client.AsyncModernMcpToolCallbackProvider;
import org.springframework.ai.mcp.modern.client.McpModernToolFilter;
import org.springframework.ai.mcp.modern.client.McpModernToolNamePrefixGenerator;
import org.springframework.ai.mcp.modern.client.SyncModernMcpChangeSubscriber;
import org.springframework.ai.mcp.modern.client.SyncModernMcpConnection;
import org.springframework.ai.mcp.modern.client.SyncModernMcpToolCallCustomizer;
import org.springframework.ai.mcp.modern.client.SyncModernMcpToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Responsible to convert MCP (sync and async) clients into Spring AI
 * ToolCallbacksProviders. These providers are used by Spring AI to discover and execute
 * tools.
 */
@AutoConfiguration
@EnableConfigurationProperties({ McpClientCommonProperties.class, McpClientModernProperties.class })
@Conditional(McpToolCallbackAutoConfiguration.McpToolCallbackAutoConfigurationCondition.class)
public class McpToolCallbackAutoConfiguration {

	private static final Log logger = LogFactory.getLog(McpToolCallbackAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public McpToolNamePrefixGenerator defaultMcpToolNamePrefixGenerator() {
		return new DefaultMcpToolNamePrefixGenerator();
	}

	/**
	 * Creates tool callbacks for all configured MCP clients.
	 *
	 * <p>
	 * These callbacks enable integration with Spring AI's tool execution framework,
	 * allowing MCP tools to be used as part of AI interactions.
	 * @param syncClientsToolFilter list of {@link McpToolFilter}s for the sync client to
	 * filter the discovered tools
	 * @param syncMcpClients provider of MCP sync clients
	 * @param mcpToolNamePrefixGenerator the tool name prefix generator
	 * @param toolContextToMcpMetaConverter the converter of the tool context, used for
	 * legacy and modern connections alike
	 * @param modernMcpConnections the connections that speak the modern protocol, whose
	 * tools the provider returns together with those of the legacy clients
	 * @param modernToolFilter the filter of the modern connections' tools; required when
	 * a legacy {@link McpToolFilter} bean is defined, which does not apply to them.
	 * Several such beans without a primary one fail the startup
	 * @param modernToolNamePrefixGenerator the name generator of the modern connections'
	 * tools
	 * @param modernProperties the properties of the modern connections
	 * @param handlersRegistry the registry of the annotated client handler methods, if
	 * the annotation scanner is enabled; its elicitation and progress methods handle the
	 * tool calls of the modern connections
	 * @param toolCallCustomizers the customizers of the tool calls of the modern
	 * connections, applied in their order after the annotated handlers are set
	 * @param changeSubscriber the subscriber of the listen streams of the modern
	 * connections, which tells the provider when the tools of a server changed
	 * @return list of tool callbacks for MCP integration
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public SyncMcpToolCallbackProvider mcpToolCallbacks(ObjectProvider<McpToolFilter> syncClientsToolFilter,
			ObjectProvider<List<McpSyncClient>> syncMcpClients,
			ObjectProvider<McpToolNamePrefixGenerator> mcpToolNamePrefixGenerator,
			ObjectProvider<ToolContextToMcpMetaConverter> toolContextToMcpMetaConverter,
			ObjectProvider<List<SyncModernMcpConnection>> modernMcpConnections,
			ObjectProvider<McpModernToolFilter> modernToolFilter,
			ObjectProvider<McpModernToolNamePrefixGenerator> modernToolNamePrefixGenerator,
			McpClientModernProperties modernProperties, ObjectProvider<ClientMcpSyncHandlersRegistry> handlersRegistry,
			ObjectProvider<SyncModernMcpToolCallCustomizer> toolCallCustomizers,
			ObjectProvider<SyncModernMcpChangeSubscriber> changeSubscriber) {

		List<McpSyncClient> mcpClients = syncMcpClients.stream().flatMap(List::stream).toList();

		ToolContextToMcpMetaConverter metaConverter = toolContextToMcpMetaConverter
			.getIfUnique(() -> ToolContextToMcpMetaConverter.defaultConverter());

		SyncMcpToolCallbackProvider.Builder builder = SyncMcpToolCallbackProvider.builder()
			.mcpClients(mcpClients)
			.toolFilter(syncClientsToolFilter.getIfUnique((() -> (McpSyncClient, tool) -> true)))
			.toolNamePrefixGenerator(
					mcpToolNamePrefixGenerator.getIfUnique(() -> McpToolNamePrefixGenerator.noPrefix()))
			.toolContextToMcpMetaConverter(metaConverter);

		// The tools of the connections that speak the modern protocol are served by the
		// same provider bean, so that the application registers a single provider.
		List<SyncModernMcpConnection> modernConnections = modernMcpConnections.stream().flatMap(List::stream).toList();
		if (!modernConnections.isEmpty()) {
			List<String> modernConnectionNames = modernConnections.stream()
				.map(connection -> connection.info().name())
				.toList();
			checkLegacyFilterAndNameGenerator(modernConnectionNames, syncClientsToolFilter, mcpToolNamePrefixGenerator,
					modernToolFilter, modernToolNamePrefixGenerator);
			SyncModernMcpToolCallbackProvider.Builder modernBuilder = SyncModernMcpToolCallbackProvider.builder()
				.connections(modernConnections)
				// getIfAvailable, not getIfUnique: several beans without a primary one
				// fail the startup. Falling back to the default would expose all tools.
				.toolFilter(modernToolFilter.getIfAvailable(McpModernToolFilter::acceptAll))
				.toolNamePrefixGenerator(modernToolNamePrefixGenerator
					.getIfAvailable(McpModernToolNamePrefixGenerator::connectionPrefix))
				.toolContextToMcpMetaConverter(metaConverter)
				.unreachableCoolDown(modernProperties.getUnreachableCoolDown());
			// The annotated handlers are set for each tool call, so that they receive
			// the ToolContext of that call. The customizers of the application run after
			// them and can replace what they set.
			Stream<SyncModernMcpToolCallCustomizer> annotatedHandlers = handlersRegistry.stream()
				.map(McpClientModernAutoConfiguration::annotatedHandlersToolCallCustomizer);
			Stream.concat(annotatedHandlers, toolCallCustomizers.orderedStream())
				.reduce(SyncModernMcpToolCallCustomizer::andThen)
				.ifPresent(modernBuilder::toolCallCustomizer);
			SyncModernMcpToolCallbackProvider modernProvider = modernBuilder.build();
			// The provider drops a kept tool listing when the server reports a change on
			// the listen stream of the connection. It is not a bean, so it is not among
			// the listener beans that the subscriber was created with.
			changeSubscriber.ifAvailable(subscriber -> subscriber.addListener(modernProvider));
			builder.modernToolCallbackProvider(modernProvider);
		}

		return builder.build();
	}

	/**
	 * Creates tool callbacks for all configured MCP clients, with the asynchronous client
	 * type.
	 * @param asyncClientsToolFilter list of {@link McpToolFilter}s for the async client
	 * to filter the discovered tools
	 * @param mcpClientsProvider provider of MCP async clients
	 * @param toolNamePrefixGenerator the tool name prefix generator
	 * @param toolContextToMcpMetaConverter the converter of the tool context, used for
	 * legacy and modern connections alike
	 * @param modernMcpConnections the connections that speak the modern protocol, whose
	 * tools the provider returns together with those of the legacy clients
	 * @param modernToolFilter the filter of the modern connections' tools; required when
	 * a legacy {@link McpToolFilter} bean is defined, which does not apply to them.
	 * Several such beans without a primary one fail the startup
	 * @param modernToolNamePrefixGenerator the name generator of the modern connections'
	 * tools
	 * @param modernProperties the properties of the modern connections
	 * @param handlersRegistry the registry of the annotated client handler methods, if
	 * the annotation scanner is enabled; its reactive elicitation and progress methods
	 * handle the tool calls of the modern connections
	 * @param toolCallCustomizers the customizers of the tool calls of the modern
	 * connections, applied in their order after the annotated handlers are set
	 * @param changeSubscriber the subscriber of the listen streams of the modern
	 * connections, which tells the provider when the tools of a server changed
	 * @return list of tool callbacks for MCP integration
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public AsyncMcpToolCallbackProvider mcpAsyncToolCallbacks(ObjectProvider<McpToolFilter> asyncClientsToolFilter,
			ObjectProvider<List<McpAsyncClient>> mcpClientsProvider,
			ObjectProvider<McpToolNamePrefixGenerator> toolNamePrefixGenerator,
			ObjectProvider<ToolContextToMcpMetaConverter> toolContextToMcpMetaConverter,
			ObjectProvider<List<AsyncModernMcpConnection>> modernMcpConnections,
			ObjectProvider<McpModernToolFilter> modernToolFilter,
			ObjectProvider<McpModernToolNamePrefixGenerator> modernToolNamePrefixGenerator,
			McpClientModernProperties modernProperties, ObjectProvider<ClientMcpAsyncHandlersRegistry> handlersRegistry,
			ObjectProvider<AsyncModernMcpToolCallCustomizer> toolCallCustomizers,
			ObjectProvider<AsyncModernMcpChangeSubscriber> changeSubscriber) {

		List<McpAsyncClient> mcpClients = mcpClientsProvider.stream().flatMap(List::stream).toList();

		ToolContextToMcpMetaConverter metaConverter = toolContextToMcpMetaConverter
			.getIfUnique(() -> ToolContextToMcpMetaConverter.defaultConverter());

		AsyncMcpToolCallbackProvider.Builder builder = AsyncMcpToolCallbackProvider.builder()
			.toolFilter(asyncClientsToolFilter.getIfUnique(() -> (McpAsyncClient, tool) -> true))
			.toolNamePrefixGenerator(toolNamePrefixGenerator.getIfUnique(() -> McpToolNamePrefixGenerator.noPrefix()))
			.toolContextToMcpMetaConverter(metaConverter)
			.mcpClients(mcpClients);

		// The tools of the connections that speak the modern protocol are served by the
		// same provider bean, so that the application registers a single provider.
		List<AsyncModernMcpConnection> modernConnections = modernMcpConnections.stream().flatMap(List::stream).toList();
		if (!modernConnections.isEmpty()) {
			List<String> modernConnectionNames = modernConnections.stream()
				.map(connection -> connection.info().name())
				.toList();
			checkLegacyFilterAndNameGenerator(modernConnectionNames, asyncClientsToolFilter, toolNamePrefixGenerator,
					modernToolFilter, modernToolNamePrefixGenerator);
			AsyncModernMcpToolCallbackProvider.Builder modernBuilder = AsyncModernMcpToolCallbackProvider.builder()
				.connections(modernConnections)
				// getIfAvailable, not getIfUnique: several beans without a primary one
				// fail the startup. Falling back to the default would expose all tools.
				.toolFilter(modernToolFilter.getIfAvailable(McpModernToolFilter::acceptAll))
				.toolNamePrefixGenerator(modernToolNamePrefixGenerator
					.getIfAvailable(McpModernToolNamePrefixGenerator::connectionPrefix))
				.toolContextToMcpMetaConverter(metaConverter)
				.unreachableCoolDown(modernProperties.getUnreachableCoolDown());
			// The annotated handlers are set for each tool call, so that they receive
			// the ToolContext of that call. The customizers of the application run after
			// them and can replace what they set.
			Stream<AsyncModernMcpToolCallCustomizer> annotatedHandlers = handlersRegistry.stream()
				.map(McpClientModernAutoConfiguration::annotatedHandlersToolCallCustomizer);
			Stream.concat(annotatedHandlers, toolCallCustomizers.orderedStream())
				.reduce(AsyncModernMcpToolCallCustomizer::andThen)
				.ifPresent(modernBuilder::toolCallCustomizer);
			AsyncModernMcpToolCallbackProvider modernProvider = modernBuilder.build();
			// The provider drops a kept tool listing when the server reports a change on
			// the listen stream of the connection. It is not a bean, so it is not among
			// the listener beans that the subscriber was created with.
			changeSubscriber.ifAvailable(subscriber -> subscriber.addListener(modernProvider));
			builder.modernToolCallbackProvider(modernProvider);
		}

		return builder.build();
	}

	/**
	 * The legacy filter and name generator take the legacy schema types and are not
	 * applied to modern tools. A connection that moves to the modern protocol must not
	 * lose its filter without anyone noticing.
	 */
	private static void checkLegacyFilterAndNameGenerator(List<String> modernConnectionNames,
			ObjectProvider<McpToolFilter> legacyToolFilter,
			ObjectProvider<McpToolNamePrefixGenerator> legacyToolNamePrefixGenerator,
			ObjectProvider<McpModernToolFilter> modernToolFilter,
			ObjectProvider<McpModernToolNamePrefixGenerator> modernToolNamePrefixGenerator) {
		if (legacyToolFilter.stream().findAny().isPresent() && modernToolFilter.stream().findAny().isEmpty()) {
			throw new IllegalStateException("A McpToolFilter bean is defined, but it does not filter the tools of "
					+ "the MCP connections that use the MODERN protocol " + modernConnectionNames
					+ ". Define a McpModernToolFilter bean for them, for example McpModernToolFilter.acceptAll() "
					+ "to expose all their tools.");
		}
		McpToolNamePrefixGenerator legacyNameGenerator = legacyToolNamePrefixGenerator.getIfUnique();
		if (legacyNameGenerator != null && !(legacyNameGenerator instanceof DefaultMcpToolNamePrefixGenerator)
				&& modernToolNamePrefixGenerator.stream().findAny().isEmpty()) {
			logger.warn("The McpToolNamePrefixGenerator bean does not name the tools of the MCP connections that "
					+ "use the MODERN protocol " + modernConnectionNames
					+ ". They are named <connection>_<tool> unless a McpModernToolNamePrefixGenerator bean is "
					+ "defined.");
		}
	}

	public static class McpToolCallbackAutoConfigurationCondition extends AllNestedConditions {

		public McpToolCallbackAutoConfigurationCondition() {
			super(ConfigurationPhase.PARSE_CONFIGURATION);
		}

		@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
				matchIfMissing = true)
		static class McpAutoConfigEnabled {

		}

		@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX + ".toolcallback", name = "enabled",
				havingValue = "true", matchIfMissing = true)
		static class ToolCallbackProviderEnabled {

		}

	}

}
