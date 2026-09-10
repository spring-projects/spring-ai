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

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.web.servlet.function.ServerResponse.SseBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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
			.expectBody(String.class)
			.value(body -> assertThat(body).contains("Internal server error. Check server logs for details.")
				.doesNotContain(sensitiveDetail));
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
			.expectBody(String.class)
			.value(body -> assertThat(body).contains("Max number of sessions reached"));
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
			.expectBody(String.class)
			.value(body -> assertThat(body).contains("Session already initialized"));
	}

	@Test
	void sseLifecycleCallbacksCloseStreamOnCompleteTimeoutAndError() {
		SseBuilder sseBuilder = mock(SseBuilder.class);
		AtomicInteger closeCount = new AtomicInteger();

		WebMvcStreamableServerTransportProvider.registerSseLifecycle(sseBuilder, "session-1",
				closeCount::incrementAndGet);

		ArgumentCaptor<Runnable> completeCaptor = ArgumentCaptor.captor();
		verify(sseBuilder).onComplete(completeCaptor.capture());
		completeCaptor.getValue().run();

		ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.captor();
		verify(sseBuilder).onTimeout(timeoutCaptor.capture());
		timeoutCaptor.getValue().run();

		ArgumentCaptor<Consumer<Throwable>> errorCaptor = ArgumentCaptor.captor();
		verify(sseBuilder).onError(errorCaptor.capture());
		errorCaptor.getValue().accept(new RuntimeException("boom"));

		assertThat(closeCount).hasValue(3);
	}

	@Test
	void sseWriteFailureClosesOnlyCurrentTransportWithoutRemovingSession() throws Exception {
		String sessionId = "session-send-failure";
		McpStreamableServerSession session = mock(McpStreamableServerSession.class);
		when(session.getId()).thenReturn(sessionId);
		when(session.delete()).thenReturn(Mono.empty());

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

		SseBuilder sseBuilder = mock(SseBuilder.class);
		when(sseBuilder.id(anyString())).thenReturn(sseBuilder);
		when(sseBuilder.event(anyString())).thenReturn(sseBuilder);
		doThrow(new IOException("broken pipe")).when(sseBuilder).data(any());

		var transport = provider.createSessionTransport(sessionId, sseBuilder);
		McpSchema.JSONRPCMessage message = new McpSchema.JSONRPCNotification("2.0", "server/notification", Map.of());
		transport.sendMessage(message, "message-1").block();

		verify(sseBuilder).complete();

		// A DELETE with the same id still finds the session instead of returning 404,
		// proving the write failure closed only the transport and did not remove the
		// logical MCP session.
		client.delete().uri("/mcp").header(HttpHeaders.MCP_SESSION_ID, sessionId).exchange().expectStatus().isOk();
	}

}
