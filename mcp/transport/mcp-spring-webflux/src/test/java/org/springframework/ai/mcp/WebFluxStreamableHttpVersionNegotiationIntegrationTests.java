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

package org.springframework.ai.mcp;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.ai.mcp.client.webflux.transport.WebClientStreamableHttpTransport;
import org.springframework.ai.mcp.server.webflux.transport.WebFluxStreamableServerTransportProvider;
import org.springframework.ai.mcp.utils.McpTestRequestRecordingExchangeFilterFunction;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

class WebFluxStreamableHttpVersionNegotiationIntegrationTests {

	private DisposableServer httpServer;

	private int port;

	private final McpTestRequestRecordingExchangeFilterFunction recordingFilterFunction = new McpTestRequestRecordingExchangeFilterFunction();

	private final McpSchema.Tool toolSpec = McpSchema.Tool.builder()
		.name("test-tool")
		.description("return the protocol version used")
		.build();

	private final BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> toolHandler = (
			exchange, request) -> new McpSchema.CallToolResult(
					exchange.transportContext().get("protocol-version").toString(), null);

	private final WebFluxStreamableServerTransportProvider mcpStreamableServerTransportProvider = WebFluxStreamableServerTransportProvider
		.builder()
		.contextExtractor(req -> McpTransportContext
			.create(Map.of("protocol-version", req.headers().firstHeader("MCP-protocol-version"))))
		.build();

	private final McpSyncServer mcpServer = McpServer.sync(this.mcpStreamableServerTransportProvider)
		.capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
		.tools(new McpServerFeatures.SyncToolSpecification(this.toolSpec, null, this.toolHandler))
		.build();

	@BeforeEach
	void setUp() {
		RouterFunction<ServerResponse> filteredRouter = this.mcpStreamableServerTransportProvider.getRouterFunction()
			.filter(this.recordingFilterFunction);

		HttpHandler httpHandler = RouterFunctions.toHttpHandler(filteredRouter);

		ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);

		this.httpServer = HttpServer.create().port(0).handle(adapter).bindNow();
		this.port = this.httpServer.port();
	}

	@AfterEach
	public void after() {
		if (this.httpServer != null) {
			this.httpServer.disposeNow();
		}
		if (this.mcpServer != null) {
			this.mcpServer.close();
		}
	}

	@Test
	void usesLatestVersion() {
		var client = McpClient
			.sync(WebClientStreamableHttpTransport.builder(WebClient.builder().baseUrl("http://127.0.0.1:" + this.port))
				.build())
			.requestTimeout(Duration.ofHours(10))
			.build();

		client.initialize();

		McpSchema.CallToolResult response = client.callTool(new McpSchema.CallToolRequest("test-tool", Map.of()));

		var calls = this.recordingFilterFunction.getCalls();
		assertThat(calls).filteredOn(c -> !c.body().contains("\"method\":\"initialize\""))
			// GET /mcp ; POST notification/initialized ; POST tools/call
			.hasSize(3)
			.map(McpTestRequestRecordingExchangeFilterFunction.Call::headers)
			.allSatisfy(headers -> assertThat(headers).containsEntry("mcp-protocol-version",
					ProtocolVersions.MCP_2025_11_25));

		assertThat(response).isNotNull();
		assertThat(response.content()).hasSize(1)
			.first()
			.extracting(McpSchema.TextContent.class::cast)
			.extracting(McpSchema.TextContent::text)
			.isEqualTo(ProtocolVersions.MCP_2025_11_25);
		this.mcpServer.close();
	}

	@Test
	void usesServerSupportedVersion() {
		var transport = WebClientStreamableHttpTransport
			.builder(WebClient.builder().baseUrl("http://127.0.0.1:" + this.port))
			.supportedProtocolVersions(List.of(ProtocolVersions.MCP_2025_11_25, "2263-03-18"))
			.build();
		var client = McpClient.sync(transport).requestTimeout(Duration.ofHours(10)).build();

		client.initialize();

		McpSchema.CallToolResult response = client.callTool(new McpSchema.CallToolRequest("test-tool", Map.of()));

		var calls = this.recordingFilterFunction.getCalls();
		// Initialize tells the server the Client's latest supported version
		// FIXME: Set the correct protocol version on GET /mcp
		assertThat(calls)
			.filteredOn(c -> !c.body().contains("\"method\":\"initialize\"") && c.method().equals(HttpMethod.POST))
			// POST notification/initialized ; POST tools/call
			.hasSize(2)
			.map(McpTestRequestRecordingExchangeFilterFunction.Call::headers)
			.allSatisfy(headers -> assertThat(headers).containsEntry("mcp-protocol-version",
					ProtocolVersions.MCP_2025_11_25));

		assertThat(response).isNotNull();
		assertThat(response.content()).hasSize(1)
			.first()
			.extracting(McpSchema.TextContent.class::cast)
			.extracting(McpSchema.TextContent::text)
			.isEqualTo(ProtocolVersions.MCP_2025_11_25);
		this.mcpServer.close();
	}

}
