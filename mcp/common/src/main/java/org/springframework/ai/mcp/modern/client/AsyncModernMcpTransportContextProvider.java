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

import io.modelcontextprotocol.common.McpTransportContext;

/**
 * Supplies the caller of the current request to the asynchronous modern connections.
 * <p>
 * The asynchronous client of the MCP Java SDK reads the caller of a request from the
 * Reactor context, under {@link McpTransportContext#KEY}. The tools of a connection are
 * resolved by a blocking call that has no Reactor context, so the caller is asked for
 * here, on the thread that resolves the tools: once for each call to
 * {@link AsyncModernMcpToolCallbackProvider#getToolCallbacks()}. The context that is
 * returned goes with the tool listing, and the tool callbacks of that call keep it, so a
 * tool that runs later, or on another thread, is still called for that caller.
 * <p>
 * The caller that is returned here is the only caller of the listing and of the tool
 * calls. A transport context in the Reactor context of a chat call is not used, because
 * it may belong to someone else, such as the request that an MCP server of the same
 * application is serving. Return {@link McpTransportContext#EMPTY} for no caller.
 * <p>
 * Do not combine a provider that returns different callers with a
 * {@link AsyncModernMcpConnection#shared shared} connection for a server that answers
 * callers differently: a shared client keeps a {@code private} tool listing for all its
 * requests, whoever the caller is.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see AsyncModernMcpConnection#withTransportContext
 */
@FunctionalInterface
public interface AsyncModernMcpTransportContextProvider {

	/**
	 * Returns the transport context of the caller that the current thread works for.
	 * @param connectionInfo the connection the request goes to
	 * @return the transport context, {@link McpTransportContext#EMPTY} for no caller
	 */
	McpTransportContext transportContext(McpModernConnectionInfo connectionInfo);

}
