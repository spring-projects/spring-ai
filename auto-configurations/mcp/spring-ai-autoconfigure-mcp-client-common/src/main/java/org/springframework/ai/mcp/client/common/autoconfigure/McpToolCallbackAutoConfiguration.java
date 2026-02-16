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

import java.util.List;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpSyncClient;

import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.DefaultMcpToolNamePrefixGenerator;
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
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
@AutoConfiguration(after = McpClientAutoConfiguration.class,
		beforeName = "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration")
@EnableConfigurationProperties(McpClientCommonProperties.class)
@Conditional(McpToolCallbackAutoConfiguration.McpToolCallbackAutoConfigurationCondition.class)
public class McpToolCallbackAutoConfiguration {

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
	 * @param properties the MCP client common properties
	 * @return list of tool callbacks for MCP integration
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public SyncMcpToolCallbackProvider mcpToolCallbacks(ObjectProvider<McpToolFilter> syncClientsToolFilter,
			ObjectProvider<List<McpSyncClient>> syncMcpClients,
			ObjectProvider<McpToolNamePrefixGenerator> mcpToolNamePrefixGenerator,
			ObjectProvider<ToolContextToMcpMetaConverter> toolContextToMcpMetaConverter,
			McpClientCommonProperties properties) {

		List<McpSyncClient> mcpClients = syncMcpClients.stream().flatMap(List::stream).toList();

		return SyncMcpToolCallbackProvider.builder()
			.mcpClients(mcpClients)
			.toolFilter(syncClientsToolFilter.getIfUnique((() -> (McpSyncClient, tool) -> true)))
			.toolNamePrefixGenerator(
					mcpToolNamePrefixGenerator.getIfUnique(() -> McpToolNamePrefixGenerator.noPrefix()))
			.toolContextToMcpMetaConverter(
					toolContextToMcpMetaConverter.getIfUnique(() -> ToolContextToMcpMetaConverter.defaultConverter()))
			.cacheTtl(properties.getToolcallback().getCacheTtl())
			.build();
	}

	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public AsyncMcpToolCallbackProvider mcpAsyncToolCallbacks(ObjectProvider<McpToolFilter> asyncClientsToolFilter,
			ObjectProvider<List<McpAsyncClient>> mcpClientsProvider,
			ObjectProvider<McpToolNamePrefixGenerator> toolNamePrefixGenerator,
			ObjectProvider<ToolContextToMcpMetaConverter> toolContextToMcpMetaConverter,
			McpClientCommonProperties properties) {
		List<McpAsyncClient> mcpClients = mcpClientsProvider.stream().flatMap(List::stream).toList();
		return AsyncMcpToolCallbackProvider.builder()
			.toolFilter(asyncClientsToolFilter.getIfUnique(() -> (McpAsyncClient, tool) -> true))
			.toolNamePrefixGenerator(toolNamePrefixGenerator.getIfUnique(() -> McpToolNamePrefixGenerator.noPrefix()))
			.toolContextToMcpMetaConverter(
					toolContextToMcpMetaConverter.getIfUnique(() -> ToolContextToMcpMetaConverter.defaultConverter()))
			.mcpClients(mcpClients)
			.cacheTtl(properties.getToolcallback().getCacheTtl())
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
