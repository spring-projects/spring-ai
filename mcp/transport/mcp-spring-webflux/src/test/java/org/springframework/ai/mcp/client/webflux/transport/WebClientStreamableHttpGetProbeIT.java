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
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.test.StepVerifier;

import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for
 * <a href="https://github.com/spring-projects/spring-ai/issues/5239"> issue #5239</a>:
 * the {@code streamable-http} MCP client must tolerate a {@code GET /mcp} probe that
 * returns {@code 200 OK} with a non-SSE {@code Content-Type} (e.g.
 * {@code application/json}) as a no-stream endpoint, and fall back to request-response
 * mode rather than failing the initialization with an {@code McpTransportException}.
 *
 * <p>
 * The MCP streamable-http spec requires the server to return either
 * {@code Content-Type: text/event-stream} or {@code 405 Method Not Allowed} on the GET
 * probe. Some MCP servers (e.g. ModelScope Model API MCP) instead return {@code 200 OK}
 * with a plain JSON body when they do not offer a server-initiated stream, and that
 * response must not break the client.
 */
@Timeout(15)
public class WebClientStreamableHttpGetProbeIT {

	private HttpServer server;

	private McpClientTransport transport;

	// Latches that observe which path the test server actually received.
	private CountDownLatch firstPostLatch = new CountDownLatch(1);

	private CountDownLatch getProbeLatch = new CountDownLatch(1);

	// Per-test config set in @BeforeEach.
	private String getContentType;

	private int getStatus;

	private boolean hasGetBody;

	private String getBody;

	@BeforeEach
	void resetLatches() {
		this.firstPostLatch = new CountDownLatch(1);
		this.getProbeLatch = new CountDownLatch(1);
	}

	@AfterEach
	void stopServer() {
		if (this.server != null) {
			this.server.stop(0);
		}
		if (this.transport != null) {
			this.transport.closeGracefully().block(Duration.ofSeconds(2));
		}
	}

	/**
	 * The server returns {@code 200 OK} with {@code Content-Type: application/json} on
	 * the GET probe (no SSE stream). The client must treat this as a stateless endpoint,
	 * fall back to request-response mode, and successfully complete the request-response
	 * flow. The transport's exception handler must not be invoked with an error raised by
	 * the GET probe.
	 */
	@Test
	void testGetProbeWith200ApplicationJson() throws Exception {
		configureGetProbe("application/json", 200, true, "{\"info\":\"return 200 for GET /mcp.\"}");
		startServer();
		AtomicInteger exceptionCount = installExceptionCounter();

		// The reconnect() lifecycle is triggered by sendMessage() once a session is
		// established. First connect, then send a message — that establishes the
		// session via POST, then the background GET probe runs.
		StepVerifier.create(this.transport.connect(msg -> msg)).verifyComplete();
		StepVerifier.create(this.transport.sendMessage(buildInitializeRequest())).verifyComplete();

		assertThat(this.firstPostLatch.await(5, TimeUnit.SECONDS)).as("Server should have received the initial POST")
			.isTrue();
		assertThat(this.getProbeLatch.await(5, TimeUnit.SECONDS)).as("Server should have received the GET probe")
			.isTrue();
		// Give the background reconnect loop a moment to surface any error via the
		// exception handler. With the bug, this handler would have been invoked
		// with the McpTransportException from the unexpected 200-without-SSE GET.
		Thread.sleep(1000);
		assertThat(exceptionCount.get()).as("Exception handler should not have been invoked for a benign 200 GET probe")
			.isZero();
	}

