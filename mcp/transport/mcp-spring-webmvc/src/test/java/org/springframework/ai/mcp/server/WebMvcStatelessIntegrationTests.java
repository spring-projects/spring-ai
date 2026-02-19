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
import java.util.stream.Stream;

import io.modelcontextprotocol.AbstractStatelessIntegrationTests;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.StatelessAsyncSpecification;
import io.modelcontextprotocol.server.McpServer.StatelessSyncSpecification;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.provider.Arguments;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.mcp.client.webflux.transport.WebClientStreamableHttpTransport;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(15)
class WebMvcStatelessIntegrationTests extends AbstractStatelessIntegrationTests {

	private static final String MESSAGE_ENDPOINT = "/mcp/message";

	private WebMvcStatelessServerTransport mcpServerTransport;

	static Stream<Arguments> clientsForTesting() {
		return Stream.of(Arguments.of("httpclient"), Arguments.of("webflux"));
	}

	private TomcatTestUtil.TomcatServer tomcatServer;

	@Override
	protected StatelessAsyncSpecification prepareAsyncServerBuilder() {
		return McpServer.async(this.mcpServerTransport);
	}

	@Override
	protected StatelessSyncSpecification prepareSyncServerBuilder() {
		return McpServer.sync(this.mcpServerTransport);
	}

	@Override
	protected void prepareClients(int port, String mcpEndpoint) {

		clientBuilders.put("httpclient", McpClient
			.sync(HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port).endpoint(mcpEndpoint).build())
			.requestTimeout(Duration.ofHours(10)));

		clientBuilders.put("webflux",
				McpClient
					.sync(WebClientStreamableHttpTransport
						.builder(WebClient.builder().baseUrl("http://127.0.0.1:" + port))
						.endpoint(mcpEndpoint)
						.build())
					.requestTimeout(Duration.ofHours(10)));
	}

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
		this.mcpServerTransport = this.tomcatServer.appContext().getBean(WebMvcStatelessServerTransport.class);

	}

	@AfterEach
	public void after() {
		reactor.netty.http.HttpResources.disposeLoopsAndConnections();
		if (this.mcpServerTransport != null) {
			this.mcpServerTransport.closeGracefully().block();
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

	@Configuration
	@EnableWebMvc
	static class TestConfig {

		@Bean
		public WebMvcStatelessServerTransport webMvcStatelessServerTransport() {

			return WebMvcStatelessServerTransport.builder().messageEndpoint(MESSAGE_ENDPOINT).build();

		}

		@Bean
		public RouterFunction<ServerResponse> routerFunction(WebMvcStatelessServerTransport statelessServerTransport) {
			return statelessServerTransport.getRouterFunction();
		}

	}

}
