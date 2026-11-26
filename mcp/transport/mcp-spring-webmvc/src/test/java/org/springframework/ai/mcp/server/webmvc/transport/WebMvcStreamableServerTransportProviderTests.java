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

package org.springframework.ai.mcp.server.webmvc.transport;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WebMvcStreamableServerTransportProvider}.
 *
 * @author Dimitar Proynov
 */
class WebMvcStreamableServerTransportProviderTests {

	private static final String INITIALIZE_REQUEST = """
			{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2024-11-05",\
			"capabilities":{},"clientInfo":{"name":"test-client","version":"1.0.0"}}}""";

	@ParameterizedTest
	@ValueSource(strings = { "{not json", "{}" })
	void malformedRequestReturnsJsonRpcErrorWithoutExceptionDetails(String body) {
		WebMvcStreamableServerTransportProvider provider = WebMvcStreamableServerTransportProvider.builder().build();
		WebTestClient client = MockMvcWebTestClient.bindToRouterFunction(provider.getRouterFunction()).build();

		client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.bodyValue(body)
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody()
			.json("""
					{"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"Invalid message format"}}
					""", JsonCompareMode.STRICT);
	}

	@ParameterizedTest
	@ValueSource(strings = { "42", "\"request-42\"" })
	void missingSessionReturnsJsonRpcErrorWithRequestId(String id) {
		WebMvcStreamableServerTransportProvider provider = WebMvcStreamableServerTransportProvider.builder().build();
		WebTestClient client = MockMvcWebTestClient.bindToRouterFunction(provider.getRouterFunction()).build();

		client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.bodyValue("""
					{"jsonrpc":"2.0","id":%s,"method":"tools/list"}
					""".formatted(id))
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody()
			.json("""
					{"jsonrpc":"2.0","id":%s,"error":{"code":-32601,"message":"Session ID missing"}}
					""".formatted(id), JsonCompareMode.STRICT);
	}

