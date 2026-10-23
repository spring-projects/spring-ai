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

package org.springframework.ai.mcp;

import java.util.List;
import java.util.stream.Stream;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;

import org.springframework.ai.util.JacksonUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpToolCallbackContentTests {

	@ParameterizedTest
	@MethodSource("content")
	void syncCallShouldPreserveContentTypes(String contentJson) {
		var mapper = JacksonUtils.getDefaultJsonMapper();
		var response = mapper.readValue("{\"content\":" + contentJson + ",\"isError\":false}", CallToolResult.class);
		var client = mock(McpSyncClient.class);
		when(client.callTool(any(CallToolRequest.class))).thenReturn(response);
		var callback = SyncMcpToolCallback.builder()
			.mcpClient(client)
			.tool(Tool.builder().name("testTool").build())
			.build();

		String result = callback.call("{}");

		assertThat(mapper.readTree(result)).isEqualTo(mapper.readTree(contentJson));
		assertThat(mapper.readValue(result, new TypeReference<List<Content>>() {
		})).isEqualTo(response.content());
	}

	@ParameterizedTest
	@MethodSource("content")
	void asyncCallShouldPreserveContentTypes(String contentJson) {
		var mapper = JacksonUtils.getDefaultJsonMapper();
		var response = mapper.readValue("{\"content\":" + contentJson + ",\"isError\":false}", CallToolResult.class);
		var client = mock(McpAsyncClient.class);
		when(client.callTool(any(CallToolRequest.class))).thenReturn(Mono.just(response));
		var callback = AsyncMcpToolCallback.builder()
			.mcpClient(client)
			.tool(Tool.builder().name("testTool").build())
			.build();

		String result = callback.call("{}");

		assertThat(mapper.readTree(result)).isEqualTo(mapper.readTree(contentJson));
		assertThat(mapper.readValue(result, new TypeReference<List<Content>>() {
		})).isEqualTo(response.content());
	}

	static Stream<String> content() {
		return Stream.of("[]", """
				[{"type":"text","text":"hello"}]
				""", """
				[{"type":"image","data":"aGVsbG8=","mimeType":"image/png"}]
				""", """
				[{"type":"audio","data":"aGVsbG8=","mimeType":"audio/wav"}]
				""", """
				[{"type":"resource","resource":{"uri":"file:///test.txt","mimeType":"text/plain","text":"hello"}}]
				""", """
				[{"type":"resource","resource":{"uri":"file:///test.png","mimeType":"image/png","blob":"aGVsbG8="}}]
				""", """
				[{"type":"resource_link","name":"test","uri":"file:///test.txt","mimeType":"text/plain"}]
				""", """
				[
				{"type":"text","text":"hello","annotations":{"audience":["user"],"priority":0.5},
				"_meta":{"source":"test"}},
				{"type":"image","data":"aGVsbG8=","mimeType":"image/png"},
				{"type":"audio","data":"aGVsbG8=","mimeType":"audio/wav"},
				{"type":"resource","resource":{"uri":"file:///test.txt","text":"hello",
				"_meta":{"source":"resource"}}},
				{"type":"resource_link","name":"test","uri":"file:///test.txt"}
				]
				""");
	}

}
