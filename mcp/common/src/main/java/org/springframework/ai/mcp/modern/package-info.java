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
 * The modern SDK exposes primitives through repositories rather than specification lists.
 * This package bridges the two styles: the {@code Sync*Specification} records pair a
 * primitive definition with its handler, the {@code AbstractSync*Repository} classes turn
 * per-request definitions and specifications into an SDK repository (paging, list cache
 * scope, visibility, dispatch, template matching, change broadcasts), and the
 * {@code InMemorySync*Repository} classes are their map-backed implementations. A
 * {@link org.springframework.ai.mcp.modern.McpVisibilityFilter} restricts what each
 * caller sees, typically from the request headers that
 * {@link org.springframework.ai.mcp.modern.McpTransportContexts} exposes.
 * {@link org.springframework.ai.mcp.modern.McpSchemaConverter} converts between the
 * legacy and modern schema types, which lets handlers written against the legacy types be
 * exposed on a modern server.
 * <p>
 * <strong>Internal note:</strong> the in-memory repositories and the converter are
 * candidates for contribution to the MCP Java SDK. Their shape mirrors the SDK naming so
 * that they can be replaced by SDK types without changes to user code.
 */
@NullMarked
package org.springframework.ai.mcp.modern;

import org.jspecify.annotations.NullMarked;
