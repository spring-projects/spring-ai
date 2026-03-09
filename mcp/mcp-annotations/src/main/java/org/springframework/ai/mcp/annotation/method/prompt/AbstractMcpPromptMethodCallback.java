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

package org.springframework.ai.mcp.annotation.method.prompt;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.util.Assert;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpProgressToken;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.context.DefaultMcpAsyncRequestContext;
import org.springframework.ai.mcp.annotation.context.DefaultMcpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

/**
 * Abstract base class for creating callbacks around prompt methods.
 *
 * This class provides common functionality for both synchronous and asynchronous prompt
 * method callbacks.
 *
 * @author Christian Tzolov
 */
public abstract class AbstractMcpPromptMethodCallback {

	protected final Method method;

	protected final Object bean;

	protected final Prompt prompt;

	/**
	 * Constructor for AbstractMcpPromptMethodCallback.
	 * @param method The method to create a callback for
	 * @param bean The bean instance that contains the method
	 * @param prompt The prompt
	 */
	protected AbstractMcpPromptMethodCallback(Method method, Object bean, Prompt prompt) {
		this.method = method;
		this.bean = bean;
		this.prompt = prompt;
		this.validateMethod(this.method);
	}

	/**
	 * Validates that the method signature is compatible with the prompt callback.
	 * @param method The method to validate
	 * @throws IllegalArgumentException if the method signature is not compatible
	 */
	protected void validateMethod(Method method) {
		if (method == null) {
			throw new IllegalArgumentException("Method must not be null");
		}

		this.validateReturnType(method);
		this.validateParameters(method);
	}

	/**
	 * Validates that the method return type is compatible with the prompt callback.
	 * @param method The method to validate
	 * @throws IllegalArgumentException if the return type is not compatible
	 */
	protected abstract void validateReturnType(Method method);

	/**
	 * Checks if a parameter type is compatible with the exchange type.
	 * @param paramType The parameter type to check
	 * @return true if the parameter type is compatible with the exchange type, false
	 * otherwise
	 */
	protected abstract boolean isSupportedExchangeOrContextType(Class<?> paramType);

	protected void validateParamType(Class<?> paramType) {
	}

	/**
	 * Validates method parameters.
	 * @param method The method to validate
	 * @throws IllegalArgumentException if the parameters are not compatible
	 */
	protected void validateParameters(Method method) {
		java.lang.reflect.Parameter[] parameters = method.getParameters();

		// Check for duplicate parameter types
		boolean hasExchangeParam = false;
		boolean hasRequestParam = false;
		boolean hasMapParam = false;
		boolean hasProgressTokenParam = false;
		boolean hasMetaParam = false;
		boolean hasRequestContextParam = false;

		for (java.lang.reflect.Parameter param : parameters) {
			Class<?> paramType = param.getType();

			this.validateParamType(paramType);

			// Skip @McpProgressToken annotated parameters from validation
			if (param.isAnnotationPresent(McpProgressToken.class)) {
				if (hasProgressTokenParam) {
					throw new IllegalArgumentException("Method cannot have more than one @McpProgressToken parameter: "
							+ method.getName() + " in " + method.getDeclaringClass().getName());
				}
				hasProgressTokenParam = true;
				continue;
			}

			// Skip McpMeta parameters from validation
			if (McpMeta.class.isAssignableFrom(paramType)) {
				if (hasMetaParam) {
					throw new IllegalArgumentException("Method cannot have more than one McpMeta parameter: "
							+ method.getName() + " in " + method.getDeclaringClass().getName());
				}
				hasMetaParam = true;
				continue;
			}

			if (McpSyncRequestContext.class.isAssignableFrom(paramType)) {
				if (hasRequestContextParam) {
					throw new IllegalArgumentException("Method cannot have more than one request context parameter: "
							+ method.getName() + " in " + method.getDeclaringClass().getName());
				}
				if (McpPredicates.isReactiveReturnType.test(method)) {
					throw new IllegalArgumentException(
							"Sync complete methods should use McpSyncRequestContext instead of McpAsyncRequestContext parameter: "
									+ method.getName() + " in " + method.getDeclaringClass().getName());
				}
				hasRequestContextParam = true;
			}
			else if (McpAsyncRequestContext.class.isAssignableFrom(paramType)) {
				if (hasRequestContextParam) {
					throw new IllegalArgumentException("Method cannot have more than one request context parameter: "
							+ method.getName() + " in " + method.getDeclaringClass().getName());
				}
				if (McpPredicates.isNotReactiveReturnType.test(method)) {
					throw new IllegalArgumentException(
							"Async complete methods should use McpAsyncRequestContext instead of McpSyncRequestContext parameter: "
									+ method.getName() + " in " + method.getDeclaringClass().getName());
				}
				hasRequestContextParam = true;
			}
			else if (isSupportedExchangeOrContextType(paramType)) {
				if (hasExchangeParam) {
					throw new IllegalArgumentException("Method cannot have more than one exchange parameter: "
							+ method.getName() + " in " + method.getDeclaringClass().getName());
				}
				hasExchangeParam = true;
			}
			else if (GetPromptRequest.class.isAssignableFrom(paramType)) {
				if (hasRequestParam) {
					throw new IllegalArgumentException("Method cannot have more than one GetPromptRequest parameter: "
							+ method.getName() + " in " + method.getDeclaringClass().getName());
				}
				hasRequestParam = true;
			}
			else if (Map.class.isAssignableFrom(paramType)) {
				if (hasMapParam) {
					throw new IllegalArgumentException("Method cannot have more than one Map parameter: "
							+ method.getName() + " in " + method.getDeclaringClass().getName());
				}
				hasMapParam = true;
			}
			// Other parameter types are assumed to be individual arguments
		}
	}

