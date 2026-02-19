/*
 * Copyright 2026-2026 the original author or authors.
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

package org.springframework.ai.mcp.server;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import io.modelcontextprotocol.AbstractMcpClientServerIntegrationTests;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.AsyncSpecification;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.provider.Arguments;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.mcp.client.webflux.transport.WebClientStreamableHttpTransport;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(15)
class WebMvcStreamableIntegrationTests extends AbstractMcpClientServerIntegrationTests {

	private static final String MESSAGE_ENDPOINT = "/mcp/message";

	private WebMvcStreamableServerTransportProvider mcpServerTransportProvider;

	static McpTransportContextExtractor<ServerRequest> TEST_CONTEXT_EXTRACTOR = r -> McpTransportContext
		.create(Map.of("important", "value"));

	static Stream<Arguments> clientsForTesting() {
		return Stream.of(Arguments.of("httpclient"), Arguments.of("webflux"));
	}

	private TomcatTestUtil.TomcatServer tomcatServer;

	@BeforeEach
	public void before() {

		this.tomcatServer = TomcatTestUtil.createTomcatServer("", 0, TestConfig.class);

		try {
			this.tomcatServer.tomcat().start();
			assertThat(this.tomcatServer.tomcat().getServer().getState()).isEqualTo(LifecycleState.STARTED);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to start Tomcat", e);
		}

		int port = this.tomcatServer.tomcat().getConnector().getLocalPort();

		this.clientBuilders
			.put("httpclient",
					McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
						.endpoint(MESSAGE_ENDPOINT)
						.build()).initializationTimeout(Duration.ofHours(10)).requestTimeout(Duration.ofHours(10)));

		this.clientBuilders.put("webflux",
				McpClient.sync(WebClientStreamableHttpTransport
					.builder(WebClient.builder().baseUrl("http://localhost:" + port))
					.endpoint(MESSAGE_ENDPOINT)
					.build()));

		// Get the transport from Spring context
		this.mcpServerTransportProvider = this.tomcatServer.appContext()
			.getBean(WebMvcStreamableServerTransportProvider.class);

	}

	@Override
	protected AsyncSpecification<?> prepareAsyncServerBuilder() {
		return McpServer.async(this.mcpServerTransportProvider);
	}

	@Override
	protected SyncSpecification<?> prepareSyncServerBuilder() {
		return McpServer.sync(this.mcpServerTransportProvider);
	}

	@AfterEach
	public void after() {
		reactor.netty.http.HttpResources.disposeLoopsAndConnections();
		if (this.mcpServerTransportProvider != null) {
			this.mcpServerTransportProvider.closeGracefully().block();
		}
		Schedulers.shutdownNow();
		if (this.tomcatServer.appContext() != null) {
			this.tomcatServer.appContext().close();
		}
		if (this.tomcatServer.tomcat() != null) {
			try {
				this.tomcatServer.tomcat().stop();
				this.tomcatServer.tomcat().destroy();
			}
			catch (LifecycleException e) {
				throw new RuntimeException("Failed to stop Tomcat", e);
			}
		}
	}

	@Override
	protected void prepareClients(int port, String mcpEndpoint) {

		this.clientBuilders.put("httpclient", McpClient
			.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).endpoint(mcpEndpoint).build())
			.requestTimeout(Duration.ofHours(10)));

		this.clientBuilders.put("webflux",
				McpClient
					.sync(WebClientStreamableHttpTransport
						.builder(WebClient.builder().baseUrl("http://localhost:" + port))
						.endpoint(mcpEndpoint)
						.build())
					.requestTimeout(Duration.ofHours(10)));
	}

	@Configuration
	@EnableWebMvc
	static class TestConfig {

		@Bean
		public WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider() {
			return WebMvcStreamableServerTransportProvider.builder()
				.contextExtractor(TEST_CONTEXT_EXTRACTOR)
				.mcpEndpoint(MESSAGE_ENDPOINT)
				.build();
		}

		@Bean
		public RouterFunction<ServerResponse> routerFunction(
				WebMvcStreamableServerTransportProvider transportProvider) {
			return transportProvider.getRouterFunction();
		}

	}

}
