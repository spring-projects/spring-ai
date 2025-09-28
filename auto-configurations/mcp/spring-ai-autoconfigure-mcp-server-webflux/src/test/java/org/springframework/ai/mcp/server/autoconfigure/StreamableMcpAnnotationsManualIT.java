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

package org.springframework.ai.mcp.server.autoconfigure;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.WebFluxStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;
import io.modelcontextprotocol.spec.McpSchema.LoggingMessageNotification;
import io.modelcontextprotocol.spec.McpSchema.ModelHint;
import io.modelcontextprotocol.spec.McpSchema.ModelPreferences;
import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.PromptReference;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpComplete;
import org.springaicommunity.mcp.annotation.McpElicitation;
import org.springaicommunity.mcp.annotation.McpLogging;
import org.springaicommunity.mcp.annotation.McpMeta;
import org.springaicommunity.mcp.annotation.McpProgress;
import org.springaicommunity.mcp.annotation.McpProgressToken;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpSampling;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.method.elicitation.SyncElicitationSpecification;
import org.springaicommunity.mcp.method.logging.SyncLoggingSpecification;
import org.springaicommunity.mcp.method.progress.SyncProgressSpecification;
import org.springaicommunity.mcp.method.sampling.SyncSamplingSpecification;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration;
import org.springframework.ai.mcp.client.webflux.autoconfigure.StreamableHttpWebFluxTransportAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.ToolCallbackConverterAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.map;

public class StreamableMcpAnnotationsManualIT {

	private final ApplicationContextRunner serverContextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.server.protocol=STREAMABLE")
		.withConfiguration(AutoConfigurations.of(McpServerAutoConfiguration.class,
				ToolCallbackConverterAutoConfiguration.class, McpServerStreamableHttpWebFluxAutoConfiguration.class));

