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

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import org.springframework.ai.mcp.annotation.context.McpRequestContextTypes;

/**
 * Abstract base class for creating Function callbacks around synchronous tool methods.
 *
 * This class extends {@link AbstractAsyncMcpToolMethodCallback} and provides synchronous
 * wrapper methods for handling tool requests. It converts the asynchronous reactive
 * methods from the parent class into synchronous equivalents suitable for blocking
 * operations.
 *
 * @param <T> The type of the context parameter (e.g., McpTransportContext or
 * McpSyncServerExchange)
 * @author Christian Tzolov
 */
public abstract class AbstractSyncMcpToolMethodCallback<T, RC extends McpRequestContextTypes<?>>
		extends AbstractAsyncMcpToolMethodCallback<T, RC> {

	protected AbstractSyncMcpToolMethodCallback(ReturnMode returnMode, Method toolMethod, Object toolObject,
			Class<? extends Throwable> toolCallExceptionClass) {
		super(returnMode, toolMethod, toolObject, toolCallExceptionClass);
	}

	/**
	 * Processes the result of the method invocation and converts it to a CallToolResult.
	 * This is a synchronous wrapper around the parent class's reactive result processing.
	 * @param result The result from the method invocation
	 * @return A CallToolResult representing the processed result
	 */
	protected CallToolResult processResult(Object result) {
		return mapValueToCallToolResult(result);
	}

	/**
	 * Creates an error result for exceptions that occur during method invocation. This is
	 * a synchronous wrapper around the parent class's reactive error handling.
	 * @param e The exception that occurred
	 * @return A CallToolResult representing the error
	 */
	protected CallToolResult createSyncErrorResult(Exception e) {
		Throwable rootCause = findCauseUsingPlainJava(e);
		return CallToolResult.builder()
			.isError(true)
			.addTextContent(e.getMessage() + System.lineSeparator() + rootCause.getMessage())
			.build();
	}

	/**
	 * Validates that the request is not null. This is a synchronous wrapper around the
	 * parent class's reactive validation.
	 * @param request The request to validate
	 * @throws IllegalArgumentException if the request is null
	 */
	protected void validateSyncRequest(CallToolRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Request must not be null");
		}
	}

}
