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
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.AsyncSpecification;
import io.modelcontextprotocol.server.McpServer.SingleSessionSyncSpecification;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.provider.Arguments;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.mcp.client.webflux.transport.WebFluxSseClientTransport;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcSseServerTransportProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(15)
class WebMvcSseIntegrationTests extends AbstractMcpClientServerIntegrationTests {

	private static final String MESSAGE_ENDPOINT = "/mcp/message";

	private WebMvcSseServerTransportProvider mcpServerTransportProvider;

	static McpTransportContextExtractor<ServerRequest> TEST_CONTEXT_EXTRACTOR = r -> McpTransportContext
		.create(Map.of("important", "value"));

	static Stream<Arguments> clientsForTesting() {
		return Stream.of(Arguments.of("httpclient"), Arguments.of("webflux"));
	}

	@Override
	protected void prepareClients(int port, String mcpEndpoint) {

		clientBuilders.put("httpclient",
				McpClient.sync(HttpClientSseClientTransport.builder("http://localhost:" + port).build())
					.requestTimeout(Duration.ofHours(10)));

		clientBuilders.put("webflux", McpClient
			.sync(WebFluxSseClientTransport.builder(WebClient.builder().baseUrl("http://localhost:" + port)).build())
			.requestTimeout(Duration.ofHours(10)));
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
		prepareClients(port, MESSAGE_ENDPOINT);

		// Get the transport from Spring context
		this.mcpServerTransportProvider = this.tomcatServer.appContext()
			.getBean(WebMvcSseServerTransportProvider.class);

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
	protected AsyncSpecification<?> prepareAsyncServerBuilder() {
		return McpServer.async(this.mcpServerTransportProvider);
	}

	@Override
	protected SingleSessionSyncSpecification prepareSyncServerBuilder() {
		return McpServer.sync(this.mcpServerTransportProvider);
	}

	@Configuration
	@EnableWebMvc
	static class TestConfig {

		@Bean
		public WebMvcSseServerTransportProvider webMvcSseServerTransportProvider() {
			return WebMvcSseServerTransportProvider.builder()
				.messageEndpoint(MESSAGE_ENDPOINT)
				.contextExtractor(TEST_CONTEXT_EXTRACTOR)
				.build();
		}

		@Bean
		public RouterFunction<ServerResponse> routerFunction(WebMvcSseServerTransportProvider transportProvider) {
			return transportProvider.getRouterFunction();
		}

	}

}