	private final ApplicationContextRunner clientApplicationContext = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpToolCallbackAutoConfiguration.class,
				McpClientAutoConfiguration.class, StreamableHttpWebFluxTransportAutoConfiguration.class));

	@Test
	void clientServerCapabilities() {

		int serverPort = TestSocketUtils.findAvailableTcpPort();

		this.serverContextRunner.withUserConfiguration(TestMcpServerConfiguration.class)
			.withPropertyValues(// @formatter:off
				"spring.ai.mcp.server.name=test-mcp-server",
				"spring.ai.mcp.server.version=1.0.0",
				"spring.ai.mcp.server.streamable-http.keep-alive-interval=1s",
				// "spring.ai.mcp.server.requestTimeout=1m",
				"spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp") // @formatter:on
			.run(serverContext -> {
				// Verify all required beans are present
				assertThat(serverContext).hasSingleBean(WebFluxStreamableServerTransportProvider.class);
				assertThat(serverContext).hasSingleBean(RouterFunction.class);
				assertThat(serverContext).hasSingleBean(McpSyncServer.class);

				// Verify server properties are configured correctly
				McpServerProperties properties = serverContext.getBean(McpServerProperties.class);
				assertThat(properties.getName()).isEqualTo("test-mcp-server");
				assertThat(properties.getVersion()).isEqualTo("1.0.0");

				McpServerStreamableHttpProperties streamableHttpProperties = serverContext
					.getBean(McpServerStreamableHttpProperties.class);
				assertThat(streamableHttpProperties.getMcpEndpoint()).isEqualTo("/mcp");
				assertThat(streamableHttpProperties.getKeepAliveInterval()).isEqualTo(Duration.ofSeconds(1));

				var httpServer = startHttpServer(serverContext, serverPort);

				this.clientApplicationContext.withUserConfiguration(TestMcpClientConfiguration.class)
					.withPropertyValues(// @formatter:off
						"spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:" + serverPort,
						// "spring.ai.mcp.client.request-timeout=20m",
						"spring.ai.mcp.client.initialized=false") // @formatter:on
					.run(clientContext -> {
						McpSyncClient mcpClient = getMcpSyncClient(clientContext);
						assertThat(mcpClient).isNotNull();
						var initResult = mcpClient.initialize();
						assertThat(initResult).isNotNull();

						// TOOLS / SAMPLING / ELICITATION

						// tool list
						assertThat(mcpClient.listTools().tools()).hasSize(2);

						// Call a tool that sends progress notifications
						CallToolRequest toolRequest = CallToolRequest.builder()
							.name("tool1")
							.arguments(Map.of())
							.progressToken("test-progress-token")
							.build();

						CallToolResult response = mcpClient.callTool(toolRequest);

						assertThat(response).isNotNull();
						assertThat(response.isError()).isFalse();
						String responseText = ((TextContent) response.content().get(0)).text();
						assertThat(responseText).contains("CALL RESPONSE");
						assertThat(responseText).contains("Response Test Sampling Message with model hint OpenAi");
						assertThat(responseText).contains("ElicitResult");

						// PROGRESS
						TestMcpClientConfiguration.TestContext testContext = clientContext
							.getBean(TestMcpClientConfiguration.TestContext.class);
						assertThat(testContext.progressLatch.await(5, TimeUnit.SECONDS))
							.as("Should receive progress notifications in reasonable time")
							.isTrue();
						assertThat(testContext.progressNotifications).hasSize(3);

						Map<String, McpSchema.ProgressNotification> notificationMap = testContext.progressNotifications
							.stream()
							.collect(Collectors.toMap(n -> n.message(), n -> n));

						// First notification should be 0.0/1.0 progress
						assertThat(notificationMap.get("tool call start").progressToken())
							.isEqualTo("test-progress-token");
						assertThat(notificationMap.get("tool call start").progress()).isEqualTo(0.0);
						assertThat(notificationMap.get("tool call start").total()).isEqualTo(1.0);
						assertThat(notificationMap.get("tool call start").message()).isEqualTo("tool call start");

						// Second notification should be 1.0/1.0 progress
						assertThat(notificationMap.get("elicitation completed").progressToken())
							.isEqualTo("test-progress-token");
						assertThat(notificationMap.get("elicitation completed").progress()).isEqualTo(0.5);
						assertThat(notificationMap.get("elicitation completed").total()).isEqualTo(1.0);
						assertThat(notificationMap.get("elicitation completed").message())
							.isEqualTo("elicitation completed");

						// Third notification should be 0.5/1.0 progress
						assertThat(notificationMap.get("sampling completed").progressToken())
							.isEqualTo("test-progress-token");
						assertThat(notificationMap.get("sampling completed").progress()).isEqualTo(1.0);
						assertThat(notificationMap.get("sampling completed").total()).isEqualTo(1.0);
						assertThat(notificationMap.get("sampling completed").message()).isEqualTo("sampling completed");

						// TOOL STRUCTURED OUTPUT
						// Call tool with valid structured output
						CallToolResult calculatorToolResponse = mcpClient.callTool(new McpSchema.CallToolRequest(
								"calculator", Map.of("expression", "2 + 3"), Map.of("meta1", "value1")));

						assertThat(calculatorToolResponse).isNotNull();
						assertThat(calculatorToolResponse.isError()).isFalse();

						assertThat(calculatorToolResponse.structuredContent()).isNotNull();

						assertThat(calculatorToolResponse.structuredContent())
							.asInstanceOf(map(String.class, Object.class))
							.containsEntry("result", 5.0)
							.containsEntry("operation", "2 + 3")
							.containsEntry("timestamp", "2024-01-01T10:00:00Z");

						JsonAssertions.assertThatJson(calculatorToolResponse.structuredContent())
							.when(Option.IGNORING_ARRAY_ORDER)
							.when(Option.IGNORING_EXTRA_ARRAY_ITEMS)
							.isObject()
							.isEqualTo(JsonAssertions.json("""
									{"result":5.0,"operation":"2 + 3","timestamp":"2024-01-01T10:00:00Z"}"""));

						assertThat(calculatorToolResponse.meta()).containsEntry("meta1Response", "value1");

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

						// logging message
						var logMessage = testContext.loggingNotificationRef.get();
						assertThat(logMessage).isNotNull();
						assertThat(logMessage.level()).isEqualTo(LoggingLevel.INFO);
						assertThat(logMessage.logger()).isEqualTo("test-logger");
						assertThat(logMessage.data()).contains("User prompt");

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

	// Helper method to get the MCP sync client
	private static McpSyncClient getMcpSyncClient(ApplicationContext clientContext) {
		ObjectProvider<List<McpSyncClient>> mcpClients = clientContext
			.getBeanProvider(ResolvableType.forClassWithGenerics(List.class, McpSyncClient.class));
		return mcpClients.getIfAvailable().get(0);
	}

	public static class TestMcpServerConfiguration {

		@Bean
		public McpServerHandlers serverSideSpecProviders() {
			return new McpServerHandlers();
		}

		@Bean
		public List<McpServerFeatures.SyncToolSpecification> myTools(McpServerHandlers serverSideSpecProviders) {
			return SyncMcpAnnotationProviders.toolSpecifications(List.of(serverSideSpecProviders));
		}

		@Bean
		public List<McpServerFeatures.SyncResourceSpecification> myResources(
				McpServerHandlers serverSideSpecProviders) {
			return SyncMcpAnnotationProviders.resourceSpecifications(List.of(serverSideSpecProviders));
		}

		@Bean
		public List<McpServerFeatures.SyncPromptSpecification> myPrompts(McpServerHandlers serverSideSpecProviders) {
			return SyncMcpAnnotationProviders.promptSpecifications(List.of(serverSideSpecProviders));
		}

		@Bean
		public List<McpServerFeatures.SyncCompletionSpecification> myCompletions(
				McpServerHandlers serverSideSpecProviders) {
			return SyncMcpAnnotationProviders.completeSpecifications(List.of(serverSideSpecProviders));
		}

		public static class McpServerHandlers {

			@McpTool(description = "Test tool", name = "tool1")
			public String toolWithSamplingAndElicitation(McpSyncServerExchange exchange, @McpToolParam String input,
					@McpProgressToken String progressToken) {

				exchange.loggingNotification(LoggingMessageNotification.builder().data("Tool1 Started!").build());

				exchange.progressNotification(new ProgressNotification(progressToken, 0.0, 1.0, "tool call start"));

				exchange.ping(); // call client ping

				// call elicitation
				var elicitationRequest = McpSchema.ElicitRequest.builder()
					.message("Test message")
					.requestedSchema(
							Map.of("type", "object", "properties", Map.of("message", Map.of("type", "string"))))
					.build();

				ElicitResult elicitationResult = exchange.createElicitation(elicitationRequest);

				exchange
					.progressNotification(new ProgressNotification(progressToken, 0.50, 1.0, "elicitation completed"));

				// call sampling
				var createMessageRequest = McpSchema.CreateMessageRequest.builder()
					.messages(List.of(new McpSchema.SamplingMessage(McpSchema.Role.USER,
							new McpSchema.TextContent("Test Sampling Message"))))
					.modelPreferences(ModelPreferences.builder()
						.hints(List.of(ModelHint.of("OpenAi"), ModelHint.of("Ollama")))
						.costPriority(1.0)
						.speedPriority(1.0)
						.intelligencePriority(1.0)
						.build())
					.build();

				CreateMessageResult samplingResponse = exchange.createMessage(createMessageRequest);

				exchange.progressNotification(new ProgressNotification(progressToken, 1.0, 1.0, "sampling completed"));

				exchange.loggingNotification(LoggingMessageNotification.builder().data("Tool1 Done!").build());

				return "CALL RESPONSE: " + samplingResponse.toString() + ", " + elicitationResult.toString();
			}

			@McpTool(name = "calculator", description = "Performs mathematical calculations")
			public CallToolResult calculator(@McpToolParam String expression, McpMeta meta) {
				double result = evaluateExpression(expression);
				return CallToolResult.builder()
					.structuredContent(
							Map.of("result", result, "operation", expression, "timestamp", "2024-01-01T10:00:00Z"))
					.meta(Map.of("meta1Response", meta.get("meta1")))
					.build();
			}

			private static double evaluateExpression(String expression) {
				// Simple expression evaluator for testing
				return switch (expression) {
					case "2 + 3" -> 5.0;
					case "10 * 2" -> 20.0;
					case "7 + 8" -> 15.0;
					case "5 + 3" -> 8.0;
					default -> 0.0;
				};
			}

			@McpResource(name = "Test Resource", uri = "file://resource", mimeType = "text/plain",
					description = "Test resource description")
			public McpSchema.ReadResourceResult testResource(McpSchema.ReadResourceRequest request) {
				try {
					var systemInfo = Map.of("os", System.getProperty("os.name"), "os_version",
							System.getProperty("os.version"), "java_version", System.getProperty("java.version"));
					String jsonContent = new ObjectMapper().writeValueAsString(systemInfo);
					return new McpSchema.ReadResourceResult(List
						.of(new McpSchema.TextResourceContents(request.uri(), "application/json", jsonContent)));
				}
				catch (Exception e) {
					throw new RuntimeException("Failed to generate system info", e);
				}
			}

			@McpPrompt(name = "code-completion", description = "this is code review prompt")
			public McpSchema.GetPromptResult codeCompletionPrompt(McpSyncServerExchange exchange,
					@McpArg(name = "language", required = false) String languageArgument) {

				if (languageArgument == null) {
					languageArgument = "java";
				}

				exchange.loggingNotification(LoggingMessageNotification.builder()
					.logger("test-logger")
					.data("User prompt: Hello " + languageArgument + "! How can I assist you today?")
					.build());

				var userMessage = new PromptMessage(Role.USER,
						new TextContent("Hello " + languageArgument + "! How can I assist you today?"));

				return new GetPromptResult("A personalized greeting message", List.of(userMessage));
			}

			@McpComplete(prompt = "code-completion") // the code-completion is a reference
														// to the prompt code completion
			public McpSchema.CompleteResult codeCompletion() {
				var expectedValues = List.of("python", "pytorch", "pyside");
				return new McpSchema.CompleteResult(new CompleteResult.CompleteCompletion(expectedValues, 10, // total
						true // hasMore
				));
			}

		}

	}

	public static class TestMcpClientConfiguration {

		@Bean
		public TestContext testContext() {
			return new TestContext();
		}

		@Bean
		public McpClientHandlers mcpClientHandlers(TestContext testContext) {
			return new McpClientHandlers(testContext);
		}

		@Bean
		List<SyncLoggingSpecification> loggingSpecs(McpClientHandlers clientMcpHandlers) {
			return SyncMcpAnnotationProviders.loggingSpecifications(List.of(clientMcpHandlers));
		}

		@Bean
		List<SyncSamplingSpecification> samplingSpecs(McpClientHandlers clientMcpHandlers) {
			return SyncMcpAnnotationProviders.samplingSpecifications(List.of(clientMcpHandlers));
		}

		@Bean
		List<SyncElicitationSpecification> elicitationSpecs(McpClientHandlers clientMcpHandlers) {
			return SyncMcpAnnotationProviders.elicitationSpecifications(List.of(clientMcpHandlers));
		}

		@Bean
		List<SyncProgressSpecification> progressSpecs(McpClientHandlers clientMcpHandlers) {
			return SyncMcpAnnotationProviders.progressSpecifications(List.of(clientMcpHandlers));
		}

		public static class TestContext {

			final AtomicReference<LoggingMessageNotification> loggingNotificationRef = new AtomicReference<>();

			final CountDownLatch progressLatch = new CountDownLatch(3);

			final List<McpSchema.ProgressNotification> progressNotifications = new CopyOnWriteArrayList<>();

		}

		public static class McpClientHandlers {

			private static final Logger logger = LoggerFactory.getLogger(McpClientHandlers.class);

			private TestContext testContext;

			public McpClientHandlers(TestContext testContext) {
				this.testContext = testContext;
			}

			@McpProgress(clients = "server1")
			public void progressHandler(ProgressNotification progressNotification) {
				logger.info("MCP PROGRESS: [{}] progress: {} total: {} message: {}",
						progressNotification.progressToken(), progressNotification.progress(),
						progressNotification.total(), progressNotification.message());
				this.testContext.progressNotifications.add(progressNotification);
				this.testContext.progressLatch.countDown();
			}

			@McpLogging(clients = "server1")
			public void loggingHandler(LoggingMessageNotification loggingMessage) {
				this.testContext.loggingNotificationRef.set(loggingMessage);
				logger.info("MCP LOGGING: [{}] {}", loggingMessage.level(), loggingMessage.data());
			}

			@McpSampling(clients = "server1")
			public CreateMessageResult samplingHandler(CreateMessageRequest llmRequest) {
				logger.info("MCP SAMPLING: {}", llmRequest);

				String userPrompt = ((McpSchema.TextContent) llmRequest.messages().get(0).content()).text();
				String modelHint = llmRequest.modelPreferences().hints().get(0).name();

				return CreateMessageResult.builder()
					.content(new McpSchema.TextContent("Response " + userPrompt + " with model hint " + modelHint))
					.build();
			}

			@McpElicitation(clients = "server1")
			public ElicitResult elicitationHandler(McpSchema.ElicitRequest request) {
				logger.info("MCP ELICITATION: {}", request);
				return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("message", request.message()));
			}

		}

	}

}
