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

package org.springframework.ai.mcp.customizer;

import io.modelcontextprotocol.modern.server.McpServer;

/**
 * Callback interface that can be implemented by beans wishing to customize the
 * auto-configured 2026-07-28 ("modern") MCP server before it is built. Use it to add
 * extension features, change the supported protocol versions, or replace the request
 * state codec.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
@FunctionalInterface
public interface McpModernServerCustomizer {

	/**
	 * Customize the modern server builder.
	 * @param serverBuilder the builder of the modern server
	 */
	void customize(McpServer.Builder serverBuilder);

}
