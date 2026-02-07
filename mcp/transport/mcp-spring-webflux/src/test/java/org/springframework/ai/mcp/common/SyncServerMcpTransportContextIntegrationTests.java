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

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.TestUtil;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link McpTransportContext} propagation between MCP client and
 * server using synchronous operations in a Spring WebFlux environment.
 * <p>
 * This test class validates the end-to-end flow of transport context propagation across
 * different WebFlux-based MCP transport implementations
 *
 * <p>
 * The test scenario follows these steps:
 * <ol>
 * <li>The client stores a value in a thread-local variable</li>
 * <li>The client's transport context provider reads this value and includes it in the MCP
 * context</li>
 * <li>A WebClient filter extracts the context value and adds it as an HTTP header
 * (x-test)</li>
 * <li>The server's {@link McpTransportContextExtractor} reads the header from the
 * request</li>
 * <li>The server returns the header value as the tool call result, validating the
 * round-trip</li>
 * </ol>
 *
 * <p>
 * This test demonstrates how custom context can be propagated through HTTP headers in a
 * reactive WebFlux environment, enabling features like authentication tokens, correlation
 * IDs, or other metadata to flow between MCP client and server.
 *
 * @author Daniel Garnier-Moiroux
 * @author Christian Tzolov
 * @since 1.0.0
 * @see McpTransportContext
 * @see McpTransportContextExtractor
 * @see WebFluxStatelessServerTransport
 * @see WebFluxStreamableServerTransportProvider
 * @see WebFluxSseServerTransportProvider
 */
@Timeout(15)
public class SyncServerMcpTransportContextIntegrationTests {

	private static final int PORT = TestUtil.findAvailablePort();

	private static final ThreadLocal<String> CLIENT_SIDE_HEADER_VALUE_HOLDER = new ThreadLocal<>();

	private static final String HEADER_NAME = "x-test";

	private final Supplier<McpTransportContext> clientContextProvider = () -> {
		var headerValue = CLIENT_SIDE_HEADER_VALUE_HOLDER.get();
		return headerValue != null ? McpTransportContext.create(Map.of("client-side-header-value", headerValue))
				: McpTransportContext.EMPTY;
	};

	private final BiFunction<McpTransportContext, McpSchema.CallToolRequest, McpSchema.CallToolResult> statelessHandler = (
			transportContext,
			request) -> new McpSchema.CallToolResult(transportContext.get("server-side-header-value").toString(), null);

	private final BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> statefulHandler = (
			exchange, request) -> this.statelessHandler.apply(exchange.transportContext(), request);

	private final McpTransportContextExtractor<ServerRequest> serverContextExtractor = (ServerRequest r) -> {
		var headerValue = r.headers().firstHeader(HEADER_NAME);
		return headerValue != null ? McpTransportContext.create(Map.of("server-side-header-value", headerValue))
				: McpTransportContext.EMPTY;
	};

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

	private final McpSyncClient streamableClient = McpClient
		.sync(WebClientStreamableHttpTransport.builder(WebClient.builder()
			.baseUrl("http://localhost:" + PORT)
			.filter((request, next) -> Mono.deferContextual(ctx -> {
				var context = ctx.getOrDefault(McpTransportContext.KEY, McpTransportContext.EMPTY);
				// // do stuff with the context
				var headerValue = context.get("client-side-header-value");
				if (headerValue == null) {
					return next.exchange(request);
				}
				var reqWithHeader = ClientRequest.from(request).header(HEADER_NAME, headerValue.toString()).build();
				return next.exchange(reqWithHeader);
			}))).build())
		.transportContextProvider(this.clientContextProvider)
		.build();

	private final McpSyncClient sseClient = McpClient.sync(WebFluxSseClientTransport.builder(WebClient.builder()
		.baseUrl("http://localhost:" + PORT)
		.filter((request, next) -> Mono.deferContextual(ctx -> {
			var context = ctx.getOrDefault(McpTransportContext.KEY, McpTransportContext.EMPTY);
			// // do stuff with the context
			var headerValue = context.get("client-side-header-value");
			if (headerValue == null) {
				return next.exchange(request);
			}
			var reqWithHeader = ClientRequest.from(request).header(HEADER_NAME, headerValue.toString()).build();
			return next.exchange(reqWithHeader);
		}))).build()).transportContextProvider(this.clientContextProvider).build();

