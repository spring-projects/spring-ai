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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpTransportException;
import io.modelcontextprotocol.spec.ProtocolVersions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.test.StepVerifier;

import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JSON response handling in {@link WebClientStreamableHttpTransport}.
 *
 * @author git-syl
 */
@Timeout(10)
class WebClientStreamableHttpTransportTests {

	private static final String VALID_JSON_RPC_RESPONSE = "{\"jsonrpc\":\"2.0\",\"result\":{},\"id\":\"test-id\"}";

	private HttpServer server;

	private WebClientStreamableHttpTransport transport;

	@AfterEach
	void stopServer() {
		if (this.transport != null) {
			StepVerifier.create(this.transport.closeGracefully()).verifyComplete();
		}
		if (this.server != null) {
			this.server.stop(0);
		}
	}

	@Test
	void rejectsNonJsonRpcJsonResponseWithoutTimingOutOrEstablishingSession() throws IOException {
		AtomicInteger postCount = new AtomicInteger();
		AtomicBoolean getRequested = new AtomicBoolean();
		AtomicReference<String> secondRequestSessionId = new AtomicReference<>();
		startServer(exchange -> {
			if ("GET".equals(exchange.getRequestMethod())) {
				getRequested.set(true);
				writeResponse(exchange, 405, "");
				return;
			}
			if ("DELETE".equals(exchange.getRequestMethod())) {
				writeResponse(exchange, 200, "");
				return;
			}

			int request = postCount.incrementAndGet();
			if (request == 2) {
				secondRequestSessionId.set(exchange.getRequestHeaders().getFirst(HttpHeaders.MCP_SESSION_ID));
			}
			exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
			if (request == 1) {
				exchange.getResponseHeaders().set(HttpHeaders.MCP_SESSION_ID, "invalid-session");
			}
			String response = request == 1 ? "{\"status\":\"0\",\"info\":\"INVALID_USER_KEY\",\"infocode\":\"10001\"}"
					: VALID_JSON_RPC_RESPONSE;
			writeResponse(exchange, 200, response);
		});

		this.transport.connect(Function.identity()).block();

		StepVerifier.create(this.transport.sendMessage(createTestMessage()))
			.expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Cannot deserialize JSONRPCMessage")
				.hasMessageContaining("INVALID_USER_KEY"))
			.verify(Duration.ofSeconds(2));

		StepVerifier.create(this.transport.sendMessage(createTestMessage())).verifyComplete();

		assertThat(getRequested).isFalse();
		assertThat(secondRequestSessionId.get()).isNull();
	}

	@Test
	void rejectsMalformedJsonResponseWithoutTimingOut() throws IOException {
		startServer(exchange -> {
			if ("DELETE".equals(exchange.getRequestMethod())) {
				writeResponse(exchange, 200, "");
				return;
			}
			exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
			writeResponse(exchange, 200, "{\"jsonrpc\":");
		});

		this.transport.connect(Function.identity()).block();

		StepVerifier.create(this.transport.sendMessage(createTestMessage()))
			.expectError(McpTransportException.class)
			.verify(Duration.ofSeconds(2));
	}

	@Test
	void acceptsValidJsonRpcResponseAndDispatchesItToRequestHandler() throws IOException {
		AtomicReference<McpSchema.JSONRPCMessage> receivedMessage = new AtomicReference<>();
		startServer(exchange -> {
			if ("DELETE".equals(exchange.getRequestMethod())) {
				writeResponse(exchange, 200, "");
				return;
			}
			exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
			writeResponse(exchange, 200, VALID_JSON_RPC_RESPONSE);
		});

		this.transport.connect(message -> message.doOnNext(receivedMessage::set)).block();

		StepVerifier.create(this.transport.sendMessage(createTestMessage())).verifyComplete();

		assertThat(receivedMessage)
			.hasValueSatisfying(message -> assertThat(message).isInstanceOf(McpSchema.JSONRPCResponse.class));
	}

	private void startServer(HttpHandler handler) throws IOException {
		this.server = HttpServer.create(new InetSocketAddress(0), 0);
		this.server.createContext("/mcp", handler);
		this.server.start();
		String host = "http://localhost:" + this.server.getAddress().getPort();
		this.transport = WebClientStreamableHttpTransport.builder(WebClient.builder().baseUrl(host))
			.endpoint("/mcp")
			.build();
	}

	private static void writeResponse(HttpExchange exchange, int status, String response) throws IOException {
		byte[] body = response.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, body.length);
		try {
			exchange.getResponseBody().write(body);
		}
		finally {
			exchange.close();
		}
	}

	private McpSchema.JSONRPCRequest createTestMessage() {
		var initializeRequest = McpSchema.InitializeRequest
			.builder(ProtocolVersions.MCP_2025_03_26, McpSchema.ClientCapabilities.builder().roots(true).build(),
					McpSchema.Implementation.builder("Test Client", "1.0.0").build())
			.build();
		return new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_INITIALIZE, "test-id",
				initializeRequest);
	}

}