	/**
	 * The server returns {@code 200 OK} with no {@code Content-Type} header on the GET
	 * probe. The client must still treat it as a stateless endpoint and must not raise an
	 * error.
	 */
	@Test
	void testGetProbeWith200NoContentType() throws Exception {
		configureGetProbe(null, 200, true, "{\"info\":\"return 200 for GET /mcp.\"}");
		startServer();
		AtomicInteger exceptionCount = installExceptionCounter();

		StepVerifier.create(this.transport.connect(msg -> msg)).verifyComplete();
		StepVerifier.create(this.transport.sendMessage(buildInitializeRequest())).verifyComplete();

		assertThat(this.firstPostLatch.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(this.getProbeLatch.await(5, TimeUnit.SECONDS)).as("Server should have received the GET probe")
			.isTrue();
		Thread.sleep(1000);
		assertThat(exceptionCount.get()).as("Exception handler should not have been invoked for a benign 200 GET probe")
			.isZero();
	}

	/**
	 * Sanity check: the server returns {@code 405 Method Not Allowed} on the GET probe.
	 * The client must continue to treat that as a stateless endpoint and successfully
	 * complete the flow. This is the well-formed case the client has always supported.
	 */
	@Test
	void testGetProbeWith405() throws Exception {
		configureGetProbe(null, 405, false, null);
		startServer();
		AtomicInteger exceptionCount = installExceptionCounter();

		StepVerifier.create(this.transport.connect(msg -> msg)).verifyComplete();
		StepVerifier.create(this.transport.sendMessage(buildInitializeRequest())).verifyComplete();

		assertThat(this.firstPostLatch.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(this.getProbeLatch.await(5, TimeUnit.SECONDS)).as("Server should have received the GET probe")
			.isTrue();
		Thread.sleep(1000);
		assertThat(exceptionCount.get()).as("Exception handler should not have been invoked for a 405 GET probe")
			.isZero();
	}

	private AtomicInteger installExceptionCounter() {
		AtomicInteger counter = new AtomicInteger();
		this.transport.setExceptionHandler((Consumer<Throwable>) t -> counter.incrementAndGet());
		return counter;
	}

	private void configureGetProbe(String contentType, int status, boolean hasBody, String body) {
		this.getContentType = contentType;
		this.getStatus = status;
		this.hasGetBody = hasBody;
		this.getBody = body;
	}

	private void startServer() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress(0), 0);
		AtomicReference<String> sessionIdToReturn = new AtomicReference<>("test-session-5239");
		this.server.createContext("/mcp", exchange -> {
			String method = exchange.getRequestMethod();
			if ("GET".equals(method)) {
				this.getProbeLatch.countDown();
				if (this.getContentType != null) {
					exchange.getResponseHeaders().set("Content-Type", this.getContentType);
				}
				if (this.hasGetBody) {
					byte[] body = this.getBody.getBytes();
					exchange.sendResponseHeaders(this.getStatus, body.length);
					exchange.getResponseBody().write(body);
				}
				else {
					exchange.sendResponseHeaders(this.getStatus, 0);
				}
				exchange.close();
				return;
			}
			if ("POST".equals(method)) {
				this.firstPostLatch.countDown();
				// Respond with a minimal JSON-RPC success carrying a session id so
				// the client establishes a session and the background GET probe runs.
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.getResponseHeaders().set("Mcp-Session-Id", sessionIdToReturn.get());
				String response = "{\"jsonrpc\":\"2.0\",\"result\":{},\"id\":\"test-id\"}";
				byte[] body = response.getBytes();
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
				exchange.close();
				return;
			}
			exchange.sendResponseHeaders(405, 0);
			exchange.close();
		});
		this.server.setExecutor(null);
		this.server.start();
		String host = "http://localhost:" + this.server.getAddress().getPort();
		this.transport = WebClientStreamableHttpTransport.builder(WebClient.builder().baseUrl(host)).build();
	}

	private static McpSchema.JSONRPCRequest buildInitializeRequest() {
		var initializeRequest = new McpSchema.InitializeRequest(ProtocolVersions.MCP_2025_03_26,
				McpSchema.ClientCapabilities.builder().roots(true).build(),
				new McpSchema.Implementation("Test Client", "1.0.0"));
		return new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_INITIALIZE, "test-id",
				initializeRequest);
	}

}
