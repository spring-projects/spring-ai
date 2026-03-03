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

package org.springframework.ai.mcp.common;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.client.webflux.transport.WebClientStreamableHttpTransport;
import org.springframework.ai.mcp.client.webflux.transport.WebFluxSseClientTransport;
import org.springframework.ai.mcp.server.webflux.transport.WebFluxSseServerTransportProvider;
import org.springframework.ai.mcp.server.webflux.transport.WebFluxStatelessServerTransport;
import org.springframework.ai.mcp.server.webflux.transport.WebFluxStreamableServerTransportProvider;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link McpTransportContext} propagation between MCP clients and
 * async servers using Spring WebFlux infrastructure.
 *
 * <p>
 * This test class validates the end-to-end flow of transport context propagation in MCP
 * communication for asynchronous client and server implementations. It tests various
 * combinations of client types and server transport mechanisms (stateless, streamable,
 * SSE) to ensure proper context handling across different configurations.
 *
 * <h2>Context Propagation Flow</h2>
 * <ol>
 * <li>Client sets a value in its transport context via thread-local Reactor context</li>
 * <li>Client-side context provider extracts the value and adds it as an HTTP header to
 * the request</li>
 * <li>Server-side context extractor reads the header from the incoming request</li>
 * <li>Server handler receives the extracted context and returns the value as the tool
 * call result</li>
 * <li>Test verifies the round-trip context propagation was successful</li>
 * </ol>
 *
 * @author Daniel Garnier-Moiroux
 * @author Christian Tzolov
 */
@Timeout(15)
public class AsyncServerMcpTransportContextIT {

	private static final String HEADER_NAME = "x-test";

	// Async client context provider
	ExchangeFilterFunction asyncClientContextProvider = (request, next) -> Mono.deferContextual(ctx -> {
		var transportContext = ctx.getOrDefault(McpTransportContext.KEY, McpTransportContext.EMPTY);
		// // do stuff with the context
		var headerValue = transportContext.get("client-side-header-value");
		if (headerValue == null) {
			return next.exchange(request);
		}
		var reqWithHeader = ClientRequest.from(request).header(HEADER_NAME, headerValue.toString()).build();
		return next.exchange(reqWithHeader);
	});

	// Tools
	private final McpSchema.Tool tool = McpSchema.Tool.builder()
		.name("test-tool")
		.description("return the value of the x-test header from call tool request")
		.build();

	private final BiFunction<McpTransportContext, McpSchema.CallToolRequest, Mono<McpSchema.CallToolResult>> asyncStatelessHandler = (
			transportContext,
			request) -> Mono.just(McpSchema.CallToolResult.builder()
				.content(
						List.of(new McpSchema.TextContent(transportContext.get("server-side-header-value").toString())))
				.build());

	private final BiFunction<McpAsyncServerExchange, McpSchema.CallToolRequest, Mono<McpSchema.CallToolResult>> asyncStatefulHandler = (
			exchange, request) -> this.asyncStatelessHandler.apply(exchange.transportContext(), request);

	// Server context extractor
	private final McpTransportContextExtractor<ServerRequest> serverContextExtractor = (ServerRequest r) -> {
		var headerValue = r.headers().firstHeader(HEADER_NAME);
		return headerValue != null ? McpTransportContext.create(Map.of("server-side-header-value", headerValue))
				: McpTransportContext.EMPTY;
	};

	// Server transports
	private final WebFluxStatelessServerTransport statelessServerTransport = WebFluxStatelessServerTransport.builder()
		.contextExtractor(this.serverContextExtractor)
		.build();

	private final WebFluxStreamableServerTransportProvider streamableServerTransport = WebFluxStreamableServerTransportProvider
		.builder()
		.contextExtractor(this.serverContextExtractor)
		.build();

	private final WebFluxSseServerTransportProvider sseServerTransport = WebFluxSseServerTransportProvider.builder()
		.contextExtractor(this.serverContextExtractor)
		.messageEndpoint("/mcp/message")
		.build();

	// Async clients (initialized in startHttpServer after port is known)
	private McpAsyncClient asyncStreamableClient;

	private McpAsyncClient asyncSseClient;

	private DisposableServer httpServer;

	@AfterEach
	public void after() {
		if (this.statelessServerTransport != null) {
			this.statelessServerTransport.closeGracefully().block();
		}
		if (this.streamableServerTransport != null) {
			this.streamableServerTransport.closeGracefully().block();
		}
		if (this.sseServerTransport != null) {
			this.sseServerTransport.closeGracefully().block();
		}
		if (this.asyncStreamableClient != null) {
			this.asyncStreamableClient.closeGracefully().block();
		}
		if (this.asyncSseClient != null) {
			this.asyncSseClient.closeGracefully().block();
		}
		stopHttpServer();
	}

