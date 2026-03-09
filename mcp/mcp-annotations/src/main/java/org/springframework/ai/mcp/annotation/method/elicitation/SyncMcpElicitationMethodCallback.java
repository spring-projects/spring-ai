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

package org.springframework.ai.mcp.annotation.method.elicitation;

import java.lang.reflect.Method;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonParser;

/**
 * Class for creating Function callbacks around elicitation methods.
 *
 * This class provides a way to convert methods annotated with {@link McpElicitation} into
 * callback functions that can be used to handle elicitation requests. It supports methods
 * with a single ElicitRequest parameter.
 *
 * @author Christian Tzolov
 */
public final class SyncMcpElicitationMethodCallback extends AbstractMcpElicitationMethodCallback
		implements Function<ElicitRequest, ElicitResult> {

	private SyncMcpElicitationMethodCallback(Builder builder) {
		super(builder.method, builder.bean);
	}

	/**
	 * Apply the callback to the given request.
	 * <p>
	 * This method builds the arguments for the method call, invokes the method, and
	 * returns the result.
	 * @param request The elicitation request, must not be null
	 * @return The result of the method invocation
	 * @throws McpElicitationMethodException if there is an error invoking the elicitation
	 * method
	 * @throws IllegalArgumentException if the request is null
	 */
	@Override
	public ElicitResult apply(ElicitRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Request must not be null");
		}

		try {
			// Build arguments for the method call
			Object[] args = this.buildArgs(this.method, null, request);

			// Invoke the method
			this.method.setAccessible(true);
			Object result = this.method.invoke(this.bean, args);

			if (this.method.getReturnType().isAssignableFrom(StructuredElicitResult.class)) {
				StructuredElicitResult<?> structuredElicitResult = (StructuredElicitResult<?>) result;
				var content = structuredElicitResult.structuredContent() != null
						? McpJsonParser.toMap(structuredElicitResult.structuredContent()) : null;

				return ElicitResult.builder()
					.message(structuredElicitResult.action())
					.content(content)
					.meta(structuredElicitResult.meta())
					.build();
			}
			else if (this.method.getReturnType().isAssignableFrom(ElicitResult.class)) {
				// If the method returns ElicitResult, return it directly
				return (ElicitResult) result;

			}
			else {

				// TODO add support for methods returning simple types or Objects of
				// elicitation schema type.

				throw new IllegalStateException("Method must return ElicitResult or StructuredElicitResult: "
						+ this.method.getName() + " in " + this.method.getDeclaringClass().getName() + " returns "
						+ this.method.getReturnType().getName());
			}
		}
		catch (Exception e) {
			throw new McpElicitationMethodException("Error invoking elicitation method: " + this.method.getName(), e);
		}
	}

	/**
	 * Validates that the method return type is compatible with the elicitation callback.
	 * @param method The method to validate
	 * @throws IllegalArgumentException if the return type is not compatible
	 */
	@Override
	protected void validateReturnType(Method method) {
		Class<?> returnType = method.getReturnType();

		if (!ElicitResult.class.isAssignableFrom(returnType)
				&& !StructuredElicitResult.class.isAssignableFrom(returnType)) {
			throw new IllegalArgumentException("Method must return ElicitResult: " + method.getName() + " in "
					+ method.getDeclaringClass().getName() + " returns " + returnType.getName());
		}
	}

	/**
	 * Checks if a parameter type is compatible with the exchange type.
	 * @param paramType The parameter type to check
	 * @return true if the parameter type is compatible with the exchange type, false
	 * otherwise
	 */
	@Override
	protected boolean isExchangeType(Class<?> paramType) {
		// No exchange type for elicitation methods
		return false;
	}

	/**
	 * Create a new builder.
	 * @return A new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for creating SyncMcpElicitationMethodCallback instances.
	 * <p>
	 * This builder provides a fluent API for constructing
	 * SyncMcpElicitationMethodCallback instances with the required parameters.
	 */
	public static class Builder extends AbstractBuilder<Builder, SyncMcpElicitationMethodCallback> {

		/**
		 * Build the callback.
		 * @return A new SyncMcpElicitationMethodCallback instance
		 */
		@Override
		public SyncMcpElicitationMethodCallback build() {
			validate();
			return new SyncMcpElicitationMethodCallback(this);
		}

	}

}
