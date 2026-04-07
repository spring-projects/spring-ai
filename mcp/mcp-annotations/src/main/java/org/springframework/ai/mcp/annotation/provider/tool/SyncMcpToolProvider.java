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
import java.util.function.BiFunction;
import java.util.stream.Stream;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.common.MetaUtils;
import org.springframework.ai.mcp.annotation.method.tool.ReturnMode;
import org.springframework.ai.mcp.annotation.method.tool.SyncMcpToolMethodCallback;
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator;
import org.springframework.util.ClassUtils;

/**
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Vadzim Shurmialiou
 * @author Craig Walls
 */
public class SyncMcpToolProvider extends AbstractMcpToolProvider {

	private static final Logger logger = LoggerFactory.getLogger(SyncMcpToolProvider.class);

	/**
	 * Create a new SyncMcpToolProvider.
	 * @param toolObjects the objects containing methods annotated with {@link McpTool}
	 */
	public SyncMcpToolProvider(List<Object> toolObjects) {
		super(toolObjects);
	}

	/**
	 * Get the tool handler.
	 * @return the tool handler
	 * @throws IllegalStateException if no tool methods are found or if multiple tool
	 * methods are found
	 */
	public List<SyncToolSpecification> getToolSpecifications() {

		List<SyncToolSpecification> toolSpecs = this.toolObjects.stream()
			.map(toolObject -> Stream.of(this.doGetClassMethods(toolObject))
				.filter(method -> method.isAnnotationPresent(McpTool.class))
				.filter(McpPredicates.filterReactiveReturnTypeMethod())
				.sorted(Comparator.comparing(Method::getName))
				.map(mcpToolMethod -> {

					McpTool toolJavaAnnotation = this.doGetMcpToolAnnotation(mcpToolMethod);

					String toolName = Utils.hasText(toolJavaAnnotation.name()) ? toolJavaAnnotation.name()
							: mcpToolMethod.getName();

					String toolDescription = toolJavaAnnotation.description();

					String inputSchema = McpJsonSchemaGenerator.generateForMethodInput(mcpToolMethod);

					var meta = MetaUtils.getMeta(toolJavaAnnotation.metaProvider());

					var toolBuilder = McpSchema.Tool.builder()
						.name(toolName)
						.description(toolDescription)
						.inputSchema(this.getJsonMapper(), inputSchema)
						.meta(meta);

					var title = toolJavaAnnotation.title();

					// Tool annotations
					if (toolJavaAnnotation.annotations() != null) {
						var toolAnnotations = toolJavaAnnotation.annotations();
						toolBuilder.annotations(new McpSchema.ToolAnnotations(toolAnnotations.title(),
								toolAnnotations.readOnlyHint(), toolAnnotations.destructiveHint(),
								toolAnnotations.idempotentHint(), toolAnnotations.openWorldHint(), null));

						// If not provided, the name should be used for display (except
						// for Tool, where annotations.title should be given precedence
						// over using name, if present).
						if (!Utils.hasText(title)) {
							title = toolAnnotations.title();
						}
					}

					// If not provided, the name should be used for display (except
					// for Tool, where annotations.title should be given precedence
					// over using name, if present).
					if (!Utils.hasText(title)) {
						title = toolName;
					}
					toolBuilder.title(title);

					// Generate Output Schema from the method return type.
					// Output schema is not generated for primitive types, void,
					// CallToolResult, simple value types (String, etc.)
					// or if generateOutputSchema attribute is set to false.
					Class<?> methodReturnType = mcpToolMethod.getReturnType();
					if (toolJavaAnnotation.generateOutputSchema() && methodReturnType != null
							&& methodReturnType != CallToolResult.class && methodReturnType != Void.class
							&& methodReturnType != void.class && !ClassUtils.isPrimitiveOrWrapper(methodReturnType)
							&& !ClassUtils.isSimpleValueType(methodReturnType)) {

						toolBuilder.outputSchema(this.getJsonMapper(),
								McpJsonSchemaGenerator.generateFromType(mcpToolMethod.getGenericReturnType()));
					}

					var tool = toolBuilder.build();

					boolean useStructuredOtput = tool.outputSchema() != null;

					ReturnMode returnMode = useStructuredOtput ? ReturnMode.STRUCTURED
							: (methodReturnType == Void.TYPE || methodReturnType == void.class ? ReturnMode.VOID
									: ReturnMode.TEXT);

					BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> methodCallback = new SyncMcpToolMethodCallback(
							returnMode, mcpToolMethod, toolObject, this.doGetToolCallException(),
							this.doGetToolCallExceptionHandler());

					var toolSpec = SyncToolSpecification.builder().tool(tool).callHandler(methodCallback).build();

					return toolSpec;
				})
				.toList())
			.flatMap(List::stream)
			.toList();

		if (toolSpecs.isEmpty()) {
			logger.warn("No tool methods found in the provided tool objects: {}", this.toolObjects);
		}

		return toolSpecs;
	}

}
