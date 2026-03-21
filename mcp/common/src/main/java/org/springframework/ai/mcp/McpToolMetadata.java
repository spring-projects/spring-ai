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
import org.jspecify.annotations.Nullable;

import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * MCP-aware implementation of {@link ToolMetadata} that exposes MCP tool annotations
 * (introduced in MCP spec 2025-03-26) to Spring AI consumers.
 *
 * <p>
 * Tool annotations provide hints about a tool's behavior, such as whether it is
 * read-only, destructive, or idempotent. These hints help clients make informed decisions
 * about tool execution, including safety checks and user confirmation prompts.
 *
 * @author Mehdi Ghaeini
 * @since 2.0.0
 * @see McpSchema.ToolAnnotations
 */
public class McpToolMetadata implements ToolMetadata {

	private final @Nullable McpSchema.ToolAnnotations annotations;

	private McpToolMetadata(@Nullable McpSchema.ToolAnnotations annotations) {
		this.annotations = annotations;
	}

	/**
	 * Creates McpToolMetadata from MCP tool annotations.
	 * @param annotations the MCP tool annotations, may be null
	 * @return a new McpToolMetadata instance
	 */
	public static McpToolMetadata from(@Nullable McpSchema.ToolAnnotations annotations) {
		return new McpToolMetadata(annotations);
	}

	@Override
	public boolean returnDirect() {
		if (this.annotations != null && this.annotations.returnDirect() != null) {
			return this.annotations.returnDirect();
		}
		return false;
	}

	/**
	 * Whether the tool performs only read operations and does not modify state.
	 * @return true if the tool is read-only, null if unknown
	 */
	public @Nullable Boolean readOnlyHint() {
		return this.annotations != null ? this.annotations.readOnlyHint() : null;
	}

	/**
	 * Whether the tool may perform destructive operations (e.g., deleting data).
	 * @return true if the tool may be destructive, null if unknown
	 */
	public @Nullable Boolean destructiveHint() {
		return this.annotations != null ? this.annotations.destructiveHint() : null;
	}

	/**
	 * Whether calling the tool multiple times with the same arguments produces the same
	 * result.
	 * @return true if the tool is idempotent, null if unknown
	 */
	public @Nullable Boolean idempotentHint() {
		return this.annotations != null ? this.annotations.idempotentHint() : null;
	}

	/**
	 * Whether the tool interacts with entities outside the client's controlled
	 * environment (e.g., external APIs, the internet).
	 * @return true if the tool operates in an open-world context, null if unknown
	 */
	public @Nullable Boolean openWorldHint() {
		return this.annotations != null ? this.annotations.openWorldHint() : null;
	}

	/**
	 * Returns the underlying MCP tool annotations, or null if none were provided.
	 * @return the MCP tool annotations
	 */
	public @Nullable McpSchema.ToolAnnotations getAnnotations() {
		return this.annotations;
	}

}
