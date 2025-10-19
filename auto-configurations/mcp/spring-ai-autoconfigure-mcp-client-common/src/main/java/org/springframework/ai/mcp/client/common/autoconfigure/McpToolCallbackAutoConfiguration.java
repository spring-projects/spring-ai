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
@AutoConfiguration(after = { McpClientAutoConfiguration.class },
		beforeName = { "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration" })
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
	 *
	 * <p>
	 * IMPORTANT: This method receives the same list reference that is populated by
	 * {@link McpClientAutoConfiguration.McpSyncClientInitializer} in its
	 * {@code afterSingletonsInstantiated()} method. This ensures that when
	 * {@code getToolCallbacks()} is called, even if it's called before full
	 * initialization completes, it will eventually see the populated list.
	 * @param syncClientsToolFilter list of {@link McpToolFilter}s for the sync client to
	 * filter the discovered tools
	 * @param syncMcpClients the MCP sync clients list (same reference as returned by
	 * mcpSyncClients() bean method)
	 * @param mcpToolNamePrefixGenerator the tool name prefix generator
	 * @return list of tool callbacks for MCP integration
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public SyncMcpToolCallbackProvider mcpToolCallbacks(ObjectProvider<McpToolFilter> syncClientsToolFilter,
			List<McpSyncClient> syncMcpClients, ObjectProvider<McpToolNamePrefixGenerator> mcpToolNamePrefixGenerator,
			ObjectProvider<ToolContextToMcpMetaConverter> toolContextToMcpMetaConverter) {

		// Use mcpClientsReference to share the list reference - it will be populated by
		// SmartInitializingSingleton
		return SyncMcpToolCallbackProvider.builder()
			.mcpClientsReference(syncMcpClients)
			.toolFilter(syncClientsToolFilter.getIfUnique((() -> (mcpClient, tool) -> true)))
			.toolNamePrefixGenerator(
					mcpToolNamePrefixGenerator.getIfUnique(() -> McpToolNamePrefixGenerator.noPrefix()))
			.toolContextToMcpMetaConverter(
					toolContextToMcpMetaConverter.getIfUnique(() -> ToolContextToMcpMetaConverter.defaultConverter()))
			.build();
	}

	/**
	 * Creates async tool callbacks for all configured MCP clients.
	 *
	 * <p>
	 * IMPORTANT: This method receives the same list reference that is populated by
	 * {@link McpClientAutoConfiguration.McpAsyncClientInitializer} in its
	 * {@code afterSingletonsInstantiated()} method.
	 * @param asyncClientsToolFilter tool filter for async clients
	 * @param mcpClients the MCP async clients list (same reference as returned by
	 * mcpAsyncClients() bean method)
	 * @param toolNamePrefixGenerator the tool name prefix generator
	 * @param toolContextToMcpMetaConverter converter for tool context to MCP metadata
	 * @return async tool callback provider
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public AsyncMcpToolCallbackProvider mcpAsyncToolCallbacks(ObjectProvider<McpToolFilter> asyncClientsToolFilter,
			List<McpAsyncClient> mcpClients, ObjectProvider<McpToolNamePrefixGenerator> toolNamePrefixGenerator,
			ObjectProvider<ToolContextToMcpMetaConverter> toolContextToMcpMetaConverter) {

		// Use mcpClientsReference to share the list reference - it will be populated by
		// SmartInitializingSingleton
		return AsyncMcpToolCallbackProvider.builder()
			.toolFilter(asyncClientsToolFilter.getIfUnique(() -> (mcpClient, tool) -> true))
			.toolNamePrefixGenerator(toolNamePrefixGenerator.getIfUnique(() -> McpToolNamePrefixGenerator.noPrefix()))
			.toolContextToMcpMetaConverter(
					toolContextToMcpMetaConverter.getIfUnique(() -> ToolContextToMcpMetaConverter.defaultConverter()))
			.mcpClientsReference(mcpClients)
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
