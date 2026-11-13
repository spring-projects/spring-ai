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

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
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
 * <li>{@link #scoped} leaves the choice to a {@link SyncModernMcpClientScope}, for
 * example one that keeps a client for each user.</li>
 * </ul>
 * In all cases {@link SyncModernMcpToolCallbackProvider} keeps a listing the server marks
 * as {@code public} for as long as the server allows.
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
	 * Returns a client for what the application does for itself, outside of any request:
	 * a listen stream, or listing the tools, prompts or resources after the server
	 * reported a change. The caller keeps the client for as long as it needs it. By
	 * default this is {@link #client()}.
	 * @return the client
	 */
	default McpSyncClient applicationClient() {
		return this.client();
	}

	/**
	 * Whether a client of this connection serves one request only, so that nothing it
	 * keeps, such as a tool listing that the server marks as {@code private}, lasts
	 * beyond that request. {@link SyncModernMcpToolCallbackProvider} uses the answer to
	 * tell when such a listing could be kept with another kind of connection.
	 * @return {@code true} if {@link #client()} returns a new client each time,
	 * {@code false} by default
	 */
	default boolean clientPerRequest() {
		return false;
	}

	/**
	 * A connection that serves every request with the given client.
	 * @param info the connection info
	 * @param client the shared client
	 * @return the connection
	 */
	static SyncModernMcpConnection shared(McpModernConnectionInfo info, McpSyncClient client) {
		Assert.notNull(client, "client must not be null");
		return connection(info, () -> client, () -> client, false);
	}

	/**
	 * A connection that asks the given factory for a client each time {@link #client()}
	 * is called, to give each request its own client. A factory is expected to build a
	 * new client on each call, and the connection {@link #clientPerRequest() says so}. To
	 * keep clients, for example one for each user, use {@link #scoped}.
	 * @param info the connection info
	 * @param clientFactory creates the client for a request
	 * @return the connection
	 */
	static SyncModernMcpConnection perRequest(McpModernConnectionInfo info, Supplier<McpSyncClient> clientFactory) {
		Supplier<McpSyncClient> newClient = notNull(clientFactory);
		return connection(info, newClient, newClient, true);
	}

	/**
	 * A connection that asks the given scope for the client of each request. The scope
	 * gets the factory to build a client with, and it may keep the clients it builds, for
	 * example one for each user. A scope may serve several connections, and it must
	 * return a client that the factory of this connection built: a client of another
	 * connection, which would send the requests to the server of that connection, is
	 * rejected. {@link #applicationClient()} does not ask the scope: it returns a new
	 * client from the factory, so that the scope is never asked for a client when there
	 * is no request.
	 * @param info the connection info
	 * @param clientFactory builds a new client for the connection on each call
	 * @param clientScope selects the client for a request
	 * @return the connection
	 */
	static SyncModernMcpConnection scoped(McpModernConnectionInfo info, Supplier<McpSyncClient> clientFactory,
			SyncModernMcpClientScope clientScope) {
		Assert.notNull(info, "info must not be null");
		Assert.notNull(clientScope, "clientScope must not be null");
		Supplier<McpSyncClient> factory = notNull(clientFactory);
		// The clients of this connection, held weakly: a scope decides how long it keeps
		// a client.
		Set<McpSyncClient> built = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
		Supplier<McpSyncClient> newClient = () -> {
			McpSyncClient client = factory.get();
			built.add(client);
			return client;
		};
		return connection(info, () -> {
			McpSyncClient client = clientScope.client(info, newClient);
			Assert.state(client != null, "clientScope must not return null");
			Assert.state(built.contains(client),
					() -> "The SyncModernMcpClientScope returned a client that was not built for the MCP connection '"
							+ info.name() + "'. A scope that keeps clients must keep them for each connection, for "
							+ "example under a key that contains the name of the connection.");
			return client;
		}, newClient, false);
	}

	private static SyncModernMcpConnection connection(McpModernConnectionInfo info, Supplier<McpSyncClient> client,
			Supplier<McpSyncClient> applicationClient, boolean clientPerRequest) {
		Assert.notNull(info, "info must not be null");
		return new SyncModernMcpConnection() {

			@Override
			public McpModernConnectionInfo info() {
				return info;
			}

			@Override
			public McpSyncClient client() {
				return client.get();
			}

			@Override
			public McpSyncClient applicationClient() {
				return applicationClient.get();
			}

			@Override
			public boolean clientPerRequest() {
				return clientPerRequest;
			}

		};
	}

	/**
	 * A factory that fails if the given one returns no client.
	 */
	private static Supplier<McpSyncClient> notNull(Supplier<McpSyncClient> clientFactory) {
		Assert.notNull(clientFactory, "clientFactory must not be null");
		return () -> {
			McpSyncClient client = clientFactory.get();
			Assert.state(client != null, "clientFactory must not return null");
			return client;
		};
	}

}
