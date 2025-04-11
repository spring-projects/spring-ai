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

package org.springframework.ai.mcp.client.autoconfigure;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpClientCommonProperties;

import java.util.List;

/**
 * Utility class for managing MCP (Model Context Protocol) clients using stdio transports.
 * <p>
 * This utility class provides methods to dynamically create and add MCP clients to existing
 * client collections at runtime. It supports both synchronous and asynchronous client creation
 * through the appropriate helper methods.
 * <p>
 * Key features:
 * <ul>
 *   <li>Dynamic creation of MCP clients with stdio transport</li>
 *   <li>Support for both synchronous and asynchronous client types</li>
 *   <li>Consistent configuration using common properties</li>
 *   <li>Runtime management of client connections</li>
 * </ul>
 * <p>
 * Example usage: <pre>{@code
 * // Obtain common properties and client lists
 * McpClientCommonProperties properties = // obtain properties
 * List<McpSyncClient> syncClients = // obtain sync client list
 *
 * // Add a new stdio connection
 * McpStdioUtils.addConnectionSync(properties, syncClients,
 *                               "search-service",
 *                               "/path/to/executable",
 *                               "--arg1", "--arg2");
 * }</pre>
 *
 * @author Changue Lim
 * @see McpSyncClient
 * @see McpAsyncClient
 * @see StdioClientTransport
 * @see ServerParameters
 */
public class McpClientStdioUtils {
	/**
	 * Adds a new MCP Sync Client connection to the existing list of clients.
	 * This method dynamically creates and configures a synchronous MCP client
	 * using a stdio transport, making it possible to add new connections at runtime.
	 *
	 * <p>The newly created client is configured with:
	 * <ul>
	 *   <li>A stdio transport using the specified command and arguments</li>
	 *   <li>Client information derived from common properties and the connection name</li>
	 *   <li>Request timeout from common properties</li>
	 * </ul>
	 *
	 * <p>If initialization is enabled in the common properties, the client will be initialized
	 * immediately before being added to the client list.
	 *
	 * @param mcpClientCommonProperties common MCP client configuration properties
	 *                                 used for timeout, name, version, and initialization settings
	 * @param mcpSyncClients the list of existing clients to which the new client will be added
	 * @param name a unique identifier for this connection (used as part of the client info name)
	 * @param command the command to execute for the stdio transport (e.g., path to executable)
	 * @param args variable number of command-line arguments to pass to the command
	 *
	 * @see McpSyncClient
	 * @see StdioClientTransport
	 * @see ServerParameters
	 */
	private static void addConnectionSync(McpClientCommonProperties mcpClientCommonProperties,
										  List<McpSyncClient> mcpSyncClients,
										  String name,
										  String command,
										  String... args) {
		final McpSyncClient syncClient = McpClient.sync(new StdioClientTransport(ServerParameters.builder(command).args(args).build()))
				.clientInfo(new McpSchema.Implementation(mcpClientCommonProperties.getName() + "-" + name, mcpClientCommonProperties.getVersion()))
				.requestTimeout(mcpClientCommonProperties.getRequestTimeout())
				.build();

		if(mcpClientCommonProperties.isInitialized())
			syncClient.initialize();

		mcpSyncClients.add(syncClient);
	}

	/**
	 * Adds a new MCP Async Client connection to the existing list of clients.
	 * This method dynamically creates and configures an asynchronous MCP client
	 * using a stdio transport, making it possible to add new connections at runtime.
	 *
	 * <p>The newly created client is configured with:
	 * <ul>
	 *   <li>A stdio transport using the specified command and arguments</li>
	 *   <li>Client information derived from common properties and the connection name</li>
	 *   <li>Request timeout from common properties</li>
	 * </ul>
	 *
	 * <p>If initialization is enabled in the common properties, the client will be initialized
	 * immediately before being added to the client list. For async clients, the initialization
	 * is performed by blocking on the Mono completion.
	 *
	 * @param mcpClientCommonProperties common MCP client configuration properties
	 *                                 used for timeout, name, version, and initialization settings
	 * @param mcpAsyncClients the list of existing clients to which the new client will be added
	 * @param name a unique identifier for this connection (used as part of the client info name)
	 * @param command the command to execute for the stdio transport (e.g., path to executable)
	 * @param args variable number of command-line arguments to pass to the command
	 *
	 * @see McpAsyncClient
	 * @see StdioClientTransport
	 * @see ServerParameters
	 */
	private static void addConnectionAsync(McpClientCommonProperties mcpClientCommonProperties,
										   List<McpAsyncClient> mcpAsyncClients,
										   String name,
										   String command,
										   String... args) {
		final McpAsyncClient asyncClient = McpClient.async(new StdioClientTransport(ServerParameters.builder(command).args(args).build()))
				.clientInfo(new McpSchema.Implementation(mcpClientCommonProperties.getName() + "-" + name, mcpClientCommonProperties.getVersion()))
				.requestTimeout(mcpClientCommonProperties.getRequestTimeout())
				.build();

		if(mcpClientCommonProperties.isInitialized())
			asyncClient.initialize().block();

		mcpAsyncClients.add(asyncClient);
	}
}
