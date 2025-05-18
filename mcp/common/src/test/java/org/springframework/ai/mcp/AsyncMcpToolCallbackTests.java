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

import java.util.Map;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.chat.model.ToolContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncMcpToolCallbackTests {

	@Mock
	private McpAsyncClient mcpClient;

	@Mock
	private Tool tool;

	@Test
	void getToolDefinitionShouldReturnCorrectDefinition() {
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		when(this.tool.name()).thenReturn("testTool");
		when(this.tool.description()).thenReturn("Test tool description");

		AsyncMcpToolCallback callback = new AsyncMcpToolCallback(this.mcpClient, this.tool);

		var toolDefinition = callback.getToolDefinition();

		assertThat(toolDefinition.name()).isEqualTo(clientInfo.name() + "_testTool");
		assertThat(toolDefinition.description()).isEqualTo("Test tool description");
	}

	@Test
	void callShouldHandleJsonInputAndOutput() {
		when(this.tool.name()).thenReturn("testTool");
		CallToolResult callResult = mock(CallToolResult.class);
		when(this.mcpClient.callTool(any(CallToolRequest.class))).thenReturn(Mono.just(callResult));

		AsyncMcpToolCallback callback = new AsyncMcpToolCallback(this.mcpClient, this.tool);

		String response = callback.call("{\"param\":\"value\"}");

		assertThat(response).isNotNull();
	}

	@Test
	void callShouldIgnoreToolContext() {
		when(this.tool.name()).thenReturn("testTool");
		CallToolResult callResult = mock(CallToolResult.class);
		when(this.mcpClient.callTool(any(CallToolRequest.class))).thenReturn(Mono.just(callResult));

		AsyncMcpToolCallback callback = new AsyncMcpToolCallback(this.mcpClient, this.tool);

		String response = callback.call("{\"param\":\"value\"}", new ToolContext(Map.of("foo", "bar")));

		assertThat(response).isNotNull();
	}

	@Test
	void callAsyncShouldHandleJsonInputAndOutput() {
		when(this.tool.name()).thenReturn("testTool");
		CallToolResult callResult = mock(CallToolResult.class);
		when(this.mcpClient.callTool(any(CallToolRequest.class))).thenReturn(Mono.just(callResult));

		AsyncMcpToolCallback callback = new AsyncMcpToolCallback(this.mcpClient, this.tool);

		StepVerifier.create(callback.callAsync("{\"param\":\"value\"}"))
				.assertNext(response -> assertThat(response).isNotNull())
				.verifyComplete();
	}

	@Test
	void callAsyncShouldIgnoreToolContext() {
		when(this.tool.name()).thenReturn("testTool");
		CallToolResult callResult = mock(CallToolResult.class);
		when(this.mcpClient.callTool(any(CallToolRequest.class))).thenReturn(Mono.just(callResult));

		AsyncMcpToolCallback callback = new AsyncMcpToolCallback(this.mcpClient, this.tool);

		StepVerifier.create(callback.callAsync("{\"param\":\"value\"}", new ToolContext(Map.of("foo", "bar"))))
				.assertNext(response -> assertThat(response).isNotNull())
				.verifyComplete();
	}

	@Test
	void callAsyncShouldPropagateErrors() {
		when(this.tool.name()).thenReturn("testTool");
		RuntimeException testException = new RuntimeException("Test error");
		when(this.mcpClient.callTool(any(CallToolRequest.class))).thenReturn(Mono.error(testException));

		AsyncMcpToolCallback callback = new AsyncMcpToolCallback(this.mcpClient, this.tool);

		StepVerifier.create(callback.callAsync("{\"param\":\"value\"}"))
				.expectErrorMatches(error -> error instanceof RuntimeException && 
						"Test error".equals(error.getMessage()))
				.verify();
	}
} 