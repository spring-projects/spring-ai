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

package org.springframework.ai.mcp.security;

import java.time.Duration;
import java.util.stream.Stream;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.BeforeParameterizedClassInvocation;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.ai.mcp.client.webflux.transport.WebClientStreamableHttpTransport;
import org.springframework.ai.mcp.client.webflux.transport.WebFluxSseClientTransport;
import org.springframework.ai.mcp.server.webflux.transport.WebFluxSseServerTransportProvider;
import org.springframework.ai.mcp.server.webflux.transport.WebFluxStatelessServerTransport;
import org.springframework.ai.mcp.server.webflux.transport.WebFluxStreamableServerTransportProvider;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test the header security validation for all transport types.
 *
 * @author Daniel Garnier-Moiroux
 */
@Disabled
@ParameterizedClass
@MethodSource("transports")
public class WebFluxServerTransportSecurityIT {

	private static final String DISALLOWED_ORIGIN = "https://malicious.example.com";

	private static final String DISALLOWED_HOST = "malicious.example.com:8080";

	@Parameter
	private static Transport transport;

	private static DisposableServer httpServer;

	private static String baseUrl;

	@BeforeParameterizedClassInvocation
	static void createTransportAndStartServer(Transport transport) {
		startServer(transport.routerFunction());
	}

	@AfterAll
	static void afterAll() {
		stopServer();
	}

	private McpSyncClient mcpClient;

	private final TestHeaderExchangeFilterFunction exchangeFilterFunction = new TestHeaderExchangeFilterFunction();

	@BeforeEach
	void setUp() {
		this.mcpClient = transport.createMcpClient(baseUrl, this.exchangeFilterFunction);
	}

	@AfterEach
	void tearDown() {
		this.mcpClient.close();
	}

	@Test
	void originAllowed() {
		this.exchangeFilterFunction.setOriginHeader(baseUrl);
		var result = this.mcpClient.initialize();
		var tools = this.mcpClient.listTools();

		assertThat(result.protocolVersion()).isNotEmpty();
		assertThat(tools.tools()).isEmpty();
	}

	@Test
	void noOrigin() {
		this.exchangeFilterFunction.setOriginHeader(null);
		var result = this.mcpClient.initialize();
		var tools = this.mcpClient.listTools();

		assertThat(result.protocolVersion()).isNotEmpty();
		assertThat(tools.tools()).isEmpty();
	}

	@Test
	void connectOriginNotAllowed() {
		this.exchangeFilterFunction.setOriginHeader(DISALLOWED_ORIGIN);
		assertThatThrownBy(() -> this.mcpClient.initialize());
	}

	@Test
	void messageOriginNotAllowed() {
		this.exchangeFilterFunction.setOriginHeader(baseUrl);
		this.mcpClient.initialize();
		this.exchangeFilterFunction.setOriginHeader(DISALLOWED_ORIGIN);
		assertThatThrownBy(() -> this.mcpClient.listTools());
	}

	@Test
	void hostAllowed() {
		// Host header is set by default by WebClient to the request URI host
		var result = this.mcpClient.initialize();
		var tools = this.mcpClient.listTools();

		assertThat(result.protocolVersion()).isNotEmpty();
		assertThat(tools.tools()).isEmpty();
	}

	@Test
	void connectHostNotAllowed() {
		this.exchangeFilterFunction.setHostHeader(DISALLOWED_HOST);
		assertThatThrownBy(() -> this.mcpClient.initialize());
	}

	@Test
	void messageHostNotAllowed() {
		this.mcpClient.initialize();
		this.exchangeFilterFunction.setHostHeader(DISALLOWED_HOST);
		assertThatThrownBy(() -> this.mcpClient.listTools());
	}

	// ----------------------------------------------------
	// Server management
	// ----------------------------------------------------

	private static void startServer(RouterFunction<?> routerFunction) {
		HttpHandler httpHandler = RouterFunctions.toHttpHandler(routerFunction);
		ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
		httpServer = HttpServer.create().port(0).handle(adapter).bindNow();
		baseUrl = "http://localhost:" + httpServer.port();
	}

	private static void stopServer() {
		if (httpServer != null) {
			httpServer.disposeNow();
		}
	}

	// ----------------------------------------------------
	// Transport servers to test
	// ----------------------------------------------------

	/**
	 * All transport types we want to test. We use a {@link MethodSource} rather than a
	 * {@link org.junit.jupiter.params.provider.ValueSource} to provide a readable name.
	 */
	static Stream<Arguments> transports() {
		//@formatter:off
		return Stream.of(
				Arguments.of(Named.named("SSE", new Sse())),
				Arguments.of(Named.named("Streamable HTTP", new StreamableHttp())),
				Arguments.of(Named.named("Stateless", new Stateless()))
		);
		//@formatter:on
	}

