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

package org.springframework.ai.mcp.client.webflux.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.server.TestUtil;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpTransportException;
import io.modelcontextprotocol.spec.McpTransportSessionNotFoundException;
import io.modelcontextprotocol.spec.ProtocolVersions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Tests for error handling in WebClientStreamableHttpTransport. Addresses concurrency
 * issues with proper Reactor patterns.
 *
 * @author Christian Tzolov
 */
@Timeout(15)
public class WebClientStreamableHttpTransportErrorHandlingTest {

	private static final int PORT = TestUtil.findAvailablePort();

	private static final String HOST = "http://localhost:" + PORT;

	private HttpServer server;

	private AtomicReference<Integer> serverResponseStatus = new AtomicReference<>(200);

	private AtomicReference<String> currentServerSessionId = new AtomicReference<>(null);

	private AtomicReference<String> lastReceivedSessionId = new AtomicReference<>(null);

	private McpClientTransport transport;

	// Initialize latches for proper request synchronization
	CountDownLatch firstRequestLatch;

	CountDownLatch secondRequestLatch;

	CountDownLatch getRequestLatch;

	@BeforeEach
	void startServer() throws IOException {

		// Initialize latches for proper synchronization
		this.firstRequestLatch = new CountDownLatch(1);
		this.secondRequestLatch = new CountDownLatch(1);
		this.getRequestLatch = new CountDownLatch(1);

		this.server = HttpServer.create(new InetSocketAddress(PORT), 0);

		// Configure the /mcp endpoint with dynamic response
		this.server.createContext("/mcp", exchange -> {
			String method = exchange.getRequestMethod();

			if ("GET".equals(method)) {
				// This is the SSE connection attempt after session establishment
				this.getRequestLatch.countDown();
				// Return 405 Method Not Allowed to indicate SSE not supported
				exchange.sendResponseHeaders(405, 0);
				exchange.close();
				return;
			}

			String requestSessionId = exchange.getRequestHeaders().getFirst(HttpHeaders.MCP_SESSION_ID);
			this.lastReceivedSessionId.set(requestSessionId);

			int status = this.serverResponseStatus.get();

			// Track which request this is
			if (this.firstRequestLatch.getCount() > 0) {
				// // First request - should have no session ID
				this.firstRequestLatch.countDown();
			}
			else if (this.secondRequestLatch.getCount() > 0) {
				// Second request - should have session ID
				this.secondRequestLatch.countDown();
			}

			exchange.getResponseHeaders().set("Content-Type", "application/json");

			// Don't include session ID in 404 and 400 responses - the implementation
			// checks if the transport has a session stored locally
			String responseSessionId = this.currentServerSessionId.get();
			if (responseSessionId != null && status == 200) {
				exchange.getResponseHeaders().set(HttpHeaders.MCP_SESSION_ID, responseSessionId);
			}
			if (status == 200) {
				String response = "{\"jsonrpc\":\"2.0\",\"result\":{},\"id\":\"test-id\"}";
				exchange.sendResponseHeaders(200, response.length());
				exchange.getResponseBody().write(response.getBytes());
			}
			else {
				exchange.sendResponseHeaders(status, 0);
			}
			exchange.close();
		});

		this.server.setExecutor(null);
		this.server.start();

		this.transport = WebClientStreamableHttpTransport.builder(WebClient.builder().baseUrl(HOST)).build();
	}

	@AfterEach
	void stopServer() {
		if (this.server != null) {
			this.server.stop(0);
		}
		StepVerifier.create(this.transport.closeGracefully()).verifyComplete();
	}

	/**
	 * Test that 404 response WITHOUT session ID throws McpTransportException (not
	 * SessionNotFoundException)
	 */
	@Test
	void test404WithoutSessionId() {
		this.serverResponseStatus.set(404);
		this.currentServerSessionId.set(null); // No session ID in response

		var testMessage = createTestMessage();

		StepVerifier.create(this.transport.sendMessage(testMessage))
			.expectErrorMatches(throwable -> throwable instanceof McpTransportException
					&& throwable.getMessage().contains("Not Found") && throwable.getMessage().contains("404")
					&& !(throwable instanceof McpTransportSessionNotFoundException))
			.verify(Duration.ofSeconds(5));
	}

