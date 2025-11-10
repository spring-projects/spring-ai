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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;

import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.ai.mcp.client.common.autoconfigure.WebClientFactory;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties.ConnectionParameters;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration for WebFlux-based Streamable HTTP client transport in the Model
 * Context Protocol (MCP).
 *
 * <p>
 * This configuration class sets up the necessary beans for Streamable HTTP-based WebFlux
 * transport, providing reactive transport implementation for MCP client communication
 * when WebFlux is available on the classpath.
 *
 * <p>
 * Key features:
 * <ul>
 * <li>Creates WebFlux-based Streamable HTTP transports for configured MCP server
 * connections
 * <li>Configures WebClient.Builder for HTTP client operations
 * <li>Sets up ObjectMapper for JSON serialization/deserialization
 * <li>Supports multiple named server connections with different base URLs
 * </ul>
 *
 * @see WebClientStreamableHttpTransport
 * @see McpStreamableHttpClientProperties
 */
@AutoConfiguration(after = DefaultWebClientFactory.class)
@ConditionalOnClass({ WebClientStreamableHttpTransport.class, WebClient.class })
@EnableConfigurationProperties({ McpStreamableHttpClientProperties.class, McpClientCommonProperties.class })
@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class StreamableHttpWebFluxTransportAutoConfiguration {

	/**
	 * Creates a list of WebFlux-based Streamable HTTP transports for MCP communication.
	 *
	 * <p>
	 * Each transport is configured with:
	 * <ul>
	 * <li>A WebClient.Builder created per connection name via WebClientFactory
	 * <li>ObjectMapper for JSON processing
	 * <li>Server connection parameters from properties
	 * </ul>
	 * @param streamableProperties the Streamable HTTP client properties containing server
	 * configurations
	 * @param webClientFactory the factory for creating WebClient.Builder instances per
	 * connection name
	 * @param objectMapperProvider the provider for ObjectMapper or a new instance if not
	 * available
	 * @return list of named MCP transports
	 */
	@Bean
	public List<NamedClientMcpTransport> streamableHttpWebFluxClientTransports(
			McpStreamableHttpClientProperties streamableProperties, WebClientFactory webClientFactory,
			ObjectProvider<ObjectMapper> objectMapperProvider) {

		List<NamedClientMcpTransport> streamableHttpTransports = new ArrayList<>();

		var objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);

		for (Map.Entry<String, ConnectionParameters> serverParameters : streamableProperties.getConnections()
			.entrySet()) {
			String connectionName = serverParameters.getKey();
			var webClientBuilder = webClientFactory.create(connectionName).baseUrl(serverParameters.getValue().url());
			String streamableHttpEndpoint = serverParameters.getValue().endpoint() != null
					? serverParameters.getValue().endpoint() : "/mcp";

			var transport = WebClientStreamableHttpTransport.builder(webClientBuilder)
				.endpoint(streamableHttpEndpoint)
				.jsonMapper(new JacksonMcpJsonMapper(objectMapper))
				.build();

			streamableHttpTransports.add(new NamedClientMcpTransport(connectionName, transport));
		}

		return streamableHttpTransports;
	}

}
