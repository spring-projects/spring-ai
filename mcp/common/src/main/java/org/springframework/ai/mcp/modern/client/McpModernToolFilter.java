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

import java.util.function.BiPredicate;

import io.modelcontextprotocol.modern.McpSchema.Tool;

/**
 * Decides which tools of a modern MCP server {@link SyncModernMcpToolCallbackProvider}
 * exposes. A tool is exposed when the predicate returns {@code true} for the connection
 * and the tool.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@FunctionalInterface
public interface McpModernToolFilter extends BiPredicate<McpModernConnectionInfo, Tool> {

	/**
	 * A filter that exposes every tool.
	 * @return the filter
	 */
	static McpModernToolFilter acceptAll() {
		return (connectionInfo, tool) -> true;
	}

}
