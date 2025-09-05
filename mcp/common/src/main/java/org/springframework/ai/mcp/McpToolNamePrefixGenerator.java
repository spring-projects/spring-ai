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

package org.springframework.ai.mcp;

import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Strategy interface for generating prefixed tool name based on MCP client/server and
 * Tool information.
 *
 * <p>
 * Implementations of this interface can define custom logic to create meaningful and
 * unique prefixes for tools, useful for avoiding name collisions in environments where
 * multiple MCP Servers provide tools.
 * </p>
 *
 * <p>
 * The prefix generation can take into account various aspects of the MCP client, server
 * and tool, such as client capabilities, client information, and server initialization
 * results, as well as specific attributes of the tool itself.
 * </p>
 *
 * @author Christian Tzolov
 */
public interface McpToolNamePrefixGenerator {

	String prefixedToolName(McpConnectionInfo mcpConnectionInfo, Tool tool);

	/**
	 * Default implementation that uses the MCP client name to generate the prefixed tool
	 * name.
	 * @return the default prefix generator
	 */
	static McpToolNamePrefixGenerator defaultGenerator() {
		return (mcpConnectionIfo, tool) -> McpToolUtils.prefixedToolName(mcpConnectionIfo.clientInfo().name(),
				tool.name());
	}

	/**
	 * Static factory method to create a no-op prefix generator that returns the tool name
	 * @return a prefix generator that returns the tool name as-is
	 */
	static McpToolNamePrefixGenerator noPrefix() {
		return (mcpConnectinInfo, tool) -> tool.name();
	}

}
