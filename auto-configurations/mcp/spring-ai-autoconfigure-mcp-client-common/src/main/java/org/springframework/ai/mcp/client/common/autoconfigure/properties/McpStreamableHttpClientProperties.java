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

package org.springframework.ai.mcp.client.common.autoconfigure.properties;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Streamable Http client connections.
 *
 * <p>
 * These properties allow configuration of multiple named Streamable Http connections to
 * MCP servers. Each connection is configured with a URL endpoint for communication.
 *
 * <p>
 * Example configuration: <pre>
 * spring.ai.mcp.client.streamable-http:
 *   connections:
 *     server1:
 *       url: http://localhost:8080/events
 *     server2:
 *       url: http://otherserver:8081/events
 * </pre>
 *
 * @author Christian Tzolov
 * @see ConnectionParameters
 */
@ConfigurationProperties(McpStreamableHttpClientProperties.CONFIG_PREFIX)
public class McpStreamableHttpClientProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mcp.client.streamable-http";

	/**
	 * Map of named Streamable Http connection configurations.
	 * <p>
	 * The key represents the connection name, and the value contains the Streamable Http
	 * parameters for that connection.
	 */
	private final Map<String, ConnectionParameters> connections = new HashMap<>();

	/**
	 * Returns the map of configured Streamable Http connections.
	 * @return map of connection names to their Streamable Http parameters
	 */
	public Map<String, ConnectionParameters> getConnections() {
		return this.connections;
	}

	/**
	 * Parameters for configuring an Streamable Http connection to an MCP server.
	 *
	 * @param url the URL endpoint for Streamable Http communication with the MCP server
	 * @param endpoint the endpoint for the MCP server
	 */
	public record ConnectionParameters(@Nullable String url, @Nullable String endpoint) {
	}

}
