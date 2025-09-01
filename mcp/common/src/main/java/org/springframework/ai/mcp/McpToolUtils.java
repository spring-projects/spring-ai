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
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micrometer.common.util.StringUtils;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.Role;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
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
 * <li>Convert Spring AI's {@link ToolCallback} instances to MCP tool specification</li>
 * <li>Generate JSON schemas for tool input validation</li>
 * </ul>
 *
 * @author Christian Tzolov
 */
public final class McpToolUtils {

	/**
	 * The name of tool context key used to store the MCP exchange object.
	 */
	public static final String TOOL_CONTEXT_MCP_EXCHANGE_KEY = "exchange";

	private McpToolUtils() {
	}

	public static String prefixedToolName(String prefix, String toolName) {

		if (StringUtils.isEmpty(prefix) || StringUtils.isEmpty(toolName)) {
			throw new IllegalArgumentException("Prefix or toolName cannot be null or empty");
		}

		String input = prefix + "_" + toolName;

		// Replace any character that isn't alphanumeric, underscore, or hyphen with
		// concatenation. Support Han script + CJK blocks for complete Chinese character
		// coverage
		String formatted = input
			.replaceAll("[^\\p{IsHan}\\p{InCJK_Unified_Ideographs}\\p{InCJK_Compatibility_Ideographs}a-zA-Z0-9_-]", "");

		formatted = formatted.replaceAll("-", "_");

		// If the string is longer than 64 characters, keep the last 64 characters
		if (formatted.length() > 64) {
			formatted = formatted.substring(formatted.length() - 64);
		}

		return formatted;
	}

	/**
	 * Converts a list of Spring AI tool callbacks to MCP synchronous tool specification.
	 * <p>
	 * This method processes multiple tool callbacks in bulk, converting each one to its
	 * corresponding MCP tool specification while maintaining synchronous execution
	 * semantics.
	 * @param toolCallbacks the list of tool callbacks to convert
	 * @return a list of MCP synchronous tool specification
	 */
	public static List<McpServerFeatures.SyncToolSpecification> toSyncToolSpecification(
			List<ToolCallback> toolCallbacks) {
		return toolCallbacks.stream().map(McpToolUtils::toSyncToolSpecification).toList();
	}

	/**
	 * Convenience method to convert a variable number of tool callbacks to MCP
	 * synchronous tool specification.
	 * <p>
	 * This is a varargs wrapper around {@link #toSyncToolSpecification(List)} for easier
	 * usage when working with individual callbacks.
	 * @param toolCallbacks the tool callbacks to convert
	 * @return a list of MCP synchronous tool specification
	 */
	public static List<McpServerFeatures.SyncToolSpecification> toSyncToolSpecifications(
			ToolCallback... toolCallbacks) {
		return toSyncToolSpecification(List.of(toolCallbacks));
	}

	/**
	 * Converts a Spring AI ToolCallback to an MCP SyncToolSpecification. This enables
	 * Spring AI functions to be exposed as MCP tools that can be discovered and invoked
	 * by language models.
	 *
	 * <p>
	 * The conversion process:
	 * <ul>
	 * <li>Creates an MCP Tool with the function's name and input schema</li>
	 * <li>Wraps the function's execution in a SyncToolSpecification that handles the MCP
	 * protocol</li>
	 * <li>Provides error handling and result formatting according to MCP
	 * specifications</li>
	 * </ul>
	 *
	 * You can use the ToolCallback builder to create a new instance of ToolCallback using
	 * either java.util.function.Function or Method reference.
	 * @param toolCallback the Spring AI function callback to convert
	 * @return an MCP SyncToolSpecification that wraps the function callback
	 * @throws RuntimeException if there's an error during the function execution
	 */
	public static McpServerFeatures.SyncToolSpecification toSyncToolSpecification(ToolCallback toolCallback) {
		return toSyncToolSpecification(toolCallback, null);
	}

