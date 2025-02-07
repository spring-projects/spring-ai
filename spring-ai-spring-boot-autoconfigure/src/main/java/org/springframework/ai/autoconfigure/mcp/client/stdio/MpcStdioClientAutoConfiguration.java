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

package org.springframework.ai.autoconfigure.mcp.client.stdio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.ai.mcp.McpSyncClientCustomizer;
import org.springframework.ai.mcp.McpToolCallback;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Model Context Protocol (MCP) STDIO clients.
 *
 * <p>
 * This configuration is responsible for setting up MCP clients that communicate with MCP
 * servers through standard input/output (STDIO). It creates and configures
 * {@link McpSyncClient} instances based on the provided configuration properties.
 *
 * <p>
 * The configuration is conditionally enabled when:
 * <ul>
 * <li>Required classes ({@link McpSchema} and {@link McpSyncClient}) are present on the
 * classpath</li>
 * <li>The 'spring.ai.mcp.client.stdio.enabled' property is set to 'true'</li>
 * </ul>
 *
 * <p>
 * This auto-configuration provides:
 * <ul>
 * <li>A {@code List<McpSyncClient>} bean configured for STDIO communication</li>
 * <li>A {@link McpSyncClientConfigurer} bean for customizing the MCP sync client
 * configuration</li>
 * <li>A {@code List<ToolCallback>} bean containing tool callbacks from the MCP
 * clients</li>
 * </ul>
 *
 * @author Christian Tzolov
 * @since 1.0.0
 * @see McpStdioClientProperties
 * @see McpSyncClient
 * @see McpToolCallback
 */
@AutoConfiguration
@ConditionalOnClass({ McpSchema.class, McpSyncClient.class })
@EnableConfigurationProperties(McpStdioClientProperties.class)
@ConditionalOnProperty(prefix = McpStdioClientProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true")
public class MpcStdioClientAutoConfiguration {

	@Bean
	public List<McpSyncClient> mcpSyncClients(McpSyncClientConfigurer mcpSyncClientConfigurer,
			McpStdioClientProperties clientProperties) {

		List<McpSyncClient> clients = new ArrayList<>();

		for (Map.Entry<String, ServerParameters> serverParameters : clientProperties.toServerParameters().entrySet()) {

			var transport = new StdioClientTransport(serverParameters.getValue());

			McpSchema.Implementation clientInfo = new McpSchema.Implementation(serverParameters.getKey(),
					clientProperties.getVersion());

			McpClient.SyncSpec syncSpec = McpClient.sync(transport)
				.clientInfo(clientInfo)
				.requestTimeout(clientProperties.getRequestTimeout());

			syncSpec = mcpSyncClientConfigurer.configure(serverParameters.getKey(), syncSpec);

			var syncClient = syncSpec.build();

			if (clientProperties.isInitialize()) {
				syncClient.initialize();
			}

			clients.add(syncClient);

		}

		return clients;
	}

	@Bean
	public List<ToolCallback> toolCallbacks(List<McpSyncClient> mcpClients) {
		return McpToolUtils.getToolCallbacks(mcpClients);
	}

	public record ClosebleMcpSyncClients(List<McpSyncClient> clients) implements AutoCloseable {

		@Override
		public void close() {
			this.clients.forEach(McpSyncClient::close);
		}
	}

	@Bean
	public ClosebleMcpSyncClients makeThemClosable(List<McpSyncClient> clients) {
		return new ClosebleMcpSyncClients(clients);
	}

	@Bean
	@ConditionalOnMissingBean
	McpSyncClientConfigurer mcpSyncClientConfigurer(ObjectProvider<McpSyncClientCustomizer> customizerProvider) {
		McpSyncClientConfigurer configurer = new McpSyncClientConfigurer();
		configurer.setCustomizers(customizerProvider.orderedStream().toList());
		return configurer;
	}

}
