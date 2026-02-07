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

package org.springframework.ai.mcp.client.httpclient.autoconfigure;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.customizer.McpAsyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.ai.mcp.client.common.autoconfigure.McpSseClientConnectionDetails;
import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.ai.mcp.client.common.autoconfigure.PropertiesMcpSseClientConnectionDetails;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpSseClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpSseClientProperties.SseParameters;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.log.LogAccessor;

/**
 * Auto-configuration for Server-Sent Events (SSE) HTTP client transport in the Model
 * Context Protocol (MCP).
 *
 * <p>
 * This configuration class sets up the necessary beans for SSE-based HTTP client
 * transport. It provides HTTP client-based SSE transport implementation for MCP client
 * communication.
 *
 * <p>
 * Key features:
 * <ul>
 * <li>Creates HTTP client-based SSE transports for configured MCP server connections
 * <li>Configures ObjectMapper for JSON serialization/deserialization
 * <li>Supports multiple named server connections with different URLs
 * </ul>
 *
 * @see HttpClientSseClientTransport
 * @see McpSseClientProperties
 */
@AutoConfiguration
@ConditionalOnClass({ McpSchema.class, McpSyncClient.class })
@EnableConfigurationProperties({ McpSseClientProperties.class, McpClientCommonProperties.class })
@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class SseHttpClientTransportAutoConfiguration {

	private static final LogAccessor logger = new LogAccessor(SseHttpClientTransportAutoConfiguration.class);

	@Bean
	PropertiesMcpSseClientConnectionDetails mcpSseClientConnectionDetails(McpSseClientProperties sseProperties) {
		return new PropertiesMcpSseClientConnectionDetails(sseProperties);
	}

	/**
	 * Creates a list of HTTP client-based SSE transports for MCP communication.
	 *
	 * <p>
	 * Each transport is configured with:
	 * <ul>
	 * <li>A new HttpClient instance
	 * <li>Server URL from properties
	 * <li>ObjectMapper for JSON processing
	 * <li>A sync or async HTTP request customizer. Sync takes precedence.
	 * </ul>
	 * @param connectionDetails the SSE client connection details containing server
	 * configurations
	 * @param objectMapperProvider the provider for ObjectMapper or a new instance if not
	 * available
	 * @param syncHttpRequestCustomizer provider for
	 * {@link McpSyncHttpClientRequestCustomizer} if available
	 * @param asyncHttpRequestCustomizer provider fo
	 * {@link McpAsyncHttpClientRequestCustomizer} if available
	 * @return list of named MCP transports
	 */
	@Bean
	public List<NamedClientMcpTransport> sseHttpClientTransports(McpSseClientConnectionDetails connectionDetails,
			ObjectProvider<McpSyncHttpClientRequestCustomizer> syncHttpRequestCustomizer,
			ObjectProvider<McpAsyncHttpClientRequestCustomizer> asyncHttpRequestCustomizer) {

		List<NamedClientMcpTransport> sseTransports = new ArrayList<>();

		for (Map.Entry<String, SseParameters> serverParameters : connectionDetails.getConnections().entrySet()) {
			String connectionName = serverParameters.getKey();
			SseParameters params = serverParameters.getValue();

			String baseUrl = params.url();
			String sseEndpoint = params.sseEndpoint() != null ? params.sseEndpoint() : "/sse";
			if (baseUrl == null || baseUrl.trim().isEmpty()) {
				throw new IllegalArgumentException("SSE connection '" + connectionName
						+ "' requires a 'url' property. Example: url: http://localhost:3000");
			}

			try {
				var transportBuilder = HttpClientSseClientTransport.builder(baseUrl)
					.sseEndpoint(sseEndpoint)
					.clientBuilder(HttpClient.newBuilder())
					.jsonMapper(McpJsonMapper.getDefault());

				asyncHttpRequestCustomizer.ifUnique(transportBuilder::asyncHttpRequestCustomizer);
				syncHttpRequestCustomizer.ifUnique(transportBuilder::httpRequestCustomizer);
				if (asyncHttpRequestCustomizer.getIfUnique() != null
						&& syncHttpRequestCustomizer.getIfUnique() != null) {
					logger.warn("Found beans of type %s and %s. Using %s.".formatted(
							McpAsyncHttpClientRequestCustomizer.class.getSimpleName(),
							McpSyncHttpClientRequestCustomizer.class.getSimpleName(),
							McpSyncHttpClientRequestCustomizer.class.getSimpleName()));
				}
				sseTransports.add(new NamedClientMcpTransport(connectionName, transportBuilder.build()));
			}
			catch (Exception e) {
				throw new IllegalArgumentException("Failed to create SSE transport for connection '" + connectionName
						+ "'. Check URL splitting: url='" + baseUrl + "', sse-endpoint='" + sseEndpoint
						+ "'. Full URL should be split as: url=http://host:port, sse-endpoint=/path/to/endpoint", e);
			}
		}

		return sseTransports;
	}

}
