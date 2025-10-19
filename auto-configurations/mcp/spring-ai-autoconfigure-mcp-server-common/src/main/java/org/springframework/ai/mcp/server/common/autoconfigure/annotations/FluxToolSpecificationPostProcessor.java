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

package org.springframework.ai.mcp.server.common.autoconfigure.annotations;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpTransportContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.util.ReflectionUtils;

/**
 * Post-processor that wraps AsyncToolSpecifications to handle Flux return types properly
 * by collecting all elements before serialization.
 *
 * <p>
 * <strong>Background:</strong> This class fixes Issue #4542 where Flux-returning @McpTool
 * methods only return the first element. The root cause is in the external {@code
 * org.springaicommunity.mcp.provider.tool.AsyncStatelessMcpToolProvider} library, which
 * treats Flux as a single-value Publisher and only takes the first element.
 *
 * <p>
 * <strong>Solution:</strong> This post-processor intercepts tool specifications and wraps
 * their call handlers. When a method returns a Flux, it collects all elements into a list
 * before passing the result to the MCP serialization layer.
 *
 * <p>
 * <strong>Note:</strong> Users can also work around this issue by returning {@code
 * Mono<List<T>>} instead of {@code Flux<T>} from their {@code @McpTool} methods.
 *
 * @author liugddx
 * @since 1.1.0
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/4542">Issue #4542</a>
 */
public final class FluxToolSpecificationPostProcessor {

	private static final Logger logger = LoggerFactory.getLogger(FluxToolSpecificationPostProcessor.class);

	private static final ObjectMapper objectMapper = new ObjectMapper();

	private FluxToolSpecificationPostProcessor() {
		// Utility class - no instances allowed
	}

	/**
	 * Wraps tool specifications to properly handle Flux return types by collecting all
	 * elements into a list.
	 * @param originalSpecs the original tool specifications from the annotation provider
	 * @param toolBeans the bean objects containing @McpTool annotated methods
	 * @return wrapped tool specifications that properly collect Flux elements
	 */
	public static List<McpStatelessServerFeatures.AsyncToolSpecification> processToolSpecifications(
			List<McpStatelessServerFeatures.AsyncToolSpecification> originalSpecs, List<Object> toolBeans) {

		List<McpStatelessServerFeatures.AsyncToolSpecification> processedSpecs = new ArrayList<>();

		for (McpStatelessServerFeatures.AsyncToolSpecification spec : originalSpecs) {
			ToolMethodInfo methodInfo = findToolMethod(toolBeans, spec.tool().name());
			if (methodInfo != null && methodInfo.returnsFlux()) {
				logger.info("Detected Flux return type for MCP tool '{}', applying collection wrapper",
						spec.tool().name());
				McpStatelessServerFeatures.AsyncToolSpecification wrappedSpec = wrapToolSpecificationForFlux(spec,
						methodInfo);
				processedSpecs.add(wrappedSpec);
			}
			else {
				processedSpecs.add(spec);
			}
		}

		return processedSpecs;
	}

	/**
	 * Finds the method annotated with @McpTool that matches the given tool name.
	 * @param toolBeans the bean objects containing @McpTool annotated methods
	 * @param toolName the name of the tool to find
	 * @return the ToolMethodInfo object, or null if not found
	 */
	private static ToolMethodInfo findToolMethod(List<Object> toolBeans, String toolName) {
		for (Object bean : toolBeans) {
			Class<?> clazz = bean.getClass();
			Method[] methods = ReflectionUtils.getAllDeclaredMethods(clazz);
			for (Method method : methods) {
				McpTool annotation = method.getAnnotation(McpTool.class);
				if (annotation != null && annotation.name().equals(toolName)) {
					return new ToolMethodInfo(bean, method);
				}
			}
		}
		return null;
	}

