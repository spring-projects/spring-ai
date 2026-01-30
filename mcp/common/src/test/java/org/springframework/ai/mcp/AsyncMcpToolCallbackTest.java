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
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolCallResult;
import org.springframework.ai.tool.execution.ToolExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

		var callback = AsyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName(this.tool.name())
			.build();
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

		var callback = AsyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName(this.tool.name())
			.build();
		assertThatThrownBy(() -> callback.call("{\"param\":\"value\"}")).isInstanceOf(ToolExecutionException.class)
			.rootCause()
			.hasMessage("Testing tool error");
	}

	@Test
	void callShouldSucceedWithValidInput() {
		when(this.tool.name()).thenReturn("testTool");

		var callToolResult = McpSchema.CallToolResult.builder()
			.addTextContent("Success response")
			.isError(false)
			.build();
		when(this.mcpClient.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(Mono.just(callToolResult));

		// Act
		var callback = AsyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName("prefixed_testTool")
			.build();

		ToolCallResult result = callback.call("{\"param\":\"value\"}");

		// Assert
		assertThat(result.content()).contains("Success response");

		// Verify the correct tool name was used in the request
		ArgumentCaptor<McpSchema.CallToolRequest> requestCaptor = ArgumentCaptor
			.forClass(McpSchema.CallToolRequest.class);
		verify(this.mcpClient).callTool(requestCaptor.capture());
		assertThat(requestCaptor.getValue().name()).isEqualTo("testTool"); // Original
																			// name, not
																			// prefixed
	}

	@Test
	void callShouldHandleNullInput() {
		when(this.tool.name()).thenReturn("testTool");
		var callToolResult = McpSchema.CallToolResult.builder()
			.addTextContent("Success with empty input")
			.isError(false)
			.build();
		when(this.mcpClient.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(Mono.just(callToolResult));

		// Act
		var callback = AsyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName("testTool")
			.build();

		ToolCallResult result = callback.call(null);

		// Assert
		assertThat(result.content()).contains("Success with empty input");

		// Verify empty JSON object was used
		ArgumentCaptor<McpSchema.CallToolRequest> requestCaptor = ArgumentCaptor
			.forClass(McpSchema.CallToolRequest.class);
		verify(this.mcpClient).callTool(requestCaptor.capture());
		assertThat(requestCaptor.getValue().arguments()).isEmpty();
	}

	@Test
	void callShouldHandleEmptyInput() {
		when(this.tool.name()).thenReturn("testTool");
		var callToolResult = McpSchema.CallToolResult.builder()
			.addTextContent("Success with empty input")
			.isError(false)
			.build();
		when(this.mcpClient.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(Mono.just(callToolResult));

		// Act
		var callback = AsyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName("testTool")
			.build();

		ToolCallResult result = callback.call("");

		// Assert
		assertThat(result.content()).contains("Success with empty input");

		// Verify empty JSON object was used
		ArgumentCaptor<McpSchema.CallToolRequest> requestCaptor = ArgumentCaptor
			.forClass(McpSchema.CallToolRequest.class);
		verify(this.mcpClient).callTool(requestCaptor.capture());
		assertThat(requestCaptor.getValue().arguments()).isEmpty();
	}

	@Test
	void callShouldIncludeToolContext() {
		when(this.tool.name()).thenReturn("testTool");
		var callToolResult = McpSchema.CallToolResult.builder()
			.addTextContent("Success with context")
			.isError(false)
			.build();
		when(this.mcpClient.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(Mono.just(callToolResult));

		ToolContext toolContext = mock(ToolContext.class);
		when(toolContext.getContext()).thenReturn(Map.of("key", "value"));

		// Act
		var callback = AsyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName("testTool")
			.build();

		ToolCallResult result = callback.call("{\"param\":\"value\"}", toolContext);

		// Assert
		assertThat(result.content()).contains("Success with context");

		// Verify the context was included in the request
		ArgumentCaptor<McpSchema.CallToolRequest> requestCaptor = ArgumentCaptor
			.forClass(McpSchema.CallToolRequest.class);
		verify(this.mcpClient).callTool(requestCaptor.capture());
		assertThat(requestCaptor.getValue().meta()).isNotNull();
	}

	@Test
	void getToolDefinitionShouldReturnCorrectDefinition() {
		when(this.tool.description()).thenReturn("Test tool description");
		var jsonSchema = mock(McpSchema.JsonSchema.class);
		when(this.tool.inputSchema()).thenReturn(jsonSchema);

		// Act
		var callback = AsyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName("prefix_testTool")
			.build();

		ToolDefinition definition = callback.getToolDefinition();

		// Assert
		assertThat(definition.name()).isEqualTo("prefix_testTool");
		assertThat(definition.description()).isEqualTo("Test tool description");
		assertThat(definition.inputSchema()).isNotNull();
	}

	@Test
	void getOriginalToolNameShouldReturnCorrectName() {
		when(this.tool.name()).thenReturn("originalToolName");

		// Act
		var callback = AsyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName("prefix_originalToolName")
			.build();

		// Assert
		assertThat(callback.getOriginalToolName()).isEqualTo("originalToolName");
	}

	@Test
	void builderShouldGeneratePrefixedToolNameWhenNotProvided() {
		when(this.tool.name()).thenReturn("testTool");

		// Act
		var callback = AsyncMcpToolCallback.builder().mcpClient(this.mcpClient).tool(this.tool).build();

		// Assert
		ToolDefinition definition = callback.getToolDefinition();
		assertThat(definition.name()).contains("testTool"); // Should contain the tool
															// name
	}

	@Test
	void builderShouldThrowWhenMcpClientIsNull() {
		// Act & Assert
		assertThatThrownBy(() -> AsyncMcpToolCallback.builder().tool(this.tool).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("MCP client must not be null");
	}

	@Test
	void builderShouldThrowWhenToolIsNull() {
		// Act & Assert
		assertThatThrownBy(() -> AsyncMcpToolCallback.builder().mcpClient(this.mcpClient).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("MCP tool must not be null");
	}

	@Test
	void builderShouldAcceptCustomToolContextConverter() {
		when(this.tool.name()).thenReturn("testTool");
		ToolContextToMcpMetaConverter customConverter = mock(ToolContextToMcpMetaConverter.class);

		var callToolResult = McpSchema.CallToolResult.builder().addTextContent("Success").isError(false).build();
		when(this.mcpClient.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(Mono.just(callToolResult));

		ToolContext toolContext = mock(ToolContext.class);
		when(customConverter.convert(toolContext)).thenReturn(Map.of("custom", "meta"));

		// Act
		var callback = AsyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName("testTool")
			.toolContextToMcpMetaConverter(customConverter)
			.build();

		callback.call("{}", toolContext);

		// Assert
		verify(customConverter).convert(toolContext);
	}

	@Test
	@SuppressWarnings("deprecation")
	void deprecatedConstructorShouldWork() {
		when(this.tool.name()).thenReturn("testTool");
		when(this.tool.description()).thenReturn("Test description");
		when(this.tool.inputSchema()).thenReturn(mock(McpSchema.JsonSchema.class));
		var clientInfo = new Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);

		// Act
		var callback = new AsyncMcpToolCallback(this.mcpClient, this.tool);

		// Assert
		assertThat(callback.getOriginalToolName()).isEqualTo("testTool");
		assertThat(callback.getToolDefinition().description()).isEqualTo("Test description");
	}

	@Test
	void callShouldHandleComplexJsonResponse() {
		when(this.tool.name()).thenReturn("testTool");
		var callToolResult = McpSchema.CallToolResult.builder()
			.addTextContent("Part 1")
			.addTextContent("Part 2")
			.isError(false)
			.build();
		when(this.mcpClient.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(Mono.just(callToolResult));

		// Act
		var callback = AsyncMcpToolCallback.builder()
			.mcpClient(this.mcpClient)
			.tool(this.tool)
			.prefixedToolName("testTool")
			.build();

		ToolCallResult result = callback.call("{\"input\":\"test\"}");

		// Assert
		assertThat(result.content()).contains("Part 1");
		assertThat(result.content()).contains("Part 2");
	}

}
