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

package org.springframework.ai.mcp.toolbox;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.tool.Tool;
import com.google.cloud.mcp.tool.ToolDefinition;
import com.google.cloud.mcp.tool.ToolDefinition.Parameter;
import com.google.cloud.mcp.tool.ToolResult;
import com.google.cloud.mcp.tool.ToolResult.Content;
import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link McpToolboxToolCallback} and
 * {@link McpToolboxToolCallbackProvider}.
 *
 * @author Stenal P Jolly
 */
class McpToolboxToolCallbackTests {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Test
	void shouldBuildStructuredJsonSchemaAndInvokeTool() throws Exception {
		McpToolboxClient mockClient = mock(McpToolboxClient.class);
		ToolDefinition definition = new ToolDefinition("Search hotels by city",
				List.of(new Parameter("city", "string", true, "City name", List.of(), "Zurich")), List.of());
		ToolResult toolResult = new ToolResult(List.of(new Content("text", "[{\"name\":\"Grand Hotel\"}]")), false);

		when(mockClient.invokeTool(eq("search-hotels"), anyMap(), anyMap()))
			.thenReturn(CompletableFuture.completedFuture(toolResult));

		McpToolboxToolCallback callback = new McpToolboxToolCallback("search-hotels", definition, mockClient);

		assertThat(callback.getToolDefinition().name()).isEqualTo("search-hotels");
		assertThat(callback.getToolDefinition().description()).isEqualTo("Search hotels by city");

		JsonNode schemaNode = OBJECT_MAPPER.readTree(callback.getToolDefinition().inputSchema());
		assertThat(schemaNode.get("type").asText()).isEqualTo("object");
		assertThat(schemaNode.get("additionalProperties").asBoolean()).isFalse();
		assertThat(schemaNode.at("/properties/city/type").asText()).isEqualTo("string");
		assertThat(schemaNode.at("/properties/city/description").asText()).isEqualTo("City name");
		assertThat(schemaNode.at("/properties/city/default").asText()).isEqualTo("Zurich");
		assertThat(schemaNode.get("required").get(0).asText()).isEqualTo("city");

		String response = callback.call("{\"city\":\"Basel\"}");
		assertThat(response).isEqualTo("[{\"name\":\"Grand Hotel\"}]");
	}

	@Test
	void shouldThrowToolExecutionExceptionWhenToolReturnsError() {
		McpToolboxClient mockClient = mock(McpToolboxClient.class);
		ToolDefinition definition = new ToolDefinition("Failing tool", List.of(), List.of());
		ToolResult errorResult = new ToolResult(List.of(new Content("text", "Database constraint violation")), true);

		when(mockClient.invokeTool(eq("failing-tool"), anyMap(), anyMap()))
			.thenReturn(CompletableFuture.completedFuture(errorResult));

		McpToolboxToolCallback callback = new McpToolboxToolCallback("failing-tool", definition, mockClient);

		assertThatThrownBy(() -> callback.call("{}")).isInstanceOf(ToolExecutionException.class)
			.hasCauseInstanceOf(IllegalStateException.class)
			.hasRootCauseMessage("MCP Toolbox tool execution failed: Database constraint violation");
	}

	@Test
	void shouldThrowToolExecutionExceptionOnInvalidJsonInput() {
		McpToolboxClient mockClient = mock(McpToolboxClient.class);
		ToolDefinition definition = new ToolDefinition("Search tool", List.of(), List.of());
		McpToolboxToolCallback callback = new McpToolboxToolCallback("search-tool", definition, mockClient);

		assertThatThrownBy(() -> callback.call("{invalid-json")).isInstanceOf(ToolExecutionException.class);
	}

	@Test
	void shouldUnwrapExecutionExceptionAndEnforceTimeout() {
		McpToolboxClient mockClient = mock(McpToolboxClient.class);
		ToolDefinition definition = new ToolDefinition("Slow tool", List.of(), List.of());
		CompletableFuture<ToolResult> neverCompletingFuture = new CompletableFuture<>();

		when(mockClient.invokeTool(eq("slow-tool"), anyMap(), anyMap())).thenReturn(neverCompletingFuture);

		McpToolboxToolCallback callback = new McpToolboxToolCallback("slow-tool", definition, mockClient,
				Duration.ofMillis(25), OBJECT_MAPPER);

		assertThatThrownBy(() -> callback.call("{}")).isInstanceOf(ToolExecutionException.class)
			.hasCauseInstanceOf(TimeoutException.class);
	}

	@Test
	void providerShouldMemoizeToolCallbacksAndSupportInvalidation() {
		McpToolboxClient mockClient = mock(McpToolboxClient.class);
		ToolDefinition definition = new ToolDefinition("List products", List.of(), List.of());
		when(mockClient.listTools()).thenReturn(CompletableFuture.completedFuture(Map.of("list-products", definition)));

		McpToolboxToolCallbackProvider provider = new McpToolboxToolCallbackProvider(mockClient);
		ToolCallback[] firstCall = provider.getToolCallbacks();
		ToolCallback[] secondCall = provider.getToolCallbacks();

		assertThat(firstCall).hasSize(1);
		assertThat(secondCall).hasSize(1);
		verify(mockClient, times(1)).listTools();

		provider.invalidateCache();
		provider.getToolCallbacks();
		verify(mockClient, times(2)).listTools();
	}

	@Test
	void providerShouldLoadConfiguredToolsetsAndToolsAndDeduplicate() {
		McpToolboxClient mockClient = mock(McpToolboxClient.class);
		ToolDefinition sharedDef = new ToolDefinition("Shared tool", List.of(), List.of());
		ToolDefinition extraDef = new ToolDefinition("Extra tool", List.of(), List.of());
		Tool loadedSharedTool = new Tool("shared-tool", sharedDef, mockClient);

		when(mockClient.loadToolset("catalog-toolset"))
			.thenReturn(CompletableFuture.completedFuture(Map.of("shared-tool", sharedDef, "extra-tool", extraDef)));
		when(mockClient.loadTool("shared-tool")).thenReturn(CompletableFuture.completedFuture(loadedSharedTool));

		McpToolboxToolCallbackProvider provider = new McpToolboxToolCallbackProvider(mockClient,
				List.of("catalog-toolset"), List.of("shared-tool"));

		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(2);
		assertThat(List.of(callbacks[0].getToolDefinition().name(), callbacks[1].getToolDefinition().name()))
			.containsExactlyInAnyOrder("shared-tool", "extra-tool");
	}

}
