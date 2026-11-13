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

import io.modelcontextprotocol.modern.client.McpAsyncClient;

/**
 * Decides which {@link McpAsyncClient} of a connection serves a request, for an
 * application that wants neither a new client for each request nor one client for all of
 * them. A typical scope keeps one client for each user, so that the tool listing a server
 * marks as {@code private} is kept for that user for as long as the server allows:
 *
 * <pre class="code">
 * Map&lt;String, McpAsyncClient&gt; clients = new ConcurrentHashMap&lt;&gt;();
 * AsyncModernMcpClientScope perUser = (connection, clientFactory) -&gt; clients
 *     .computeIfAbsent(connection.name() + "/" + currentUserName(), key -&gt; clientFactory.get());
 * </pre>
 *
 * The scope is called once for each call to
 * {@link AsyncModernMcpToolCallbackProvider#getToolCallbacks()}, on the thread that
 * resolves the tools, and the returned tool callbacks are bound to the client it returns.
 * It is not called for what the application does for itself, such as a listen stream: see
 * {@link AsyncModernMcpConnection#applicationClient()}.
 * <p>
 * A scope that keeps clients decides how many it keeps and for how long. A client holds
 * no resource that must be released, so a client that is no longer needed is simply
 * dropped. An asynchronous client holds no caller: the caller of a request comes from an
 * {@link AsyncModernMcpTransportContextProvider}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see AsyncModernMcpConnection#scoped
 */
@FunctionalInterface
public interface AsyncModernMcpClientScope {

	/**
	 * Returns the client that serves the current request of the given connection.
	 * @param connectionInfo the connection the request goes to
	 * @param clientFactory builds a new client for the connection on each call,
	 * configured as every client of that connection is
	 * @return the client, either one that the scope kept or a new one from the factory
	 */
	McpAsyncClient client(McpModernConnectionInfo connectionInfo, Supplier<McpAsyncClient> clientFactory);

}
