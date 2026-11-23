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

import java.util.LinkedHashMap;
import java.util.Map;

import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.JsonHelper;
import org.springframework.ai.util.json.schema.JsonSchemaUtils;
import org.springframework.util.StringUtils;

/**
 * What the synchronous and the asynchronous tool callback of a modern connection have in
 * common: how a tool is presented to the model, what goes into a request, and how a
 * result is returned to the model.
 *
 * @author Christian Tzolov
 */
final class ModernToolCalls {

	private static final JsonHelper jsonHelper = new JsonHelper();

	/**
	 * Prefix of the {@code _meta} keys the modern client sets itself and rejects when the
	 * application sets them.
	 */
	private static final String RESERVED_META_PREFIX = "io.modelcontextprotocol/";

	private ModernToolCalls() {
	}

	/**
	 * The definition of a tool as the model sees it.
	 * @param prefixedToolName the name of the tool for the model
	 * @param tool the tool, as listed by the server
	 * @return the tool definition
	 */
	static ToolDefinition toolDefinition(String prefixedToolName, Tool tool) {
		return DefaultToolDefinition.builder()
			.name(prefixedToolName)
			.description(tool.description())
			.inputSchema(JsonSchemaUtils.ensureValidInputSchema(jsonHelper.toJson(tool.inputSchema())))
			.build();
	}

	/**
	 * The arguments of a tool call. A streaming model may send no arguments at all.
	 * @param toolCallInput the arguments as the model sent them
	 * @return the arguments
	 */
	static Map<String, Object> arguments(String toolCallInput) {
		return jsonHelper.fromJsonToMap(StringUtils.hasText(toolCallInput) ? toolCallInput : "{}");
	}

	/**
	 * The {@code _meta} entries of a request: the {@link ToolContext} entries without the
	 * keys the modern client sets itself.
	 * @param toolContextToMcpMetaConverter turns the tool context into {@code _meta}
	 * entries
	 * @param toolContext the tool context of the call, if any
	 * @return the {@code _meta} entries
	 */
	static Map<String, Object> requestMeta(ToolContextToMcpMetaConverter toolContextToMcpMetaConverter,
			@Nullable ToolContext toolContext) {
		if (toolContext == null) {
			return Map.of();
		}
		Map<String, Object> meta = new LinkedHashMap<>();
		toolContextToMcpMetaConverter.convert(toolContext).forEach((key, value) -> {
			if (!MetaKeys.PROGRESS_TOKEN.equals(key) && !key.startsWith(RESERVED_META_PREFIX)) {
				meta.put(key, value);
			}
		});
		return meta;
	}

	/**
	 * The text returned to the model. A result without structured content is returned as
	 * the JSON of its content blocks, like the legacy callbacks do. A result with
	 * structured content is returned as a JSON object that holds both.
	 * @param result the result of the tool call
	 * @return the text for the model
	 */
	static String toModelResponse(CallToolResult result) {
		if (result.structuredContent() == null) {
			return jsonHelper.toJson(result.content());
		}
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("content", result.content());
		response.put("structuredContent", result.structuredContent());
		return jsonHelper.toJson(response);
	}

}
