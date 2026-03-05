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

package org.springframework.ai.mcp.annotation.method.elicitation;

import java.lang.reflect.Method;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;
import org.springframework.ai.util.json.JsonParser;

/**
 * Class for creating Function callbacks around elicitation methods that return Mono.
 *
 * This class provides a way to convert methods annotated with {@link McpElicitation} into
 * callback functions that can be used to handle elicitation requests in a reactive way.
 * It supports methods with a single ElicitRequest parameter.
 *
 * @author Christian Tzolov
 */
public final class AsyncMcpElicitationMethodCallback extends AbstractMcpElicitationMethodCallback
		implements Function<ElicitRequest, Mono<ElicitResult>> {

	private AsyncMcpElicitationMethodCallback(Builder builder) {
		super(builder.method, builder.bean);
	}

	/**
	 * Apply the callback to the given request.
	 * <p>
	 * This method builds the arguments for the method call, invokes the method, and
	 * returns a Mono that completes with the result.
	 * @param request The elicitation request, must not be null
	 * @return A Mono that completes with the result of the method invocation
	 * @throws McpElicitationMethodException if there is an error invoking the elicitation
	 * method
	 * @throws IllegalArgumentException if the request is null
	 */
	@Override
	public Mono<ElicitResult> apply(ElicitRequest request) {
		if (request == null) {
			return Mono.error(new IllegalArgumentException("Request must not be null"));
		}

		try {
			// Build arguments for the method call
			Object[] args = this.buildArgs(this.method, null, request);

			// Invoke the method
			this.method.setAccessible(true);
			Object result = this.method.invoke(this.bean, args);

			// If the method returns a Mono, handle it
			if (result instanceof Mono) {
				Mono<?> monoResult = (Mono<?>) result;
				return monoResult.flatMap(value -> {
					if (value instanceof StructuredElicitResult) {
						StructuredElicitResult<?> structuredElicitResult = (StructuredElicitResult<?>) value;

						var content = structuredElicitResult.structuredContent() != null
								? JsonParser.toMap(structuredElicitResult.structuredContent()) : null;

						return Mono.just(ElicitResult.builder()
							.message(structuredElicitResult.action())
							.content(content)
							.meta(structuredElicitResult.meta())
							.build());

					}
					else if (value instanceof ElicitResult) {
						return Mono.just((ElicitResult) value);
					}

					return Mono.error(new McpElicitationMethodException(
							"Method must return Mono<ElicitResult> or Mono<StructuredElicitResult>: "
									+ this.method.getName()));

				});
			}
			// Otherwise, throw an exception
			return Mono.error(new McpElicitationMethodException(
					"Method must return Mono<ElicitResult> or Mono<StructuredElicitResult>: " + this.method.getName()));
		}
		catch (Exception e) {
			return Mono.error(new McpElicitationMethodException(
					"Error invoking elicitation method: " + this.method.getName(), e));
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

		if (!Mono.class.isAssignableFrom(returnType)) {
			throw new IllegalArgumentException(
					"Method must return Mono<ElicitResult> or Mono<StructuredElicitResult>: " + method.getName()
							+ " in " + method.getDeclaringClass().getName() + " returns " + returnType.getName());
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
	 * Builder for creating AsyncMcpElicitationMethodCallback instances.
	 * <p>
	 * This builder provides a fluent API for constructing
	 * AsyncMcpElicitationMethodCallback instances with the required parameters.
	 */
	public final static class Builder extends AbstractBuilder<Builder, AsyncMcpElicitationMethodCallback> {

		/**
		 * Build the callback.
		 * @return A new AsyncMcpElicitationMethodCallback instance
		 */
		@Override
		public AsyncMcpElicitationMethodCallback build() {
			validate();
			return new AsyncMcpElicitationMethodCallback(this);
		}

	}

}
