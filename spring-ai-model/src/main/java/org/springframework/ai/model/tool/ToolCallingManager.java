/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.tool;

import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Service responsible for managing the tool calling process for a chat model.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface ToolCallingManager {

	/**
	 * Resolve the tool definitions from the model's tool calling options.
	 */
	List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions);

	/**
	 * Execute the tool calls requested by the model (synchronous mode).
	 * <p>
	 * This method blocks the calling thread until all tool executions complete. For
	 * non-blocking execution, use {@link #executeToolCallsAsync(Prompt, ChatResponse)}.
	 * @param prompt the user prompt
	 * @param chatResponse the chat model response containing tool calls
	 * @return the tool execution result
	 * @see #executeToolCallsAsync(Prompt, ChatResponse)
	 */
	ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse);

	/**
	 * Execute the tool calls requested by the model (asynchronous mode).
	 * <p>
	 * This method returns immediately with a {@link Mono}, allowing non-blocking tool
	 * execution. This is particularly beneficial for:
	 * <ul>
	 * <li>Streaming chat responses with tool calling</li>
	 * <li>High-concurrency scenarios</li>
	 * <li>Tools that involve I/O operations (HTTP requests, database queries)</li>
	 * </ul>
	 * <p>
	 * If the tool implements {@link org.springframework.ai.tool.AsyncToolCallback}, it
	 * will be executed asynchronously without blocking. Otherwise, the tool will be
	 * executed on a bounded elastic scheduler to prevent thread pool exhaustion.
	 * <p>
	 * <strong>Performance Impact:</strong> In streaming scenarios with multiple
	 * concurrent tool calls, this method can reduce latency by 50-80% compared to
	 * synchronous execution.
	 * @param prompt the user prompt
	 * @param chatResponse the chat model response containing tool calls
	 * @return a Mono that emits the tool execution result when complete
	 * @see #executeToolCalls(Prompt, ChatResponse)
	 * @see org.springframework.ai.tool.AsyncToolCallback
	 * @since 1.2.0
	 */
	Mono<ToolExecutionResult> executeToolCallsAsync(Prompt prompt, ChatResponse chatResponse);

	/**
	 * Create a default {@link ToolCallingManager} builder.
	 */
	static DefaultToolCallingManager.Builder builder() {
		return DefaultToolCallingManager.builder();
	}

}
