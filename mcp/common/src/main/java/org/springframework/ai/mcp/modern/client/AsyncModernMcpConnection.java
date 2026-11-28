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

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.client.McpAsyncClient;

import org.springframework.util.Assert;

/**
 * A connection to one modern MCP server, and the rule that decides which
 * {@link McpAsyncClient} serves a request.
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
 * <li>{@link #scoped} leaves the choice to a {@link AsyncModernMcpClientScope}, for
 * example one that keeps a client for each user.</li>
 * </ul>
 * In all cases {@link AsyncModernMcpToolCallbackProvider} keeps a listing the server
 * marks as {@code public} for as long as the server allows.
 * <p>
 * {@link AsyncModernMcpToolCallbackProvider} asks for the client once for each call to
 * {@code getToolCallbacks()} and binds the returned tool callbacks to it. Listing and
 * calling a tool therefore use the same client.
 * <p>
 * An asynchronous client holds no caller. The caller of a request is the
 * {@link #transportContext() transport context} of the connection, which the provider
 * also asks for once for each call to {@code getToolCallbacks()}, and which goes with the
 * listing and with the tool calls of that request.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public interface AsyncModernMcpConnection {

	/**
	 * Describes the connection.
	 * @return the connection info
	 */
	McpModernConnectionInfo info();

	/**
	 * Returns the client that serves the current request.
	 * @return the client
	 */
	McpAsyncClient client();

	/**
	 * Returns a client for what the application does for itself, outside of any request:
	 * a listen stream, or listing the tools, prompts or resources after the server
	 * reported a change. The caller keeps the client for as long as it needs it. By
	 * default this is {@link #client()}.
	 * @return the client
	 */
	default McpAsyncClient applicationClient() {
		return this.client();
	}

	/**
	 * Whether a client of this connection serves one request only, so that nothing it
	 * keeps, such as a tool listing that the server marks as {@code private}, lasts
	 * beyond that request. {@link AsyncModernMcpToolCallbackProvider} uses the answer to
	 * tell when such a listing could be kept with another kind of connection.
	 * @return {@code true} if {@link #client()} returns a new client each time,
	 * {@code false} by default
	 */
	default boolean clientPerRequest() {
		return false;
	}

	/**
	 * Returns the transport context of the caller that the current thread works for. It
	 * is read on the thread that resolves the tools, as a blocking call has no Reactor
	 * context to read the caller from.
	 * @return the transport context, {@link McpTransportContext#EMPTY} by default
	 */
	default McpTransportContext transportContext() {
		return McpTransportContext.EMPTY;
	}

	/**
	 * Returns a connection that is this one, with the caller of each request supplied by
	 * the given provider.
	 * @param transportContextProvider supplies the transport context of the caller
	 * @return the connection
	 */
	default AsyncModernMcpConnection withTransportContext(
			AsyncModernMcpTransportContextProvider transportContextProvider) {
		Assert.notNull(transportContextProvider, "transportContextProvider must not be null");
		AsyncModernMcpConnection connection = this;
		return new AsyncModernMcpConnection() {

			@Override
			public McpModernConnectionInfo info() {
				return connection.info();
			}

			@Override
			public McpAsyncClient client() {
				return connection.client();
			}

			@Override
			public McpAsyncClient applicationClient() {
				return connection.applicationClient();
			}

			@Override
			public boolean clientPerRequest() {
				return connection.clientPerRequest();
			}

			@Override
			public McpTransportContext transportContext() {
				McpTransportContext transportContext = transportContextProvider.transportContext(connection.info());
				Assert.state(transportContext != null, "transportContextProvider must not return null");
				return transportContext;
			}

		};
	}

	/**
	 * A connection that serves every request with the given client.
	 * @param info the connection info
	 * @param client the shared client
	 * @return the connection
	 */
	static AsyncModernMcpConnection shared(McpModernConnectionInfo info, McpAsyncClient client) {
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
	static AsyncModernMcpConnection perRequest(McpModernConnectionInfo info, Supplier<McpAsyncClient> clientFactory) {
		Supplier<McpAsyncClient> newClient = notNull(clientFactory);
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
	static AsyncModernMcpConnection scoped(McpModernConnectionInfo info, Supplier<McpAsyncClient> clientFactory,
			AsyncModernMcpClientScope clientScope) {
		Assert.notNull(info, "info must not be null");
		Assert.notNull(clientScope, "clientScope must not be null");
		Supplier<McpAsyncClient> factory = notNull(clientFactory);
		// The clients of this connection, held weakly: a scope decides how long it keeps
		// a client.
		Set<McpAsyncClient> built = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
		Supplier<McpAsyncClient> newClient = () -> {
			McpAsyncClient client = factory.get();
			built.add(client);
			return client;
		};
		return connection(info, () -> {
			McpAsyncClient client = clientScope.client(info, newClient);
			Assert.state(client != null, "clientScope must not return null");
			Assert.state(built.contains(client),
					() -> "The AsyncModernMcpClientScope returned a client that was not built for the MCP connection '"
							+ info.name() + "'. A scope that keeps clients must keep them for each connection, for "
							+ "example under a key that contains the name of the connection.");
			return client;
		}, newClient, false);
	}

	private static AsyncModernMcpConnection connection(McpModernConnectionInfo info, Supplier<McpAsyncClient> client,
			Supplier<McpAsyncClient> applicationClient, boolean clientPerRequest) {
		Assert.notNull(info, "info must not be null");
		return new AsyncModernMcpConnection() {

			@Override
			public McpModernConnectionInfo info() {
				return info;
			}

			@Override
			public McpAsyncClient client() {
				return client.get();
			}

			@Override
			public McpAsyncClient applicationClient() {
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
	private static Supplier<McpAsyncClient> notNull(Supplier<McpAsyncClient> clientFactory) {
		Assert.notNull(clientFactory, "clientFactory must not be null");
		return () -> {
			McpAsyncClient client = clientFactory.get();
			Assert.state(client != null, "clientFactory must not return null");
			return client;
		};
	}

}
