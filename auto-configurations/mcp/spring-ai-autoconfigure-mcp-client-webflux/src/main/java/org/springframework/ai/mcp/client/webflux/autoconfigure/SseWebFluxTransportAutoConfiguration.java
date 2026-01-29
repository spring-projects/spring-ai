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

package org.springframework.ai.mcp.client.webflux.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;

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
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration for WebFlux-based Server-Sent Events (SSE) client transport in the
 * Model Context Protocol (MCP).
 *
 * <p>
 * This configuration class sets up the necessary beans for SSE-based WebFlux transport,
 * providing reactive transport implementation for MCP client communication when WebFlux
 * is available on the classpath.
 *
 * <p>
 * Key features:
 * <ul>
 * <li>Creates WebFlux-based SSE transports for configured MCP server connections
 * <li>Configures WebClient.Builder for HTTP client operations
 * <li>Sets up ObjectMapper for JSON serialization/deserialization
 * <li>Supports multiple named server connections with different base URLs
 * </ul>
 *
 * @see WebFluxSseClientTransport
 * @see McpSseClientProperties
 */
@AutoConfiguration
@ConditionalOnClass(WebFluxSseClientTransport.class)
@EnableConfigurationProperties({ McpSseClientProperties.class, McpClientCommonProperties.class })
@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class SseWebFluxTransportAutoConfiguration {

	@Bean
	PropertiesMcpSseClientConnectionDetails mcpSseClientConnectionDetails(McpSseClientProperties sseProperties) {
		return new PropertiesMcpSseClientConnectionDetails(sseProperties);
	}

	/**
	 * Creates a list of WebFlux-based SSE transports for MCP communication.
	 *
	 * <p>
	 * Each transport is configured with:
	 * <ul>
	 * <li>A cloned WebClient.Builder with server-specific base URL
	 * <li>ObjectMapper for JSON processing
	 * <li>Server connection parameters from properties
	 * </ul>
	 * @param connectionDetails the SSE client properties containing server configurations
	 * @param webClientBuilderProvider the provider for WebClient.Builder
	 * @param objectMapperProvider the provider for ObjectMapper or a new instance if not
	 * available
	 * @return list of named MCP transports
	 */
	@Bean
	public List<NamedClientMcpTransport> sseWebFluxClientTransports(McpSseClientConnectionDetails connectionDetails,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider,
			ObjectProvider<ObjectMapper> objectMapperProvider) {

		List<NamedClientMcpTransport> sseTransports = new ArrayList<>();

		var webClientBuilderTemplate = webClientBuilderProvider.getIfAvailable(WebClient::builder);
		var objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);

		for (Map.Entry<String, SseParameters> serverParameters : connectionDetails.getConnections().entrySet()) {
			String url = Objects.requireNonNull(serverParameters.getValue().url(),
					"Missing url for server named " + serverParameters.getKey());
			var webClientBuilder = webClientBuilderTemplate.clone().baseUrl(url);
			String sseEndpoint = Objects.requireNonNullElse(serverParameters.getValue().sseEndpoint(), "/sse");
			var transport = WebFluxSseClientTransport.builder(webClientBuilder)
				.sseEndpoint(sseEndpoint)
				.jsonMapper(new JacksonMcpJsonMapper(objectMapper))
				.build();
			sseTransports.add(new NamedClientMcpTransport(serverParameters.getKey(), transport));
		}

		return sseTransports;
	}

}