	/**
	 * Test that 404 response WITH session ID throws McpTransportSessionNotFoundException
	 * Fixed version using proper async coordination
	 */
	@Test
	void test404WithSessionId() throws InterruptedException {
		// First establish a session
		this.serverResponseStatus.set(200);
		this.currentServerSessionId.set("test-session-123");

		// Set up exception handler to verify session invalidation
		@SuppressWarnings("unchecked")
		Consumer<Throwable> exceptionHandler = mock(Consumer.class);
		this.transport.setExceptionHandler(exceptionHandler);

		// Connect with handler
		StepVerifier.create(this.transport.connect(msg -> msg)).verifyComplete();

		// Send initial message to establish session
		var testMessage = createTestMessage();

		// Send first message to establish session
		StepVerifier.create(this.transport.sendMessage(testMessage)).verifyComplete();

		// Wait for first request to complete
		assertThat(this.firstRequestLatch.await(5, TimeUnit.SECONDS)).isTrue();

		// Wait for the GET request (SSE connection attempt) to complete
		assertThat(this.getRequestLatch.await(5, TimeUnit.SECONDS)).isTrue();

		// Now return 404 for next request
		this.serverResponseStatus.set(404);

		// Use delaySubscription to ensure session is fully processed before next
		// request
		StepVerifier.create(Mono.delay(Duration.ofMillis(200)).then(this.transport.sendMessage(testMessage)))
			.expectError(McpTransportSessionNotFoundException.class)
			.verify(Duration.ofSeconds(5));

		// Wait for second request to be made
		assertThat(this.secondRequestLatch.await(5, TimeUnit.SECONDS)).isTrue();

		// Verify the second request included the session ID
		assertThat(this.lastReceivedSessionId.get()).isEqualTo("test-session-123");

		// Verify exception handler was called with SessionNotFoundException using
		// timeout
		verify(exceptionHandler, timeout(5000)).accept(any(McpTransportSessionNotFoundException.class));
	}

	/**
	 * Test that 400 response WITHOUT session ID throws McpTransportException (not
	 * SessionNotFoundException)
	 */
	@Test
	void test400WithoutSessionId() {
		this.serverResponseStatus.set(400);
		this.currentServerSessionId.set(null); // No session ID

		var testMessage = createTestMessage();

		StepVerifier.create(this.transport.sendMessage(testMessage))
			.expectErrorMatches(throwable -> throwable instanceof McpTransportException
					&& throwable.getMessage().contains("Bad Request") && throwable.getMessage().contains("400")
					&& !(throwable instanceof McpTransportSessionNotFoundException))
			.verify(Duration.ofSeconds(10));
	}

	/**
	 * Test that 400 response WITH session ID throws McpTransportSessionNotFoundException
	 * Fixed version using proper async coordination
	 */
	@Test
	void test400WithSessionId() throws InterruptedException {

		// First establish a session
		this.serverResponseStatus.set(200);
		this.currentServerSessionId.set("test-session-456");

		// Set up exception handler
		@SuppressWarnings("unchecked")
		Consumer<Throwable> exceptionHandler = mock(Consumer.class);
		this.transport.setExceptionHandler(exceptionHandler);

		// Connect with handler
		StepVerifier.create(this.transport.connect(msg -> msg)).verifyComplete();

		// Send initial message to establish session
		var testMessage = createTestMessage();

		// Send first message to establish session
		StepVerifier.create(this.transport.sendMessage(testMessage)).verifyComplete();

		// Wait for first request to complete
		boolean firstCompleted = this.firstRequestLatch.await(5, TimeUnit.SECONDS);
		assertThat(firstCompleted).isTrue();

		// Wait for the GET request (SSE connection attempt) to complete
		boolean getCompleted = this.getRequestLatch.await(5, TimeUnit.SECONDS);
		assertThat(getCompleted).isTrue();

		// Now return 400 for next request (simulating unknown session ID)
		this.serverResponseStatus.set(400);

		// Use delaySubscription to ensure session is fully processed before next
		// request
		StepVerifier.create(Mono.delay(Duration.ofMillis(200)).then(this.transport.sendMessage(testMessage)))
			.expectError(McpTransportSessionNotFoundException.class)
			.verify(Duration.ofSeconds(5));

		// Wait for second request to be made
		boolean secondCompleted = this.secondRequestLatch.await(5, TimeUnit.SECONDS);
		assertThat(secondCompleted).isTrue();

		// Verify the second request included the session ID
		assertThat(this.lastReceivedSessionId.get()).isEqualTo("test-session-456");

		// Verify exception handler was called with timeout
		verify(exceptionHandler, timeout(5000)).accept(any(McpTransportSessionNotFoundException.class));
	}

