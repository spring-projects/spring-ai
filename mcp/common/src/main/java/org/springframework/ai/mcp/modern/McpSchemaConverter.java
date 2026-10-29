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

package org.springframework.ai.mcp.modern;

import java.util.HashMap;
import java.util.Map;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.spec.McpError;

import org.springframework.util.Assert;

/**
 * Converts between the legacy ({@code io.modelcontextprotocol.spec.McpSchema}) and the
 * modern ({@code io.modelcontextprotocol.modern.McpSchema}) schema types.
 * <p>
 * Both type hierarchies serialize to the same JSON shapes, so the conversion is a
 * mapper-based round trip. Modern-only fields such as {@code resultType}, {@code ttlMs}
 * and {@code cacheScope} take their default values. This is what allows annotated methods
 * and {@code ToolCallback}s written against the legacy types to be served by a 2026-07-28
 * server without changes.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public final class McpSchemaConverter {

	private McpSchemaConverter() {
	}

	private static McpJsonMapper mapper() {
		return McpJsonDefaults.getMapper();
	}

	/**
	 * Converts any JSON-compatible value to the given target type using the default MCP
	 * JSON mapper.
	 * @param source the value to convert
	 * @param targetType the type to convert to
	 * @param <T> the target type
	 * @return the converted value
	 */
	public static <T> T convert(Object source, Class<T> targetType) {
		Assert.notNull(source, "source must not be null");
		Assert.notNull(targetType, "targetType must not be null");
		return mapper().convertValue(source, targetType);
	}

	// ---------------------------------------------------------------------------------
	// Legacy -> modern results and definitions
	// ---------------------------------------------------------------------------------

	public static McpSchema.CallToolResult toModern(io.modelcontextprotocol.spec.McpSchema.CallToolResult result) {
		return convert(result, McpSchema.CallToolResult.class);
	}

	public static McpSchema.ReadResourceResult toModern(
			io.modelcontextprotocol.spec.McpSchema.ReadResourceResult result) {
		return convert(result, McpSchema.ReadResourceResult.class);
	}

	public static McpSchema.GetPromptResult toModern(io.modelcontextprotocol.spec.McpSchema.GetPromptResult result) {
		return convert(result, McpSchema.GetPromptResult.class);
	}

	public static McpSchema.CompleteResult toModern(io.modelcontextprotocol.spec.McpSchema.CompleteResult result) {
		return convert(result, McpSchema.CompleteResult.class);
	}

	public static McpSchema.Tool toModern(io.modelcontextprotocol.spec.McpSchema.Tool tool) {
		return convert(tool, McpSchema.Tool.class);
	}

	public static McpSchema.Resource toModern(io.modelcontextprotocol.spec.McpSchema.Resource resource) {
		return convert(resource, McpSchema.Resource.class);
	}

	public static McpSchema.ResourceTemplate toModern(
			io.modelcontextprotocol.spec.McpSchema.ResourceTemplate resourceTemplate) {
		return convert(resourceTemplate, McpSchema.ResourceTemplate.class);
	}

	public static McpSchema.Prompt toModern(io.modelcontextprotocol.spec.McpSchema.Prompt prompt) {
		return convert(prompt, McpSchema.Prompt.class);
	}

	public static McpSchema.CompleteReference toModern(
			io.modelcontextprotocol.spec.McpSchema.CompleteReference reference) {
		if (reference instanceof io.modelcontextprotocol.spec.McpSchema.PromptReference promptReference) {
			return McpSchema.PromptReference.builder(promptReference.name()).title(promptReference.title()).build();
		}
		if (reference instanceof io.modelcontextprotocol.spec.McpSchema.ResourceReference resourceReference) {
			return new McpSchema.ResourceReference(resourceReference.uri());
		}
		throw new IllegalArgumentException("Unsupported completion reference: " + reference);
	}

	/**
	 * Converts a legacy protocol error into its modern counterpart so that it is answered
	 * with the same JSON-RPC error code and data.
	 * @param error the legacy error
	 * @return the modern exception
	 */
	public static McpException toModern(McpError error) {
		var jsonRpcError = error.getJsonRpcError();
		if (jsonRpcError == null) {
			return new McpException(McpSchema.ErrorCodes.INTERNAL_ERROR,
					error.getMessage() != null ? error.getMessage() : "Internal error");
		}
		return new McpException(jsonRpcError.code(), jsonRpcError.message(), jsonRpcError.data());
	}

	// ---------------------------------------------------------------------------------
	// Modern -> legacy requests
	// ---------------------------------------------------------------------------------

	/**
	 * Builds the legacy request a handler expects from a modern {@code tools/call}
	 * request. The MRTR fields ({@code inputResponses}, {@code requestState}) have no
	 * legacy counterpart and are dropped; handlers that need them take the modern
	 * request.
	 */
	public static io.modelcontextprotocol.spec.McpSchema.CallToolRequest toLegacy(McpSchema.CallToolRequest request) {
		return new io.modelcontextprotocol.spec.McpSchema.CallToolRequest(request.name(),
				request.arguments() != null ? request.arguments() : Map.of(), request.meta());
	}

	public static io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest toLegacy(
			McpSchema.ReadResourceRequest request) {
		return new io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest(request.uri(), request.meta());
	}

	public static io.modelcontextprotocol.spec.McpSchema.GetPromptRequest toLegacy(McpSchema.GetPromptRequest request) {
		Map<String, Object> arguments = new HashMap<>();
		if (request.arguments() != null) {
			arguments.putAll(request.arguments());
		}
		return new io.modelcontextprotocol.spec.McpSchema.GetPromptRequest(request.name(), arguments, request.meta());
	}

	public static io.modelcontextprotocol.spec.McpSchema.CompleteRequest toLegacy(McpSchema.CompleteRequest request) {
		return convert(request, io.modelcontextprotocol.spec.McpSchema.CompleteRequest.class);
	}

}
