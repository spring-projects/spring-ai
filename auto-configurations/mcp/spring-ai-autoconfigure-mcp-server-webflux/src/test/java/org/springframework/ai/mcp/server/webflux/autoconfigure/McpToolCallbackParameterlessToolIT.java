/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.mcp.server.webflux.autoconfigure;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebFluxStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.annotations.McpClientAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.client.webflux.autoconfigure.StreamableHttpWebFluxTransportAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerJsonMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.ToolCallbackConverterAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.reactive.function.server.RouterFunctions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test to reproduce the issue where MCP tools with no parameters (incomplete
 * schemas) fail to create valid tool definitions.
 *
 * @author Ilayaperumal Gopinathan
 */
class McpToolCallbackParameterlessToolIT {

	private final ApplicationContextRunner syncServerContextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.server.protocol=STREAMABLE", "spring.ai.mcp.server.type=SYNC")
		.withConfiguration(AutoConfigurations.of(McpServerAutoConfiguration.class,
				McpServerJsonMapperAutoConfiguration.class, ToolCallbackConverterAutoConfiguration.class,
				McpServerStreamableHttpWebFluxAutoConfiguration.class,
				McpServerAnnotationScannerAutoConfiguration.class,
				McpServerSpecificationFactoryAutoConfiguration.class));

	private final ApplicationContextRunner clientApplicationContext = new ApplicationContextRunner()
		.withConfiguration(baseAutoConfig(McpToolCallbackAutoConfiguration.class, McpClientAutoConfiguration.class,
				StreamableHttpWebFluxTransportAutoConfiguration.class,
				McpClientAnnotationScannerAutoConfiguration.class));

	private static AutoConfigurations baseAutoConfig(Class<?>... additional) {
		Class<?>[] dependencies = { RestClientAutoConfiguration.class, WebClientAutoConfiguration.class };
		Class<?>[] all = Stream.concat(Arrays.stream(dependencies), Arrays.stream(additional)).toArray(Class<?>[]::new);
		return AutoConfigurations.of(all);
	}

	@Test
	void testMcpServerClientIntegrationWithIncompleteSchemaSyncTool() {
		int serverPort = TestSocketUtils.findAvailableTcpPort();

		this.syncServerContextRunner
			.withPropertyValues(// @formatter:off
				"spring.ai.mcp.server.name=test-incomplete-schema-server",
				"spring.ai.mcp.server.version=1.0.0",
				"spring.ai.mcp.server.streamable-http.keep-alive-interval=1s",
				"spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp") // @formatter:on
			.run(serverContext -> {

				McpSyncServer mcpSyncServer = serverContext.getBean(McpSyncServer.class);

				JsonMapper jsonMapper = serverContext.getBean(JsonMapper.class);

				String incompleteSchemaJson = "{\"type\":\"object\",\"additionalProperties\":false}";
				McpSchema.JsonSchema incompleteSchema = jsonMapper.readValue(incompleteSchemaJson,
						McpSchema.JsonSchema.class);

				// Build the tool using the builder pattern
				McpSchema.Tool parameterlessTool = McpSchema.Tool.builder()
					.name("getCurrentTime")
					.description("Get the current server time")
					.inputSchema(incompleteSchema)
					.build();

				// Create a tool specification that returns a simple response
				McpServerFeatures.SyncToolSpecification toolSpec = new McpServerFeatures.SyncToolSpecification(
						parameterlessTool, (exchange, arguments) -> {
							McpSchema.TextContent content = new McpSchema.TextContent(
									"Current time: " + Instant.now().toString());
							return new McpSchema.CallToolResult(List.of(content), false, null);
						}, (exchange, request) -> {
							McpSchema.TextContent content = new McpSchema.TextContent(
									"Current time: " + Instant.now().toString());
							return new McpSchema.CallToolResult(List.of(content), false, null);
						});

				// Add the tool with incomplete schema to the server
				mcpSyncServer.addTool(toolSpec);

				var httpServer = startHttpServer(serverContext, serverPort);

				this.clientApplicationContext
					.withPropertyValues(// @formatter:off
							"spring.ai.mcp.client.type=SYNC",
						"spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:" + serverPort,
						"spring.ai.mcp.client.initialized=false") // @formatter:on
					.run(clientContext -> {

						ToolCallbackProvider toolCallbackProvider = clientContext
							.getBean(SyncMcpToolCallbackProvider.class);

						// Wait for the client to receive the tool from the server
						await().atMost(Duration.ofSeconds(5))
							.pollInterval(Duration.ofMillis(100))
							.untilAsserted(() -> assertThat(toolCallbackProvider.getToolCallbacks()).isNotEmpty());

						List<ToolCallback> toolCallbacks = Arrays.asList(toolCallbackProvider.getToolCallbacks());

						// We expect 1 tool: getCurrentTime (parameterless with incomplete
						// schema)
						assertThat(toolCallbacks).hasSize(1);

						// Get the tool callback
						ToolCallback toolCallback = toolCallbacks.get(0);
						ToolDefinition toolDefinition = toolCallback.getToolDefinition();

						// Verify the tool definition
						assertThat(toolDefinition).isNotNull();
						assertThat(toolDefinition.name()).contains("getCurrentTime");
						assertThat(toolDefinition.description()).isEqualTo("Get the current server time");

						// **THE KEY VERIFICATION**: The input schema should now have the
						// "properties" field
						// even though the server provided a schema without it
						String inputSchema = toolDefinition.inputSchema();
						assertThat(inputSchema).isNotNull().isNotEmpty();

						Map<String, Object> schemaMap = ModelOptionsUtils.jsonToMap(inputSchema);
						assertThat(schemaMap).isNotNull();
						assertThat(schemaMap).containsKey("type");
						assertThat(schemaMap.get("type")).isEqualTo("object");

						assertThat(schemaMap).containsKey("properties");
						assertThat(schemaMap.get("properties")).isInstanceOf(Map.class);

						// Verify the properties map is empty for a parameterless tool
						Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
						assertThat(properties).isEmpty();

						// Verify that additionalProperties is preserved after
						// normalization
						assertThat(schemaMap).containsKey("additionalProperties");
						assertThat(schemaMap.get("additionalProperties")).isEqualTo(false);

						// Test that the callback can be called successfully
						String result = toolCallback.call("{}");
						assertThat(result).isNotNull().contains("Current time:");
					});

				stopHttpServer(httpServer);
			});
	}

	// Helper methods to start and stop the HTTP server
	private static DisposableServer startHttpServer(ApplicationContext serverContext, int port) {
		WebFluxStreamableServerTransportProvider mcpStreamableServerTransport = serverContext
			.getBean(WebFluxStreamableServerTransportProvider.class);
		HttpHandler httpHandler = RouterFunctions.toHttpHandler(mcpStreamableServerTransport.getRouterFunction());
		ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
		return HttpServer.create().port(port).handle(adapter).bindNow();
	}

	private static void stopHttpServer(DisposableServer server) {
		if (server != null) {
			server.disposeNow();
		}
	}

}