	protected abstract Object assignExchangeType(Class<?> paramType, Object exchange);

	/**
	 * Builds the arguments array for invoking the method.
	 * <p>
	 * This method constructs an array of arguments based on the method's parameter types
	 * and the available values (exchange, request, arguments).
	 * @param method The method to build arguments for
	 * @param exchange The server exchange
	 * @param request The prompt request
	 * @return An array of arguments for the method invocation
	 */
	protected Object[] buildArgs(Method method, Object exchange, GetPromptRequest request) {
		java.lang.reflect.Parameter[] parameters = method.getParameters();
		Object[] args = new Object[parameters.length];

		// First, handle @McpProgressToken annotated parameters
		for (int i = 0; i < parameters.length; i++) {
			if (parameters[i].isAnnotationPresent(McpProgressToken.class)) {
				// GetPromptRequest doesn't have a progressToken method in the current
				// spec
				// Set to null for now - this would need to be updated when the spec
				// supports it
				args[i] = null;
			}
		}

		// Handle McpMeta parameters
		for (int i = 0; i < parameters.length; i++) {
			if (McpMeta.class.isAssignableFrom(parameters[i].getType())) {
				args[i] = request != null ? new McpMeta(request.meta()) : new McpMeta(null);
			}
		}

		for (int i = 0; i < parameters.length; i++) {
			// Skip if already set (e.g., @McpProgressToken, McpMeta)
			if (args[i] != null || parameters[i].isAnnotationPresent(McpProgressToken.class)
					|| McpMeta.class.isAssignableFrom(parameters[i].getType())) {
				continue;
			}

			java.lang.reflect.Parameter param = parameters[i];
			Class<?> paramType = param.getType();

			if (McpTransportContext.class.isAssignableFrom(paramType)
					|| McpSyncServerExchange.class.isAssignableFrom(paramType)
					|| McpAsyncServerExchange.class.isAssignableFrom(paramType)) {

				args[i] = this.assignExchangeType(paramType, exchange);
			}
			else if (McpSyncRequestContext.class.isAssignableFrom(paramType)) {
				args[i] = DefaultMcpSyncRequestContext.builder()
					.exchange((McpSyncServerExchange) exchange)
					.request(request)
					.build();
			}
			else if (McpAsyncRequestContext.class.isAssignableFrom(paramType)) {
				args[i] = DefaultMcpAsyncRequestContext.builder()
					.exchange((McpAsyncServerExchange) exchange)
					.request(request)
					.build();
			}
			else if (GetPromptRequest.class.isAssignableFrom(paramType)) {
				args[i] = request;
			}
			else if (Map.class.isAssignableFrom(paramType)) {
				args[i] = request.arguments() != null ? request.arguments() : new HashMap<>();
			}
			else {
				// For individual argument parameters, extract from the request arguments
				McpArg arg = param.getAnnotation(McpArg.class);
				String paramName = arg != null && !arg.name().isBlank() ? arg.name() : param.getName();
				if (request.arguments() != null && request.arguments().containsKey(paramName)) {
					Object argValue = request.arguments().get(paramName);
					args[i] = convertArgumentValue(argValue, paramType);
				}
				else {
					args[i] = null; // No matching argument found
				}
			}
		}

		return args;
	}

