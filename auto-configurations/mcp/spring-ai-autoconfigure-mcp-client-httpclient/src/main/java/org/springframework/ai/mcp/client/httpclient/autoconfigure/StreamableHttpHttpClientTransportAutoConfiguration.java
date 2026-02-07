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
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpAsyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties.ConnectionParameters;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.log.LogAccessor;

/**
 * Auto-configuration for Streamable HTTP client transport in the Model Context Protocol
 * (MCP).
 *
 * <p>
 * This configuration class sets up the necessary beans for Streamable HTTP client
 * transport. It provides HTTP client-based Streamable HTTP transport implementation for
 * MCP client communication.
 *
 * <p>
 * Key features:
 * <ul>
 * <li>Creates HTTP client-based Streamable HTTP transports for configured MCP server
 * connections
 * <li>Configures ObjectMapper for JSON serialization/deserialization
 * <li>Supports multiple named server connections with different URLs
 * <li>Adds a sync or async HTTP request customizer. Sync takes precedence.
 * </ul>
 *
 * @see HttpClientStreamableHttpTransport
 * @see McpStreamableHttpClientProperties
 */
@AutoConfiguration
@ConditionalOnClass({ McpSchema.class, McpSyncClient.class })
@EnableConfigurationProperties({ McpStreamableHttpClientProperties.class, McpClientCommonProperties.class })
@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class StreamableHttpHttpClientTransportAutoConfiguration {

	private static final LogAccessor logger = new LogAccessor(StreamableHttpHttpClientTransportAutoConfiguration.class);

	/**
	 * Creates a list of HTTP client-based Streamable HTTP transports for MCP
	 * communication.
	 *
	 * <p>
	 * Each transport is configured with:
	 * <ul>
	 * <li>A new HttpClient instance
	 * <li>Server URL from properties
	 * <li>ObjectMapper for JSON processing
	 * </ul>
	 * @param streamableProperties the Streamable HTTP client properties containing server
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
	public List<NamedClientMcpTransport> streamableHttpHttpClientTransports(
			McpStreamableHttpClientProperties streamableProperties,
			ObjectProvider<McpSyncHttpClientRequestCustomizer> syncHttpRequestCustomizer,
			ObjectProvider<McpAsyncHttpClientRequestCustomizer> asyncHttpRequestCustomizer) {

		List<NamedClientMcpTransport> streamableHttpTransports = new ArrayList<>();

		for (Map.Entry<String, ConnectionParameters> serverParameters : streamableProperties.getConnections()
			.entrySet()) {

			String baseUrl = serverParameters.getValue().url();
			String streamableHttpEndpoint = serverParameters.getValue().endpoint() != null
					? serverParameters.getValue().endpoint() : "/mcp";

			HttpClientStreamableHttpTransport.Builder transportBuilder = HttpClientStreamableHttpTransport
				.builder(baseUrl)
				.endpoint(streamableHttpEndpoint)
				.clientBuilder(HttpClient.newBuilder())
				.jsonMapper(McpJsonMapper.getDefault());

			asyncHttpRequestCustomizer.ifUnique(transportBuilder::asyncHttpRequestCustomizer);
			syncHttpRequestCustomizer.ifUnique(transportBuilder::httpRequestCustomizer);
			if (asyncHttpRequestCustomizer.getIfUnique() != null && syncHttpRequestCustomizer.getIfUnique() != null) {
				logger.warn("Found beans of type %s and %s. Using %s.".formatted(
						McpAsyncHttpClientRequestCustomizer.class.getSimpleName(),
						McpSyncHttpClientRequestCustomizer.class.getSimpleName(),
						McpSyncHttpClientRequestCustomizer.class.getSimpleName()));
			}

			HttpClientStreamableHttpTransport transport = transportBuilder.build();

			streamableHttpTransports.add(new NamedClientMcpTransport(serverParameters.getKey(), transport));
		}

		return streamableHttpTransports;
	}

}
