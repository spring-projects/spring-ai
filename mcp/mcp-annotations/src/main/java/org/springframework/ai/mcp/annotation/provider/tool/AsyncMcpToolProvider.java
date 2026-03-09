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

package org.springframework.ai.mcp.annotation.provider.tool;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.common.MetaUtils;
import org.springframework.ai.mcp.annotation.method.tool.AsyncMcpToolMethodCallback;
import org.springframework.ai.mcp.annotation.method.tool.ReactiveUtils;
import org.springframework.ai.mcp.annotation.method.tool.ReturnMode;
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator;
import org.springframework.util.ClassUtils;

/**
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Vadzim Shurmialiou
 * @author Craig Walls
 */
public class AsyncMcpToolProvider extends AbstractMcpToolProvider {

	private static final Logger logger = LoggerFactory.getLogger(AsyncMcpToolProvider.class);

	/**
	 * Create a new SyncMcpToolProvider.
	 * @param toolObjects the objects containing methods annotated with {@link McpTool}
	 */
	public AsyncMcpToolProvider(List<Object> toolObjects) {
		super(toolObjects);
	}

	/**
	 * Get the tool handler.
	 * @return the tool handler
	 * @throws IllegalStateException if no tool methods are found or if multiple tool
	 * methods are found
	 */
	public List<AsyncToolSpecification> getToolSpecifications() {

		List<AsyncToolSpecification> toolSpecs = this.toolObjects.stream()
			.map(toolObject -> Stream.of(this.doGetClassMethods(toolObject))
				.filter(method -> method.isAnnotationPresent(McpTool.class))
				.filter(McpPredicates.filterNonReactiveReturnTypeMethod())
				.sorted(Comparator.comparing(Method::getName))
				.map(mcpToolMethod -> {

					var toolJavaAnnotation = this.doGetMcpToolAnnotation(mcpToolMethod);

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
					if (toolJavaAnnotation.generateOutputSchema()
							&& !ReactiveUtils.isReactiveReturnTypeOfVoid(mcpToolMethod)
							&& !ReactiveUtils.isReactiveReturnTypeOfCallToolResult(mcpToolMethod)) {

						ReactiveUtils.getReactiveReturnTypeArgument(mcpToolMethod).ifPresent(typeArgument -> {
							Class<?> methodReturnType = typeArgument instanceof Class<?> ? (Class<?>) typeArgument
									: null;
							if (!ClassUtils.isPrimitiveOrWrapper(methodReturnType)
									&& !ClassUtils.isSimpleValueType(methodReturnType)) {
								toolBuilder.outputSchema(this.getJsonMapper(),
										McpJsonSchemaGenerator.generateFromClass((Class<?>) typeArgument));
							}
						});
					}
					var tool = toolBuilder.build();

					ReturnMode returnMode = tool.outputSchema() != null ? ReturnMode.STRUCTURED
							: ReactiveUtils.isReactiveReturnTypeOfVoid(mcpToolMethod) ? ReturnMode.VOID
									: ReturnMode.TEXT;

					BiFunction<McpAsyncServerExchange, CallToolRequest, Mono<CallToolResult>> methodCallback = new AsyncMcpToolMethodCallback(
							returnMode, mcpToolMethod, toolObject, this.doGetToolCallException());

					AsyncToolSpecification toolSpec = AsyncToolSpecification.builder()
						.tool(tool)
						.callHandler(methodCallback)
						.build();

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
