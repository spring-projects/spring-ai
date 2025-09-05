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

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import org.springframework.ai.tool.execution.ToolExecutionException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncMcpToolCallbackTest {

	@Mock
	private McpAsyncClient mcpClient;

	@Mock
	private McpSchema.Tool tool;

	@Test
	void callShouldThrowOnError() {
		when(this.tool.name()).thenReturn("testTool");
		var callToolResult = McpSchema.CallToolResult.builder().addTextContent("Some error data").isError(true).build();
		when(this.mcpClient.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(Mono.just(callToolResult));

		var callback = new AsyncMcpToolCallback(this.mcpClient, this.tool, this.tool.name());
		assertThatThrownBy(() -> callback.call("{\"param\":\"value\"}")).isInstanceOf(ToolExecutionException.class)
			.cause()
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Error calling tool: [TextContent[annotations=null, text=Some error data, meta=null]]");
	}

	@Test
	void callShouldWrapReactiveErrors() {
		when(this.tool.name()).thenReturn("testTool");
		when(this.mcpClient.callTool(any(McpSchema.CallToolRequest.class)))
			.thenReturn(Mono.error(new Exception("Testing tool error")));

		var callback = new AsyncMcpToolCallback(this.mcpClient, this.tool, this.tool.name());
		assertThatThrownBy(() -> callback.call("{\"param\":\"value\"}")).isInstanceOf(ToolExecutionException.class)
			.rootCause()
			.hasMessage("Testing tool error");
	}

}
