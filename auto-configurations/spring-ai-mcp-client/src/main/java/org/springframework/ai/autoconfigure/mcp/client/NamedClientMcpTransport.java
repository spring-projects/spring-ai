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
package org.springframework.ai.autoconfigure.mcp.client;

import io.modelcontextprotocol.spec.ClientMcpTransport;

/**
 * A named MCP client transport. Usually created by the transport auto-configurations, but
 * you can also create them manually. Expose the list castom NamedClientMcpTransport
 * as @Bean.
 *
 * @param name the name of the transport. Usually the name of the server connection.
 * @param transport the MCP client transport.
 * @author Christian Tzolov
 * @since 1.0.0
 */
public record NamedClientMcpTransport(String name, ClientMcpTransport transport) {

}
