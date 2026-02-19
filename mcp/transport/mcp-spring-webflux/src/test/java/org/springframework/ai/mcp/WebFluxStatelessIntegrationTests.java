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
import java.util.stream.Stream;

import io.modelcontextprotocol.AbstractStatelessIntegrationTests;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.StatelessAsyncSpecification;
import io.modelcontextprotocol.server.McpServer.StatelessSyncSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.provider.Arguments;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.ai.mcp.client.webflux.transport.WebClientStreamableHttpTransport;
import org.springframework.ai.mcp.server.webflux.transport.WebFluxStatelessServerTransport;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunctions;

@Timeout(15)
class WebFluxStatelessIntegrationTests extends AbstractStatelessIntegrationTests {

	private static final String CUSTOM_MESSAGE_ENDPOINT = "/otherPath/mcp/message";

	private DisposableServer httpServer;

	private WebFluxStatelessServerTransport mcpStreamableServerTransport;

	static Stream<Arguments> clientsForTesting() {
		return Stream.of(Arguments.of("httpclient"), Arguments.of("webflux"));
	}

	@Override
	protected void prepareClients(int port, String mcpEndpoint) {
		clientBuilders
			.put("httpclient",
					McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
						.endpoint(CUSTOM_MESSAGE_ENDPOINT)
						.build()).initializationTimeout(Duration.ofHours(10)).requestTimeout(Duration.ofHours(10)));
		clientBuilders
			.put("webflux", McpClient
				.sync(WebClientStreamableHttpTransport.builder(WebClient.builder().baseUrl("http://localhost:" + port))
					.endpoint(CUSTOM_MESSAGE_ENDPOINT)
					.build())
				.initializationTimeout(Duration.ofHours(10))
				.requestTimeout(Duration.ofHours(10)));
	}

	@Override
	protected StatelessAsyncSpecification prepareAsyncServerBuilder() {
		return McpServer.async(this.mcpStreamableServerTransport);
	}

	@Override
	protected StatelessSyncSpecification prepareSyncServerBuilder() {
		return McpServer.sync(this.mcpStreamableServerTransport);
	}

	@BeforeEach
	public void before() {
		this.mcpStreamableServerTransport = WebFluxStatelessServerTransport.builder()
			.messageEndpoint(CUSTOM_MESSAGE_ENDPOINT)
			.build();

		HttpHandler httpHandler = RouterFunctions.toHttpHandler(this.mcpStreamableServerTransport.getRouterFunction());
		ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
		this.httpServer = HttpServer.create().port(0).handle(adapter).bindNow();

		prepareClients(this.httpServer.port(), null);
	}

	@AfterEach
	public void after() {
		if (this.httpServer != null) {
			this.httpServer.disposeNow();
		}
	}

}
