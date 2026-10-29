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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.util.Utils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.common.MetaUtils;
import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.method.tool.ReturnMode;
import org.springframework.ai.mcp.annotation.method.tool.SyncModernMcpToolMethodCallback;
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator;
import org.springframework.ai.mcp.modern.SyncToolSpecification;
import org.springframework.util.ClassUtils;

/**
 * Creates {@link SyncToolSpecification}s for a 2026-07-28 ("modern") MCP server from
 * methods annotated with {@link McpTool}.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public class SyncModernMcpToolProvider extends AbstractMcpToolProvider {

	private static final Log logger = LogFactory.getLog(SyncModernMcpToolProvider.class);

	public SyncModernMcpToolProvider(List<Object> toolObjects) {
		super(toolObjects);
	}

	public List<SyncToolSpecification> getToolSpecifications() {
		List<SyncToolSpecification> toolSpecs = this.toolObjects.stream()
			.map(toolObject -> Stream.of(this.doGetClassMethods(toolObject))
				.filter(method -> method.isAnnotationPresent(McpTool.class))
				.filter(McpPredicates.filterReactiveReturnTypeMethod())
				// Doesn't support streamable http (stateful) arguments
				.filter(ModernMethodSupport.filterLegacyContextMethods())
				.sorted(Comparator.comparing(Method::getName))
				.map(mcpToolMethod -> toSpecification(toolObject, mcpToolMethod))
				.toList())
			.flatMap(List::stream)
			.toList();

		if (toolSpecs.isEmpty() && logger.isWarnEnabled()) {
			logger.warn("No tool methods found in the provided tool objects: " + this.toolObjects);
		}
		return toolSpecs;
	}

	private SyncToolSpecification toSpecification(Object toolObject, Method mcpToolMethod) {
		var toolJavaAnnotation = this.doGetMcpToolAnnotation(mcpToolMethod);

		String toolName = Utils.hasText(toolJavaAnnotation.name()) ? toolJavaAnnotation.name()
				: mcpToolMethod.getName();
		String inputSchema = McpJsonSchemaGenerator.generateForMethodInput(mcpToolMethod);

		var toolBuilder = Tool.builder(toolName, this.getJsonMapper(), inputSchema)
			.description(toolJavaAnnotation.description())
			.meta(MetaUtils.getMeta(toolJavaAnnotation.metaProvider()));

		String title = toolJavaAnnotation.title();
		if (toolJavaAnnotation.annotations() != null) {
			var toolAnnotations = toolJavaAnnotation.annotations();
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

		// Output schema from the return type, unless it is void, a protocol result or a
		// simple value.
		Class<?> methodReturnType = mcpToolMethod.getReturnType();
		if (toolJavaAnnotation.generateOutputSchema() && methodReturnType != null
				&& !isProtocolResultType(methodReturnType) && methodReturnType != Void.class
				&& methodReturnType != void.class && !ClassUtils.isPrimitiveOrWrapper(methodReturnType)
				&& !ClassUtils.isSimpleValueType(methodReturnType)) {
			toolBuilder.outputSchema(this.getJsonMapper(),
					McpJsonSchemaGenerator.generateFromType(mcpToolMethod.getGenericReturnType()));
		}

		Tool tool = toolBuilder.build();
		boolean useStructuredOutput = tool.outputSchema() != null;
		ReturnMode returnMode = useStructuredOutput ? ReturnMode.STRUCTURED
				: (methodReturnType == Void.TYPE || methodReturnType == void.class ? ReturnMode.VOID : ReturnMode.TEXT);

		return new SyncToolSpecification(tool,
				new SyncModernMcpToolMethodCallback(returnMode, mcpToolMethod, toolObject));
	}

	private static boolean isProtocolResultType(Class<?> type) {
		return io.modelcontextprotocol.spec.McpSchema.CallToolResult.class.isAssignableFrom(type)
				|| CallToolOutcome.class.isAssignableFrom(type);
	}

}
