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
		var clientInfo = new McpSchema.Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		var callToolResult = McpSchema.CallToolResult.builder().addTextContent("Some error data").isError(true).build();
		when(this.mcpClient.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(Mono.just(callToolResult));

		var callback = new AsyncMcpToolCallback(this.mcpClient, this.tool);
		assertThatThrownBy(() -> callback.call("{\"param\":\"value\"}")).isInstanceOf(ToolExecutionException.class)
			.cause()
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Error calling tool: [TextContent[audience=null, priority=null, text=Some error data]]");
	}

	@Test
	void callShouldWrapReactiveErrors() {
		when(this.tool.name()).thenReturn("testTool");
		var clientInfo = new McpSchema.Implementation("testClient", "1.0.0");
		when(this.mcpClient.getClientInfo()).thenReturn(clientInfo);
		when(this.mcpClient.callTool(any(McpSchema.CallToolRequest.class)))
			.thenReturn(Mono.error(new Exception("Testing tool error")));

		var callback = new AsyncMcpToolCallback(this.mcpClient, this.tool);
		assertThatThrownBy(() -> callback.call("{\"param\":\"value\"}")).isInstanceOf(ToolExecutionException.class)
			.rootCause()
			.hasMessage("Testing tool error");
	}

}
