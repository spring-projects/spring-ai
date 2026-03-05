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

package org.springframework.ai.mcp.annotation.method.complete;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult.CompleteCompletion;
import io.modelcontextprotocol.util.DefaultMcpUriTemplateManagerFactory;

import org.springframework.ai.mcp.annotation.McpComplete;

/**
 * Class for creating BiFunction callbacks around complete methods.
 *
 * This class provides a way to convert methods annotated with {@link McpComplete} into
 * callback functions that can be used to handle completion requests. It supports various
 * method signatures and return types, and handles both prompt and URI template
 * completions.
 *
 * @author Christian Tzolov
 */
public final class SyncMcpCompleteMethodCallback extends AbstractMcpCompleteMethodCallback
		implements BiFunction<McpSyncServerExchange, CompleteRequest, CompleteResult> {

	private SyncMcpCompleteMethodCallback(Builder builder) {
		super(builder.method, builder.bean, builder.prompt, builder.uri, builder.uriTemplateManagerFactory);
		this.validateMethod(this.method);
	}

	/**
	 * Apply the callback to the given exchange and request.
	 * <p>
	 * This method builds the arguments for the method call, invokes the method, and
	 * converts the result to a CompleteResult.
	 * @param exchange The server exchange, may be null if the method doesn't require it
	 * @param request The complete request, must not be null
	 * @return The complete result
	 * @throws McpCompleteMethodException if there is an error invoking the complete
	 * method
	 * @throws IllegalArgumentException if the request is null
	 */
	@Override
	public CompleteResult apply(McpSyncServerExchange exchange, CompleteRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Request must not be null");
		}

		try {
			// Build arguments for the method call
			Object[] args = this.buildArgs(this.method, exchange, request);

			// Invoke the method
			this.method.setAccessible(true);
			Object result = this.method.invoke(this.bean, args);

			// Convert the result to a CompleteResult
			return convertToCompleteResult(result);
		}
		catch (Exception e) {
			throw new McpCompleteMethodException("Error invoking complete method: " + this.method.getName(), e);
		}
	}

	/**
	 * Converts the method result to a CompleteResult.
	 * @param result The method result
	 * @return The CompleteResult
	 */
	private CompleteResult convertToCompleteResult(Object result) {
		if (result == null) {
			return new CompleteResult(new CompleteCompletion(List.of(), 0, false));
		}

		if (result instanceof CompleteResult) {
			return (CompleteResult) result;
		}

		if (result instanceof CompleteCompletion) {
			return new CompleteResult((CompleteCompletion) result);
		}

		if (result instanceof List) {
			List<?> list = (List<?>) result;
			List<String> values = new ArrayList<>();

			for (Object item : list) {
				if (item instanceof String) {
					values.add((String) item);
				}
				else {
					throw new IllegalArgumentException("List items must be of type String");
				}
			}

			return new CompleteResult(new CompleteCompletion(values, values.size(), false));
		}

		if (result instanceof String) {
			return new CompleteResult(new CompleteCompletion(List.of((String) result), 1, false));
		}

		throw new IllegalArgumentException("Unsupported return type: " + result.getClass().getName());
	}

	/**
	 * Validates that the method return type is compatible with the complete callback.
	 * @param method The method to validate
	 * @throws IllegalArgumentException if the return type is not compatible
	 */
	@Override
	protected void validateReturnType(Method method) {
		Class<?> returnType = method.getReturnType();

		boolean validReturnType = CompleteResult.class.isAssignableFrom(returnType)
				|| CompleteCompletion.class.isAssignableFrom(returnType) || List.class.isAssignableFrom(returnType)
				|| String.class.isAssignableFrom(returnType);

		if (!validReturnType) {
			throw new IllegalArgumentException(
					"Method must return either CompleteResult, CompleteCompletion, List<String>, " + "or String: "
							+ method.getName() + " in " + method.getDeclaringClass().getName() + " returns "
							+ returnType.getName());
		}
	}

	@Override
	protected McpTransportContext resolveTransportContext(Object exchange) {
		if (exchange instanceof McpSyncServerExchange e) {
			return e.transportContext();
		}
		return null;
	}

	/**
	 * Create a new builder.
	 * @return A new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Checks if a parameter type is compatible with the exchange type.
	 * @param paramType The parameter type to check
	 * @return true if the parameter type is compatible with the exchange type, false
	 * otherwise
	 */
	@Override
	protected boolean isExchangeType(Class<?> paramType) {
		return McpSyncServerExchange.class.isAssignableFrom(paramType);
	}

	/**
	 * Builder for creating SyncMcpCompleteMethodCallback instances.
	 * <p>
	 * This builder provides a fluent API for constructing SyncMcpCompleteMethodCallback
	 * instances with the required parameters.
	 */
	public static class Builder extends AbstractBuilder<Builder, SyncMcpCompleteMethodCallback> {

		/**
		 * Constructor for Builder.
		 */
		public Builder() {
			this.uriTemplateManagerFactory = new DefaultMcpUriTemplateManagerFactory();
		}

		/**
		 * Build the callback.
		 * @return A new SyncMcpCompleteMethodCallback instance
		 */
		@Override
		public SyncMcpCompleteMethodCallback build() {
			validate();
			return new SyncMcpCompleteMethodCallback(this);
		}

	}

}
