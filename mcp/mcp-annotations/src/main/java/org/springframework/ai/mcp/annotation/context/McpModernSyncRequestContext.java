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

package org.springframework.ai.mcp.annotation.context;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.mcp.annotation.context.McpRequestContextTypes.ProgressSpec;

/**
 * The request context injected into annotated methods served by a 2026-07-28 ("modern")
 * MCP server. The modern protocol is stateless: there is no session and no in-call
 * elicitation, sampling, roots or logging. Instead, server-initiated input is requested
 * with an {@code InputRequiredResult} and arrives on the retried call through
 * {@link #inputResponses()}.
 * <p>
 * Progress notifications are delivered on the response stream. A method that declares
 * this parameter is always served as a streaming response.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public interface McpModernSyncRequestContext {

	// --------------------------------------
	// Request
	// --------------------------------------

	/** The SDK request context. */
	McpRequestContext context();

	/** The protocol version the client declared for this request. */
	String protocolVersion();

	/** The client capabilities declared for this request. */
	ClientCapabilities clientCapabilities();

	/** The client info declared for this request, if any. */
	@Nullable Implementation clientInfo();

	/** The request {@code _meta} map. */
	Map<String, Object> requestMeta();

	/** The transport context (headers, connection info, ...). */
	McpTransportContext transportContext();

	/** The progress token, or {@code null} if the client did not request progress. */
	@Nullable Object progressToken();

	// --------------------------------------
	// Multi round-trip requests
	// --------------------------------------

	/** Whether this call is a retry carrying input responses or request state. */
	boolean isRetry();

	/** The raw input responses of a retried call, keyed as the server requested them. */
	Map<String, Object> inputResponses();

	/** The request state the server attached to its input-required result, if any. */
	@Nullable String requestState();

	/**
	 * The client's response for one input request, converted to the given type.
	 * @param key the server-assigned key used in the input-required result
	 * @param type the type to convert the response to
	 * @param <T> the response type
	 * @return the response, or empty if the client did not answer this request
	 */
	<T> Optional<T> inputResponse(String key, Class<T> type);

	// --------------------------------------
	// Progress
	// --------------------------------------

	/** Whether progress notifications reach the client for this call. */
	boolean progressEnabled();

	void progress(int percentage);

	void progress(double progress, @Nullable Double total, @Nullable String message);

	void progress(Consumer<ProgressSpec> progressSpec);

	/**
	 * Pushes a request-scoped notification on the response stream.
	 * @param method the notification method
	 * @param params the notification params
	 */
	void notify(String method, Object params);

}
