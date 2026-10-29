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

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCRequest;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpTransportResponse;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import org.junit.jupiter.api.Test;

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
			.tool(McpModernToolUtils.toSyncToolSpecification(callback))
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

		assertThat(McpModernToolUtils.getMcpRequestContext(seen.get())).isPresent()
			.get()
			.isInstanceOf(McpRequestContext.class);
		assertThat(McpToolUtils.getMcpExchange(seen.get())).isEmpty();
	}

}
