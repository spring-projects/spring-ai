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

package org.springframework.ai.mcp.modern.client;

import java.util.function.Supplier;

import io.modelcontextprotocol.modern.client.McpSyncClient;

import org.springframework.util.Assert;

/**
 * A connection to one modern MCP server, and the rule that decides which
 * {@link McpSyncClient} serves a request.
 * <p>
 * A modern client is cheap to create and does no I/O when built, so an application may
 * hold one client for all requests or create a client for each request:
 * <ul>
 * <li>{@link #perRequest} returns a new client each time. A client keeps nothing from one
 * request to the next, so a tool listing the server marks as {@code private} is never
 * handed to another caller.</li>
 * <li>{@link #shared} always returns the same client. That client keeps the server's tool
 * listing for as long as the server allows, even a {@code private} one. It fits an
 * application that calls the server under a single identity.</li>
 * </ul>
 * In both cases {@link SyncModernMcpToolCallbackProvider} keeps a listing the server
 * marks as {@code public} for as long as the server allows.
 * <p>
 * {@link SyncModernMcpToolCallbackProvider} asks for the client once for each call to
 * {@code getToolCallbacks()} and binds the returned tool callbacks to it. Listing and
 * calling a tool therefore use the same client.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public interface SyncModernMcpConnection {

	/**
	 * Describes the connection.
	 * @return the connection info
	 */
	McpModernConnectionInfo info();

	/**
	 * Returns the client that serves the current request.
	 * @return the client
	 */
	McpSyncClient client();

	/**
	 * A connection that serves every request with the given client.
	 * @param info the connection info
	 * @param client the shared client
	 * @return the connection
	 */
	static SyncModernMcpConnection shared(McpModernConnectionInfo info, McpSyncClient client) {
		Assert.notNull(client, "client must not be null");
		return perRequest(info, () -> client);
	}

	/**
	 * A connection that asks the given factory for a client each time {@link #client()}
	 * is called. A factory that builds a new client on each call gives each request its
	 * own client.
	 * @param info the connection info
	 * @param clientFactory creates or selects the client for a request
	 * @return the connection
	 */
	static SyncModernMcpConnection perRequest(McpModernConnectionInfo info, Supplier<McpSyncClient> clientFactory) {
		Assert.notNull(info, "info must not be null");
		Assert.notNull(clientFactory, "clientFactory must not be null");
		return new SyncModernMcpConnection() {

			@Override
			public McpModernConnectionInfo info() {
				return info;
			}

			@Override
			public McpSyncClient client() {
				McpSyncClient client = clientFactory.get();
				Assert.state(client != null, "clientFactory must not return null");
				return client;
			}

		};
	}

}
