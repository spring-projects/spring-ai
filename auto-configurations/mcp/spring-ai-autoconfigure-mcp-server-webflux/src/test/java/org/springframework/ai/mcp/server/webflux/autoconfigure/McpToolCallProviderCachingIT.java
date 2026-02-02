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
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Stream;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebFluxStreamableServerTransportProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.annotations.McpClientAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.client.webflux.autoconfigure.StreamableHttpWebFluxTransportAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerObjectMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.ToolCallbackConverterAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.reactive.function.server.RouterFunctions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".*")
public class McpToolCallProviderCachingIT {

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
		Class<?>[] dependencies = { ToolCallingAutoConfiguration.class, RestClientAutoConfiguration.class,
				WebClientAutoConfiguration.class };
		Class<?>[] all = Stream.concat(Arrays.stream(dependencies), Arrays.stream(additional)).toArray(Class<?>[]::new);
		return AutoConfigurations.of(all);
	}

	@Test
	void clientToolCallbacksUpdateWhenServerToolsChangeAsync() {

		int serverPort = TestSocketUtils.findAvailableTcpPort();

		this.serverContextRunner.withUserConfiguration(TestMcpServerConfiguration.class)
			.withPropertyValues(// @formatter:off
						"spring.ai.mcp.server.name=test-mcp-server",
						"spring.ai.mcp.server.version=1.0.0",
						"spring.ai.mcp.server.streamable-http.keep-alive-interval=1s",
						"spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp") // @formatter:on
			.run(serverContext -> {

				var httpServer = startHttpServer(serverContext, serverPort);

				this.clientApplicationContext
					.withPropertyValues(// @formatter:off
									"spring.ai.mcp.client.streamable-http.connections.server1.url=http://localhost:" + serverPort,
									"spring.ai.mcp.client.initialized=false") // @formatter:on
					.run(clientContext -> {

						ToolCallbackProvider tcp = clientContext.getBean(ToolCallbackProvider.class);

						assertThat(tcp.getToolCallbacks()).hasSize(1);

						McpSyncServer mcpSyncServer = serverContext.getBean(McpSyncServer.class);

						var toolSpec = McpToolUtils
							.toSyncToolSpecification(FunctionToolCallback.builder("currentTime", new TimeService())
								.description("Get the current time by location")
								.inputType(TimeRequest.class)
								.build(), null);

						mcpSyncServer.addTool(toolSpec);

						// Wait for the tool to be added asynchronously
						await().atMost(Duration.ofSeconds(5))
							.pollInterval(Duration.ofMillis(100))
							.untilAsserted(() -> assertThat(tcp.getToolCallbacks()).hasSize(2));

						mcpSyncServer.removeTool("weather");

						// Wait for the tool to be removed asynchronously
						await().atMost(Duration.ofSeconds(5))
							.pollInterval(Duration.ofMillis(100))
							.untilAsserted(() -> assertThat(tcp.getToolCallbacks()).hasSize(1));
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
				return "Weather is 22C with rain ";
			}

		}

	}

	public class TimeService implements Function<TimeRequest, String> {

		public String apply(TimeRequest request) {
			return "The time in " + request.location() + " is 12:00 PM.";
		}

	}

	public record TimeRequest(String location) {
	}

}
