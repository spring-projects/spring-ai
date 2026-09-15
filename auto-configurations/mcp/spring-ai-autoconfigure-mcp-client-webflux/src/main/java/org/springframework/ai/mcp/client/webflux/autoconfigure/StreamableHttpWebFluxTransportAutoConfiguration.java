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

package org.springframework.ai.mcp.client.webflux.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties.ConnectionParameters;
import org.springframework.ai.mcp.client.webflux.transport.WebClientStreamableHttpTransport;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
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
 * <li>Sets up JsonMapper for JSON serialization/deserialization
 * <li>Supports multiple named server connections with different base URLs
 * <li>Applies {@link McpClientCustomizer<WebClientStreamableHttpTransport.Builder>} beans
 * to each transport builder.
 * </ul>
 *
 * @see WebClientStreamableHttpTransport
 * @see McpStreamableHttpClientProperties
 */
@AutoConfiguration
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
	 * <li>A cloned WebClient.Builder with server-specific base URL
	 * <li>JsonMapper for JSON processing
	 * <li>Server connection parameters from properties
	 * </ul>
	 * @param streamableProperties the Streamable HTTP client properties containing server
	 * configurations
	 * @param webClientBuilderProvider the provider for WebClient.Builder
	 * @param jsonMapperProvider the provider for JsonMapper or a new instance if not
	 * available
	 * @param transportCustomizers provider for
	 * {@link McpClientCustomizer<WebClientStreamableHttpTransport.Builder>} beans
	 * @return list of named MCP transports
	 */
	@Bean
	public List<NamedClientMcpTransport> streamableHttpWebFluxClientTransports(
			McpStreamableHttpClientProperties streamableProperties,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider, ObjectProvider<JsonMapper> jsonMapperProvider,
			ObjectProvider<McpClientCustomizer<WebClientStreamableHttpTransport.Builder>> transportCustomizers) {

		List<NamedClientMcpTransport> streamableHttpTransports = new ArrayList<>();

		var webClientBuilderTemplate = webClientBuilderProvider.getIfAvailable(WebClient::builder);
		var jsonMapper = jsonMapperProvider.getIfAvailable(JsonMapper::new);

		for (Map.Entry<String, ConnectionParameters> serverParameters : streamableProperties.getConnections()
			.entrySet()) {
			String connectionName = serverParameters.getKey();
			String url = Objects.requireNonNull(serverParameters.getValue().url(),
					"Missing url for server named " + connectionName);
			var webClientBuilder = webClientBuilderTemplate.clone().baseUrl(url);
			String streamableHttpEndpoint = Objects.requireNonNullElse(serverParameters.getValue().endpoint(), "/mcp");

			var transportBuilder = WebClientStreamableHttpTransport.builder(webClientBuilder)
				.endpoint(streamableHttpEndpoint)
				.jsonMapper(new JacksonMcpJsonMapper(jsonMapper));

			for (McpClientCustomizer<WebClientStreamableHttpTransport.Builder> customizer : transportCustomizers) {
				customizer.customize(connectionName, transportBuilder);
			}

			streamableHttpTransports.add(new NamedClientMcpTransport(connectionName, transportBuilder.build()));
		}

		return streamableHttpTransports;
	}

}
