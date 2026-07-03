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

import java.lang.reflect.UndeclaredThrowableException;
import java.util.function.BiFunction;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

/**
 * Class for creating Function callbacks around tool methods.
 *
 * This class provides a way to convert methods annotated with {@link McpTool} into
 * callback functions that can be used to handle tool requests.
 *
 * @author James Ward
 * @author Christian Tzolov
 */
public final class SyncStatelessMcpToolMethodCallback
		extends AbstractSyncMcpToolMethodCallback<McpTransportContext, McpSyncRequestContext>
		implements BiFunction<McpTransportContext, CallToolRequest, CallToolResult> {

	public SyncStatelessMcpToolMethodCallback(ReturnMode returnMode, java.lang.reflect.Method toolMethod,
			Object toolObject) {
		super(returnMode, toolMethod, toolObject);
	}

	/**
	 * @deprecated use
	 * {@link #SyncStatelessMcpToolMethodCallback(ReturnMode, java.lang.reflect.Method, Object)}.
	 * The {@code toolCallExceptionClass} argument is ignored: exception handling now
	 * follows the {@code @Tool} contract based on the exception type. Will be removed in
	 * 2.1.0.
	 */
	@Deprecated
	public SyncStatelessMcpToolMethodCallback(ReturnMode returnMode, java.lang.reflect.Method toolMethod,
			Object toolObject, Class<? extends Throwable> toolCallExceptionClass) {
		super(returnMode, toolMethod, toolObject, toolCallExceptionClass);
	}

	@Override
	protected boolean isExchangeOrContextType(Class<?> paramType) {
		return McpTransportContext.class.isAssignableFrom(paramType)
				|| McpSyncRequestContext.class.isAssignableFrom(paramType);
	}

	@Override
	protected McpSyncRequestContext createRequestContext(McpTransportContext exchange, CallToolRequest request) {
		throw new UnsupportedOperationException(
				"Stateless tool methods do not support McpSyncRequestContext parameter.");
	}

	@Override
	protected McpTransportContext resolveTransportContext(McpTransportContext context) {
		return context;
	}

	@Override
	public CallToolResult apply(McpTransportContext mcpTransportContext, CallToolRequest callToolRequest) {
		validateSyncRequest(callToolRequest);

		try {
			// Build arguments for the method call
			Object[] args = this.buildMethodArguments(mcpTransportContext, callToolRequest.arguments(),
					callToolRequest);

			// Invoke the method
			Object result = this.callMethod(args);

			// Return the processed result
			return this.processResult(result);
		}
		catch (UndeclaredThrowableException e) {
			throw e;
		}
		// McpError extends RuntimeException — this catch must precede the plain
		// RuntimeException catch below, or McpError would be silently swallowed into
		// an error CallToolResult instead of propagating as a hard failure.
		catch (McpError e) {
			// A protocol-level error (e.g. URL elicitation) carries MCP semantics that
			// must reach the protocol layer, so it bubbles up rather than being conveyed
			// to the model as an error result.
			throw e;
		}
		catch (RuntimeException e) {
			return this.createSyncErrorResult(e);
		}
	}

}
