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

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP metadata record containing the client/server specific meta data.
 *
 * @param clientCapabilities the MCP client capabilities
 * @param clientInfo the MCP client information
 * @param initializeResult the MCP server initialization result
 * @author Ilayaperumal Gopinathan
 * @author Christian Tzolov
 */
public record McpMetadata(// @formatter:off
	McpSchema.ClientCapabilities clientCapabilities,
	McpSchema.Implementation clientInfo,
	McpSchema.InitializeResult initializeResult) { // @formatter:on
}
