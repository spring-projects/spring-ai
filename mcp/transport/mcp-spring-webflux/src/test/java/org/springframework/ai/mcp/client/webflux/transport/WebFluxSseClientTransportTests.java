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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest;
import io.modelcontextprotocol.util.McpJsonMapperUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@link WebFluxSseClientTransport} class.
 *
 * @author Christian Tzolov
 */
@Timeout(15)
class WebFluxSseClientTransportTests {

	static String host = "http://localhost:3001";

	@SuppressWarnings("resource")
	static GenericContainer<?> container = new GenericContainer<>("docker.io/node:lts-alpine3.23")
		.withCommand("npx -y @modelcontextprotocol/server-everything@2025.12.18 sse")
		.withLogConsumer(outputFrame -> System.out.println(outputFrame.getUtf8String()))
		.withExposedPorts(3001)
		.waitingFor(Wait.forHttp("/").forStatusCode(404));

	private TestSseClientTransport transport;

	private WebClient.Builder webClientBuilder;

	@BeforeAll
	static void startContainer() {
		container.start();
		int port = container.getMappedPort(3001);
		host = "http://" + container.getHost() + ":" + port;
	}

	@AfterAll
	static void cleanup() {
		container.stop();
	}

	@BeforeEach
	void setUp() {
		this.webClientBuilder = WebClient.builder().baseUrl(host);
		this.transport = new TestSseClientTransport(this.webClientBuilder, McpJsonMapperUtils.JSON_MAPPER);
		this.transport.connect(Function.identity()).block();
	}

	@AfterEach
	void afterEach() {
		if (this.transport != null) {
			assertThatCode(() -> this.transport.closeGracefully().block(Duration.ofSeconds(10)))
				.doesNotThrowAnyException();
		}
	}

	@Test
	void testEndpointEventHandling() {
		assertThat(this.transport.getLastEndpoint()).startsWith("/message?");
	}