	/**
	 * Represents a server transport we want to test, and how to create a client for the
	 * resulting MCP Server.
	 */
	interface Transport {

		McpSyncClient createMcpClient(String baseUrl, TestHeaderExchangeFilterFunction customizer);

		RouterFunction<?> routerFunction();

	}

	/**
	 * SSE-based transport.
	 */
	static class Sse implements Transport {

		private final WebFluxSseServerTransportProvider transportProvider;

		Sse() {
			this.transportProvider = WebFluxSseServerTransportProvider.builder()
				.messageEndpoint("/mcp/message")
				.securityValidator(DefaultServerTransportSecurityValidator.builder()
					.allowedOrigin("http://localhost:*")
					.allowedHost("localhost:*")
					.build())
				.build();
			McpServer.sync(this.transportProvider)
				.serverInfo("test-server", "1.0.0")
				.capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
				.build();
		}

		@Override
		public McpSyncClient createMcpClient(String baseUrl, TestHeaderExchangeFilterFunction exchangeFilterFunction) {
			var transport = WebFluxSseClientTransport
				.builder(WebClient.builder().baseUrl(baseUrl).filter(exchangeFilterFunction))
				.jsonMapper(McpJsonDefaults.getMapper())
				.build();
			return McpClient.sync(transport).initializationTimeout(Duration.ofMillis(500)).build();
		}

		@Override
		public RouterFunction<?> routerFunction() {
			return this.transportProvider.getRouterFunction();
		}

	}

	static class StreamableHttp implements Transport {

		private final WebFluxStreamableServerTransportProvider transportProvider;

		StreamableHttp() {
			this.transportProvider = WebFluxStreamableServerTransportProvider.builder()
				.securityValidator(DefaultServerTransportSecurityValidator.builder()
					.allowedOrigin("http://localhost:*")
					.allowedHost("localhost:*")
					.build())
				.build();
			McpServer.sync(this.transportProvider)
				.serverInfo("test-server", "1.0.0")
				.capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
				.build();
		}

		@Override
		public McpSyncClient createMcpClient(String baseUrl, TestHeaderExchangeFilterFunction exchangeFilterFunction) {
			var transport = WebClientStreamableHttpTransport
				.builder(WebClient.builder().baseUrl(baseUrl).filter(exchangeFilterFunction))
				.jsonMapper(McpJsonDefaults.getMapper())
				.openConnectionOnStartup(true)
				.build();
			return McpClient.sync(transport).initializationTimeout(Duration.ofMillis(500)).build();
		}

		@Override
		public RouterFunction<?> routerFunction() {
			return this.transportProvider.getRouterFunction();
		}

	}

	static class Stateless implements Transport {

		private final WebFluxStatelessServerTransport transportProvider;

		Stateless() {
			this.transportProvider = WebFluxStatelessServerTransport.builder()
				.securityValidator(DefaultServerTransportSecurityValidator.builder()
					.allowedOrigin("http://localhost:*")
					.allowedHost("localhost:*")
					.build())
				.build();
			McpServer.sync(this.transportProvider)
				.serverInfo("test-server", "1.0.0")
				.capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
				.build();
		}

		@Override
		public McpSyncClient createMcpClient(String baseUrl, TestHeaderExchangeFilterFunction exchangeFilterFunction) {
			var transport = WebClientStreamableHttpTransport
				.builder(WebClient.builder().baseUrl(baseUrl).filter(exchangeFilterFunction))
				.jsonMapper(McpJsonDefaults.getMapper())
				.openConnectionOnStartup(true)
				.build();
			return McpClient.sync(transport).initializationTimeout(Duration.ofMillis(500)).build();
		}

		@Override
		public RouterFunction<?> routerFunction() {
			return this.transportProvider.getRouterFunction();
		}

	}

	static class TestHeaderExchangeFilterFunction implements ExchangeFilterFunction {

		private String origin = null;

		private String host = null;

		public void setOriginHeader(String origin) {
			this.origin = origin;
		}

		public void setHostHeader(String host) {
			this.host = host;
		}

		@Override
		public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
			var builder = ClientRequest.from(request);
			if (this.origin != null) {
				builder.header("Origin", this.origin);
			}
			if (this.host != null) {
				builder.header("Host", this.host);
			}
			return next.exchange(builder.build());
		}

	}

}
