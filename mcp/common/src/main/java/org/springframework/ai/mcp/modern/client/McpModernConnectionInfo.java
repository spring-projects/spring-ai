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

import io.modelcontextprotocol.modern.McpSchema.Implementation;

import org.springframework.util.Assert;

/**
 * Describes a connection to a modern MCP server, for {@link McpModernToolFilter} and
 * {@link McpModernToolNamePrefixGenerator}.
 * <p>
 * The modern protocol has no initialization handshake, so the server's info and
 * capabilities are not part of this record. Use the client's {@code discover()} method
 * when they are needed.
 *
 * @param name the name of the connection, as configured by the application
 * @param clientInfo the name and version the client sends to the server
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record McpModernConnectionInfo(String name, Implementation clientInfo) {

	public McpModernConnectionInfo {
		Assert.hasText(name, "name must not be empty");
		Assert.notNull(clientInfo, "clientInfo must not be null");
	}

}
