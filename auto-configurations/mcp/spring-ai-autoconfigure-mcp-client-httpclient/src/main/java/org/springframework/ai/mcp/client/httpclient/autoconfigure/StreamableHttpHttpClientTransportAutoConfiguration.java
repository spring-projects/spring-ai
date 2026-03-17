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

package org.springframework.ai.mcp.client.httpclient.autoconfigure;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties.ConnectionParameters;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

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
 * <li>Configures JsonMapper for JSON serialization/deserialization
 * <li>Supports multiple named server connections with different URLs
 * <li>Applies {@link McpClientCustomizer<HttpClientStreamableHttpTransport.Builder>}
 * beans to each transport builder.
 * </ul>
 *
 * @see HttpClientStreamableHttpTransport
 * @see McpStreamableHttpClientProperties
 */
@AutoConfiguration
@EnableConfigurationProperties({ McpStreamableHttpClientProperties.class, McpClientCommonProperties.class })
@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class StreamableHttpHttpClientTransportAutoConfiguration {

	/**
	 * Creates a list of HTTP client-based Streamable HTTP transports for MCP
	 * communication.
	 *
	 * <p>
	 * Each transport is configured with:
	 * <ul>
	 * <li>A new HttpClient instance
	 * <li>Server URL from properties
	 * <li>JsonMapper for JSON processing
	 * <li>All available
	 * {@link McpClientCustomizer<HttpClientStreamableHttpTransport.Builder>} beans
	 * applied with the connection name and transport builder
	 * </ul>
	 * @param streamableProperties the Streamable HTTP client properties containing server
	 * configurations
	 * @param jsonMapperProvider the provider for JsonMapper or a new instance if not
	 * available
	 * @param transportCustomizers provider for
	 * {@link McpClientCustomizer<HttpClientStreamableHttpTransport.Builder>} beans
	 * @return list of named MCP transports
	 */
	@Bean
	public List<NamedClientMcpTransport> streamableHttpHttpClientTransports(
			McpStreamableHttpClientProperties streamableProperties, ObjectProvider<JsonMapper> jsonMapperProvider,
			ObjectProvider<McpClientCustomizer<HttpClientStreamableHttpTransport.Builder>> transportCustomizers) {

		JsonMapper jsonMapper = jsonMapperProvider.getIfAvailable(JsonMapper::shared);

		List<NamedClientMcpTransport> streamableHttpTransports = new ArrayList<>();

		for (Map.Entry<String, ConnectionParameters> serverParameters : streamableProperties.getConnections()
			.entrySet()) {

			String name = serverParameters.getKey();
			String baseUrl = serverParameters.getValue().url();
			String streamableHttpEndpoint = serverParameters.getValue().endpoint() != null
					? serverParameters.getValue().endpoint() : "/mcp";

			HttpClientStreamableHttpTransport.Builder transportBuilder = HttpClientStreamableHttpTransport
				.builder(baseUrl)
				.endpoint(streamableHttpEndpoint)
				.clientBuilder(HttpClient.newBuilder())
				.jsonMapper(new JacksonMcpJsonMapper(jsonMapper));

			for (McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> customizer : transportCustomizers) {
				customizer.customize(name, transportBuilder);
			}

			HttpClientStreamableHttpTransport transport = transportBuilder.build();

			streamableHttpTransports.add(new NamedClientMcpTransport(name, transport));
		}

		return streamableHttpTransports;
	}

}