	@Test
	void invalidAcceptReturnsJsonRpcError() {
		WebMvcStreamableServerTransportProvider provider = WebMvcStreamableServerTransportProvider.builder().build();
		WebTestClient client = MockMvcWebTestClient.bindToRouterFunction(provider.getRouterFunction()).build();

		client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.TEXT_EVENT_STREAM)
			.bodyValue(INITIALIZE_REQUEST)
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody()
			.json("""
					{"jsonrpc":"2.0","id":null,"error":{"code":-32601,
					"message":"Invalid Accept headers. Expected TEXT_EVENT_STREAM and APPLICATION_JSON"}}
					""", JsonCompareMode.STRICT);
	}

	@Test
	void unknownSessionReturnsJsonRpcErrorWithRequestId() {
		WebMvcStreamableServerTransportProvider provider = WebMvcStreamableServerTransportProvider.builder().build();
		WebTestClient client = MockMvcWebTestClient.bindToRouterFunction(provider.getRouterFunction()).build();

		client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.header(HttpHeaders.MCP_SESSION_ID, "unknown")
			.bodyValue("""
					{"jsonrpc":"2.0","id":"request-42","method":"tools/list"}
					""")
			.exchange()
			.expectStatus()
			.isNotFound()
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody()
			.json("""
					{"jsonrpc":"2.0","id":"request-42","error":{"code":-32603,"message":"Session not found: unknown"}}
					""", JsonCompareMode.STRICT);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
			"{\"jsonrpc\":\"2.0\",\"id\":42,\"result\":{}}" })
	void rejectedNotificationOrResponseOmitsId(String body) {
		WebMvcStreamableServerTransportProvider provider = WebMvcStreamableServerTransportProvider.builder().build();
		WebTestClient client = MockMvcWebTestClient.bindToRouterFunction(provider.getRouterFunction()).build();

		client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.bodyValue(body)
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody()
			.json("""
					{"jsonrpc":"2.0","error":{"code":-32601,"message":"Session ID missing"}}
					""", JsonCompareMode.STRICT);
	}

	@Test
	void missingSessionFactoryReturnsJsonRpcError() {
		WebMvcStreamableServerTransportProvider provider = WebMvcStreamableServerTransportProvider.builder().build();
		WebTestClient client = MockMvcWebTestClient.bindToRouterFunction(provider.getRouterFunction()).build();

		client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.bodyValue(INITIALIZE_REQUEST)
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody()
			.json("""
					{"jsonrpc":"2.0","id":"1","error":{"code":-32603,"message":"SessionFactory not configured"}}
					""", JsonCompareMode.STRICT);
	}

	@Test
	void synchronousInitializationFailureRetainsRequestId() {
		WebMvcStreamableServerTransportProvider provider = WebMvcStreamableServerTransportProvider.builder().build();
		provider.setSessionFactory(initializeRequest -> {
			throw new IllegalStateException("private initialization detail");
		});
		WebTestClient client = MockMvcWebTestClient.bindToRouterFunction(provider.getRouterFunction()).build();

		client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.bodyValue(INITIALIZE_REQUEST)
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody()
			.json("""
					{"jsonrpc":"2.0","id":"1","error":{"code":-32603,
					"message":"Internal server error. Check server logs for details."}}
					""", JsonCompareMode.STRICT);
	}

	@Test
	void deleteFailureReturnsJsonRpcErrorWithoutExceptionDetails() {
		McpStreamableServerSession session = mock(McpStreamableServerSession.class);
		when(session.getId()).thenReturn("test-session");
		when(session.delete()).thenReturn(Mono.error(new IllegalStateException("private deletion detail")));
		McpSchema.InitializeResult initResult = McpSchema.InitializeResult
			.builder("2024-11-05", McpSchema.ServerCapabilities.builder().build(),
					McpSchema.Implementation.builder("test-server", "1.0.0").build())
			.build();
		WebMvcStreamableServerTransportProvider provider = WebMvcStreamableServerTransportProvider.builder().build();
		provider.setSessionFactory(initializeRequest -> new McpStreamableServerSession.McpStreamableServerSessionInit(
				session, Mono.just(initResult)));
		WebTestClient client = MockMvcWebTestClient.bindToRouterFunction(provider.getRouterFunction()).build();

		client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.bodyValue(INITIALIZE_REQUEST)
			.exchange()
			.expectStatus()
			.isOk();

		client.delete()
			.uri("/mcp")
			.header(HttpHeaders.MCP_SESSION_ID, "test-session")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody()
			.json("""
					{"jsonrpc":"2.0","id":null,"error":{"code":-32603,
					"message":"Internal server error. Check server logs for details."}}
					""", JsonCompareMode.STRICT);
	}

	@Test
	void internalErrorResponseDoesNotLeakExceptionMessageToClient() {
		String sensitiveDetail = "/opt/app/config/prod-secrets.yaml (No such file or directory)";

		WebMvcStreamableServerTransportProvider provider = WebMvcStreamableServerTransportProvider.builder().build();
		provider.setSessionFactory(initializeRequest -> {
			McpStreamableServerSession session = mock(McpStreamableServerSession.class);
			when(session.getId()).thenReturn("test-session");
			// The session initialization fails with an exception whose message embeds
			// sensitive internal detail that must not reach the remote client.
			return new McpStreamableServerSession.McpStreamableServerSessionInit(session,
					Mono.error(new RuntimeException(sensitiveDetail)));
		});

		WebTestClient client = MockMvcWebTestClient.bindToRouterFunction(provider.getRouterFunction()).build();

		client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.bodyValue(INITIALIZE_REQUEST)
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody()
			.json("""
					{"jsonrpc":"2.0","id":"1","error":{"code":-32603,
					"message":"Internal server error. Check server logs for details."}}
					""", JsonCompareMode.STRICT);
	}

	@Test
	void buildRejectsSessionIdleTimeoutNotGreaterThanKeepAliveInterval() {
		assertThatThrownBy(() -> WebMvcStreamableServerTransportProvider.builder()
			.keepAliveInterval(Duration.ofSeconds(2))
			.sessionIdleTimeout(Duration.ofSeconds(1))
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("sessionIdleTimeout must be greater than keepAliveInterval");
	}

	@Test
	void idleSessionIsEvictedAfterTimeout() {
		McpStreamableServerSession session = mock(McpStreamableServerSession.class);
		when(session.getId()).thenReturn("test-session");
		when(session.closeGracefully()).thenReturn(Mono.empty());

		McpSchema.InitializeResult initResult = McpSchema.InitializeResult
			.builder("2024-11-05", McpSchema.ServerCapabilities.builder().build(),
					McpSchema.Implementation.builder("test-server", "1.0.0").build())
			.build();

		WebMvcStreamableServerTransportProvider provider = WebMvcStreamableServerTransportProvider.builder()
			.sessionIdleTimeout(Duration.ofMillis(25))
			.build();
		provider.setSessionFactory(initializeRequest -> new McpStreamableServerSession.McpStreamableServerSessionInit(
				session, Mono.just(initResult)));

		WebTestClient client = MockMvcWebTestClient.bindToRouterFunction(provider.getRouterFunction()).build();

		client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.bodyValue(INITIALIZE_REQUEST)
			.exchange()
			.expectStatus()
			.isOk();

		// No request touches the session while it sits idle, so the scheduler should
		// evict it and close it within a few eviction intervals.
		// 20x compared to sessionIdleTimeout should be safe for CI
		verify(session, timeout(500)).closeGracefully();
	}

	@Test
	void initializeRequestIsRejectedWhenMaxSessionsReached() {
		McpSchema.InitializeResult initResult = McpSchema.InitializeResult
			.builder("2024-11-05", McpSchema.ServerCapabilities.builder().build(),
					McpSchema.Implementation.builder("test-server", "1.0.0").build())
			.build();

		AtomicInteger sessionCounter = new AtomicInteger();
		WebMvcStreamableServerTransportProvider provider = WebMvcStreamableServerTransportProvider.builder()
			.maxSessions(1)
			.build();
		provider.setSessionFactory(initializeRequest -> {
			McpStreamableServerSession session = mock(McpStreamableServerSession.class);
			when(session.getId()).thenReturn("test-session-" + sessionCounter.incrementAndGet());
			return new McpStreamableServerSession.McpStreamableServerSessionInit(session, Mono.just(initResult));
		});

		WebTestClient client = MockMvcWebTestClient.bindToRouterFunction(provider.getRouterFunction()).build();

		// The first initialize request fills the single available session slot.
		client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.bodyValue(INITIALIZE_REQUEST)
			.exchange()
			.expectStatus()
			.isOk();

		// The second initialize request is rejected because maxSessions is reached.
		client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.bodyValue(INITIALIZE_REQUEST)
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody()
			.json("""
					{"jsonrpc":"2.0","id":"1","error":{"code":-32603,"message":"Max number of sessions reached"}}
					""", JsonCompareMode.STRICT);
	}

	@Test
	void initializeRequestIsRejectedWhenSessionIdHeaderIsPresent() {
		McpSchema.InitializeResult initResult = McpSchema.InitializeResult
			.builder("2024-11-05", McpSchema.ServerCapabilities.builder().build(),
					McpSchema.Implementation.builder("test-server", "1.0.0").build())
			.build();

		WebMvcStreamableServerTransportProvider provider = WebMvcStreamableServerTransportProvider.builder().build();
		provider.setSessionFactory(initializeRequest -> {
			McpStreamableServerSession session = mock(McpStreamableServerSession.class);
			when(session.getId()).thenReturn("test-session");
			return new McpStreamableServerSession.McpStreamableServerSessionInit(session, Mono.just(initResult));
		});

		WebTestClient client = MockMvcWebTestClient.bindToRouterFunction(provider.getRouterFunction()).build();

		// An initialize request must not carry a session id yet; the server rejects it
		// rather than silently starting a fresh session.
		client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.header(HttpHeaders.MCP_SESSION_ID, "some-existing-session-id")
			.bodyValue(INITIALIZE_REQUEST)
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.BAD_REQUEST)
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody()
			.json("""
					{"jsonrpc":"2.0","id":"1","error":{"code":-32603,"message":"Session already initialized"}}
					""", JsonCompareMode.STRICT);
	}

}
