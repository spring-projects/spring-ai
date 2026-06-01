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
import java.util.function.BiFunction;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.ErrorCodes;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.common.ErrorUtils;

/**
 * Class for creating BiFunction callbacks around prompt methods with asynchronous
 * processing.
 *
 * This class provides a way to convert methods annotated with {@link McpPrompt} into
 * callback functions that can be used to handle prompt requests asynchronously. It
 * supports various method signatures and return types.
 *
 * @author Christian Tzolov
 */
public final class AsyncMcpPromptMethodCallback extends AbstractMcpPromptMethodCallback
		implements BiFunction<McpAsyncServerExchange, GetPromptRequest, Mono<GetPromptResult>> {

	private AsyncMcpPromptMethodCallback(Builder builder) {
		super(builder.method, builder.bean, builder.prompt);
	}

	@Override
	protected void validateParamType(Class<?> paramType) {

		if (McpSyncServerExchange.class.isAssignableFrom(paramType)) {
			throw new IllegalArgumentException("Async prompt method must not declare parameter of type: "
					+ paramType.getName() + ". Use McpAsyncServerExchange instead." + " Method: "
					+ this.method.getName() + " in " + this.method.getDeclaringClass().getName());
		}
	}

	@Override
	protected Object assignExchangeType(Class<?> paramType, Object exchange) {

		if (McpTransportContext.class.isAssignableFrom(paramType)) {
			if (exchange instanceof McpTransportContext transportContext) {
				return transportContext;
			}
			else if (exchange instanceof McpSyncServerExchange syncServerExchange) {
				throw new IllegalArgumentException("Unsupported Async exchange type: "
						+ syncServerExchange.getClass().getName() + " for Async method: " + method.getName() + " in "
						+ method.getDeclaringClass().getName());

			}
			else if (exchange instanceof McpAsyncServerExchange asyncServerExchange) {
				return asyncServerExchange.transportContext();
			}
		}
		else if (McpAsyncServerExchange.class.isAssignableFrom(paramType)) {
			if (exchange instanceof McpAsyncServerExchange asyncServerExchange) {
				return asyncServerExchange;
			}

			throw new IllegalArgumentException(
					"Unsupported exchange type: " + (exchange != null ? exchange.getClass().getName() : "null")
							+ " for Async method: " + method.getName() + " in " + method.getDeclaringClass().getName());
		}

		throw new IllegalArgumentException(
				"Unsupported exchange type: " + (exchange != null ? exchange.getClass().getName() : "null")
						+ " for method: " + method.getName() + " in " + method.getDeclaringClass().getName());
	}

	/**
	 * Apply the callback to the given exchange and request.
	 * <p>
	 * This method builds the arguments for the method call, invokes the method, and
	 * converts the result to a GetPromptResult.
	 * @param exchange The server exchange, may be null if the method doesn't require it
	 * @param request The prompt request, must not be null
	 * @return A Mono that emits the prompt result
	 * @throws McpError if there is an error invoking the prompt method
	 * @throws IllegalArgumentException if the request is null
	 */
	@Override
	public Mono<GetPromptResult> apply(McpAsyncServerExchange exchange, GetPromptRequest request) {
		if (request == null) {
			return Mono.error(new IllegalArgumentException("Request must not be null"));
		}

		return Mono.defer(() -> {
			try {
				// Build arguments for the method call
				Object[] args = this.buildArgs(this.method, exchange, request);

				// Invoke the method
				this.method.setAccessible(true);
				Object result = this.method.invoke(this.bean, args);

				// Handle the result based on its type
				if (result instanceof Mono<?>) {
					// If the result is already a Mono, map it to a GetPromptResult
					return ((Mono<?>) result).map(r -> convertToGetPromptResult(r));
				}
				else {
					// Otherwise, convert the result to a GetPromptResult and wrap in a
					// Mono
					return Mono.just(convertToGetPromptResult(result));
				}
			}
			catch (Exception e) {
				if (e instanceof McpError mcpError && mcpError.getJsonRpcError() != null) {
					return Mono.error(mcpError);
				}

				return Mono.error(McpError.builder(ErrorCodes.INVALID_PARAMS)
					.message("Error invoking prompt method: " + this.method.getName() + " in "
							+ this.bean.getClass().getName() + ". /nCause: "
							+ ErrorUtils.findCauseUsingPlainJava(e).getMessage())
					.data(ErrorUtils.findCauseUsingPlainJava(e).getMessage())
					.build());
			}
		});
	}

	@Override
	protected boolean isSupportedExchangeOrContextType(Class<?> paramType) {
		return (McpAsyncServerExchange.class.isAssignableFrom(paramType)
				|| McpTransportContext.class.isAssignableFrom(paramType));
	}

	@Override
	protected void validateReturnType(Method method) {
		Class<?> returnType = method.getReturnType();

		// For AsyncMcpPromptMethodCallback, the method must return a Mono
		if (!Mono.class.isAssignableFrom(returnType)) {
			throw new IllegalArgumentException(
					"Method must return a Mono<T> where T is one of GetPromptResult, List<PromptMessage>, "
							+ "List<String>, PromptMessage, or String: " + method.getName() + " in "
							+ method.getDeclaringClass().getName() + " returns " + returnType.getName());
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
	 * Builder for creating AsyncMcpPromptMethodCallback instances.
	 * <p>
	 * This builder provides a fluent API for constructing AsyncMcpPromptMethodCallback
	 * instances with the required parameters.
	 */
	public final static class Builder extends AbstractBuilder<Builder, AsyncMcpPromptMethodCallback> {

		/**
		 * Build the callback.
		 * @return A new AsyncMcpPromptMethodCallback instance
		 */
		@Override
		public AsyncMcpPromptMethodCallback build() {
			validate();
			return new AsyncMcpPromptMethodCallback(this);
		}

	}

}