	/**
	 * Converts an argument value to the expected parameter type.
	 * @param value The value to convert
	 * @param targetType The target type
	 * @return The converted value
	 */
	protected Object convertArgumentValue(Object value, Class<?> targetType) {
		if (value == null) {
			return null;
		}

		// Handle primitive types and their wrappers
		if (targetType == String.class) {
			return value.toString();
		}
		else if (targetType == Integer.class || targetType == int.class) {
			if (value instanceof Number) {
				return ((Number) value).intValue();
			}
			else {
				return Integer.parseInt(value.toString());
			}
		}
		else if (targetType == Long.class || targetType == long.class) {
			if (value instanceof Number) {
				return ((Number) value).longValue();
			}
			else {
				return Long.parseLong(value.toString());
			}
		}
		else if (targetType == Double.class || targetType == double.class) {
			if (value instanceof Number) {
				return ((Number) value).doubleValue();
			}
			else {
				return Double.parseDouble(value.toString());
			}
		}
		else if (targetType == Boolean.class || targetType == boolean.class) {
			if (value instanceof Boolean) {
				return value;
			}
			else {
				return Boolean.parseBoolean(value.toString());
			}
		}

		// For other types, return as is and hope for the best
		return value;
	}

	/**
	 * Converts a method result to a GetPromptResult.
	 * @param result The result to convert
	 * @return The converted GetPromptResult
	 */
	@SuppressWarnings("unchecked")
	protected GetPromptResult convertToGetPromptResult(Object result) {
		if (result instanceof GetPromptResult) {
			return (GetPromptResult) result;
		}
		else if (result instanceof List) {
			List<?> list = (List<?>) result;
			if (!list.isEmpty()) {
				if (list.get(0) instanceof PromptMessage) {
					return new GetPromptResult(null, (List<PromptMessage>) list);
				}
				else if (list.get(0) instanceof String) {
					// Convert List<String> to List<PromptMessage>
					List<PromptMessage> messages = ((List<String>) list).stream()
						.map(text -> new PromptMessage(io.modelcontextprotocol.spec.McpSchema.Role.ASSISTANT,
								new io.modelcontextprotocol.spec.McpSchema.TextContent(text)))
						.collect(java.util.stream.Collectors.toList());
					return new GetPromptResult(null, messages);
				}
			}
		}
		else if (result instanceof PromptMessage) {
			// If the result is a single PromptMessage, wrap it in a list
			return new GetPromptResult(null, List.of((PromptMessage) result));
		}
		else if (result instanceof String) {
			// If the result is a simple string, create a single assistant message with
			// that content
			return new GetPromptResult(null,
					List.of(new PromptMessage(io.modelcontextprotocol.spec.McpSchema.Role.ASSISTANT,
							new io.modelcontextprotocol.spec.McpSchema.TextContent((String) result))));
		}

		throw new IllegalArgumentException(
				"Unsupported result type: " + (result != null ? result.getClass().getName() : "null"));
	}

	/**
	 * Abstract builder for creating prompt method callback instances.
	 *
	 * @param <B> The builder type
	 * @param <T> The callback type
	 */
	protected abstract static class AbstractBuilder<B extends AbstractBuilder<B, T>, T extends AbstractMcpPromptMethodCallback> {

		protected Method method;

		protected Object bean;

		protected Prompt prompt;

		/**
		 * Set the method to create a callback for.
		 * @param method The method to create a callback for
		 * @return This builder
		 */
		@SuppressWarnings("unchecked")
		public B method(Method method) {
			this.method = method;
			return (B) this;
		}

		/**
		 * Set the bean instance that contains the method.
		 * @param bean The bean instance
		 * @return This builder
		 */
		@SuppressWarnings("unchecked")
		public B bean(Object bean) {
			this.bean = bean;
			return (B) this;
		}

		/**
		 * Set the prompt.
		 * @param prompt The prompt
		 * @return This builder
		 */
		@SuppressWarnings("unchecked")
		public B prompt(Prompt prompt) {
			this.prompt = prompt;
			return (B) this;
		}

		/**
		 * Validate the builder state.
		 * @throws IllegalArgumentException if the builder state is invalid
		 */
		protected void validate() {
			Assert.notNull(this.method, "Method must not be null");
			Assert.notNull(this.bean, "Bean must not be null");
			Assert.notNull(this.prompt, "Prompt must not be null");
		}

		/**
		 * Build the callback.
		 * @return A new callback instance
		 */
		public abstract T build();

	}

}
