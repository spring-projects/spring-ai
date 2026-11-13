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

package org.springframework.ai.mcp.client.common.autoconfigure;

import io.modelcontextprotocol.modern.client.transport.McpClientTransport;

/**
 * A transport to a modern MCP server, together with the name of its connection. The
 * modern counterpart of {@link NamedClientMcpTransport}.
 * <p>
 * Applications can contribute connections of their own by defining a bean of type
 * {@code List<NamedModernClientMcpTransport>}.
 *
 * @param name the name of the connection
 * @param transport the transport to the server
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record NamedModernClientMcpTransport(String name, McpClientTransport transport) {

}
