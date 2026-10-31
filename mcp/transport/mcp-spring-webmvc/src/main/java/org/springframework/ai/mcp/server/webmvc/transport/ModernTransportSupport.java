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

package org.springframework.ai.mcp.server.webmvc.transport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCMessage;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCRequest;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCResponse;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.ErrorCodes;
import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
import io.modelcontextprotocol.server.transport.HeaderAccessor;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.web.servlet.function.ServerRequest;

/**
 * Stack-neutral protocol logic for the 2026-07-28 (modern) HTTP transport: header-to-body
 * consistency checks, {@code Mcp-Name} decoding, JSON-RPC error code to HTTP status
 * mapping and SSE framing. Mirrors the private helpers of the SDK's
 * {@code io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport} so it
 * can be replaced by an SDK helper once one is published. Internal use only.
 *
 * @author Christian Tzolov
 */
final class ModernTransportSupport {

	static final String MCP_METHOD_HEADER = "Mcp-Method";

	static final String MCP_NAME_HEADER = "Mcp-Name";

	static final String MCP_PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";

	static final String SSE_EVENT_MESSAGE = "message";

	/** Marker emitted on the frame stream when a keep-alive comment is due. */
	static final Object KEEP_ALIVE = new Object();

	/**
	 * The statuses the MCP specification gives its error codes; any other code is
	 * answered with 200.
	 */
	static final Map<Integer, Integer> DEFAULT_ERROR_STATUSES = Map.of(ErrorCodes.PARSE_ERROR, 400,
			ErrorCodes.INVALID_REQUEST, 400, ErrorCodes.INVALID_PARAMS, 400, ErrorCodes.HEADER_MISMATCH, 400,
			ErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY, 400, ErrorCodes.UNSUPPORTED_PROTOCOL_VERSION, 400,
			ErrorCodes.METHOD_NOT_FOUND, 404);

	private ModernTransportSupport() {
	}

	static boolean isListen(JSONRPCRequest request) {
		return McpSchema.METHOD_SUBSCRIPTIONS_LISTEN.equals(request.method());
	}

	static int statusFor(Map<Integer, Integer> errorStatuses, JSONRPCResponse errorResponse) {
		return errorStatuses.getOrDefault(errorResponse.error().code(), 200);
	}

	/**
	 * Validates {@code Mcp-Method}, {@code MCP-Protocol-Version} and, for the three named
	 * methods, {@code Mcp-Name} against the request body.
	 * @param headerLookup returns the first value of a header, or {@code null}
	 * @param jsonRpcRequest the parsed body
	 * @return a human-readable mismatch description, or {@code null} if the headers are
	 * consistent with the body
	 */
	static @Nullable String validateHeaders(Function<String, @Nullable String> headerLookup,
			JSONRPCRequest jsonRpcRequest) {
		String methodHeader = headerLookup.apply(MCP_METHOD_HEADER);
		if (methodHeader == null) {
			return "Missing required header: " + MCP_METHOD_HEADER;
		}
		if (!methodHeader.equals(jsonRpcRequest.method())) {
			return MCP_METHOD_HEADER + " header does not match request method";
		}

		// A body without a readable version is left to McpServer's _meta validation,
		// which answers it with -32602.
		String bodyVersion = protocolVersionFromMeta(jsonRpcRequest.params());
		if (bodyVersion != null) {
			String versionHeader = headerLookup.apply(MCP_PROTOCOL_VERSION_HEADER);
			if (versionHeader == null) {
				return "Missing required header: " + MCP_PROTOCOL_VERSION_HEADER;
			}
			if (!versionHeader.equals(bodyVersion)) {
				return MCP_PROTOCOL_VERSION_HEADER + " header does not match _meta protocol version";
			}
		}

		String nameKey = switch (jsonRpcRequest.method()) {
			case McpSchema.METHOD_TOOLS_CALL, McpSchema.METHOD_PROMPTS_GET -> "name";
			case McpSchema.METHOD_RESOURCES_READ -> "uri";
			default -> null;
		};
		if (nameKey == null || !(jsonRpcRequest.params() instanceof Map<?, ?> paramsMap)) {
			return null;
		}
		if (!(paramsMap.get(nameKey) instanceof String expectedName)) {
			return null;
		}
		String nameHeader = headerLookup.apply(MCP_NAME_HEADER);
		if (nameHeader == null) {
			return "Missing required header: " + MCP_NAME_HEADER;
		}
		String decoded = decodeMcpNameHeader(nameHeader);
		if (decoded == null) {
			return MCP_NAME_HEADER + " header has a malformed Base64 value";
		}
		if (!expectedName.equals(decoded)) {
			return MCP_NAME_HEADER + " header does not match request name/uri";
		}
		return null;
	}

	static @Nullable String protocolVersionFromMeta(@Nullable Object params) {
		if (params instanceof Map<?, ?> paramsMap && paramsMap.get("_meta") instanceof Map<?, ?> meta
				&& meta.get(MetaKeys.PROTOCOL_VERSION) instanceof String version) {
			return version;
		}
		return null;
	}

	static @Nullable String decodeMcpNameHeader(String value) {
		if (value.startsWith("=?base64?") && value.endsWith("?=")) {
			String base64 = value.substring("=?base64?".length(), value.length() - "?=".length());
			try {
				return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
			}
			catch (IllegalArgumentException ex) {
				return null;
			}
		}
		return value;
	}

	static HeaderAccessor headerAccessor(ServerRequest request) {
		return new HeaderAccessor() {
			@Override
			public List<String> getHeader(String name) {
				return request.headers().header(name);
			}

			@Override
			public List<String> getHeaderNames() {
				return List.copyOf(request.headers().asHttpHeaders().headerNames());
			}
		};
	}

	/**
	 * The messages of a stream, interleaved with {@link #KEEP_ALIVE} markers every
	 * {@code keepAliveInterval} while the stream is open. {@code null} disables
	 * keep-alives.
	 */
	static Flux<Object> frames(Flux<JSONRPCMessage> messages, @Nullable Duration keepAliveInterval) {
		Flux<Object> events = messages.cast(Object.class);
		if (keepAliveInterval == null) {
			return events;
		}
		return events.publish(shared -> shared.mergeWith(Flux.interval(keepAliveInterval)
			.onBackpressureDrop()
			.map(tick -> KEEP_ALIVE)
			.takeUntilOther(shared.then())));
	}

	static String toJson(McpJsonMapper jsonMapper, Object message) {
		try {
			return jsonMapper.writeValueAsString(message);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
