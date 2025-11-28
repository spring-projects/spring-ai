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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebFluxStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpLogging;
import org.springaicommunity.mcp.annotation.McpProgress;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.annotations.McpClientAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.client.webflux.autoconfigure.StreamableHttpWebFluxTransportAutoConfiguration;
import org.springframework.ai.mcp.server.autoconfigure.capabilities.McpHandlerConfiguration;
import org.springframework.ai.mcp.server.autoconfigure.capabilities.McpHandlerService;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerObjectMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.ToolCallbackConverterAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Daniel Garnier-Moiroux
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".*")
public class StreamableMcpAnnotationsWithLLMIT {

	private final ApplicationContextRunner serverContextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.server.protocol=STREAMABLE")
		.withConfiguration(AutoConfigurations.of(McpServerAutoConfiguration.class,
				McpServerObjectMapperAutoConfiguration.class, ToolCallbackConverterAutoConfiguration.class,
				McpServerStreamableHttpWebFluxAutoConfiguration.class,
				McpServerAnnotationScannerAutoConfiguration.class,
				McpServerSpecificationFactoryAutoConfiguration.class));

	private final ApplicationContextRunner clientApplicationContext = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.anthropic.apiKey=" + System.getenv("ANTHROPIC_API_KEY"))
		.withConfiguration(anthropicAutoConfig(McpToolCallbackAutoConfiguration.class, McpClientAutoConfiguration.class,
				StreamableHttpWebFluxTransportAutoConfiguration.class,
				McpClientAnnotationScannerAutoConfiguration.class, AnthropicChatAutoConfiguration.class,
				ChatClientAutoConfiguration.class));

	private static AutoConfigurations anthropicAutoConfig(Class<?>... additional) {
		Class<?>[] dependencies = { SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class,
				RestClientAutoConfiguration.class, WebClientAutoConfiguration.class };
		Class<?>[] all = Stream.concat(Arrays.stream(dependencies), Arrays.stream(additional)).toArray(Class<?>[]::new);
		return AutoConfigurations.of(all);
	}

	private static AtomicInteger toolCounter = new AtomicInteger(0);

	@Test
	void clientServerCapabilities() {

		int serverPort = TestSocketUtils.findAvailableTcpPort();

		this.serverContextRunner.withUserConfiguration(TestMcpServerConfiguration.class)
			.withPropertyValues(// @formatter:off
						"spring.ai.mcp.server.name=test-mcp-server",
						"spring.ai.mcp.server.version=1.0.0",
						"spring.ai.mcp.server.streamable-http.keep-alive-interval=1s",
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
					.withUserConfiguration(TestMcpClientHandlers.class)
					.withPropertyValues(// @formatter:off
									"spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:" + serverPort,
									"spring.ai.mcp.client.initialized=false") // @formatter:on
					.run(clientContext -> {

						ChatClient.Builder builder = clientContext.getBean(ChatClient.Builder.class);

						ToolCallbackProvider tcp = clientContext.getBean(ToolCallbackProvider.class);

						assertThat(builder).isNotNull();

						ChatClient chatClient = builder.defaultToolCallbacks(tcp)
							.defaultToolContext(Map.of("progressToken", "test-progress-token"))
							.build();

						String cResponse = chatClient.prompt()
							.user("What is the weather in Amsterdam right now")
							.call()
							.content();

						assertThat(cResponse).isNotEmpty();
						assertThat(cResponse).contains("22");

						assertThat(toolCounter.get()).isEqualTo(1);

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

	public static class TestMcpServerConfiguration {

		@Bean
		public McpServerHandlers serverSideSpecProviders() {
			return new McpServerHandlers();
		}

		public static class McpServerHandlers {

			@McpTool(description = "Provides weather information by city name")
			public String weather(McpSyncRequestContext ctx, @McpToolParam String cityName) {

				toolCounter.incrementAndGet();

				ctx.info("Weather called!");

				ctx.progress(p -> p.progress(0.0).total(1.0).message("tool call start"));

				ctx.ping(); // call client ping

				// call elicitation
				var elicitationResult = ctx.elicit(e -> e.message("Test message"),
						McpHandlerConfiguration.ElicitInput.class);

				ctx.progress(p -> p.progress(0.50).total(1.0).message("elicitation completed"));

				// call sampling
				CreateMessageResult samplingResponse = ctx.sample(s -> s.message("Test Sampling Message")
					.modelPreferences(pref -> pref.modelHints("OpenAi", "Ollama")
						.costPriority(1.0)
						.speedPriority(1.0)
						.intelligencePriority(1.0)));

				ctx.progress(p -> p.progress(1.0).total(1.0).message("sampling completed"));

				ctx.info("Tool1 Done!");

				return "Weahter is 22C with rain " + samplingResponse.toString() + ", " + elicitationResult.toString();
			}

		}

	}

	public static class TestMcpClientConfiguration {

		@Bean
		public TestContext testContext() {
			return new TestContext();
		}

		public static class TestContext {

			final AtomicReference<McpSchema.LoggingMessageNotification> loggingNotificationRef = new AtomicReference<>();

			final CountDownLatch progressLatch = new CountDownLatch(3);

			final List<McpSchema.ProgressNotification> progressNotifications = new CopyOnWriteArrayList<>();

		}

	}

	// We also include scanned beans, because those are registered differently.
	@ComponentScan(basePackageClasses = McpHandlerService.class)
	public static class TestMcpClientHandlers {

		private static final Logger logger = LoggerFactory.getLogger(TestMcpClientHandlers.class);

		private TestMcpClientConfiguration.TestContext testContext;

		public TestMcpClientHandlers(TestMcpClientConfiguration.TestContext testContext) {
			this.testContext = testContext;
		}

		@McpProgress(clients = "server1")
		public void progressHandler(McpSchema.ProgressNotification progressNotification) {
			logger.info("MCP PROGRESS: [{}] progress: {} total: {} message: {}", progressNotification.progressToken(),
					progressNotification.progress(), progressNotification.total(), progressNotification.message());
			this.testContext.progressNotifications.add(progressNotification);
			this.testContext.progressLatch.countDown();
		}

		@McpLogging(clients = "server1")
		public void loggingHandler(McpSchema.LoggingMessageNotification loggingMessage) {
			this.testContext.loggingNotificationRef.set(loggingMessage);
			logger.info("MCP LOGGING: [{}] {}", loggingMessage.level(), loggingMessage.data());
		}

	}

}
