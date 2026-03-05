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

package org.springframework.ai.mcp.annotation.method.changed.resource;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpResourceListChanged;

/**
 * Class for creating Function callbacks around resource list changed consumer methods
 * that return Mono.
 *
 * This class provides a way to convert methods annotated with
 * {@link McpResourceListChanged} into callback functions that can be used to handle
 * resource list change notifications in a reactive way. It supports methods with a single
 * List&lt;McpSchema.Resource&gt; parameter.
 *
 * @author Christian Tzolov
 */
public final class AsyncMcpResourceListChangedMethodCallback extends AbstractMcpResourceListChangedMethodCallback
		implements Function<List<McpSchema.Resource>, Mono<Void>> {

	private AsyncMcpResourceListChangedMethodCallback(Builder builder) {
		super(builder.method, builder.bean);
	}

	/**
	 * Apply the callback to the given resource list.
	 * <p>
	 * This method builds the arguments for the method call, invokes the method, and
	 * returns a Mono that completes when the method execution is done.
	 * @param updatedResources The updated list of resources, must not be null
	 * @return A Mono that completes when the method execution is done
	 * @throws McpResourceListChangedConsumerMethodException if there is an error invoking
	 * the resource list changed consumer method
	 * @throws IllegalArgumentException if the updatedResources is null
	 */
	@Override
	public Mono<Void> apply(List<McpSchema.Resource> updatedResources) {
		if (updatedResources == null) {
			return Mono.error(new IllegalArgumentException("Updated resources list must not be null"));
		}

		try {
			// Build arguments for the method call
			Object[] args = this.buildArgs(this.method, null, updatedResources);

			// Invoke the method
			this.method.setAccessible(true);
			Object result = this.method.invoke(this.bean, args);

			// If the method returns a Mono, handle it
			if (result instanceof Mono) {
				// We need to handle the case where the Mono is not a Mono<Void>
				// This is expected by the test testInvalidMonoReturnType
				Mono<?> monoResult = (Mono<?>) result;

				// Convert the Mono to a Mono<Void> by checking the value
				// If the value is not null (i.e., not Void), throw a ClassCastException
				return monoResult.flatMap(value -> {
					if (value != null) {
						// This will be caught by the test testInvalidMonoReturnType
						throw new ClassCastException(
								"Expected Mono<Void> but got Mono<" + value.getClass().getName() + ">");
					}
					return Mono.empty();
				}).then();
			}
			// If the method returns void, return an empty Mono
			return Mono.empty();
		}
		catch (Exception e) {
			return Mono.error(new McpResourceListChangedConsumerMethodException(
					"Error invoking resource list changed consumer method: " + this.method.getName(), e));
		}
	}

	/**
	 * Validates that the method return type is compatible with the resource list changed
	 * consumer callback.
	 * @param method The method to validate
	 * @throws IllegalArgumentException if the return type is not compatible
	 */
	@Override
	protected void validateReturnType(Method method) {
		Class<?> returnType = method.getReturnType();

		if (returnType != void.class && !Mono.class.isAssignableFrom(returnType)) {
			throw new IllegalArgumentException("Method must have void or Mono<Void> return type: " + method.getName()
					+ " in " + method.getDeclaringClass().getName() + " returns " + returnType.getName());
		}
	}

	/**
	 * Create a new builder.
	 * @return A new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for creating AsyncMcpResourceListChangedMethodCallback instances.
	 * <p>
	 * This builder provides a fluent API for constructing
	 * AsyncMcpResourceListChangedMethodCallback instances with the required parameters.
	 */
	public static class Builder extends AbstractBuilder<Builder, AsyncMcpResourceListChangedMethodCallback> {

		/**
		 * Build the callback.
		 * @return A new AsyncMcpResourceListChangedMethodCallback instance
		 */
		@Override
		public AsyncMcpResourceListChangedMethodCallback build() {
			validate();
			return new AsyncMcpResourceListChangedMethodCallback(this);
		}

	}

}
