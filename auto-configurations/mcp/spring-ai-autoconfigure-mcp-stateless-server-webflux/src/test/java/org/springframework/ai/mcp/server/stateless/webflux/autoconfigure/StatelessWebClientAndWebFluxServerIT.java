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

package org.springframework.ai.mcp.server.stateless.webflux.autoconfigure;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.json;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration;
import org.springframework.ai.mcp.client.webflux.autoconfigure.StreamableHttpWebFluxTransportAutoConfiguration;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.ai.mcp.server.stateless.autoconfigure.McpStatelessServerAutoConfiguration;
import org.springframework.ai.mcp.server.stateless.autoconfigure.McpStatelessServerProperties;
import org.springframework.ai.mcp.server.stateless.autoconfigure.ToolCallbackConverterAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ResolvableType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.WebFluxStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptArgument;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.PromptReference;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import net.javacrumbs.jsonunit.core.Option;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

public class StatelessWebClientAndWebFluxServerIT {

	private final ApplicationContextRunner serverContextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpStatelessServerAutoConfiguration.class,
				ToolCallbackConverterAutoConfiguration.class, McpStatelessServerWebFluxAutoConfiguration.class));

	private final ApplicationContextRunner clientApplicationContext = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpToolCallbackAutoConfiguration.class,
				McpClientAutoConfiguration.class, StreamableHttpWebFluxTransportAutoConfiguration.class));

	@Test
	void clientServerCapabilities() {

		int serverPort = TestSocketUtils.findAvailableTcpPort();

		this.serverContextRunner.withUserConfiguration(TestMcpServerConfiguration.class)
			.withPropertyValues(// @formatter:off
			"spring.ai.mcp.server.stateless.mcp-endpoint=/mcp",
					"spring.ai.mcp.server.stateless.name=test-mcp-server",
					"spring.ai.mcp.server.stateless.keep-alive-interval=1s",
					"spring.ai.mcp.server.stateless.version=1.0.0") // @formatter:on
			.run(serverContext -> {
				// Verify all required beans are present
				assertThat(serverContext).hasSingleBean(WebFluxStatelessServerTransport.class);
				assertThat(serverContext).hasSingleBean(RouterFunction.class);
				assertThat(serverContext).hasSingleBean(McpStatelessSyncServer.class);

				// Verify server properties are configured correctly
				McpStatelessServerProperties properties = serverContext.getBean(McpStatelessServerProperties.class);
				assertThat(properties.getName()).isEqualTo("test-mcp-server");
				assertThat(properties.getVersion()).isEqualTo("1.0.0");
				assertThat(properties.getMcpEndpoint()).isEqualTo("/mcp");

				var httpServer = startHttpServer(serverContext, serverPort);

				clientApplicationContext.withUserConfiguration(TestMcpClientConfiguration.class)
					.withPropertyValues(// @formatter:off
						"spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:" + serverPort,
						"spring.ai.mcp.client.initialized=false") // @formatter:on
					.run(clientContext -> {
						McpSyncClient mcpClient = getMcpSyncClient(clientContext);
						assertThat(mcpClient).isNotNull();
						var initResult = mcpClient.initialize();
						assertThat(initResult).isNotNull();

						// TOOLS / SAMPLING / ELICITATION

						// tool list
						assertThat(mcpClient.listTools().tools()).hasSize(2);
						assertThat(mcpClient.listTools().tools())
							.contains(Tool.builder().name("tool1").description("tool1 description").inputSchema("""
									{
										"": "http://json-schema.org/draft-07/schema#",
										"type": "object",
										"properties": {}
									}
									""").build());

						// Call a tool that sends progress notifications
						CallToolRequest toolRequest = CallToolRequest.builder()
							.name("tool1")
							.arguments(Map.of())
							.progressToken("test-progress-token")
							.build();

						CallToolResult response = mcpClient.callTool(toolRequest);

						assertThat(response).isNotNull();
						assertThat(response.isError()).isNull();
						String responseText = ((TextContent) response.content().get(0)).text();
						assertThat(responseText).contains("CALL RESPONSE");

						// TOOL STRUCTURED OUTPUT
						// Call tool with valid structured output
						CallToolResult calculatorToolResponse = mcpClient
							.callTool(new McpSchema.CallToolRequest("calculator", Map.of("expression", "2 + 3")));

						assertThat(calculatorToolResponse).isNotNull();
						assertThat(calculatorToolResponse.isError()).isFalse();

						assertThat(calculatorToolResponse.structuredContent()).isNotNull();

						assertThat(calculatorToolResponse.structuredContent()).containsEntry("result", 5.0)
							.containsEntry("operation", "2 + 3")
							.containsEntry("timestamp", "2024-01-01T10:00:00Z");

						assertThatJson(calculatorToolResponse.structuredContent()).when(Option.IGNORING_ARRAY_ORDER)
							.when(Option.IGNORING_EXTRA_ARRAY_ITEMS)
							.isObject()
							.isEqualTo(json("""
									{"result":5.0,"operation":"2 + 3","timestamp":"2024-01-01T10:00:00Z"}"""));

						// PROMPT / COMPLETION

						// list prompts
						assertThat(mcpClient.listPrompts()).isNotNull();
						assertThat(mcpClient.listPrompts().prompts()).hasSize(1);

						// get prompt
						GetPromptResult promptResult = mcpClient
							.getPrompt(new GetPromptRequest("code-completion", Map.of("language", "java")));
						assertThat(promptResult).isNotNull();

						// completion
						CompleteRequest completeRequest = new CompleteRequest(
								new PromptReference("ref/prompt", "code-completion", "Code completion"),
								new CompleteRequest.CompleteArgument("language", "py"));

						CompleteResult completeResult = mcpClient.completeCompletion(completeRequest);

						assertThat(completeResult).isNotNull();
						assertThat(completeResult.completion().total()).isEqualTo(10);
						assertThat(completeResult.completion().values()).containsExactly("python", "pytorch", "pyside");
						assertThat(completeResult.meta()).isNull();

						// RESOURCES
						assertThat(mcpClient.listResources()).isNotNull();
						assertThat(mcpClient.listResources().resources()).hasSize(1);
						assertThat(mcpClient.listResources().resources().get(0))
							.isEqualToComparingFieldByFieldRecursively(Resource.builder()
								.uri("file://resource")
								.name("Test Resource")
								.mimeType("text/plain")
								.description("Test resource description")
								.build());

					});

				stopHttpServer(httpServer);
			});
	}

	public static class TestMcpServerConfiguration {

		@Bean
		public List<McpStatelessServerFeatures.SyncToolSpecification> myTools() {

			// Tool 1
			McpStatelessServerFeatures.SyncToolSpecification tool1 = McpStatelessServerFeatures.SyncToolSpecification
				.builder()
				.tool(Tool.builder().name("tool1").description("tool1 description").inputSchema("""
						{
							"": "http://json-schema.org/draft-07/schema#",
							"type": "object",
							"properties": {}
						}
						""").build())
				.callHandler((exchange, request) -> {

					return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("CALL RESPONSE")), null);
				})
				.build();

			// Tool 2

			// Create a tool with output schema
			Map<String, Object> outputSchema = Map.of(
					"type", "object", "properties", Map.of("result", Map.of("type", "number"), "operation",
							Map.of("type", "string"), "timestamp", Map.of("type", "string")),
					"required", List.of("result", "operation"));

			Tool calculatorTool = Tool.builder()
				.name("calculator")
				.description("Performs mathematical calculations")
				.outputSchema(outputSchema)
				.build();

			McpStatelessServerFeatures.SyncToolSpecification tool2 = McpStatelessServerFeatures.SyncToolSpecification
				.builder()
				.tool(calculatorTool)
				.callHandler((exchange, request) -> {
					String expression = (String) request.arguments().getOrDefault("expression", "2 + 3");
					double result = this.evaluateExpression(expression);
					return CallToolResult.builder()
						.structuredContent(
								Map.of("result", result, "operation", expression, "timestamp", "2024-01-01T10:00:00Z"))
						.build();
				})
				.build();

			return List.of(tool1, tool2);
		}

		@Bean
		public List<McpStatelessServerFeatures.SyncPromptSpecification> myPrompts() {

			var prompt = new McpSchema.Prompt("code-completion", "Code completion", "this is code review prompt",
					List.of(new PromptArgument("language", "Language", "string", false)));

			var promptSpecification = new McpStatelessServerFeatures.SyncPromptSpecification(prompt,
					(exchange, getPromptRequest) -> {
						String languageArgument = (String) getPromptRequest.arguments().get("language");
						if (languageArgument == null) {
							languageArgument = "java";
						}

						var userMessage = new PromptMessage(Role.USER,
								new TextContent("Hello " + languageArgument + "! How can I assist you today?"));
						return new GetPromptResult("A personalized greeting message", List.of(userMessage));
					});

			return List.of(promptSpecification);
		}

		@Bean
		public List<McpStatelessServerFeatures.SyncCompletionSpecification> myCompletions() {
			var completion = new McpStatelessServerFeatures.SyncCompletionSpecification(
					new McpSchema.PromptReference("ref/prompt", "code-completion", "Code completion"),
					(exchange, request) -> {
						var expectedValues = List.of("python", "pytorch", "pyside");
						return new McpSchema.CompleteResult(new CompleteResult.CompleteCompletion(expectedValues, 10, // total
								true // hasMore
						));
					});

			return List.of(completion);
		}

		@Bean
		public List<McpStatelessServerFeatures.SyncResourceSpecification> myResources() {

			var systemInfoResource = Resource.builder()
				.uri("file://resource")
				.name("Test Resource")
				.mimeType("text/plain")
				.description("Test resource description")
				.build();

			var resourceSpecification = new McpStatelessServerFeatures.SyncResourceSpecification(systemInfoResource,
					(exchange, request) -> {
						try {
							var systemInfo = Map.of("os", System.getProperty("os.name"), "os_version",
									System.getProperty("os.version"), "java_version",
									System.getProperty("java.version"));
							String jsonContent = new ObjectMapper().writeValueAsString(systemInfo);
							return new McpSchema.ReadResourceResult(List.of(new McpSchema.TextResourceContents(
									request.uri(), "application/json", jsonContent)));
						}
						catch (Exception e) {
							throw new RuntimeException("Failed to generate system info", e);
						}
					});

			return List.of(resourceSpecification);
		}

		private double evaluateExpression(String expression) {
			// Simple expression evaluator for testing
			return switch (expression) {
				case "2 + 3" -> 5.0;
				case "10 * 2" -> 20.0;
				case "7 + 8" -> 15.0;
				case "5 + 3" -> 8.0;
				default -> 0.0;
			};
		}

	}

	public static class TestMcpClientConfiguration {

		@Bean
		McpSyncClientCustomizer clientCustomizer() {

			return (name, mcpClientSpec) -> {
				// stateless server clients won't receive message notifications or
				// requests from the server
			};
		}

	}

	// Helper methods to start and stop the HTTP server
	private static DisposableServer startHttpServer(ApplicationContext serverContext, int port) {
		WebFluxStatelessServerTransport mcpStatelessServerTransport = serverContext
			.getBean(WebFluxStatelessServerTransport.class);
		HttpHandler httpHandler = RouterFunctions.toHttpHandler(mcpStatelessServerTransport.getRouterFunction());
		ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
		return HttpServer.create().port(port).handle(adapter).bindNow();
	}

	private static void stopHttpServer(DisposableServer server) {
		if (server != null) {
			server.disposeNow();
		}
	}

	// Helper method to get the MCP sync client
	private static McpSyncClient getMcpSyncClient(ApplicationContext clientContext) {
		ObjectProvider<List<McpSyncClient>> mcpClients = clientContext
			.getBeanProvider(ResolvableType.forClassWithGenerics(List.class, McpSyncClient.class));
		return mcpClients.getIfAvailable().get(0);
	}

}
