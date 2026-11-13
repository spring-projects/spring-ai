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
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientModernProperties;
import org.springframework.ai.mcp.modern.client.McpModernToolFilter;
import org.springframework.ai.mcp.modern.client.McpModernToolNamePrefixGenerator;
import org.springframework.ai.mcp.modern.client.SyncModernMcpConnection;
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
	 * a legacy {@link McpToolFilter} bean is defined, which does not apply to them
	 * @param modernToolNamePrefixGenerator the name generator of the modern connections'
	 * tools
	 * @param modernProperties the properties of the modern connections
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
			McpClientModernProperties modernProperties) {

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
			// The legacy filter and name generator take the legacy schema types and are
			// not applied to modern tools. A connection that moves to the modern protocol
			// must not lose its filter without anyone noticing.
			if (syncClientsToolFilter.stream().findAny().isPresent() && modernToolFilter.stream().findAny().isEmpty()) {
				throw new IllegalStateException("A McpToolFilter bean is defined, but it does not filter the tools of "
						+ "the MCP connections that use the MODERN protocol " + modernConnectionNames
						+ ". Define a McpModernToolFilter bean for them, for example McpModernToolFilter.acceptAll() "
						+ "to expose all their tools.");
			}
			McpToolNamePrefixGenerator legacyNameGenerator = mcpToolNamePrefixGenerator.getIfUnique();
			if (legacyNameGenerator != null && !(legacyNameGenerator instanceof DefaultMcpToolNamePrefixGenerator)
					&& modernToolNamePrefixGenerator.stream().findAny().isEmpty()) {
				logger.warn("The McpToolNamePrefixGenerator bean does not name the tools of the MCP connections that "
						+ "use the MODERN protocol " + modernConnectionNames
						+ ". They are named <connection>_<tool> unless a McpModernToolNamePrefixGenerator bean is "
						+ "defined.");
			}
			builder.modernToolCallbackProvider(SyncModernMcpToolCallbackProvider.builder()
				.connections(modernConnections)
				.toolFilter(modernToolFilter.getIfUnique(McpModernToolFilter::acceptAll))
				.toolNamePrefixGenerator(
						modernToolNamePrefixGenerator.getIfUnique(McpModernToolNamePrefixGenerator::connectionPrefix))
				.toolContextToMcpMetaConverter(metaConverter)
				.unreachableCoolDown(modernProperties.getUnreachableCoolDown())
				.build());
		}

		return builder.build();
	}

	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public AsyncMcpToolCallbackProvider mcpAsyncToolCallbacks(ObjectProvider<McpToolFilter> asyncClientsToolFilter,
			ObjectProvider<List<McpAsyncClient>> mcpClientsProvider,
			ObjectProvider<McpToolNamePrefixGenerator> toolNamePrefixGenerator,
			ObjectProvider<ToolContextToMcpMetaConverter> toolContextToMcpMetaConverter) { // TODO
		List<McpAsyncClient> mcpClients = mcpClientsProvider.stream().flatMap(List::stream).toList();
		return AsyncMcpToolCallbackProvider.builder()
			.toolFilter(asyncClientsToolFilter.getIfUnique(() -> (McpAsyncClient, tool) -> true))
			.toolNamePrefixGenerator(toolNamePrefixGenerator.getIfUnique(() -> McpToolNamePrefixGenerator.noPrefix()))
			.toolContextToMcpMetaConverter(
					toolContextToMcpMetaConverter.getIfUnique(() -> ToolContextToMcpMetaConverter.defaultConverter()))
			.mcpClients(mcpClients)
			.build();
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
