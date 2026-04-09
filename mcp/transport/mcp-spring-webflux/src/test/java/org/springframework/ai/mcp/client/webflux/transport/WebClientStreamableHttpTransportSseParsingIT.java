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

package org.springframework.ai.mcp.client.webflux.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.test.StepVerifier;

import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(15)
class WebClientStreamableHttpTransportSseParsingIT {

	private HttpServer server;

	private WebClientStreamableHttpTransport transport;

	private CountDownLatch handlerLatch;

	private AtomicReference<McpSchema.JSONRPCMessage> receivedMessage;

	@BeforeEach
	void setUp() throws IOException {
		this.handlerLatch = new CountDownLatch(1);
		this.receivedMessage = new AtomicReference<>();
		this.server = HttpServer.create(new InetSocketAddress(0), 0);
		this.server.createContext("/mcp", exchange -> {
			exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
			exchange.sendResponseHeaders(200, 0);
			String response = "data: {\"jsonrpc\":\"2.0\",\"id\":\"test-id\",\"result\":{}}\n\n";
			exchange.getResponseBody().write(response.getBytes());
			exchange.close();
		});
		this.server.start();
		String host = "http://localhost:" + this.server.getAddress().getPort();
		this.transport = WebClientStreamableHttpTransport.builder(WebClient.builder().baseUrl(host)).build();
	}

	@AfterEach
	void tearDown() {
		if (this.transport != null) {
			StepVerifier.create(this.transport.closeGracefully()).verifyComplete();
		}
		if (this.server != null) {
			this.server.stop(0);
		}
	}

	@Test
	void shouldParseSseEventWithoutEventFieldAsMessage() throws InterruptedException {
		StepVerifier.create(this.transport.connect(inbound -> inbound.doOnNext(message -> {
			this.receivedMessage.set(message);
			this.handlerLatch.countDown();
		}))).verifyComplete();

		StepVerifier.create(this.transport.sendMessage(createInitializeMessage())).verifyComplete();

		assertThat(this.handlerLatch.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(this.receivedMessage.get()).isInstanceOf(McpSchema.JSONRPCResponse.class);

		McpSchema.JSONRPCResponse response = (McpSchema.JSONRPCResponse) this.receivedMessage.get();
		assertThat(response.id()).isEqualTo("test-id");
	}

	private McpSchema.JSONRPCRequest createInitializeMessage() {
		var initializeRequest = new McpSchema.InitializeRequest(ProtocolVersions.MCP_2025_03_26,
				McpSchema.ClientCapabilities.builder().roots(true).build(),
				new McpSchema.Implementation("Test Client", "1.0.0"));
		return new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_INITIALIZE, "test-id",
				initializeRequest);
	}

}
