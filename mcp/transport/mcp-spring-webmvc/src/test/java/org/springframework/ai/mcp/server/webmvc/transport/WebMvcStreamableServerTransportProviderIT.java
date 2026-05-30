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

package org.springframework.ai.mcp.server.webmvc.transport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.server.TomcatTestUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link WebMvcStreamableServerTransportProvider} covering
 * edge-case request validation.
 *
 * @author Gorre Surya
 */
class WebMvcStreamableServerTransportProviderIT {

	private static final String MCP_ENDPOINT = "/mcp/message";

	private TomcatTestUtil.TomcatServer tomcatServer;

	private int port;

	private HttpClient httpClient;

	@BeforeEach
	void before() {
		this.tomcatServer = TomcatTestUtil.createTomcatServer("", 0, TestConfig.class);
		try {
			this.tomcatServer.tomcat().start();
			assertThat(this.tomcatServer.tomcat().getServer().getState()).isEqualTo(LifecycleState.STARTED);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to start Tomcat", e);
		}
		this.port = this.tomcatServer.tomcat().getConnector().getLocalPort();
		this.httpClient = HttpClient.newHttpClient();
	}

	@AfterEach
	void after() {
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
	void malformedInitializeParamsReturns400WithInvalidParams() throws Exception {
		String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:" + this.port + MCP_ENDPOINT))
			.header("Content-Type", "application/json")
			.header("Accept", "application/json, text/event-stream")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();

		HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.body()).contains("protocolVersion");
	}

	@Configuration
	@EnableWebMvc
	static class TestConfig {

		@Bean
		public WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider() {
			return WebMvcStreamableServerTransportProvider.builder().mcpEndpoint(MCP_ENDPOINT).build();
		}

		@Bean
		public RouterFunction<ServerResponse> routerFunction(
				WebMvcStreamableServerTransportProvider transportProvider) {
			return transportProvider.getRouterFunction();
		}

	}

}
