/*
* Copyright 2024 - 2024 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springframework.ai.autoconfigure.mcp.client.properties;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Server-Sent Events (SSE) based MCP client connections.
 *
 * <p>
 * These properties allow configuration of multiple named SSE connections to MCP servers.
 * Each connection is configured with a URL endpoint for SSE communication.
 *
 * <p>
 * Example configuration: <pre>
 * spring.ai.mcp.client.sse:
 *   connections:
 *     server1:
 *       url: http://localhost:8080/events
 *     server2:
 *       url: http://otherserver:8081/events
 * </pre>
 *
 * @author Christian Tzolov
 * @since 1.0.0
 * @see SseParameters
 */
@ConfigurationProperties(McpSseClientProperties.CONFIG_PREFIX)
public class McpSseClientProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mcp.client.sse";

	/**
	 * Parameters for configuring an SSE connection to an MCP server.
	 *
	 * @param url the URL endpoint for SSE communication with the MCP server
	 */
	public record SseParameters(String url) {
	}

	/**
	 * Map of named SSE connection configurations.
	 * <p>
	 * The key represents the connection name, and the value contains the SSE parameters
	 * for that connection.
	 */
	private final Map<String, SseParameters> connections = new HashMap<>();

	/**
	 * Returns the map of configured SSE connections.
	 * @return map of connection names to their SSE parameters
	 */
	public Map<String, SseParameters> getConnections() {
		return this.connections;
	}

}
