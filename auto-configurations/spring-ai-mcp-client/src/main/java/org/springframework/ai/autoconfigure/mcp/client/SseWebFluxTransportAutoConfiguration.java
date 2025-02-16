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

package org.springframework.ai.autoconfigure.mcp.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;

import org.springframework.ai.autoconfigure.mcp.client.properties.McpClientCommonProperties;
import org.springframework.ai.autoconfigure.mcp.client.properties.McpSseClientProperties;
import org.springframework.ai.autoconfigure.mcp.client.properties.McpSseClientProperties.SseParameters;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
	 * @param sseProperties the SSE client properties containing server configurations
	 * @param webClientBuilderTemplate the template WebClient.Builder to clone for each
	 * connection
	 * @param objectMapper the ObjectMapper for JSON serialization/deserialization
	 * @return list of named MCP transports
	 */
	@Bean
	public List<NamedClientMcpTransport> webFluxClientTransports(McpSseClientProperties sseProperties,
			WebClient.Builder webClientBuilderTemplate, ObjectMapper objectMapper) {

		List<NamedClientMcpTransport> sseTransports = new ArrayList<>();

		for (Map.Entry<String, SseParameters> serverParameters : sseProperties.getConnections().entrySet()) {
			var webClientBuilder = webClientBuilderTemplate.clone().baseUrl(serverParameters.getValue().url());
			var transport = new WebFluxSseClientTransport(webClientBuilder, objectMapper);
			sseTransports.add(new NamedClientMcpTransport(serverParameters.getKey(), transport));
		}

		return sseTransports;
	}

	/**
	 * Creates the default WebClient.Builder if none is provided.
	 *
	 * <p>
	 * This builder serves as a template for creating server-specific WebClient instances
	 * used in SSE transport implementation.
	 * @return the configured WebClient.Builder instance
	 */
	@Bean
	@ConditionalOnMissingBean
	public WebClient.Builder webClientBuilder() {
		return WebClient.builder();
	}

	/**
	 * Creates the default ObjectMapper if none is provided.
	 *
	 * <p>
	 * This ObjectMapper is used for JSON serialization and deserialization in the SSE
	 * transport implementation.
	 * @return the configured ObjectMapper instance
	 */
	@Bean
	@ConditionalOnMissingBean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

}