	/**
	 * Converts a Spring AI ToolCallback to an MCP SyncToolSpecification. This enables
	 * Spring AI functions to be exposed as MCP tools that can be discovered and invoked
	 * by language models.
	 * @param toolCallback the Spring AI function callback to convert
	 * @param mimeType the MIME type of the output content
	 * @return an MCP SyncToolSpecification that wraps the function callback
	 * @throws RuntimeException if there's an error during the function execution
	 */
	public static McpServerFeatures.SyncToolSpecification toSyncToolSpecification(ToolCallback toolCallback,
			MimeType mimeType) {

		SharedSyncToolSpecification sharedSpec = toSharedSyncToolSpecification(toolCallback, mimeType);

		return new McpServerFeatures.SyncToolSpecification(sharedSpec.tool(),
				(exchange, map) -> sharedSpec.sharedHandler()
					.apply(exchange, new CallToolRequest(sharedSpec.tool().name(), map)),
				(exchange, request) -> sharedSpec.sharedHandler().apply(exchange, request));
	}

	/**
	 * Converts a Spring AI ToolCallback to an MCP StatelessSyncToolSpecification. This
	 * enables Spring AI functions to be exposed as MCP tools that can be discovered and
	 * invoked by language models.
	 *
	 * You can use the ToolCallback builder to create a new instance of ToolCallback using
	 * either java.util.function.Function or Method reference.
	 * @param toolCallback the Spring AI function callback to convert
	 * @param mimeType the MIME type of the output content
	 * @return an MCP StatelessSyncToolSpecification that wraps the function callback
	 * @throws RuntimeException if there's an error during the function execution
	 */
	public static McpStatelessServerFeatures.SyncToolSpecification toStatelessSyncToolSpecification(
			ToolCallback toolCallback, MimeType mimeType) {

		var sharedSpec = toSharedSyncToolSpecification(toolCallback, mimeType);

		return new McpStatelessServerFeatures.SyncToolSpecification(sharedSpec.tool(),
				(exchange, request) -> sharedSpec.sharedHandler().apply(exchange, request));
	}

