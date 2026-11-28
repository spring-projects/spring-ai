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
 * Client-side support for the MCP 2026-07-28 ("modern") protocol revision, built on the
 * MCP Java SDK's {@code io.modelcontextprotocol.modern.client} package.
 * <p>
 * {@link org.springframework.ai.mcp.modern.client.SyncModernMcpToolCallbackProvider}
 * exposes the tools of modern MCP servers as Spring AI
 * {@link org.springframework.ai.tool.ToolCallback}s. Each server is described by a
 * {@link org.springframework.ai.mcp.modern.client.SyncModernMcpConnection}, which decides
 * which client serves a request: one client shared by all requests, a new client for each
 * request, or the client that a scope of the application selects.
 * <p>
 * {@link org.springframework.ai.mcp.modern.client.AsyncModernMcpToolCallbackProvider} and
 * {@link org.springframework.ai.mcp.modern.client.AsyncModernMcpConnection} are their
 * counterparts on the asynchronous client of the SDK. The tool filter, the tool name
 * generator and the connection info are shared by both.
 */
@NullMarked
package org.springframework.ai.mcp.modern.client;

import org.jspecify.annotations.NullMarked;
