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
package org.springframework.ai.mcp;

import java.util.List;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolRegistration;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Role;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;

/**
 * Utility class that provides helper methods for working with Model Context Protocol
 * (MCP) tools in a Spring AI environment. This class facilitates the integration between
 * Spring AI's tool callbacks and MCP's tool system.
 *
 * <p>
 * The MCP tool system enables servers to expose executable functionality to language
 * models, allowing them to interact with external systems, perform computations, and take
 * actions in the real world. Each tool is uniquely identified by a name and includes
 * metadata describing its schema.
 *
 * <p>
 * This helper class provides methods to:
 * <ul>
 * <li>Convert Spring AI's {@link ToolCallback} instances to MCP tool registrations</li>
 * <li>Generate JSON schemas for tool input validation</li>
 * </ul>
 *
 * @author Christian Tzolov
 */
public final class McpToolUtils {

	private McpToolUtils() {
	}

	/**
	 * Converts a list of Spring AI tool callbacks to MCP synchronous tool registrations.
	 * <p>
	 * This method processes multiple tool callbacks in bulk, converting each one to its
	 * corresponding MCP tool registration while maintaining synchronous execution
	 * semantics.
	 * @param toolCallbacks the list of tool callbacks to convert
	 * @return a list of MCP synchronous tool registrations
	 * @see #toSyncToolRegistration(ToolCallback)
	 */
	public static List<McpServerFeatures.SyncToolRegistration> toSyncToolRegistration(
			List<ToolCallback> toolCallbacks) {
		return toolCallbacks.stream().map(McpToolUtils::toSyncToolRegistration).toList();
	}

	/**
	 * Convenience method to convert a variable number of tool callbacks to MCP
	 * synchronous tool registrations.
	 * <p>
	 * This is a varargs wrapper around {@link #toSyncToolRegistration(List)} for easier
	 * usage when working with individual callbacks.
	 * @param toolCallbacks the tool callbacks to convert
	 * @return a list of MCP synchronous tool registrations
	 * @see #toSyncToolRegistration(List)
	 */
	public static List<McpServerFeatures.SyncToolRegistration> toSyncToolRegistration(ToolCallback... toolCallbacks) {
		return toSyncToolRegistration(List.of(toolCallbacks));
	}

	/**
	 * Converts a Spring AI FunctionCallback to an MCP SyncToolRegistration. This enables
	 * Spring AI functions to be exposed as MCP tools that can be discovered and invoked
	 * by language models.
	 *
	 * <p>
	 * The conversion process:
	 * <ul>
	 * <li>Creates an MCP Tool with the function's name and input schema</li>
	 * <li>Wraps the function's execution in a SyncToolRegistration that handles the MCP
	 * protocol</li>
	 * <li>Provides error handling and result formatting according to MCP
	 * specifications</li>
	 * </ul>
	 *
	 * You can use the FunctionCallback builder to create a new instance of
	 * FunctionCallback using either java.util.function.Function or Method reference.
	 * @param toolCallback the Spring AI function callback to convert
	 * @return an MCP SyncToolRegistration that wraps the function callback
	 * @throws RuntimeException if there's an error during the function execution
	 */
	public static McpServerFeatures.SyncToolRegistration toSyncToolRegistration(ToolCallback toolCallback) {
		return toSyncToolRegistration(toolCallback, null);
	}