	private static SharedSyncToolSpecification toSharedSyncToolSpecification(ToolCallback toolCallback,
			MimeType mimeType) {

		var tool = McpSchema.Tool.builder()
			.name(toolCallback.getToolDefinition().name())
			.description(toolCallback.getToolDefinition().description())
			.inputSchema(toolCallback.getToolDefinition().inputSchema())
			.build();

		return new SharedSyncToolSpecification(tool, (exchangeOrContext, request) -> {
			try {
				String callResult = toolCallback.call(ModelOptionsUtils.toJsonString(request.arguments()),
						new ToolContext(Map.of(TOOL_CONTEXT_MCP_EXCHANGE_KEY, exchangeOrContext)));
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
	 * Retrieves the MCP exchange object from the provided tool context if it exists.
	 * @param toolContext the tool context from which to retrieve the MCP exchange
	 * @return the MCP exchange object, or null if not present in the context
	 */
	public static Optional<McpSyncServerExchange> getMcpExchange(ToolContext toolContext) {
		if (toolContext != null && toolContext.getContext().containsKey(TOOL_CONTEXT_MCP_EXCHANGE_KEY)) {
			return Optional
				.ofNullable((McpSyncServerExchange) toolContext.getContext().get(TOOL_CONTEXT_MCP_EXCHANGE_KEY));
		}
		return Optional.empty();
	}

	/**
	 * Converts a list of Spring AI tool callbacks to MCP asynchronous tool specification.
	 * <p>
	 * This method processes multiple tool callbacks in bulk, converting each one to its
	 * corresponding MCP tool specification while adding asynchronous execution
	 * capabilities. The resulting specifications will execute their tools on a bounded
	 * elastic scheduler.
	 * @param toolCallbacks the list of tool callbacks to convert
	 * @return a list of MCP asynchronous tool specifications
	 */
	public static List<McpServerFeatures.AsyncToolSpecification> toAsyncToolSpecifications(
			List<ToolCallback> toolCallbacks) {
		return toolCallbacks.stream().map(McpToolUtils::toAsyncToolSpecification).toList();
	}

	/**
	 * Convenience method to convert a variable number of tool callbacks to MCP
	 * asynchronous tool specification.
	 * <p>
	 * This is a varargs wrapper around {@link #toAsyncToolSpecifications(List)} for
	 * easier usage when working with individual callbacks.
	 * @param toolCallbacks the tool callbacks to convert
	 * @return a list of MCP asynchronous tool specifications
	 * @see #toAsyncToolSpecifications(List)
	 */
	public static List<McpServerFeatures.AsyncToolSpecification> toAsyncToolSpecifications(
			ToolCallback... toolCallbacks) {
		return toAsyncToolSpecifications(List.of(toolCallbacks));
	}

	/**
	 * Converts a Spring AI tool callback to an MCP asynchronous tool specification.
	 * <p>
	 * This method enables Spring AI tools to be exposed as asynchronous MCP tools that
	 * can be discovered and invoked by language models. The conversion process:
	 * <ul>
	 * <li>First converts the callback to a synchronous specification</li>
	 * <li>Wraps the synchronous execution in a reactive Mono</li>
	 * <li>Configures execution on a bounded elastic scheduler for non-blocking
	 * operation</li>
	 * </ul>
	 * <p>
	 * The resulting async specification will:
	 * <ul>
	 * <li>Execute the tool without blocking the calling thread</li>
	 * <li>Handle errors and results asynchronously</li>
	 * <li>Provide backpressure through Project Reactor</li>
	 * </ul>
	 * @param toolCallback the Spring AI tool callback to convert
	 * @return an MCP asynchronous tool specification that wraps the tool callback
	 * @see McpServerFeatures.AsyncToolSpecification
	 * @see Mono
	 * @see Schedulers#boundedElastic()
	 */
	public static McpServerFeatures.AsyncToolSpecification toAsyncToolSpecification(ToolCallback toolCallback) {
		return toAsyncToolSpecification(toolCallback, null);
	}

	/**
	 * Converts a Spring AI tool callback to an MCP asynchronous tool specification.
	 * <p>
	 * This method enables Spring AI tools to be exposed as asynchronous MCP tools that
	 * can be discovered and invoked by language models. The conversion process:
	 * <ul>
	 * <li>First converts the callback to a synchronous specification</li>
	 * <li>Wraps the synchronous execution in a reactive Mono</li>
	 * <li>Configures execution on a bounded elastic scheduler for non-blocking
	 * operation</li>
	 * </ul>
	 * <p>
	 * The resulting async specification will:
	 * <ul>
	 * <li>Execute the tool without blocking the calling thread</li>
	 * <li>Handle errors and results asynchronously</li>
	 * <li>Provide backpressure through Project Reactor</li>
	 * </ul>
	 * @param toolCallback the Spring AI tool callback to convert
	 * @param mimeType the MIME type of the output content
	 * @return an MCP asynchronous tool specification that wraps the tool callback
	 * @see McpServerFeatures.AsyncToolSpecification
	 * @see Schedulers#boundedElastic()
	 */
	public static McpServerFeatures.AsyncToolSpecification toAsyncToolSpecification(ToolCallback toolCallback,
			MimeType mimeType) {

		McpServerFeatures.SyncToolSpecification syncToolSpecification = toSyncToolSpecification(toolCallback, mimeType);

		return new AsyncToolSpecification(syncToolSpecification.tool(),
				(exchange, map) -> Mono
					.fromCallable(() -> syncToolSpecification.call().apply(new McpSyncServerExchange(exchange), map))
					.subscribeOn(Schedulers.boundedElastic()));
	}

	public static McpStatelessServerFeatures.AsyncToolSpecification toStatelessAsyncToolSpecification(
			ToolCallback toolCallback, MimeType mimeType) {

		McpStatelessServerFeatures.SyncToolSpecification statelessSyncToolSpecification = toStatelessSyncToolSpecification(
				toolCallback, mimeType);

		return new McpStatelessServerFeatures.AsyncToolSpecification(statelessSyncToolSpecification.tool(),
				(context, request) -> Mono
					.fromCallable(() -> statelessSyncToolSpecification.callHandler().apply(context, request))
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

	@JsonIgnoreProperties(ignoreUnknown = true)
	// @formatter:off
	private record Base64Wrapper(@JsonAlias("mimetype") @Nullable MimeType mimeType, @JsonAlias({
			"base64", "b64", "imageData" }) @Nullable String data) {
	}

	private record SharedSyncToolSpecification(McpSchema.Tool tool,
											BiFunction<Object, CallToolRequest, McpSchema.CallToolResult> sharedHandler) {
	}
}
