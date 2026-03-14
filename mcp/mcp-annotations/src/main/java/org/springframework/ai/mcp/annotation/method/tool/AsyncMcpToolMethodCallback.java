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

package org.springframework.ai.mcp.annotation.method.tool;

import java.lang.reflect.Method;
import java.util.function.BiFunction;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.context.DefaultMcpAsyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;

/**
 * Class for creating Function callbacks around tool methods.
 *
 * This class provides a way to convert methods annotated with {@link McpTool} into
 * callback functions that can be used to handle tool requests.
 *
 * @author Christian Tzolov
 */
public final class AsyncMcpToolMethodCallback
		extends AbstractAsyncMcpToolMethodCallback<McpAsyncServerExchange, McpAsyncRequestContext>
		implements BiFunction<McpAsyncServerExchange, CallToolRequest, Mono<CallToolResult>> {

	public AsyncMcpToolMethodCallback(ReturnMode returnMode, Method toolMethod, Object toolObject) {
		super(returnMode, toolMethod, toolObject, Exception.class);
	}

	public AsyncMcpToolMethodCallback(ReturnMode returnMode, Method toolMethod, Object toolObject,
			Class<? extends Throwable> toolCallExceptionClass) {
		super(returnMode, toolMethod, toolObject, toolCallExceptionClass);
	}

	@Override
	protected boolean isExchangeOrContextType(Class<?> paramType) {
		return McpAsyncServerExchange.class.isAssignableFrom(paramType)
				|| McpAsyncRequestContext.class.isAssignableFrom(paramType)
				|| McpTransportContext.class.isAssignableFrom(paramType);
	}

	@Override
	protected McpAsyncRequestContext createRequestContext(McpAsyncServerExchange exchange, CallToolRequest request) {

		return DefaultMcpAsyncRequestContext.builder().request(request).exchange(exchange).build();
	}

	@Override
	protected McpTransportContext resolveTransportContext(McpAsyncServerExchange exchange) {
		return exchange.transportContext();
	}

	/**
	 * Apply the callback to the given request.
	 * <p>
	 * This method builds the arguments for the method call, invokes the method, and
	 * returns the result.
	 * @param exchange The server exchange context
	 * @param request The tool call request, must not be null
	 * @return The result of the method invocation
	 */
	@Override
	public Mono<CallToolResult> apply(McpAsyncServerExchange exchange, CallToolRequest request) {

		return validateRequest(request).then(Mono.defer(() -> {
			try {
				// Build arguments for the method call, passing the full request for
				// CallToolRequest parameter support
				Object[] args = this.buildMethodArguments(exchange, request.arguments(), request);

				// Invoke the method
				Object result = this.callMethod(args);

				// Handle reactive types - method return types should always be reactive
				return this.convertToCallToolResult(result);

			}
			catch (Exception e) {
				if (this.toolCallExceptionClass.isInstance(e)) {
					return this.createAsyncErrorResult(e);
				}
				return Mono.error(e);
			}
		}));
	}

}
