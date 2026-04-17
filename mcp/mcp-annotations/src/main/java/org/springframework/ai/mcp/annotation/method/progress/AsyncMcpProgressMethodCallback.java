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

package org.springframework.ai.mcp.annotation.method.progress;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;
import reactor.core.publisher.Mono;

/**
 * Asynchronous implementation of a progress method callback.
 *
 * This class creates a Function that invokes a method annotated with @McpProgress
 * asynchronously when a progress notification is received, returning a Mono<Void>.
 *
 * @author Christian Tzolov
 */
public final class AsyncMcpProgressMethodCallback extends AbstractMcpProgressMethodCallback
		implements Function<ProgressNotification, Mono<Void>> {

	private AsyncMcpProgressMethodCallback(Builder builder) {
		super(builder.method, builder.bean);
	}

	@Override
	protected void validateReturnType(Method method) {
		Class<?> returnType = method.getReturnType();

		// Check if return type is void or Mono<Void>
		if (returnType == void.class) {
			// void is acceptable - we'll wrap it in Mono
			return;
		}

		if (Mono.class.isAssignableFrom(returnType)) {
			// Check if it's Mono<Void>
			Type genericReturnType = method.getGenericReturnType();
			if (genericReturnType instanceof ParameterizedType paramType) {
				Type[] typeArguments = paramType.getActualTypeArguments();
				if (typeArguments.length == 1 && typeArguments[0] == Void.class) {
					// Mono<Void> is acceptable
					return;
				}
				else {
					throw new IllegalArgumentException("Mono return type must be Mono<Void>: " + method.getName()
							+ " in " + method.getDeclaringClass().getName() + " returns " + returnType.getName());
				}
			}
		}

		throw new IllegalArgumentException(
				"Asynchronous progress methods must return void or Mono<Void>: " + method.getName() + " in "
						+ method.getDeclaringClass().getName() + " returns " + returnType.getName());
	}

	/**
	 * Apply the progress notification and process it asynchronously.
	 * <p>
	 * This method builds the arguments for the method call and invokes the method,
	 * returning a Mono<Void>.
	 * @param notification The progress notification, must not be null
	 * @return A Mono<Void> representing the asynchronous operation
	 * @throws McpProgressMethodException if there is an error invoking the progress
	 * method
	 * @throws IllegalArgumentException if the notification is null
	 */
	@Override
	public Mono<Void> apply(ProgressNotification notification) {
		if (notification == null) {
			return Mono.error(new IllegalArgumentException("Notification must not be null"));
		}

		return Mono.fromCallable(() -> {
			try {
				// Build arguments for the method call
				Object[] args = this.buildArgs(this.method, null, notification);

				// Invoke the method
				this.method.setAccessible(true);
				Object result = this.method.invoke(this.bean, args);

				// Handle return type
				if (result instanceof Mono) {
					return (Mono<?>) result;
				}
				else {
					// void return type
					return Mono.empty();
				}
			}
			catch (Exception e) {
				throw new McpProgressMethodException("Error invoking progress method: " + this.method.getName(), e);
			}
		}).flatMap(mono -> mono.then());
	}

	/**
	 * Create a new builder.
	 * @return A new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for creating AsyncMcpProgressMethodCallback instances.
	 * <p>
	 * This builder provides a fluent API for constructing AsyncMcpProgressMethodCallback
	 * instances with the required parameters.
	 */
	public static class Builder extends AbstractBuilder<Builder, AsyncMcpProgressMethodCallback> {

		/**
		 * Build the callback.
		 * @return A new AsyncMcpProgressMethodCallback instance
		 */
		@Override
		public AsyncMcpProgressMethodCallback build() {
			validate();
			return new AsyncMcpProgressMethodCallback(this);
		}

	}

}
