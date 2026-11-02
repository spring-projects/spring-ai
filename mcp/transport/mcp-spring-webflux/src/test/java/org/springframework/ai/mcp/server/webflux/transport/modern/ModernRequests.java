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

package org.springframework.ai.mcp.server.webflux.transport.modern;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCRequest;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.McpSchema.Tool;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;

/**
 * Builds modern (2026-07-28) JSON-RPC request bodies for the transport tests.
 */
final class ModernRequests {

	static final McpJsonMapper JSON = McpJsonDefaults.getMapper();

	static final Implementation SERVER_INFO = Implementation.builder("test-server", "1.0.0").build();

	static final Map<String, Object> EMPTY_SCHEMA = Map.of("type", "object", "properties", Map.of());

	static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE = new ParameterizedTypeReference<>() {
	};

	private ModernRequests() {
	}

	/** A valid {@code _meta} (latest version, no capabilities) plus the given pairs. */
	static Map<String, Object> meta(Object... extra) {
		Map<String, Object> meta = new HashMap<>();
		meta.put(MetaKeys.PROTOCOL_VERSION, McpSchema.LATEST_PROTOCOL_VERSION);
		meta.put(MetaKeys.CLIENT_CAPABILITIES, Map.of());
		for (int i = 0; i < extra.length; i += 2) {
			meta.put((String) extra[i], extra[i + 1]);
		}
		return meta;
	}

	/** Params carrying a valid {@code _meta} plus the given pairs. */
	static Map<String, Object> params(Object... entries) {
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("_meta", meta());
		for (int i = 0; i < entries.length; i += 2) {
			params.put((String) entries[i], entries[i + 1]);
		}
		return params;
	}

	static String request(Object id, String method, Map<String, Object> params) {
		return toJson(new JSONRPCRequest(method, id, params));
	}

	static String toolsCall(Object id, String tool, Map<String, Object> arguments) {
		return request(id, McpSchema.METHOD_TOOLS_CALL, params("name", tool, "arguments", arguments));
	}

	static String listen(Object id) {
		return request(id, McpSchema.METHOD_SUBSCRIPTIONS_LISTEN,
				params("notifications", Map.of("toolsListChanged", true)));
	}

	static Tool tool(String name) {
		return Tool.builder(name, EMPTY_SCHEMA).build();
	}

	static List<Tool> tools(String... names) {
		return List.of(names).stream().map(ModernRequests::tool).toList();
	}

	static CallToolResult text(String text) {
		return CallToolResult.builder().addContent(TextContent.builder(text).build()).build();
	}

	static String toJson(Object value) {
		try {
			return JSON.writeValueAsString(value);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
