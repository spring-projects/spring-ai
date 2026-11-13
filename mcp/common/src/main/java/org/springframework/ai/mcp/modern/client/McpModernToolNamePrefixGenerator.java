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

import io.modelcontextprotocol.modern.McpSchema.Tool;

import org.springframework.ai.mcp.McpToolUtils;

/**
 * Generates the name under which a tool of a modern MCP server is presented to the model.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@FunctionalInterface
public interface McpModernToolNamePrefixGenerator {

	/**
	 * The longest tool name most model providers accept.
	 */
	int MAX_TOOL_NAME_LENGTH = 64;

	/**
	 * Generates the name the model sees for the given tool.
	 * @param connectionInfo the connection the tool belongs to
	 * @param tool the tool, as listed by the server
	 * @return the tool name
	 */
	String prefixedToolName(McpModernConnectionInfo connectionInfo, Tool tool);

	/**
	 * Names a tool {@code <connection>_<tool>}. The name depends only on the connection
	 * name and the tool name, so a tool keeps its name when its definition changes.
	 * Characters that model providers reject are removed, and hyphens become underscores.
	 * A name longer than {@link #MAX_TOOL_NAME_LENGTH} keeps its beginning, with the
	 * connection name, and ends in eight characters that are derived from the connection
	 * name and the tool name, so that two long names do not become the same one.
	 * @return the generator
	 */
	static McpModernToolNamePrefixGenerator connectionPrefix() {
		return (connectionInfo, tool) -> {
			String name = McpToolUtils.format(connectionInfo.name()) + "_" + McpToolUtils.format(tool.name());
			if (name.length() <= MAX_TOOL_NAME_LENGTH) {
				return name;
			}
			String suffix = "_" + String.format("%08x", (connectionInfo.name() + "\n" + tool.name()).hashCode());
			return name.substring(0, MAX_TOOL_NAME_LENGTH - suffix.length()) + suffix;
		};
	}

	/**
	 * Keeps the tool's own name. Tools of different connections that share a name then
	 * collide, which {@link SyncModernMcpToolCallbackProvider} rejects.
	 * @return the generator
	 */
	static McpModernToolNamePrefixGenerator noPrefix() {
		return (connectionInfo, tool) -> tool.name();
	}

}
