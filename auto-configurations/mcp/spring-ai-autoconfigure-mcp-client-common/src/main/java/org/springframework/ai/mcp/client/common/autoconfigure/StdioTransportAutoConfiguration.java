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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStdioClientProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Standard Input/Output (stdio) transport in the Model Context
 * Protocol (MCP).
 *
 * <p>
 * This configuration class sets up the necessary beans for stdio-based transport,
 * enabling communication with MCP servers through standard input and output streams.
 *
 * <p>
 * Key features:
 * <ul>
 * <li>Creates stdio transports for configured MCP server connections
 * <li>Supports multiple named server connections with different parameters
 * <li>Configures transport with server-specific parameters
 * </ul>
 *
 * @see StdioClientTransport
 * @see McpStdioClientProperties
 */
@AutoConfiguration
@ConditionalOnClass(McpSchema.class)
@EnableConfigurationProperties({ McpStdioClientProperties.class, McpClientCommonProperties.class })
@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class StdioTransportAutoConfiguration {

	/**
	 * Creates a list of stdio-based transports for MCP communication.
	 *
	 * <p>
	 * Each transport is configured with:
	 * <ul>
	 * <li>Server-specific parameters from properties
	 * <li>Unique connection name for identification
	 * </ul>
	 * @param stdioProperties the stdio client properties containing server configurations
	 * @return list of named MCP transports
	 */
	@Bean
	public List<NamedClientMcpTransport> stdioTransports(McpStdioClientProperties stdioProperties,
			ObjectProvider<ObjectMapper> objectMapperProvider) {

		ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);

		List<NamedClientMcpTransport> stdioTransports = new ArrayList<>();

		for (Map.Entry<String, ServerParameters> serverParameters : stdioProperties.toServerParameters().entrySet()) {
			var transport = new StdioClientTransport(serverParameters.getValue(),
					new JacksonMcpJsonMapper(objectMapper));
			stdioTransports.add(new NamedClientMcpTransport(serverParameters.getKey(), transport));

		}

		return stdioTransports;
	}

}