	/**
	 * Test session recovery after SessionNotFoundException Fixed version using reactive
	 * patterns and proper synchronization
	 */
	@Test
	void testSessionRecoveryAfter404() {
		// First establish a session
		this.serverResponseStatus.set(200);
		this.currentServerSessionId.set("session-1");

		// Send initial message to establish session
		var testMessage = createTestMessage();

		// Use Mono.defer to ensure proper sequencing
		Mono<Void> establishSession = this.transport.sendMessage(testMessage).then(Mono.defer(() -> {
			// Simulate session loss - return 404
			this.serverResponseStatus.set(404);
			return this.transport.sendMessage(testMessage)
				.onErrorResume(McpTransportSessionNotFoundException.class, e -> Mono.empty());
		})).then(Mono.defer(() -> {
			// Now server is back with new session
			this.serverResponseStatus.set(200);
			this.currentServerSessionId.set("session-2");
			this.lastReceivedSessionId.set(null); // Reset to verify new session

			// Should be able to establish new session
			return this.transport.sendMessage(testMessage);
		})).then(Mono.defer(() -> {
			// Verify no session ID was sent (since old session was invalidated)
			assertThat(this.lastReceivedSessionId.get()).isNull();

			// Next request should use the new session ID
			return this.transport.sendMessage(testMessage);
		})).doOnSuccess(v -> assertThat(this.lastReceivedSessionId.get()).isEqualTo("session-2"));

		StepVerifier.create(establishSession).verifyComplete();
	}

	/**
	 * Test that reconnect (GET request) also properly handles 404/400 errors Fixed
	 * version with proper async handling
	 */
	@Test
	void testReconnectErrorHandling() throws InterruptedException {
		// Initialize latch for SSE connection
		CountDownLatch sseConnectionLatch = new CountDownLatch(1);

		// Set up SSE endpoint for GET requests
		this.server.createContext("/mcp-sse", exchange -> {
			String method = exchange.getRequestMethod();
			String requestSessionId = exchange.getRequestHeaders().getFirst(HttpHeaders.MCP_SESSION_ID);

			if ("GET".equals(method)) {
				sseConnectionLatch.countDown();
				int status = this.serverResponseStatus.get();

				if (status == 404 && requestSessionId != null) {
					// 404 with session ID - should trigger SessionNotFoundException
					exchange.sendResponseHeaders(404, 0);
				}
				else if (status == 404) {
					// 404 without session ID - should trigger McpTransportException
					exchange.sendResponseHeaders(404, 0);
				}
				else {
					// Normal SSE response
					exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
					exchange.sendResponseHeaders(200, 0);
					// Send a test SSE event
					String sseData = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"test\",\"params\":{}}\n\n";
					exchange.getResponseBody().write(sseData.getBytes());
				}
			}
			else {
				// POST request handling
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				String responseSessionId = this.currentServerSessionId.get();
				if (responseSessionId != null) {
					exchange.getResponseHeaders().set(HttpHeaders.MCP_SESSION_ID, responseSessionId);
				}
				String response = "{\"jsonrpc\":\"2.0\",\"result\":{},\"id\":\"test-id\"}";
				exchange.sendResponseHeaders(200, response.length());
				exchange.getResponseBody().write(response.getBytes());
			}
			exchange.close();
		});

		// Test with session ID - should get SessionNotFoundException
		this.serverResponseStatus.set(200);
		this.currentServerSessionId.set("sse-session-1");

		var transport = WebClientStreamableHttpTransport.builder(WebClient.builder().baseUrl(HOST))
			.endpoint("/mcp-sse")
			.openConnectionOnStartup(true) // This will trigger GET request on connect
			.build();

		// First connect successfully
		StepVerifier.create(transport.connect(msg -> msg)).verifyComplete();

		// Wait for SSE connection to be established
		boolean connected = sseConnectionLatch.await(5, TimeUnit.SECONDS);
		assertThat(connected).isTrue();

		// Send message to establish session
		var testMessage = createTestMessage();
		StepVerifier.create(transport.sendMessage(testMessage)).verifyComplete();

		// Clean up
		StepVerifier.create(transport.closeGracefully()).verifyComplete();
	}

	private McpSchema.JSONRPCRequest createTestMessage() {
		var initializeRequest = new McpSchema.InitializeRequest(ProtocolVersions.MCP_2025_03_26,
				McpSchema.ClientCapabilities.builder().roots(true).build(),
				new McpSchema.Implementation("Test Client", "1.0.0"));
		return new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_INITIALIZE, "test-id",
				initializeRequest);
	}

}