	/**
	 * Wraps a tool specification to collect all Flux elements before serialization.
	 * @param original the original tool specification
	 * @param methodInfo the method information including bean and method
	 * @return the wrapped tool specification
	 */
	private static McpStatelessServerFeatures.AsyncToolSpecification wrapToolSpecificationForFlux(
			McpStatelessServerFeatures.AsyncToolSpecification original, ToolMethodInfo methodInfo) {

		BiFunction<McpTransportContext, McpSchema.CallToolRequest, Mono<McpSchema.CallToolResult>> originalHandler = original
			.callHandler();

		BiFunction<McpTransportContext, McpSchema.CallToolRequest, Mono<McpSchema.CallToolResult>> wrappedHandler = (
				context, request) -> {
			try {
				// Invoke the method directly to get access to the Flux
				Object[] args = buildMethodArguments(methodInfo.method(), request.arguments());
				Object result = ReflectionUtils.invokeMethod(methodInfo.method(), methodInfo.bean(), args);

				if (result instanceof Flux) {
					// Collect all Flux elements into a list
					Flux<?> flux = (Flux<?>) result;
					return flux.collectList().flatMap(list -> {
						// Serialize the list to JSON
						try {
							String jsonContent = objectMapper.writeValueAsString(list);
							return Mono.just(new McpSchema.CallToolResult(
									List.of(new McpSchema.TextContent(jsonContent)), false));
						}
						catch (Exception e) {
							logger.error("Failed to serialize Flux result for tool '{}'", original.tool().name(), e);
							return Mono.just(new McpSchema.CallToolResult(
									List.of(new McpSchema.TextContent("Error: " + e.getMessage())), true));
						}
					});
				}
				else {
					// Fall back to original handler for non-Flux results
					return originalHandler.apply(context, request);
				}
			}
			catch (Exception e) {
				logger.error("Failed to invoke tool method '{}'", original.tool().name(), e);
				return Mono.just(
						new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("Error: " + e.getMessage())),
								true));
			}
		};

		return new McpStatelessServerFeatures.AsyncToolSpecification(original.tool(), wrappedHandler);
	}

	/**
	 * Builds method arguments from the request arguments map.
	 * @param method the method to invoke
	 * @param requestArgs the arguments from the CallToolRequest
	 * @return array of method arguments
	 */
	private static Object[] buildMethodArguments(Method method, Map<String, Object> requestArgs) {
		java.lang.reflect.Parameter[] parameters = method.getParameters();
		Object[] args = new Object[parameters.length];

		for (int i = 0; i < parameters.length; i++) {
			java.lang.reflect.Parameter param = parameters[i];
			McpToolParam paramAnnotation = param.getAnnotation(McpToolParam.class);

			if (paramAnnotation != null) {
				String paramName = paramAnnotation.name().isEmpty() ? param.getName() : paramAnnotation.name();
				Object value = requestArgs.get(paramName);

				// Type conversion if needed
				if (value != null) {
					args[i] = objectMapper.convertValue(value, param.getType());
				}
				else if (!paramAnnotation.required()) {
					args[i] = null;
				}
				else {
					throw new IllegalArgumentException("Required parameter '" + paramName + "' is missing");
				}
			}
			else {
				// Try to match by parameter name
				Object value = requestArgs.get(param.getName());
				if (value != null) {
					args[i] = objectMapper.convertValue(value, param.getType());
				}
				else {
					args[i] = null;
				}
			}
		}

		return args;
	}

	/**
	 * Holds information about a tool method.
	 */
	private static class ToolMethodInfo {

		private final Object bean;

		private final Method method;

		ToolMethodInfo(Object bean, Method method) {
			this.bean = bean;
			this.method = method;
			ReflectionUtils.makeAccessible(method);
		}

		Object bean() {
			return this.bean;
		}

		Method method() {
			return this.method;
		}

		boolean returnsFlux() {
			return Flux.class.isAssignableFrom(this.method.getReturnType());
		}

	}

}
