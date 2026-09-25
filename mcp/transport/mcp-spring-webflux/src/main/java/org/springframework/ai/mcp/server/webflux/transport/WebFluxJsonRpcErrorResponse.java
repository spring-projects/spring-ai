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

package org.springframework.ai.mcp.server.webflux.transport;

import java.io.IOException;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Creates JSON-RPC error responses for WebFlux server transports.
 *
 * @author Taewoong Kim
 */
final class WebFluxJsonRpcErrorResponse {

	private static final Log logger = LogFactory.getLog(WebFluxJsonRpcErrorResponse.class);

	private WebFluxJsonRpcErrorResponse() {
	}

	static Mono<ServerResponse> create(McpJsonMapper jsonMapper, HttpStatus status, @Nullable Object requestId,
			McpError mcpError) {
		try {
			// JSONRPCResponse requires a non-null id, but transport errors can occur
			// before a JSON-RPC request id is available.
			Object errorResponse = requestId != null
					? McpSchema.JSONRPCResponse.error(requestId, mcpError.getJsonRpcError())
					: new JsonRpcErrorResponse(McpSchema.JSONRPC_VERSION, mcpError.getJsonRpcError());
			String json = jsonMapper.writeValueAsString(errorResponse);
			return ServerResponse.status(status).contentType(MediaType.APPLICATION_JSON).bodyValue(json);
		}
		catch (IOException e) {
			if (logger.isErrorEnabled()) {
				logger.error("Failed to serialize JSON-RPC error response: " + e.getMessage());
			}
			return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	static @Nullable Object requestId(McpSchema.@Nullable JSONRPCMessage message) {
		return message instanceof McpSchema.JSONRPCRequest request ? request.id() : null;
	}

	private record JsonRpcErrorResponse(String jsonrpc, McpSchema.JSONRPCResponse.JSONRPCError error) {
	}

}