	/**
	 * Converts a Spring AI FunctionCallback to an MCP SyncToolRegistration. This enables
	 * Spring AI functions to be exposed as MCP tools that can be discovered and invoked
	 * by language models.
	 *
	 * <p>
	 * The conversion process:
	 * <ul>
	 * <li>Creates an MCP Tool with the function's name and input schema</li>
	 * <li>Wraps the function's execution in a SyncToolRegistration that handles the MCP
	 * protocol</li>
	 * <li>Provides error handling and result formatting according to MCP
	 * specifications</li>
	 * </ul>
	 *
	 * You can use the FunctionCallback builder to create a new instance of
	 * FunctionCallback using either java.util.function.Function or Method reference.
	 * @param toolCallback the Spring AI function callback to convert
	 * @param mimeType the MIME type of the output content
	 * @return an MCP SyncToolRegistration that wraps the function callback
	 * @throws RuntimeException if there's an error during the function execution
	 */
	public static McpServerFeatures.SyncToolRegistration toSyncToolRegistration(ToolCallback toolCallback,
			MimeType mimeType) {

		var tool = new McpSchema.Tool(toolCallback.getToolDefinition().name(),
				toolCallback.getToolDefinition().description(), toolCallback.getToolDefinition().inputSchema());

		return new McpServerFeatures.SyncToolRegistration(tool, request -> {
			try {
				String callResult = toolCallback.call(ModelOptionsUtils.toJsonString(request));
				if (mimeType != null && mimeType.toString().startsWith("image")) {
					return new McpSchema.CallToolResult(List
						.of(new McpSchema.ImageContent(List.of(Role.ASSISTANT), null, callResult, mimeType.toString())),
							false);
				}
				return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(callResult)), false);
			}
			catch (Exception e) {
				return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(e.getMessage())), true);
			}
		});
	}

	/**
	 * Converts a list of Spring AI tool callbacks to MCP asynchronous tool registrations.
	 * <p>
	 * This method processes multiple tool callbacks in bulk, converting each one to its
	 * corresponding MCP tool registration while adding asynchronous execution
	 * capabilities. The resulting registrations will execute their tools on a bounded
	 * elastic scheduler.
	 * @param toolCallbacks the list of tool callbacks to convert
	 * @return a list of MCP asynchronous tool registrations
	 * @see #toAsyncToolRegistration(ToolCallback)
	 */
	public static List<McpServerFeatures.AsyncToolRegistration> toAsyncToolRegistration(
			List<ToolCallback> toolCallbacks) {
		return toolCallbacks.stream().map(McpToolUtils::toAsyncToolRegistration).toList();
	}

	/**
	 * Convenience method to convert a variable number of tool callbacks to MCP
	 * asynchronous tool registrations.
	 * <p>
	 * This is a varargs wrapper around {@link #toAsyncToolRegistration(List)} for easier
	 * usage when working with individual callbacks.
	 * @param toolCallbacks the tool callbacks to convert
	 * @return a list of MCP asynchronous tool registrations
	 * @see #toAsyncToolRegistration(List)
	 */
	public static List<McpServerFeatures.AsyncToolRegistration> toAsyncToolRegistration(ToolCallback... toolCallbacks) {
		return toAsyncToolRegistration(List.of(toolCallbacks));
	}

	/**
	 * Converts a Spring AI tool callback to an MCP asynchronous tool registration.
	 * <p>
	 * This method enables Spring AI tools to be exposed as asynchronous MCP tools that
	 * can be discovered and invoked by language models. The conversion process:
	 * <ul>
	 * <li>First converts the callback to a synchronous registration</li>
	 * <li>Wraps the synchronous execution in a reactive Mono</li>
	 * <li>Configures execution on a bounded elastic scheduler for non-blocking
	 * operation</li>
	 * </ul>
	 * <p>
	 * The resulting async registration will:
	 * <ul>
	 * <li>Execute the tool without blocking the calling thread</li>
	 * <li>Handle errors and results asynchronously</li>
	 * <li>Provide backpressure through Project Reactor</li>
	 * </ul>
	 * @param toolCallback the Spring AI tool callback to convert
	 * @return an MCP asynchronous tool registration that wraps the tool callback
	 * @see McpServerFeatures.AsyncToolRegistration
	 * @see Mono
	 * @see Schedulers#boundedElastic()
	 */
	public static McpServerFeatures.AsyncToolRegistration toAsyncToolRegistration(ToolCallback toolCallback) {
		return toAsyncToolRegistration(toolCallback, null);
	}

	/**
	 * Converts a Spring AI tool callback to an MCP asynchronous tool registration.
	 * <p>
	 * This method enables Spring AI tools to be exposed as asynchronous MCP tools that
	 * can be discovered and invoked by language models. The conversion process:
	 * <ul>
	 * <li>First converts the callback to a synchronous registration</li>
	 * <li>Wraps the synchronous execution in a reactive Mono</li>
	 * <li>Configures execution on a bounded elastic scheduler for non-blocking
	 * operation</li>
	 * </ul>
	 * <p>
	 * The resulting async registration will:
	 * <ul>
	 * <li>Execute the tool without blocking the calling thread</li>
	 * <li>Handle errors and results asynchronously</li>
	 * <li>Provide backpressure through Project Reactor</li>
	 * </ul>
	 * @param toolCallback the Spring AI tool callback to convert
	 * @param mimeType the MIME type of the output content
	 * @return an MCP asynchronous tool registration that wraps the tool callback
	 * @see McpServerFeatures.AsyncToolRegistration
	 * @see Mono
	 * @see Schedulers#boundedElastic()
	 */
	public static McpServerFeatures.AsyncToolRegistration toAsyncToolRegistration(ToolCallback toolCallback,
			MimeType mimeType) {

		McpServerFeatures.SyncToolRegistration syncToolRegistration = toSyncToolRegistration(toolCallback, mimeType);

		return new AsyncToolRegistration(syncToolRegistration.tool(),
				map -> Mono.fromCallable(() -> syncToolRegistration.call().apply(map))
					.subscribeOn(Schedulers.boundedElastic()));
	}

	/**
	 * Convenience method to get tool callbacks from multiple synchronous MCP clients.
	 * <p>
	 * This is a varargs wrapper around {@link #getToolCallbacksFromSyncClients(List)} for
	 * easier usage when working with individual clients.
	 * @param mcpClients the synchronous MCP clients to get callbacks from
	 * @return a list of tool callbacks from all provided clients
	 * @see #getToolCallbacksFromSyncClients(List)
	 */
	public static List<ToolCallback> getToolCallbacksFromSyncClients(McpSyncClient... mcpClients) {
		return getToolCallbacksFromSyncClients(List.of(mcpClients));
	}

	/**
	 * Gets tool callbacks from a list of synchronous MCP clients.
	 * <p>
	 * This method:
	 * <ol>
	 * <li>Takes a list of synchronous MCP clients</li>
	 * <li>Creates a provider for each client</li>
	 * <li>Retrieves and combines all tool callbacks into a single list</li>
	 * </ol>
	 * @param mcpClients the list of synchronous MCP clients to get callbacks from
	 * @return a list of tool callbacks from all provided clients
	 */
	public static List<ToolCallback> getToolCallbacksFromSyncClients(List<McpSyncClient> mcpClients) {

		if (CollectionUtils.isEmpty(mcpClients)) {
			return List.of();
		}
		return List.of((new SyncMcpToolCallbackProvider(mcpClients).getToolCallbacks()));
	}

	/**
	 * Convenience method to get tool callbacks from multiple asynchronous MCP clients.
	 * <p>
	 * This is a varargs wrapper around {@link #getToolCallbacksFromAsyncClients(List)}
	 * for easier usage when working with individual clients.
	 * @param asyncMcpClients the asynchronous MCP clients to get callbacks from
	 * @return a list of tool callbacks from all provided clients
	 * @see #getToolCallbacksFromAsyncClients(List)
	 */
	public static List<ToolCallback> getToolCallbacksFromAsyncClients(McpAsyncClient... asyncMcpClients) {
		return getToolCallbacksFromAsyncClients(List.of(asyncMcpClients));
	}

	/**
	 * Gets tool callbacks from a list of asynchronous MCP clients.
	 * <p>
	 * This method:
	 * <ol>
	 * <li>Takes a list of asynchronous MCP clients</li>
	 * <li>Creates a provider for each client</li>
	 * <li>Retrieves and combines all tool callbacks into a single list</li>
	 * </ol>
	 * @param asyncMcpClients the list of asynchronous MCP clients to get callbacks from
	 * @return a list of tool callbacks from all provided clients
	 */
	public static List<ToolCallback> getToolCallbacksFromAsyncClients(List<McpAsyncClient> asyncMcpClients) {

		if (CollectionUtils.isEmpty(asyncMcpClients)) {
			return List.of();
		}
		return List.of((new AsyncMcpToolCallbackProvider(asyncMcpClients).getToolCallbacks()));
	}

}
