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

package org.springframework.ai.mcp.annotation.method.tool;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.function.Predicate;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpProgressToken;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.tool.support.ToolMethodParameterUtils;
import org.springframework.util.StringUtils;

/**
 * Utility methods for handling MCP tool method parameters.
 * <p>
 * This class contains only the common parameter handling shared by MCP tool schema
 * generation and method invocation. Caller-specific exchange/context parameter handling
 * remains in the caller.
 *
 * @author Michał Grandys
 * @since 2.0.0
 */
public final class McpToolMethodParameterUtils {

	private McpToolMethodParameterUtils() {
	}

	/**
	 * Return whether the parameter is a common MCP infrastructure parameter. Server
	 * exchange types are caller-specific and must be handled by the caller.
	 */
	public static boolean isInfrastructureParameter(Parameter parameter) {
		// Check if parameter is a request context type
		if (McpSyncRequestContext.class.isAssignableFrom(parameter.getType())
				|| McpAsyncRequestContext.class.isAssignableFrom(parameter.getType())) {
			return true;
		}

		// Check if parameter is annotated with @McpProgressToken
		if (parameter.isAnnotationPresent(McpProgressToken.class)) {
			return true;
		}

		// Check if parameter is McpMeta type
		if (McpMeta.class.isAssignableFrom(parameter.getType())) {
			return true;
		}

		// Check if parameter is CallToolRequest type
		if (CallToolRequest.class.isAssignableFrom(parameter.getType())) {
			return true;
		}

		// Check if parameter is McpTransportContext type
		return McpTransportContext.class.isAssignableFrom(parameter.getType());
	}

	public static void validateUniqueParameterNames(Method method,
			Predicate<Parameter> infrastructureParameterPredicate) {
		ToolMethodParameterUtils.validateUniqueParameterNames(method, infrastructureParameterPredicate,
				McpToolMethodParameterUtils::getParameterName);
	}

	public static String getParameterName(Parameter parameter) {
		McpToolParam toolParamAnnotation = parameter.getAnnotation(McpToolParam.class);
		if (toolParamAnnotation != null && StringUtils.hasText(toolParamAnnotation.name())) {
			return toolParamAnnotation.name();
		}
		return parameter.getName();
	}

}