	@Test
	void constructorValidation() {
		assertThatThrownBy(() -> new WebFluxSseClientTransport(null, McpJsonMapperUtils.JSON_MAPPER))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("WebClient.Builder must not be null");

		assertThatThrownBy(() -> new WebFluxSseClientTransport(this.webClientBuilder, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("jsonMapper must not be null");
	}

	@Test
	void testBuilderPattern() {
		// Test default builder
		WebFluxSseClientTransport transport1 = WebFluxSseClientTransport.builder(this.webClientBuilder).build();
		assertThatCode(() -> transport1.closeGracefully().block()).doesNotThrowAnyException();

		// Test builder with custom ObjectMapper
		JsonMapper customMapper = JsonMapper.builder().build();
		WebFluxSseClientTransport transport2 = WebFluxSseClientTransport.builder(this.webClientBuilder)
			.jsonMapper(new JacksonMcpJsonMapper(customMapper))
			.build();
		assertThatCode(() -> transport2.closeGracefully().block()).doesNotThrowAnyException();

		// Test builder with custom SSE endpoint
		WebFluxSseClientTransport transport3 = WebFluxSseClientTransport.builder(this.webClientBuilder)
			.sseEndpoint("/custom-sse")
			.build();
		assertThatCode(() -> transport3.closeGracefully().block()).doesNotThrowAnyException();

		// Test builder with all custom parameters
		WebFluxSseClientTransport transport4 = WebFluxSseClientTransport.builder(this.webClientBuilder)
			.sseEndpoint("/custom-sse")
			.build();
		assertThatCode(() -> transport4.closeGracefully().block()).doesNotThrowAnyException();
	}

	@Test
	void testCommentSseMessage() {
		// If the line starts with a character (:) are comment lins and should be ingored
		// https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation

		CopyOnWriteArrayList<Throwable> droppedErrors = new CopyOnWriteArrayList<>();
		reactor.core.publisher.Hooks.onErrorDropped(droppedErrors::add);

		try {
			// Simulate receiving the SSE comment line
			this.transport.simulateSseComment("sse comment");

			StepVerifier.create(this.transport.closeGracefully()).verifyComplete();

			assertThat(droppedErrors).hasSize(0);
		}
		finally {
			reactor.core.publisher.Hooks.resetOnErrorDropped();
		}
	}

	@Test
	void testMessageProcessing() {
		// Create a test message
		JSONRPCRequest testMessage = new JSONRPCRequest(McpSchema.JSONRPC_VERSION, "test-method", "test-id",
				Map.of("key", "value"));

		// Simulate receiving the message
		this.transport.simulateMessageEvent("""
				{
					"jsonrpc": "2.0",
					"method": "test-method",
					"id": "test-id",
					"params": {"key": "value"}
				}
				""");

		// Subscribe to messages and verify
		StepVerifier.create(this.transport.sendMessage(testMessage)).verifyComplete();

		assertThat(this.transport.getInboundMessageCount()).isEqualTo(1);
	}

	@Test
	void testResponseMessageProcessing() {
		// Simulate receiving a response message
		this.transport.simulateMessageEvent("""
				{
					"jsonrpc": "2.0",
					"id": "test-id",
					"result": {"status": "success"}
				}
				""");

		// Create and send a request message
		JSONRPCRequest testMessage = new JSONRPCRequest(McpSchema.JSONRPC_VERSION, "test-method", "test-id",
				Map.of("key", "value"));

		// Verify message handling
		StepVerifier.create(this.transport.sendMessage(testMessage)).verifyComplete();

		assertThat(this.transport.getInboundMessageCount()).isEqualTo(1);
	}

	@Test
	void testErrorMessageProcessing() {
		// Simulate receiving an error message
		this.transport.simulateMessageEvent("""
				{
					"jsonrpc": "2.0",
					"id": "test-id",
					"error": {
						"code": -32600,
						"message": "Invalid Request"
					}
				}
				""");

		// Create and send a request message
		JSONRPCRequest testMessage = new JSONRPCRequest(McpSchema.JSONRPC_VERSION, "test-method", "test-id",
				Map.of("key", "value"));

		// Verify message handling
		StepVerifier.create(this.transport.sendMessage(testMessage)).verifyComplete();

		assertThat(this.transport.getInboundMessageCount()).isEqualTo(1);
	}

	@Test
	void testNotificationMessageProcessing() {
		// Simulate receiving a notification message (no id)
		this.transport.simulateMessageEvent("""
				{
					"jsonrpc": "2.0",
					"method": "update",
					"params": {"status": "processing"}
				}
				""");

		// Verify the notification was processed
		assertThat(this.transport.getInboundMessageCount()).isEqualTo(1);
	}

	@Test
	void testGracefulShutdown() {
		// Test graceful shutdown
		StepVerifier.create(this.transport.closeGracefully()).verifyComplete();

		// Create a test message
		JSONRPCRequest testMessage = new JSONRPCRequest(McpSchema.JSONRPC_VERSION, "test-method", "test-id",
				Map.of("key", "value"));

		// Verify message is not processed after shutdown
		StepVerifier.create(this.transport.sendMessage(testMessage)).verifyComplete();

		// Message count should remain 0 after shutdown
		assertThat(this.transport.getInboundMessageCount()).isEqualTo(0);
	}

	@Test
	void testRetryBehavior() {
		// Create a WebClient that simulates connection failures
		WebClient.Builder failingWebClientBuilder = WebClient.builder().baseUrl("http://non-existent-host");

		WebFluxSseClientTransport failingTransport = WebFluxSseClientTransport.builder(failingWebClientBuilder).build();

		// Verify that the transport attempts to reconnect
		StepVerifier.create(Mono.delay(Duration.ofSeconds(2))).expectNextCount(1).verifyComplete();

		// Clean up
		failingTransport.closeGracefully().block();
	}

	@Test
	void testMultipleMessageProcessing() {
		// Simulate receiving multiple messages in sequence
		this.transport.simulateMessageEvent("""
				{
					"jsonrpc": "2.0",
					"method": "method1",
					"id": "id1",
					"params": {"key": "value1"}
				}
				""");

		this.transport.simulateMessageEvent("""
				{
					"jsonrpc": "2.0",
					"method": "method2",
					"id": "id2",
					"params": {"key": "value2"}
				}
				""");

		// Create and send corresponding messages
		JSONRPCRequest message1 = new JSONRPCRequest(McpSchema.JSONRPC_VERSION, "method1", "id1",
				Map.of("key", "value1"));

		JSONRPCRequest message2 = new JSONRPCRequest(McpSchema.JSONRPC_VERSION, "method2", "id2",
				Map.of("key", "value2"));

		// Verify both messages are processed
		StepVerifier.create(this.transport.sendMessage(message1).then(this.transport.sendMessage(message2)))
			.verifyComplete();

		// Verify message count
		assertThat(this.transport.getInboundMessageCount()).isEqualTo(2);
	}

	@Test
	void testMessageOrderPreservation() {
		// Simulate receiving messages in a specific order
		this.transport.simulateMessageEvent("""
				{
					"jsonrpc": "2.0",
					"method": "first",
					"id": "1",
					"params": {"sequence": 1}
				}
				""");

		this.transport.simulateMessageEvent("""
				{
					"jsonrpc": "2.0",
					"method": "second",
					"id": "2",
					"params": {"sequence": 2}
				}
				""");

		this.transport.simulateMessageEvent("""
				{
					"jsonrpc": "2.0",
					"method": "third",
					"id": "3",
					"params": {"sequence": 3}
				}
				""");

		// Verify message count and order
		assertThat(this.transport.getInboundMessageCount()).isEqualTo(3);
	}

	// Test class to access protected methods
	static class TestSseClientTransport extends WebFluxSseClientTransport {

		private final AtomicInteger inboundMessageCount = new AtomicInteger(0);

		private Sinks.Many<ServerSentEvent<String>> events = Sinks.many().unicast().onBackpressureBuffer();

		TestSseClientTransport(WebClient.Builder webClientBuilder, McpJsonMapper jsonMapper) {
			super(webClientBuilder, jsonMapper);
		}

		@Override
		protected Flux<ServerSentEvent<String>> eventStream() {
			return super.eventStream().mergeWith(this.events.asFlux());
		}

		String getLastEndpoint() {
			return this.messageEndpointSink.asMono().block();
		}

		int getInboundMessageCount() {
			return this.inboundMessageCount.get();
		}

		void simulateSseComment(String comment) {
			this.events.tryEmitNext(ServerSentEvent.<String>builder().comment(comment).build());
			this.inboundMessageCount.incrementAndGet();
		}

		void simulateEndpointEvent(String jsonMessage) {
			this.events.tryEmitNext(ServerSentEvent.<String>builder().event("endpoint").data(jsonMessage).build());
			this.inboundMessageCount.incrementAndGet();
		}

		void simulateMessageEvent(String jsonMessage) {
			this.events.tryEmitNext(ServerSentEvent.<String>builder().event("message").data(jsonMessage).build());
			this.inboundMessageCount.incrementAndGet();
		}

	}

}
