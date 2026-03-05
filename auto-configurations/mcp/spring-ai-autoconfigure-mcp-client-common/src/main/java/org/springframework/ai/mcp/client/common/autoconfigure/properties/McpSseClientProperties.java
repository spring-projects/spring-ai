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
 * Configuration properties for Server-Sent Events (SSE) based MCP client connections.
 *
 * <p>
 * These properties allow configuration of multiple named SSE connections to MCP servers.
 * Each connection is configured with a URL endpoint for SSE communication.
 *
 * <p>
 * Example configurations: <pre>
 * # Simple configuration with default SSE endpoint (/sse)
 * spring.ai.mcp.client.sse:
 *   connections:
 *     server1:
 *       url: http://localhost:8080
 *
 * # Custom SSE endpoints - split complex URLs correctly
 * spring.ai.mcp.client.sse:
 *   connections:
 *     mcp-hub:
 *       url: http://localhost:3000
 *       sse-endpoint: /mcp-hub/sse/cf9ec4527e3c4a2cbb149a85ea45ab01
 *     custom-server:
 *       url: http://api.example.com
 *       sse-endpoint: /v1/mcp/events?token=abc123&format=json
 *
 * # How to split a full URL:
 * # Full URL: http://localhost:3000/mcp-hub/sse/token123
 * # Split as:  url: http://localhost:3000
 * #           sse-endpoint: /mcp-hub/sse/token123
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

	/**
	 * Parameters for configuring an SSE connection to an MCP server.
	 *
	 * @param url the URL endpoint for SSE communication with the MCP server
	 * @param sseEndpoint the SSE endpoint for the MCP server
	 */
	public record SseParameters(@Nullable String url, @Nullable String sseEndpoint) {
	}

}
