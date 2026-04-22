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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpProgressToken;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpRequestContextTypes;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.util.json.JsonParser;

/**
 * Abstract base class for creating Function callbacks around tool methods.
 *
 * This class provides common functionality for converting methods annotated with
 * {@link McpTool} into callback functions that can be used to handle tool requests. It
 * contains all the shared logic between synchronous and asynchronous implementations.
 *
 * @param <T> The type of the context parameter (e.g., McpTransportContext,
 * McpSyncServerExchange, or McpAsyncServerExchange)
 * @author Christian Tzolov
 */
public abstract class AbstractMcpToolMethodCallback<T, RC extends McpRequestContextTypes<?>> {

	protected final Method toolMethod;

	protected final Object toolObject;

	protected final ReturnMode returnMode;

	protected AbstractMcpToolMethodCallback(ReturnMode returnMode, Method toolMethod, Object toolObject) {
		this.toolMethod = toolMethod;
		this.toolObject = toolObject;
		this.returnMode = returnMode;
	}

	/**
	 * Invokes the tool method with the provided arguments.
	 * @param methodArguments The arguments to pass to the method
	 * @return The result of the method invocation
	 * @throws IllegalStateException if the method cannot be accessed
	 * @throws RuntimeException if there's an error invoking the method
	 */
	protected Object callMethod(Object[] methodArguments) {
		this.toolMethod.setAccessible(true);

		Object result;
		try {
			result = this.toolMethod.invoke(this.toolObject, methodArguments);
		}
		catch (IllegalAccessException ex) {
			throw new RuntimeException("Failed to access tool method", ex);
		}
		catch (InvocationTargetException ex) {
			throw new RuntimeException("Error invoking method: " + this.toolMethod.getName(), ex.getCause());
		}
		return result;
	}

	/**
	 * Builds the method arguments from the context, tool input arguments, and optionally
	 * the full request.
	 * @param exchangeOrContext The exchange or context object (e.g.,
	 * McpSyncServerExchange, McpAsyncServerExchange, or McpTransportContext)
	 * @param toolInputArguments The input arguments from the tool request
	 * @param request The full CallToolRequest (optional, can be null)
	 * @return An array of method arguments
	 */
	protected Object[] buildMethodArguments(T exchangeOrContext, Map<String, Object> toolInputArguments,
			CallToolRequest request) {

		return Stream.of(this.toolMethod.getParameters()).map(parameter -> {

			if (McpSyncRequestContext.class.isAssignableFrom(parameter.getType())
					|| McpAsyncRequestContext.class.isAssignableFrom(parameter.getType())) {

				return this.createRequestContext(exchangeOrContext, request);
			}

			// Check if parameter is annotated with @McpProgressToken
			if (parameter.isAnnotationPresent(McpProgressToken.class)) {
				// Return the progress token from the request
				return request != null ? request.progressToken() : null;
			}

			// Check if parameter is McpMeta type
			if (McpMeta.class.isAssignableFrom(parameter.getType())) {
				// Return the meta from the request wrapped in McpMeta
				return request != null ? new McpMeta(request.meta()) : new McpMeta(null);
			}

			// Check if parameter is CallToolRequest type
			if (CallToolRequest.class.isAssignableFrom(parameter.getType())) {
				return request;
			}

			if (McpTransportContext.class.isAssignableFrom(parameter.getType())) {
				return this.resolveTransportContext(exchangeOrContext);
			}

			if (isExchangeOrContextType(parameter.getType())) {
				return exchangeOrContext;
			}

			Object rawArgument = toolInputArguments.get(parameter.getName());
			return buildTypedArgument(rawArgument, parameter.getParameterizedType());
		}).toArray();
	}

	/**
	 * Builds a typed argument from a raw value and type information.
	 * @param value The raw value
	 * @param type The target type
	 * @return The typed argument
	 */
	protected Object buildTypedArgument(Object value, Type type) {
		if (value == null) {
			return null;
		}

		if (type instanceof Class<?>) {
			return JsonParser.toTypedObject(value, (Class<?>) type);
		}

		// For generic types, use the fromJson method that accepts Type
		String json = JsonParser.toJson(value);
		return JsonParser.fromJson(json, type);
	}

	/**
	 * Converts a method result value to a CallToolResult based on the return mode and
	 * type. This method contains the common logic for processing results that is shared
	 * between synchronous and asynchronous implementations.
	 * @param result The result value to convert
	 * @return A CallToolResult representing the processed result
	 */
	protected CallToolResult convertValueToCallToolResult(Object result) {
		// Return the result if it's already a CallToolResult
		if (result instanceof CallToolResult) {
			return (CallToolResult) result;
		}

		Type returnType = this.toolMethod.getGenericReturnType();

		if (this.returnMode == ReturnMode.VOID || returnType == Void.TYPE || returnType == void.class) {
			return CallToolResult.builder().addTextContent(JsonParser.toJson("Done")).build();
		}

		if (this.returnMode == ReturnMode.STRUCTURED) {
			String jsonOutput = JsonParser.toJson(result);
			Object structuredOutput = JsonParser.fromJson(jsonOutput, Object.class);
			return CallToolResult.builder().structuredContent(structuredOutput).build();
		}

		// Default to text output
		if (result == null) {
			return CallToolResult.builder().addTextContent("null").build();
		}

		// For string results in TEXT mode, return the string directly without JSON
		// serialization
		if (result instanceof String) {
			return CallToolResult.builder().addTextContent((String) result).build();
		}

		// For other types, serialize to JSON
		return CallToolResult.builder().addTextContent(JsonParser.toJson(result)).build();
	}

	/**
	 * Creates the base error message for exceptions that occur during method invocation.
	 * @param e The exception that occurred
	 * @return The error message string
	 */
	protected String createErrorMessage(Throwable e) {
		return "Error invoking method: %s".formatted(e.getMessage());
	}

	/**
	 * Determines if the given parameter type is an exchange or context type that should
	 * be injected. Subclasses must implement this method to specify which types are
	 * considered exchange or context types.
	 * @param paramType The parameter type to check
	 * @return true if the parameter type is an exchange or context type, false otherwise
	 */
	protected abstract boolean isExchangeOrContextType(Class<?> paramType);

	protected Throwable findCauseUsingPlainJava(Throwable throwable) {
		Objects.requireNonNull(throwable);
		Throwable rootCause = throwable;
		while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
			rootCause = rootCause.getCause();
		}
		return rootCause;
	}

	protected abstract RC createRequestContext(T exchange, CallToolRequest request);

	/**
	 * Resolves the {@link McpTransportContext} from the exchange or context object.
	 * Subclasses must implement this method to extract or return the transport context
	 * appropriately based on the type of the exchange parameter.
	 * @param exchangeOrContext The exchange or context object
	 * @return The resolved McpTransportContext
	 */
	protected abstract McpTransportContext resolveTransportContext(T exchangeOrContext);

}
