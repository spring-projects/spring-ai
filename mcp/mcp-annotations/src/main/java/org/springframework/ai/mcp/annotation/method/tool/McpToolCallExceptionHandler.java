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

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

/**
 * Interface for handling exceptions that occur during MCP tool method invocation.
 * Implementations can be provided to customize how exceptions are converted into
 * {@link CallToolResult} error responses.
 *
 * <p>
 * A default implementation is available via {@link #defaultHandler()}.
 *
 * @since 2.0.0
 */
@FunctionalInterface
public interface McpToolCallExceptionHandler {

	/**
	 * Processes an exception that occurred during the invocation of an MCP tool method
	 * and returns an appropriate {@link CallToolResult} error response.
	 * @param toolName the name of the tool method that raised the exception
	 * @param exception the exception that was caught
	 * @return a {@link CallToolResult} representing the error
	 */
	CallToolResult process(String toolName, Exception exception);

	/**
	 * Returns a default handler that marks the result as an error and includes the error
	 * message in the response content.
	 * @return the default {@link McpToolCallExceptionHandler}
	 */
	static McpToolCallExceptionHandler defaultHandler() {
		return (toolName, exception) -> CallToolResult.builder()
			.isError(true)
			.addTextContent("Error invoking tool '%s': %s".formatted(toolName, exception.getMessage()))
			.build();
	}

}
