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

package org.springframework.ai.mcp.annotation.provider.tool;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.util.Utils;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.common.IconUtils;
import org.springframework.ai.mcp.annotation.common.MetaUtils;
import org.springframework.ai.mcp.annotation.common.ModernReactiveSupport;
import org.springframework.ai.mcp.annotation.method.tool.ReturnMode;
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator;
import org.springframework.util.ClassUtils;

/**
 * Derives the modern {@link Tool} definition and the {@link ReturnMode} of a method
 * annotated with {@link McpTool}, for the sync and the async modern tool providers. The
 * outcome type is read through a reactive return type, so {@code Mono<T>} yields the same
 * definition as {@code T}.
 *
 * @author Christian Tzolov
 */
final class ModernToolDefinitions {

	private ModernToolDefinitions() {
	}

	static Definition define(Method method, McpTool annotation, McpJsonMapper jsonMapper) {
		String toolName = Utils.hasText(annotation.name()) ? annotation.name() : method.getName();
		String inputSchema = McpJsonSchemaGenerator.generateForMethodInput(method);
		var toolBuilder = Tool.builder(toolName, jsonMapper, inputSchema)
			.description(annotation.description())
			.meta(MetaUtils.getMeta(annotation.metaProvider()));
		String title = annotation.title();
		if (annotation.annotations() != null) {
			var toolAnnotations = annotation.annotations();
			toolBuilder.annotations(McpSchema.ToolAnnotations.builder()
				.title(toolAnnotations.title())
				.readOnlyHint(toolAnnotations.readOnlyHint())
				.destructiveHint(toolAnnotations.destructiveHint())
				.idempotentHint(toolAnnotations.idempotentHint())
				.openWorldHint(toolAnnotations.openWorldHint())
				.build());
			if (!Utils.hasText(title)) {
				title = toolAnnotations.title();
			}
		}
		if (!Utils.hasText(title)) {
			title = toolName;
		}
		toolBuilder.title(title);
		toolBuilder.icons(IconUtils.toModernIcons(annotation.icons()));
		// Output schema from the outcome type, unless it is void, a protocol result or a
		// simple value.
		Type outcomeType = ModernReactiveSupport.outcomeType(method);
		Class<?> outcomeClass = ModernReactiveSupport.outcomeClass(method);
		if (annotation.generateOutputSchema() && outcomeClass != null && !isProtocolResultType(outcomeClass)
				&& !isVoid(outcomeClass) && !ClassUtils.isPrimitiveOrWrapper(outcomeClass)
				&& !ClassUtils.isSimpleValueType(outcomeClass)) {
			toolBuilder.outputSchema(jsonMapper, McpJsonSchemaGenerator.generateFromType(outcomeType));
		}
		Tool tool = toolBuilder.build();
		ReturnMode returnMode = tool.outputSchema() != null ? ReturnMode.STRUCTURED
				: (isVoid(outcomeClass) ? ReturnMode.VOID : ReturnMode.TEXT);
		return new Definition(tool, returnMode);
	}

	private static boolean isVoid(@Nullable Class<?> type) {
		return type == void.class || type == Void.class;
	}

	private static boolean isProtocolResultType(Class<?> type) {
		return io.modelcontextprotocol.spec.McpSchema.CallToolResult.class.isAssignableFrom(type)
				|| CallToolOutcome.class.isAssignableFrom(type);
	}

	/**
	 * A tool definition with the return mode its callback converts results with.
	 */
	record Definition(Tool tool, ReturnMode returnMode) {
	}

}
