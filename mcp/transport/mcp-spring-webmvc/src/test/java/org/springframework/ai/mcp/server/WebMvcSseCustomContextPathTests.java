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

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.server.webmvc.transport.WebMvcSseServerTransportProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

class WebMvcSseCustomContextPathTests {

	private static final String CUSTOM_CONTEXT_PATH = "/app/1";

	private static final String MESSAGE_ENDPOINT = "/mcp/message";

	private WebMvcSseServerTransportProvider mcpServerTransportProvider;

	McpClient.SyncSpec clientBuilder;

	private TomcatTestUtil.TomcatServer tomcatServer;

	@BeforeEach
	public void before() {

		this.tomcatServer = TomcatTestUtil.createTomcatServer(CUSTOM_CONTEXT_PATH, 0, TestConfig.class);

		try {
			this.tomcatServer.tomcat().start();
			assertThat(this.tomcatServer.tomcat().getServer().getState()).isEqualTo(LifecycleState.STARTED);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to start Tomcat", e);
		}

		int port = this.tomcatServer.tomcat().getConnector().getLocalPort();
		var clientTransport = HttpClientSseClientTransport.builder("http://127.0.0.1:" + port)
			.sseEndpoint(CUSTOM_CONTEXT_PATH + WebMvcSseServerTransportProvider.DEFAULT_SSE_ENDPOINT)
			.build();

		this.clientBuilder = McpClient.sync(clientTransport);

		this.mcpServerTransportProvider = this.tomcatServer.appContext()
			.getBean(WebMvcSseServerTransportProvider.class);
	}

	@AfterEach
	public void after() {
		if (this.mcpServerTransportProvider != null) {
			this.mcpServerTransportProvider.closeGracefully().block();
		}
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

	@Test
	void testCustomContextPath() {
		McpServer.async(this.mcpServerTransportProvider).serverInfo("test-server", "1.0.0").build();
		var client = this.clientBuilder.clientInfo(new McpSchema.Implementation("Sample " + "client", "0.0.0")).build();
		assertThat(client.initialize()).isNotNull();
	}

	@Configuration
	@EnableWebMvc
	static class TestConfig {

		@Bean
		public WebMvcSseServerTransportProvider webMvcSseServerTransportProvider() {

			return WebMvcSseServerTransportProvider.builder()
				.baseUrl(CUSTOM_CONTEXT_PATH)
				.messageEndpoint(MESSAGE_ENDPOINT)
				.sseEndpoint(WebMvcSseServerTransportProvider.DEFAULT_SSE_ENDPOINT)
				.build();
			// return new WebMvcSseServerTransportProvider(new ObjectMapper(),
			// CUSTOM_CONTEXT_PATH, MESSAGE_ENDPOINT,
			// WebMvcSseServerTransportProvider.DEFAULT_SSE_ENDPOINT);
		}

		@Bean
		public RouterFunction<ServerResponse> routerFunction(WebMvcSseServerTransportProvider transportProvider) {
			return transportProvider.getRouterFunction();
		}

	}

}
