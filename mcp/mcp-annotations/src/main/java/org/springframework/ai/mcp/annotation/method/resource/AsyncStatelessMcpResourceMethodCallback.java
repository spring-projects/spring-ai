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

package org.springframework.ai.mcp.annotation.method.resource;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.ErrorCodes;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.ResourceContents;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.common.ErrorUtils;

/**
 * Class for creating BiFunction callbacks around resource methods with asynchronous
 * processing for stateless contexts.
 *
 * This class provides a way to convert methods annotated with {@link McpResource} into
 * callback functions that can be used to handle resource requests asynchronously in
 * stateless environments. It supports various method signatures and return types, and
 * handles URI template variables.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Vadzim Shurmialiou
 * @author Craig Walls
 */
public final class AsyncStatelessMcpResourceMethodCallback extends AbstractMcpResourceMethodCallback
		implements BiFunction<McpTransportContext, ReadResourceRequest, Mono<ReadResourceResult>> {

	private AsyncStatelessMcpResourceMethodCallback(Builder builder) {
		super(builder.method, builder.bean, builder.uri, builder.name, builder.description, builder.mimeType,
				builder.resultConverter, builder.uriTemplateManagerFactory, builder.contentType, builder.meta);
		this.validateMethod(this.method);
	}

	@Override
	protected void validateParamType(Class<?> paramType) {

		if (McpSyncServerExchange.class.isAssignableFrom(paramType)
				|| McpAsyncServerExchange.class.isAssignableFrom(paramType)) {

			throw new IllegalArgumentException(
					"Stateless Streamable-Http prompt method must not declare parameter of type: " + paramType.getName()
							+ ". Use McpTransportContext instead." + " Method: " + this.method.getName() + " in "
							+ this.method.getDeclaringClass().getName());
		}
	}

	@Override
	protected Object assignExchangeType(Class<?> paramType, Object exchange) {

		if (McpTransportContext.class.isAssignableFrom(paramType)) {
			if (exchange instanceof McpTransportContext transportContext) {
				return transportContext;
			}
			else if (exchange instanceof McpSyncServerExchange syncServerExchange) {
				throw new IllegalArgumentException("Unsupported Sync exchange type: "
						+ syncServerExchange.getClass().getName() + " for Sync method: " + method.getName() + " in "
						+ method.getDeclaringClass().getName());

			}
			else if (exchange instanceof McpAsyncServerExchange asyncServerExchange) {
				return asyncServerExchange.transportContext();
			}
		}

		throw new IllegalArgumentException(
				"Unsupported exchange type: " + (exchange != null ? exchange.getClass().getName() : "null")
						+ " for method: " + method.getName() + " in " + method.getDeclaringClass().getName());
	}

	/**
	 * Apply the callback to the given context and request.
	 * <p>
	 * This method extracts URI variable values from the request URI, builds the arguments
	 * for the method call, invokes the method, and converts the result to a
	 * ReadResourceResult.
	 * @param context The transport context, may be null if the method doesn't require it
	 * @param request The resource request, must not be null
	 * @return A Mono that emits the resource result
	 * @throws McpError if there is an error invoking the resource method
	 * @throws IllegalArgumentException if the request is null or if URI variable
	 * extraction fails
	 */
	@Override
	public Mono<ReadResourceResult> apply(McpTransportContext context, ReadResourceRequest request) {
		if (request == null) {
			return Mono.error(new IllegalArgumentException("Request must not be null"));
		}

		return Mono.defer(() -> {
			try {
				// Extract URI variable values from the request URI
				Map<String, String> uriVariableValues = this.uriTemplateManager.extractVariableValues(request.uri());

				// Verify all URI variables were extracted if URI variables are expected
				if (!this.uriVariables.isEmpty() && uriVariableValues.size() != this.uriVariables.size()) {
					return Mono
						.error(new IllegalArgumentException("Failed to extract all URI variables from request URI: "
								+ request.uri() + ". Expected variables: " + this.uriVariables + ", but found: "
								+ uriVariableValues.keySet()));
				}

				// Build arguments for the method call
				Object[] args = this.buildArgs(this.method, context, request, uriVariableValues);

				// Invoke the method
				this.method.setAccessible(true);
				Object result = this.method.invoke(this.bean, args);

				// Handle the result based on its type
				if (result instanceof Mono<?>) {
					// If the result is already a Mono, use it
					return ((Mono<?>) result).map(r -> this.resultConverter.convertToReadResourceResult(r,
							request.uri(), this.mimeType, this.contentType, this.meta));
				}
				else {
					// Otherwise, convert the result to a ReadResourceResult and wrap in a
					// Mono
					return Mono.just(this.resultConverter.convertToReadResourceResult(result, request.uri(),
							this.mimeType, this.contentType, this.meta));
				}
			}
			catch (Exception e) {
				if (e instanceof McpError mcpError && mcpError.getJsonRpcError() != null) {
					return Mono.error(mcpError);
				}

				return Mono.error(McpError.builder(ErrorCodes.INVALID_PARAMS)
					.message("Error invoking resource method: " + this.method.getName() + " in "
							+ this.bean.getClass().getName() + ". /nCause: "
							+ ErrorUtils.findCauseUsingPlainJava(e).getMessage())
					.data(ErrorUtils.findCauseUsingPlainJava(e).getMessage())
					.build());
			}
		});
	}

	/**
	 * Validates that the method return type is compatible with the resource callback.
	 * @param method The method to validate
	 * @throws IllegalArgumentException if the return type is not compatible
	 */
	@Override
	protected void validateReturnType(Method method) {
		Class<?> returnType = method.getReturnType();

		boolean validReturnType = ReadResourceResult.class.isAssignableFrom(returnType)
				|| List.class.isAssignableFrom(returnType) || ResourceContents.class.isAssignableFrom(returnType)
				|| String.class.isAssignableFrom(returnType) || Mono.class.isAssignableFrom(returnType);

		if (!validReturnType) {
			throw new IllegalArgumentException(
					"Method must return either ReadResourceResult, List<ResourceContents>, List<String>, "
							+ "ResourceContents, String, or Mono<T>: " + method.getName() + " in "
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
	protected boolean isExchangeOrContextType(Class<?> paramType) {
		return McpTransportContext.class.isAssignableFrom(paramType);
	}

	/**
	 * Create a new builder.
	 * @return A new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for creating AsyncStatelessMcpResourceMethodCallback instances.
	 * <p>
	 * This builder provides a fluent API for constructing
	 * AsyncStatelessMcpResourceMethodCallback instances with the required parameters.
	 */
	public static class Builder extends AbstractBuilder<Builder, AsyncStatelessMcpResourceMethodCallback> {

		/**
		 * Constructor for Builder.
		 */
		public Builder() {
			this.resultConverter = new DefaultMcpReadResourceResultConverter();
		}

		/**
		 * Build the callback.
		 * @return A new AsyncStatelessMcpResourceMethodCallback instance
		 */
		@Override
		public AsyncStatelessMcpResourceMethodCallback build() {
			validate();
			return new AsyncStatelessMcpResourceMethodCallback(this);
		}

	}

}