	@Test
	void asyncClientStatelessServer() {

		startHttpServer(this.statelessServerTransport.getRouterFunction());

		var mcpServer = McpServer.async(this.statelessServerTransport)
			.capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
			.tools(new McpStatelessServerFeatures.AsyncToolSpecification(this.tool, this.asyncStatelessHandler))
			.build();

		StepVerifier.create(this.asyncStreamableClient.initialize())
			.assertNext(initResult -> assertThat(initResult).isNotNull())
			.verifyComplete();

		// Test tool call with context
		StepVerifier
			.create(this.asyncStreamableClient.callTool(new McpSchema.CallToolRequest("test-tool", Map.of()))
				.contextWrite(ctx -> ctx.put(McpTransportContext.KEY,
						McpTransportContext.create(Map.of("client-side-header-value", "some important value")))))
			.assertNext(response -> {
				assertThat(response).isNotNull();
				assertThat(response.content()).hasSize(1)
					.first()
					.extracting(McpSchema.TextContent.class::cast)
					.extracting(McpSchema.TextContent::text)
					.isEqualTo("some important value");
			})
			.verifyComplete();

		mcpServer.close();
	}

	@Test
	void asyncClientStreamableServer() {

		startHttpServer(this.streamableServerTransport.getRouterFunction());

		var mcpServer = McpServer.async(this.streamableServerTransport)
			.capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
			.tools(new McpServerFeatures.AsyncToolSpecification(this.tool, this.asyncStatefulHandler))
			.build();

		StepVerifier.create(this.asyncStreamableClient.initialize())
			.assertNext(initResult -> assertThat(initResult).isNotNull())
			.verifyComplete();

		// Test tool call with context
		StepVerifier
			.create(this.asyncStreamableClient.callTool(new McpSchema.CallToolRequest("test-tool", Map.of()))
				.contextWrite(ctx -> ctx.put(McpTransportContext.KEY,
						McpTransportContext.create(Map.of("client-side-header-value", "some important value")))))
			.assertNext(response -> {
				assertThat(response).isNotNull();
				assertThat(response.content()).hasSize(1)
					.first()
					.extracting(McpSchema.TextContent.class::cast)
					.extracting(McpSchema.TextContent::text)
					.isEqualTo("some important value");
			})
			.verifyComplete();

		mcpServer.close();
	}

	@Test
	void asyncClientSseServer() {

		startHttpServer(this.sseServerTransport.getRouterFunction());

		var mcpServer = McpServer.async(this.sseServerTransport)
			.capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
			.tools(new McpServerFeatures.AsyncToolSpecification(this.tool, this.asyncStatefulHandler))
			.build();

		StepVerifier.create(this.asyncSseClient.initialize())
			.assertNext(initResult -> assertThat(initResult).isNotNull())
			.verifyComplete();

		// Test tool call with context
		StepVerifier
			.create(this.asyncSseClient.callTool(new McpSchema.CallToolRequest("test-tool", Map.of()))
				.contextWrite(ctx -> ctx.put(McpTransportContext.KEY,
						McpTransportContext.create(Map.of("client-side-header-value", "some important value")))))
			.assertNext(response -> {
				assertThat(response).isNotNull();
				assertThat(response.content()).hasSize(1)
					.first()
					.extracting(McpSchema.TextContent.class::cast)
					.extracting(McpSchema.TextContent::text)
					.isEqualTo("some important value");
			})
			.verifyComplete();

		mcpServer.close();
	}

	private void startHttpServer(RouterFunction<?> routerFunction) {

		HttpHandler httpHandler = RouterFunctions.toHttpHandler(routerFunction);
		ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
		this.httpServer = HttpServer.create().port(0).handle(adapter).bindNow();
		int port = this.httpServer.port();
		this.asyncStreamableClient = McpClient
			.async(WebClientStreamableHttpTransport
				.builder(
						WebClient.builder().baseUrl("http://127.0.0.1:" + port).filter(this.asyncClientContextProvider))
				.build())
			.build();
		this.asyncSseClient = McpClient
			.async(WebFluxSseClientTransport
				.builder(
						WebClient.builder().baseUrl("http://127.0.0.1:" + port).filter(this.asyncClientContextProvider))
				.build())
			.build();
	}

	private void stopHttpServer() {
		if (this.httpServer != null) {
			this.httpServer.disposeNow();
		}
	}

}