	private final McpSchema.Tool tool = McpSchema.Tool.builder()
		.name("test-tool")
		.description("return the value of the x-test header from call tool request")
		.build();

	private DisposableServer httpServer;

	@AfterEach
	public void after() {
		CLIENT_SIDE_HEADER_VALUE_HOLDER.remove();
		if (this.statelessServerTransport != null) {
			this.statelessServerTransport.closeGracefully().block();
		}
		if (this.streamableServerTransport != null) {
			this.streamableServerTransport.closeGracefully().block();
		}
		if (this.sseServerTransport != null) {
			this.sseServerTransport.closeGracefully().block();
		}
		if (this.streamableClient != null) {
			this.streamableClient.closeGracefully();
		}
		if (this.sseClient != null) {
			this.sseClient.closeGracefully();
		}
		stopHttpServer();
	}

	@Test
	void statelessServer() {

		startHttpServer(this.statelessServerTransport.getRouterFunction());

		var mcpServer = McpServer.sync(this.statelessServerTransport)
			.capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
			.tools(new McpStatelessServerFeatures.SyncToolSpecification(this.tool, this.statelessHandler))
			.build();

		McpSchema.InitializeResult initResult = this.streamableClient.initialize();
		assertThat(initResult).isNotNull();

		CLIENT_SIDE_HEADER_VALUE_HOLDER.set("some important value");
		McpSchema.CallToolResult response = this.streamableClient
			.callTool(new McpSchema.CallToolRequest("test-tool", Map.of()));

		assertThat(response).isNotNull();
		assertThat(response.content()).hasSize(1)
			.first()
			.extracting(McpSchema.TextContent.class::cast)
			.extracting(McpSchema.TextContent::text)
			.isEqualTo("some important value");

		mcpServer.close();
	}

	@Test
	void streamableServer() {

		startHttpServer(this.streamableServerTransport.getRouterFunction());

		var mcpServer = McpServer.sync(this.streamableServerTransport)
			.capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
			.tools(new McpServerFeatures.SyncToolSpecification(this.tool, null, this.statefulHandler))
			.build();

		McpSchema.InitializeResult initResult = this.streamableClient.initialize();
		assertThat(initResult).isNotNull();

		CLIENT_SIDE_HEADER_VALUE_HOLDER.set("some important value");
		McpSchema.CallToolResult response = this.streamableClient
			.callTool(new McpSchema.CallToolRequest("test-tool", Map.of()));

		assertThat(response).isNotNull();
		assertThat(response.content()).hasSize(1)
			.first()
			.extracting(McpSchema.TextContent.class::cast)
			.extracting(McpSchema.TextContent::text)
			.isEqualTo("some important value");

		mcpServer.close();
	}

	@Test
	void sseServer() {
		startHttpServer(this.sseServerTransport.getRouterFunction());

		var mcpServer = McpServer.sync(this.sseServerTransport)
			.capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
			.tools(new McpServerFeatures.SyncToolSpecification(this.tool, null, this.statefulHandler))
			.build();

		McpSchema.InitializeResult initResult = this.sseClient.initialize();
		assertThat(initResult).isNotNull();

		CLIENT_SIDE_HEADER_VALUE_HOLDER.set("some important value");
		McpSchema.CallToolResult response = this.sseClient
			.callTool(new McpSchema.CallToolRequest("test-tool", Map.of()));

		assertThat(response).isNotNull();
		assertThat(response.content()).hasSize(1)
			.first()
			.extracting(McpSchema.TextContent.class::cast)
			.extracting(McpSchema.TextContent::text)
			.isEqualTo("some important value");

		mcpServer.close();
	}

	private void startHttpServer(RouterFunction<?> routerFunction) {

		HttpHandler httpHandler = RouterFunctions.toHttpHandler(routerFunction);
		ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
		this.httpServer = HttpServer.create().port(PORT).handle(adapter).bindNow();
	}

	private void stopHttpServer() {
		if (this.httpServer != null) {
			this.httpServer.disposeNow();
		}
	}

}
