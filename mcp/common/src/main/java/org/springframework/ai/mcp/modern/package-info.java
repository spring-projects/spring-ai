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

/**
 * Support for MCP servers implementing the 2026-07-28 ("modern") specification revision,
 * built on the {@code io.modelcontextprotocol.modern} server API of the MCP Java SDK.
 * <p>
 * The modern SDK serves each primitive from a repository that lists definitions and finds
 * a definition with its handler ({@code McpSyncTool}, {@code McpSyncPrompt},
 * {@code McpSyncResource}). The {@code InMemorySync*Repository} classes are map-backed
 * repositories with runtime add and remove, change broadcasts and key-cursor paging.
 * {@link org.springframework.ai.mcp.modern.SyncResourceEntry} and
 * {@link org.springframework.ai.mcp.modern.SyncResourceTemplateEntry} pair a resource or
 * template definition with its handler, which the SDK's {@code McpSyncResource} does not
 * carry; {@link org.springframework.ai.mcp.modern.SyncCompletionSpecification} does the
 * same for a completion reference.
 * {@link org.springframework.ai.mcp.modern.McpSchemaConverter} converts between the
 * legacy and modern schema types, which lets handlers written against the legacy types be
 * exposed on a modern server, and
 * {@link org.springframework.ai.mcp.modern.McpTransportContexts} reads the request
 * headers that the Spring transports put into the transport context.
 */
@NullMarked
package org.springframework.ai.mcp.modern;

import org.jspecify.annotations.NullMarked;
