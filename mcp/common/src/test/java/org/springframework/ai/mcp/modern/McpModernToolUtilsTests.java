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

package org.springframework.ai.mcp.modern;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCRequest;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.server.McpAsyncResponse;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpTransportResponse;
import io.modelcontextprotocol.modern.server.feature.McpAsyncTool;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
class McpModernToolUtilsTests {

	@Test
	@SuppressWarnings("unchecked")
	void toolCallbackSeesTheRequestContextAndLegacyAccessorStaysSafe() {
		AtomicReference<ToolContext> seen = new AtomicReference<>();
		ToolCallback callback = new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name("probe")
					.description("probe")
					.inputSchema("{\"type\":\"object\",\"properties\":{}}")
					.build();
			}

			@Override
			public String call(String toolInput) {
				return call(toolInput, null);
			}

			@Override
			public String call(String toolInput, ToolContext toolContext) {
				seen.set(toolContext);
				return "ok";
			}
		};
		InMemorySyncToolRepository repository = InMemorySyncToolRepository.builder()
			.tool(McpModernToolUtils.toSyncTool(callback))
			.build();
		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(ToolsFeature.ofSync(repository))
			.build();

		Map<String, Object> meta = new HashMap<>();
		meta.put(MetaKeys.PROTOCOL_VERSION, McpSchema.LATEST_PROTOCOL_VERSION);
		meta.put(MetaKeys.CLIENT_CAPABILITIES, Map.of());
		McpTransportResponse response = server
			.handleBlocking(McpTransportContext.EMPTY,
					new JSONRPCRequest(McpSchema.METHOD_TOOLS_CALL, 1, Map.of("_meta", meta, "name", "probe")))
			.block();

		Map<String, Object> result = (Map<String, Object>) ((McpTransportResponse.Result) response).response().result();
		List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
		assertThat(content.get(0).get("text")).isEqualTo("ok");

		assertThat(McpModernToolUtils.getMcpRequestContext(seen.get())).isInstanceOf(McpRequestContext.class);
		assertThat(McpToolUtils.getMcpExchange(seen.get())).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void asyncToolRunsTheCallbackOffTheCallingThreadUnlessTheRequestIsBlocking() {
		AtomicReference<String> thread = new AtomicReference<>();
		ToolCallback callback = probe(toolInput -> {
			thread.set(Thread.currentThread().getName());
			return "ok";
		});
		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(ToolsFeature
				.ofAsync(InMemoryAsyncToolRepository.builder().tool(McpModernToolUtils.toAsyncTool(callback)).build()))
			.build();
		JSONRPCRequest request = new JSONRPCRequest(McpSchema.METHOD_TOOLS_CALL, 1,
				Map.of("_meta", meta(), "name", "probe"));

		Map<String, Object> result = (Map<String, Object>) ((McpTransportResponse.Result) server
			.handle(McpTransportContext.EMPTY, request)
			.block()).response().result();
		List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
		assertThat(content.get(0).get("text")).isEqualTo("ok");
		assertThat(thread.get()).startsWith("boundedElastic");

		server.handleBlocking(McpTransportContext.EMPTY, request).block();
		assertThat(thread.get()).isEqualTo(Thread.currentThread().getName());
	}

	@Test
	@SuppressWarnings("unchecked")
	void asyncToolReportsACallbackFailureAsAnErrorResult() {
		ToolCallback callback = probe(toolInput -> {
			throw new IllegalStateException("boom");
		});
		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(ToolsFeature
				.ofAsync(InMemoryAsyncToolRepository.builder().tool(McpModernToolUtils.toAsyncTool(callback)).build()))
			.build();

		Map<String, Object> result = (Map<String, Object>) ((McpTransportResponse.Result) server
			.handle(McpTransportContext.EMPTY,
					new JSONRPCRequest(McpSchema.METHOD_TOOLS_CALL, 1, Map.of("_meta", meta(), "name", "probe")))
			.block()).response().result();
		assertThat(result.get("isError")).isEqualTo(true);
		List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
		assertThat(content.get(0).get("text")).isEqualTo("boom");
	}

	@Test
	void fromBlockingHonoursTheBlockingFlagOfTheRequest() {
		McpRequestContext blocking = requestContext(true);
		McpRequestContext nonBlocking = requestContext(false);

		assertThat(McpModernAsyncUtils.fromBlocking(blocking, () -> Thread.currentThread().getName()).block())
			.isEqualTo(Thread.currentThread().getName());
		assertThat(McpModernAsyncUtils.fromBlocking(nonBlocking, () -> Thread.currentThread().getName()).block())
			.startsWith("boundedElastic");
		assertThat(McpModernAsyncUtils.fromBlocking(blocking, () -> null).hasElement().block()).isFalse();
	}

	private static McpRequestContext requestContext(boolean blocking) {
		AtomicReference<McpRequestContext> seen = new AtomicReference<>();
		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test", "1").build())
			.feature(ToolsFeature.ofAsync(InMemoryAsyncToolRepository.builder()
				.tool(McpAsyncTool.of(Tool.builder("probe", Map.of("type", "object")).build(), (ctx, request) -> {
					seen.set(ctx);
					return Mono.just(McpAsyncResponse.result(CallToolResult.builder().build()));
				}))
				.build()))
			.build();
		JSONRPCRequest request = new JSONRPCRequest(McpSchema.METHOD_TOOLS_CALL, 1,
				Map.of("_meta", meta(), "name", "probe"));
		(blocking ? server.handleBlocking(McpTransportContext.EMPTY, request)
				: server.handle(McpTransportContext.EMPTY, request))
			.block();
		return seen.get();
	}

	private static Map<String, Object> meta() {
		Map<String, Object> meta = new HashMap<>();
		meta.put(MetaKeys.PROTOCOL_VERSION, McpSchema.LATEST_PROTOCOL_VERSION);
		meta.put(MetaKeys.CLIENT_CAPABILITIES, Map.of());
		return meta;
	}

	private static ToolCallback probe(Function<String, String> call) {
		return new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name("probe")
					.description("probe")
					.inputSchema("{\"type\":\"object\",\"properties\":{}}")
					.build();
			}

			@Override
			public String call(String toolInput) {
				return call.apply(toolInput);
			}

			@Override
			public String call(String toolInput, ToolContext toolContext) {
				return call.apply(toolInput);
			}
		};
	}

	@Test
	void requestContextIsNullOutsideAModernServer() {
		assertThat(McpModernToolUtils.getMcpRequestContext(null)).isNull();
		assertThat(McpModernToolUtils.getMcpRequestContext(new ToolContext(Map.of()))).isNull();
		assertThat(McpModernToolUtils.getMcpRequestContext(
				new ToolContext(Map.of(McpModernToolUtils.TOOL_CONTEXT_MCP_REQUEST_CONTEXT_KEY, "not a context"))))
			.isNull();
	}

}
