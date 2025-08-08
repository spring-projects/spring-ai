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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.AsyncHttpRequestCustomizer;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.SyncHttpRequestCustomizer;
import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
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
 * transport when WebFlux is not available. It provides HTTP client-based SSE transport
 * implementation for MCP client communication.
 *
 * <p>
 * The configuration is activated after the WebFlux SSE transport auto-configuration to
 * ensure proper fallback behavior when WebFlux is not available.
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
	 * @param sseProperties the SSE client properties containing server configurations
	 * @param objectMapperProvider the provider for ObjectMapper or a new instance if not
	 * available
	 * @param syncHttpRequestCustomizer provider for {@link SyncHttpRequestCustomizer} if
	 * available
	 * @param asyncHttpRequestCustomizer provider fo {@link AsyncHttpRequestCustomizer} if
	 * available
	 * @return list of named MCP transports
	 */
	@Bean
	public List<NamedClientMcpTransport> sseHttpClientTransports(McpSseClientProperties sseProperties,
			ObjectProvider<ObjectMapper> objectMapperProvider,
			ObjectProvider<SyncHttpRequestCustomizer> syncHttpRequestCustomizer,
			ObjectProvider<AsyncHttpRequestCustomizer> asyncHttpRequestCustomizer) {

		ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);

		List<NamedClientMcpTransport> sseTransports = new ArrayList<>();

		for (Map.Entry<String, SseParameters> serverParameters : sseProperties.getConnections().entrySet()) {

			String baseUrl = serverParameters.getValue().url();
			String sseEndpoint = serverParameters.getValue().sseEndpoint() != null
					? serverParameters.getValue().sseEndpoint() : "/sse";
			HttpClientSseClientTransport.Builder transportBuilder = HttpClientSseClientTransport.builder(baseUrl)
				.sseEndpoint(sseEndpoint)
				.clientBuilder(HttpClient.newBuilder())
				.objectMapper(objectMapper);

			asyncHttpRequestCustomizer.ifUnique(transportBuilder::asyncHttpRequestCustomizer);
			syncHttpRequestCustomizer.ifUnique(transportBuilder::httpRequestCustomizer);
			if (asyncHttpRequestCustomizer.getIfUnique() != null && syncHttpRequestCustomizer.getIfUnique() != null) {
				logger.warn("Found beans of type %s and %s. Using %s.".formatted(
						AsyncHttpRequestCustomizer.class.getSimpleName(),
						SyncHttpRequestCustomizer.class.getSimpleName(),
						SyncHttpRequestCustomizer.class.getSimpleName()));
			}

			HttpClientSseClientTransport transport = transportBuilder.build();
			sseTransports.add(new NamedClientMcpTransport(serverParameters.getKey(), transport));
		}

		return sseTransports;
	}

}
