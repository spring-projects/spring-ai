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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.test.StepVerifier;

import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for body-level error handling in {@link WebClientStreamableHttpTransport}.
 *
 * <p>
 * Verifies that body-level errors (malformed SSE streams, malformed JSON responses)
 * produce immediate synthetic error responses instead of hanging until read timeout.
 *
 * @author James Kennedy
 * @see <a href="https://github.com/modelcontextprotocol/java-sdk/issues/889">#889</a>
 */
@Timeout(15)
public class WebClientStreamableHttpTransportBodyErrorIT {

	private HttpServer server;

	private McpClientTransport transport;

	private final AtomicBoolean returnMalformedSse = new AtomicBoolean(false);

	@BeforeEach
	void startServer() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress(0), 0);

		this.server.createContext("/mcp", exchange -> {
			String method = exchange.getRequestMethod();

			if ("DELETE".equals(method)) {
				exchange.sendResponseHeaders(200, 0);
				exchange.close();
				return;
			}

			if ("GET".equals(method)) {
				exchange.sendResponseHeaders(405, 0);
				exchange.close();
				return;
			}

			if (this.returnMalformedSse.get()) {
				exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
				exchange.sendResponseHeaders(200, 0);
				OutputStream os = exchange.getResponseBody();
				os.write("event: message\ndata: {not valid json\n\n".getBytes(StandardCharsets.UTF_8));
				os.flush();
				exchange.close();
				return;
			}

			exchange.getResponseHeaders().set("Content-Type", "application/json");
			String response = "{\"jsonrpc\":\"2.0\",\"result\":{},\"id\":\"init-id\"}";
			exchange.sendResponseHeaders(200, response.length());
			exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
			exchange.close();
		});

		this.server.setExecutor(null);
		this.server.start();

		String host = "http://localhost:" + this.server.getAddress().getPort();
		this.transport = WebClientStreamableHttpTransport.builder(WebClient.builder().baseUrl(host)).build();
	}

	@AfterEach
	void stopServer() {
		if (this.server != null) {
			this.server.stop(0);
		}
		StepVerifier.create(this.transport.closeGracefully()).verifyComplete();
	}

	@Nested
	class SseStream {

		@Test
		void emitsSyntheticErrorResponseOnMalformedSseData() throws InterruptedException {
			CopyOnWriteArrayList<McpSchema.JSONRPCMessage> handlerMessages = new CopyOnWriteArrayList<>();
			CountDownLatch errorResponseLatch = new CountDownLatch(1);
			connectTransportWithErrorCapture(handlerMessages, errorResponseLatch);
			returnMalformedSse.set(true);

			StepVerifier.create(transport.sendMessage(createRequest("req-123"))).verifyComplete();

			assertThat(errorResponseLatch.await(5, TimeUnit.SECONDS))
				.as("Handler should receive synthetic error response within 5 seconds")
				.isTrue();
			assertSingleSyntheticError(handlerMessages, "req-123");
		}

	}

	@Nested
	class JsonResponse {

		@Test
		void emitsSyntheticErrorResponseOnMalformedJson() throws InterruptedException {
			CopyOnWriteArrayList<McpSchema.JSONRPCMessage> handlerMessages = new CopyOnWriteArrayList<>();
			CountDownLatch errorResponseLatch = new CountDownLatch(1);
			connectTransportWithErrorCapture(handlerMessages, errorResponseLatch);
			replaceServerWithMalformedJsonResponse();

			StepVerifier.create(transport.sendMessage(createRequest("req-456"))).verifyComplete();

			assertThat(errorResponseLatch.await(5, TimeUnit.SECONDS))
				.as("Handler should receive synthetic error response within 5 seconds")
				.isTrue();
			assertSingleSyntheticError(handlerMessages, "req-456");
		}

	}

	@Nested
	class Notification {

		@Test
		void doesNotEmitSyntheticResponseOnBodyError() throws InterruptedException {
			CopyOnWriteArrayList<McpSchema.JSONRPCMessage> handlerMessages = new CopyOnWriteArrayList<>();
			StepVerifier.create(transport.connect(msg -> msg.doOnNext(handlerMessages::add))).verifyComplete();
			returnMalformedSse.set(true);
			McpSchema.JSONRPCNotification notification = new McpSchema.JSONRPCNotification(McpSchema.JSONRPC_VERSION,
					"notifications/cancelled", null);

			StepVerifier.create(transport.sendMessage(notification)).verifyComplete();

			Thread.sleep(500);
			assertThat(handlerMessages.stream()
				.filter(m -> m instanceof McpSchema.JSONRPCResponse resp && resp.error() != null)
				.toList()).isEmpty();
		}

	}

	private void connectTransportWithErrorCapture(CopyOnWriteArrayList<McpSchema.JSONRPCMessage> handlerMessages,
			CountDownLatch errorResponseLatch) {
		StepVerifier.create(this.transport.connect(msg -> msg.doOnNext(m -> {
			handlerMessages.add(m);
			if (m instanceof McpSchema.JSONRPCResponse resp && resp.error() != null) {
				errorResponseLatch.countDown();
			}
		}))).verifyComplete();
	}

	private McpSchema.JSONRPCRequest createRequest(String requestId) {
		return new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_INITIALIZE, requestId,
				new McpSchema.InitializeRequest(ProtocolVersions.MCP_2025_03_26,
						McpSchema.ClientCapabilities.builder().roots(true).build(),
						new McpSchema.Implementation("Test Client", "1.0.0")));
	}

	private void replaceServerWithMalformedJsonResponse() {
		this.server.removeContext("/mcp");
		this.server.createContext("/mcp", exchange -> {
			String method = exchange.getRequestMethod();

			if ("DELETE".equals(method)) {
				exchange.sendResponseHeaders(200, 0);
				exchange.close();
				return;
			}

			if ("GET".equals(method)) {
				exchange.sendResponseHeaders(405, 0);
				exchange.close();
				return;
			}

			exchange.getResponseHeaders().set("Content-Type", "application/json");
			byte[] malformed = "{not valid json".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, malformed.length);
			exchange.getResponseBody().write(malformed);
			exchange.close();
		});
	}

	private void assertSingleSyntheticError(CopyOnWriteArrayList<McpSchema.JSONRPCMessage> handlerMessages,
			String expectedRequestId) {
		var errorResponses = handlerMessages.stream()
			.filter(m -> m instanceof McpSchema.JSONRPCResponse resp && resp.error() != null)
			.map(m -> (McpSchema.JSONRPCResponse) m)
			.toList();

		assertThat(errorResponses).hasSize(1);
		McpSchema.JSONRPCResponse errorResponse = errorResponses.get(0);
		assertThat(errorResponse.id()).isEqualTo(expectedRequestId);
		assertThat(errorResponse.error().code()).isEqualTo(McpSchema.ErrorCodes.INTERNAL_ERROR);
		assertThat(errorResponse.error().message()).contains("Transport error");
	}

}
