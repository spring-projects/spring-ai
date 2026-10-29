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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.modern.McpSchema.Annotations;
import io.modelcontextprotocol.modern.McpSchema.CallToolRequest;
import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.ImageContent;
import io.modelcontextprotocol.modern.McpSchema.Role;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.util.JsonHelper;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

/**
 * Exposes Spring AI {@link ToolCallback}s as tools of a 2026-07-28 MCP server.
 * <p>
 * The {@link McpRequestContext} of the current call is available to the callback through
 * the {@link ToolContext} under the {@link #TOOL_CONTEXT_MCP_REQUEST_CONTEXT_KEY} key. It
 * deliberately does not reuse the legacy exchange key, whose accessor casts to the
 * session-based exchange type.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public final class McpModernToolUtils {

	/**
	 * The {@link ToolContext} key under which the {@link McpRequestContext} of the
	 * current call is exposed to {@link ToolCallback}s.
	 */
	public static final String TOOL_CONTEXT_MCP_REQUEST_CONTEXT_KEY = "mcpRequestContext";

	private static final JsonHelper jsonHelper = new JsonHelper();

	private McpModernToolUtils() {
	}

	/**
	 * Converts the given callbacks to modern tool specifications.
	 * @param toolCallbacks the callbacks to expose
	 * @return one specification per callback
	 */
	public static List<SyncToolSpecification> toSyncToolSpecifications(List<ToolCallback> toolCallbacks) {
		Assert.notNull(toolCallbacks, "toolCallbacks must not be null");
		return toolCallbacks.stream().map(McpModernToolUtils::toSyncToolSpecification).toList();
	}

	public static List<SyncToolSpecification> toSyncToolSpecifications(ToolCallback... toolCallbacks) {
		return toSyncToolSpecifications(List.of(toolCallbacks));
	}

	public static SyncToolSpecification toSyncToolSpecification(ToolCallback toolCallback) {
		return toSyncToolSpecification(toolCallback, null);
	}

	/**
	 * Converts a Spring AI {@link ToolCallback} to a modern tool specification. The tool
	 * definition is built from the callback's name, description and input schema. The
	 * callback's text result becomes the tool's text content, or an image content when an
	 * image MIME type is given.
	 * @param toolCallback the callback to expose
	 * @param mimeType the MIME type of the callback's output, or {@code null} for text
	 * @return the tool specification
	 */
	public static SyncToolSpecification toSyncToolSpecification(ToolCallback toolCallback,
			@Nullable MimeType mimeType) {
		Assert.notNull(toolCallback, "toolCallback must not be null");
		var definition = toolCallback.getToolDefinition();
		Tool tool = Tool
			.builder(definition.name(), new JacksonMcpJsonMapper(JacksonUtils.getDefaultJsonMapper()),
					definition.inputSchema())
			.description(definition.description())
			.build();
		return new SyncToolSpecification(tool,
				(context, request) -> McpSyncResponse.result(call(toolCallback, mimeType, context, request)));
	}

	/**
	 * Retrieves the {@link McpRequestContext} of the current tool call from the
	 * {@link ToolContext}, if the callback is being invoked by a modern MCP server.
	 * @param toolContext the tool context passed to the callback
	 * @return the request context, if present
	 */
	public static Optional<McpRequestContext> getMcpRequestContext(@Nullable ToolContext toolContext) {
		if (toolContext == null || toolContext.getContext() == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(toolContext.getContext().get(TOOL_CONTEXT_MCP_REQUEST_CONTEXT_KEY))
			.filter(McpRequestContext.class::isInstance)
			.map(McpRequestContext.class::cast);
	}

	private static CallToolResult call(ToolCallback toolCallback, @Nullable MimeType mimeType,
			McpRequestContext context, CallToolRequest request) {
		try {
			Map<String, Object> arguments = request.arguments() != null ? request.arguments() : Map.of();
			String callResult = toolCallback.call(jsonHelper.toJson(arguments),
					new ToolContext(Map.of(TOOL_CONTEXT_MCP_REQUEST_CONTEXT_KEY, context)));
			if (mimeType != null && mimeType.toString().startsWith("image")) {
				Annotations annotations = Annotations.builder().audience(List.of(Role.ASSISTANT)).build();
				return CallToolResult.builder()
					.addContent(ImageContent.builder(callResult, mimeType.toString()).annotations(annotations).build())
					.isError(false)
					.build();
			}
			return CallToolResult.builder().addContent(TextContent.builder(callResult).build()).isError(false).build();
		}
		catch (Exception e) {
			String message = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
			return CallToolResult.builder().addContent(TextContent.builder(message).build()).isError(true).build();
		}
	}

}
