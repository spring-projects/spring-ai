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

package org.springframework.ai.mcp.annotation.method.sampling;

import java.lang.reflect.Method;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult;

import org.springframework.ai.mcp.annotation.McpSampling;

/**
 * Class for creating Function callbacks around sampling methods.
 *
 * This class provides a way to convert methods annotated with {@link McpSampling} into
 * callback functions that can be used to handle sampling requests. It supports methods
 * with a single CreateMessageRequest parameter.
 *
 * @author Christian Tzolov
 */
public final class SyncMcpSamplingMethodCallback extends AbstractMcpSamplingMethodCallback
		implements Function<CreateMessageRequest, CreateMessageResult> {

	private SyncMcpSamplingMethodCallback(Builder builder) {
		super(builder.method, builder.bean);
	}

	/**
	 * Apply the callback to the given request.
	 * <p>
	 * This method builds the arguments for the method call, invokes the method, and
	 * returns the result.
	 * @param request The sampling request, must not be null
	 * @return The result of the method invocation
	 * @throws McpSamplingMethodException if there is an error invoking the sampling
	 * method
	 * @throws IllegalArgumentException if the request is null
	 */
	@Override
	public CreateMessageResult apply(CreateMessageRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Request must not be null");
		}

		try {
			// Build arguments for the method call
			Object[] args = this.buildArgs(this.method, null, request);

			// Invoke the method
			this.method.setAccessible(true);
			Object result = this.method.invoke(this.bean, args);

			// Return the result
			return (CreateMessageResult) result;
		}
		catch (Exception e) {
			throw new McpSamplingMethodException("Error invoking sampling method: " + this.method.getName(), e);
		}
	}

	/**
	 * Validates that the method return type is compatible with the sampling callback.
	 * @param method The method to validate
	 * @throws IllegalArgumentException if the return type is not compatible
	 */
	@Override
	protected void validateReturnType(Method method) {
		Class<?> returnType = method.getReturnType();

		if (!CreateMessageResult.class.isAssignableFrom(returnType)) {
			throw new IllegalArgumentException("Method must return CreateMessageResult: " + method.getName() + " in "
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
		// No exchange type for sampling methods
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
	 * Builder for creating SyncMcpSamplingMethodCallback instances.
	 * <p>
	 * This builder provides a fluent API for constructing SyncMcpSamplingMethodCallback
	 * instances with the required parameters.
	 */
	public static class Builder extends AbstractBuilder<Builder, SyncMcpSamplingMethodCallback> {

		/**
		 * Build the callback.
		 * @return A new SyncMcpSamplingMethodCallback instance
		 */
		@Override
		public SyncMcpSamplingMethodCallback build() {
			validate();
			return new SyncMcpSamplingMethodCallback(this);
		}

	}

}
