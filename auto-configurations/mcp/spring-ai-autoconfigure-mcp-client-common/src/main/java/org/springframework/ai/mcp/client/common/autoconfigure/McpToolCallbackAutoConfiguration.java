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
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
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
 */
@AutoConfiguration(after = { McpClientAutoConfiguration.class })
@EnableConfigurationProperties(McpClientCommonProperties.class)
@Conditional(McpToolCallbackAutoConfiguration.McpToolCallbackAutoConfigurationCondition.class)
public class McpToolCallbackAutoConfiguration {

	/**
	 * Provides a default {@link McpToolNamePrefixGenerator} bean if none is already
	 * defined.
	 * <p>
	 * This generator is used to create uniquely prefixed tool names based on the MCP
	 * connection information, helping to avoid name collisions when integrating tools
	 * from multiple MCP servers.
	 *
	 * Register the McpToolNamePrefixGenerator.noPrefix() bean to disable the prefixing.
	 * @return the default McpToolNamePrefixGenerator
	 */
	@Bean
	@ConditionalOnMissingBean
	public McpToolNamePrefixGenerator mcpToolNamePrefixGenerator() {
		return McpToolNamePrefixGenerator.defaultGenerator();
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
	 * @return list of tool callbacks for MCP integration
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public SyncMcpToolCallbackProvider mcpToolCallbacks(ObjectProvider<McpToolFilter> syncClientsToolFilter,
			ObjectProvider<List<McpSyncClient>> syncMcpClients, McpToolNamePrefixGenerator mcpToolNamePrefixGenerator) {
		List<McpSyncClient> mcpClients = syncMcpClients.stream().flatMap(List::stream).toList();
		return new SyncMcpToolCallbackProvider(syncClientsToolFilter.getIfUnique((() -> (McpSyncClient, tool) -> true)),
				mcpToolNamePrefixGenerator, mcpClients);
	}

	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public AsyncMcpToolCallbackProvider mcpAsyncToolCallbacks(ObjectProvider<McpToolFilter> asyncClientsToolFilter,
			ObjectProvider<List<McpAsyncClient>> mcpClientsProvider,
			McpToolNamePrefixGenerator toolNamePrefixGenerator) {
		List<McpAsyncClient> mcpClients = mcpClientsProvider.stream().flatMap(List::stream).toList();
		return new AsyncMcpToolCallbackProvider(
				asyncClientsToolFilter.getIfUnique(() -> (McpAsyncClient, tool) -> true), toolNamePrefixGenerator,
				mcpClients);
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
