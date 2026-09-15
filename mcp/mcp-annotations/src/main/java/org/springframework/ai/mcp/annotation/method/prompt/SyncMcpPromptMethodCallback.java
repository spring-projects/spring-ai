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
import java.util.List;
import java.util.function.BiFunction;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.ErrorCodes;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;

import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.common.ErrorUtils;

/**
 * Class for creating BiFunction callbacks around prompt methods.
 *
 * This class provides a way to convert methods annotated with {@link McpPrompt} into
 * callback functions that can be used to handle prompt requests. It supports various
 * method signatures and return types.
 *
 * @author Christian Tzolov
 */
public final class SyncMcpPromptMethodCallback extends AbstractMcpPromptMethodCallback
		implements BiFunction<McpSyncServerExchange, GetPromptRequest, GetPromptResult> {

	private SyncMcpPromptMethodCallback(Builder builder) {
		super(builder.method, builder.bean, builder.prompt);
	}

	@Override
	protected void validateParamType(Class<?> paramType) {

		if (McpAsyncServerExchange.class.isAssignableFrom(paramType)) {
			throw new IllegalArgumentException("Sync prompt method must not declare parameter of type: "
					+ paramType.getName() + ". Use McpSyncServerExchange instead." + " Method: " + this.method.getName()
					+ " in " + this.method.getDeclaringClass().getName());
		}
	}

	@Override
	protected Object assignExchangeType(Class<?> paramType, Object exchange) {

		if (McpTransportContext.class.isAssignableFrom(paramType)) {
			if (exchange instanceof McpTransportContext transportContext) {
				return transportContext;
			}
			else if (exchange instanceof McpSyncServerExchange syncServerExchange) {
				return syncServerExchange.transportContext();
			}
			else if (exchange instanceof McpAsyncServerExchange asyncServerExchange) {
				throw new IllegalArgumentException("Unsupported Async exchange type: "
						+ asyncServerExchange.getClass().getName() + " for Sync method: " + method.getName() + " in "
						+ method.getDeclaringClass().getName());
			}
		}
		else if (McpSyncServerExchange.class.isAssignableFrom(paramType)) {
			if (exchange instanceof McpSyncServerExchange syncServerExchange) {
				return syncServerExchange;
			}

			throw new IllegalArgumentException(
					"Unsupported exchange type: " + (exchange != null ? exchange.getClass().getName() : "null")
							+ " for Sync method: " + method.getName() + " in " + method.getDeclaringClass().getName());
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
	 * @return The prompt result
	 * @throws McpError if there is an error invoking the prompt method
	 * @throws IllegalArgumentException if the request is null
	 */
	@Override
	public GetPromptResult apply(McpSyncServerExchange exchange, GetPromptRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Request must not be null");
		}

		try {
			// Build arguments for the method call
			Object[] args = this.buildArgs(this.method, exchange, request);

			// Invoke the method
			this.method.setAccessible(true);
			Object result = this.method.invoke(this.bean, args);

			// Convert the result to a GetPromptResult
			GetPromptResult promptResult = this.convertToGetPromptResult(result);

			return promptResult;
		}
		catch (Exception e) {
			if (e instanceof McpError mcpError && mcpError.getJsonRpcError() != null) {
				throw mcpError;
			}

			throw McpError.builder(ErrorCodes.INVALID_PARAMS)
				.message("Error invoking prompt method: " + this.method.getName() + " in "
						+ this.bean.getClass().getName() + "./nCause: "
						+ ErrorUtils.findCauseUsingPlainJava(e).getMessage())
				.data(ErrorUtils.findCauseUsingPlainJava(e).getMessage())
				.build();
		}
	}

	@Override
	protected boolean isSupportedExchangeOrContextType(Class<?> paramType) {
		return (McpSyncServerExchange.class.isAssignableFrom(paramType)
				|| McpTransportContext.class.isAssignableFrom(paramType));
	}

	@Override
	protected void validateReturnType(Method method) {
		Class<?> returnType = method.getReturnType();

		boolean validReturnType = GetPromptResult.class.isAssignableFrom(returnType)
				|| List.class.isAssignableFrom(returnType) || PromptMessage.class.isAssignableFrom(returnType)
				|| String.class.isAssignableFrom(returnType);

		if (!validReturnType) {
			throw new IllegalArgumentException("Method must return either GetPromptResult, List<PromptMessage>, "
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
	 * Builder for creating SyncMcpPromptMethodCallback instances.
	 * <p>
	 * This builder provides a fluent API for constructing SyncMcpPromptMethodCallback
	 * instances with the required parameters.
	 */
	public static class Builder extends AbstractBuilder<Builder, SyncMcpPromptMethodCallback> {

		/**
		 * Build the callback.
		 * @return A new SyncMcpPromptMethodCallback instance
		 */
		@Override
		public SyncMcpPromptMethodCallback build() {
			validate();
			return new SyncMcpPromptMethodCallback(this);
		}

	}

}
