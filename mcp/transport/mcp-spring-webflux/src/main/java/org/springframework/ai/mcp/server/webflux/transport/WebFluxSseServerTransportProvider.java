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

package org.springframework.ai.mcp.server.webflux.transport;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.spec.ProtocolVersions;
import io.modelcontextprotocol.util.Assert;
import io.modelcontextprotocol.util.KeepAliveScheduler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Server-side implementation of the MCP (Model Context Protocol) HTTP transport using
 * Server-Sent Events (SSE). This implementation provides a bidirectional communication
 * channel between MCP clients and servers using HTTP POST for client-to-server messages
 * and SSE for server-to-client messages.
 *
 * <p>
 * Key features:
 * <ul>
 * <li>Implements the {@link McpServerTransportProvider} interface that allows managing
 * {@link McpServerSession} instances and enabling their communication with the
 * {@link McpServerTransport} abstraction.</li>
 * <li>Uses WebFlux for non-blocking request handling and SSE support</li>
 * <li>Maintains client sessions for reliable message delivery</li>
 * <li>Supports graceful shutdown with session cleanup</li>
 * <li>Thread-safe message broadcasting to multiple clients</li>
 * </ul>
 *
 * <p>
 * The transport sets up two main endpoints:
 * <ul>
 * <li>SSE endpoint (/sse) - For establishing SSE connections with clients</li>
 * <li>Message endpoint (configurable) - For receiving JSON-RPC messages from clients</li>
 * </ul>
 *
 * <p>
 * This implementation is thread-safe and can handle multiple concurrent client
 * connections. It uses {@link ConcurrentHashMap} for session management and Project
 * Reactor's non-blocking APIs for message processing and delivery.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Dariusz Jędrzejczyk
 * @see McpServerTransport
 * @see ServerSentEvent
 */

public final class WebFluxSseServerTransportProvider implements McpServerTransportProvider {

	private static final Logger logger = LoggerFactory.getLogger(WebFluxSseServerTransportProvider.class);

	/**
	 * Event type for JSON-RPC messages sent through the SSE connection.
	 */
	public static final String MESSAGE_EVENT_TYPE = "message";

	/**
	 * Event type for sending the message endpoint URI to clients.
	 */
	public static final String ENDPOINT_EVENT_TYPE = "endpoint";

	/**
	 * Default SSE endpoint path as specified by the MCP transport specification.
	 */
	public static final String DEFAULT_SSE_ENDPOINT = "/sse";

	public static final String DEFAULT_MESSAGE_ENDPOINT = "/mcp/message";

	public static final String SESSION_ID = "sessionId";

	public static final String DEFAULT_BASE_URL = "";

	private final McpJsonMapper jsonMapper;

	/**
	 * Base URL for the message endpoint. This is used to construct the full URL for
	 * clients to send their JSON-RPC messages.
	 */
	private final String baseUrl;

	private final String messageEndpoint;

	private final String sseEndpoint;

	private final RouterFunction<?> routerFunction;

	private McpServerSession.@Nullable Factory sessionFactory;

	/**
	 * Map of active client sessions, keyed by session ID.
	 */
	private final ConcurrentHashMap<String, McpServerSession> sessions = new ConcurrentHashMap<>();

	private McpTransportContextExtractor<ServerRequest> contextExtractor;

	/**
	 * Flag indicating if the transport is shutting down.
	 */
	private volatile boolean isClosing = false;

	/**
	 * Keep-alive scheduler for managing session pings. Activated if keepAliveInterval is
	 * set. Disabled by default.
	 */
	private @Nullable KeepAliveScheduler keepAliveScheduler;

	/**
	 * Security validator for validating HTTP requests.
	 */
	private final ServerTransportSecurityValidator securityValidator;

	/**
	 * Constructs a new WebFlux SSE server transport provider instance.
	 * @param jsonMapper The ObjectMapper to use for JSON serialization/deserialization of
	 * MCP messages. Must not be null.
	 * @param baseUrl webflux message base path
	 * @param messageEndpoint The endpoint URI where clients should send their JSON-RPC
	 * messages. This endpoint will be communicated to clients during SSE connection
	 * setup. Must not be null.
	 * @param sseEndpoint The SSE endpoint path. Must not be null.
	 * @param keepAliveInterval The interval for sending keep-alive pings to clients.
	 * @param contextExtractor The context extractor to use for extracting MCP transport
	 * context from HTTP requests. Must not be null.
	 * @param securityValidator The security validator for validating HTTP requests.
	 * @throws IllegalArgumentException if either parameter is null
	 */
	private WebFluxSseServerTransportProvider(McpJsonMapper jsonMapper, String baseUrl, String messageEndpoint,
			String sseEndpoint, @Nullable Duration keepAliveInterval,
			McpTransportContextExtractor<ServerRequest> contextExtractor,
			ServerTransportSecurityValidator securityValidator) {
		Assert.notNull(jsonMapper, "ObjectMapper must not be null");
		Assert.notNull(baseUrl, "Message base path must not be null");
		Assert.notNull(messageEndpoint, "Message endpoint must not be null");
		Assert.notNull(sseEndpoint, "SSE endpoint must not be null");
		Assert.notNull(contextExtractor, "Context extractor must not be null");
		Assert.notNull(securityValidator, "Security validator must not be null");

		this.jsonMapper = jsonMapper;
		this.baseUrl = baseUrl;
		this.messageEndpoint = messageEndpoint;
		this.sseEndpoint = sseEndpoint;
		this.contextExtractor = contextExtractor;
		this.securityValidator = securityValidator;
		this.routerFunction = RouterFunctions.route()
			.GET(this.sseEndpoint, this::handleSseConnection)
			.POST(this.messageEndpoint, this::handleMessage)
			.build();

		if (keepAliveInterval != null) {

			this.keepAliveScheduler = KeepAliveScheduler
				.builder(() -> (this.isClosing) ? Flux.empty() : Flux.fromIterable(this.sessions.values()))
				.initialDelay(keepAliveInterval)
				.interval(keepAliveInterval)
				.build();

			this.keepAliveScheduler.start();
		}
	}

	@Override
	public List<String> protocolVersions() {
		return List.of(ProtocolVersions.MCP_2024_11_05);
	}

	@Override
	public void setSessionFactory(McpServerSession.Factory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * Broadcasts a JSON-RPC message to all connected clients through their SSE
	 * connections. The message is serialized to JSON and sent as a server-sent event to
	 * each active session.
	 *
	 * <p>
	 * The method:
	 * <ul>
	 * <li>Serializes the message to JSON</li>
	 * <li>Creates a server-sent event with the message data</li>
	 * <li>Attempts to send the event to all active sessions</li>
	 * <li>Tracks and reports any delivery failures</li>
	 * </ul>
	 * @param method The JSON-RPC method to send to clients
	 * @param params The method parameters to send to clients
	 * @return A Mono that completes when the message has been sent to all sessions, or
	 * errors if any session fails to receive the message
	 */
	@Override
	public Mono<Void> notifyClients(String method, Object params) {
		if (this.sessions.isEmpty()) {
			logger.debug("No active sessions to broadcast message to");
			return Mono.empty();
		}

		logger.debug("Attempting to broadcast message to {} active sessions", this.sessions.size());

		return Flux.fromIterable(this.sessions.values())
			.flatMap(session -> session.sendNotification(method, params)
				.doOnError(
						e -> logger.error("Failed to send message to session {}: {}", session.getId(), e.getMessage()))
				.onErrorComplete())
			.then();
	}

	// FIXME: This javadoc makes claims about using isClosing flag but it's not
	// actually
	// doing that.

	/**
	 * Initiates a graceful shutdown of all the sessions. This method ensures all active
	 * sessions are properly closed and cleaned up.
	 * @return A Mono that completes when all sessions have been closed
	 */
	@Override
	public Mono<Void> closeGracefully() {
		return Flux.fromIterable(this.sessions.values())
			.doFirst(() -> logger.debug("Initiating graceful shutdown with {} active sessions", this.sessions.size()))
			.flatMap(McpServerSession::closeGracefully)
			.then()
			.doOnSuccess(v -> {
				logger.debug("Graceful shutdown completed");
				this.sessions.clear();
				if (this.keepAliveScheduler != null) {
					this.keepAliveScheduler.shutdown();
				}
			});
	}

	/**
	 * Returns the WebFlux router function that defines the transport's HTTP endpoints.
	 * This router function should be integrated into the application's web configuration.
	 *
	 * <p>
	 * The router function defines two endpoints:
	 * <ul>
	 * <li>GET {sseEndpoint} - For establishing SSE connections</li>
	 * <li>POST {messageEndpoint} - For receiving client messages</li>
	 * </ul>
	 * @return The configured {@link RouterFunction} for handling HTTP requests
	 */
	public RouterFunction<?> getRouterFunction() {
		return this.routerFunction;
	}

	/**
	 * Handles new SSE connection requests from clients. Creates a new session for each
	 * connection and sets up the SSE event stream.
	 * @param request The incoming server request
	 * @return A Mono which emits a response with the SSE event stream
	 */
	private Mono<ServerResponse> handleSseConnection(ServerRequest request) {
		if (this.isClosing) {
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).bodyValue("Server is shutting down");
		}

		try {
			Map<String, List<String>> headers = request.headers().asHttpHeaders().asMultiValueMap();
			this.securityValidator.validateHeaders(headers);
		}
		catch (ServerTransportSecurityException e) {
			String errorMessage = e.getMessage();
			return ServerResponse.status(e.getStatusCode()).bodyValue(errorMessage != null ? errorMessage : "");
		}

		McpTransportContext transportContext = this.contextExtractor.extract(request);

		return ServerResponse.ok()
			.contentType(MediaType.TEXT_EVENT_STREAM)
			.body(Flux.<ServerSentEvent<?>>create(sink -> {
				WebFluxMcpSessionTransport sessionTransport = new WebFluxMcpSessionTransport(sink);

				McpServerSession session = Objects
					.requireNonNull(this.sessionFactory, "sessionFactory must be set before handling connections")
					.create(sessionTransport);
				String sessionId = session.getId();

				logger.debug("Created new SSE connection for session: {}", sessionId);
				this.sessions.put(sessionId, session);

				// Send initial endpoint event
				logger.debug("Sending initial endpoint event to session: {}", sessionId);
				sink.next(
						ServerSentEvent.builder().event(ENDPOINT_EVENT_TYPE).data(buildEndpointUrl(sessionId)).build());
				sink.onCancel(() -> {
					logger.debug("Session {} cancelled", sessionId);
					this.sessions.remove(sessionId);
				});
			}).contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext)), ServerSentEvent.class);
	}

	/**
	 * Constructs the full message endpoint URL by combining the base URL, message path,
	 * and the required session_id query parameter.
	 * @param sessionId the unique session identifier
	 * @return the fully qualified endpoint URL as a string
	 */
	private String buildEndpointUrl(String sessionId) {
		// for WebMVC compatibility
		return UriComponentsBuilder.fromUriString(this.baseUrl)
			.path(this.messageEndpoint)
			.queryParam(SESSION_ID, sessionId)
			.build()
			.toUriString();
	}

	/**
	 * Handles incoming JSON-RPC messages from clients. Deserializes the message and
	 * processes it through the configured message handler.
	 *
	 * <p>
	 * The handler:
	 * <ul>
	 * <li>Deserializes the incoming JSON-RPC message</li>
	 * <li>Passes it through the message handler chain</li>
	 * <li>Returns appropriate HTTP responses based on processing results</li>
	 * <li>Handles various error conditions with appropriate error responses</li>
	 * </ul>
	 * @param request The incoming server request containing the JSON-RPC message
	 * @return A Mono emitting the response indicating the message processing result
	 */
	private Mono<ServerResponse> handleMessage(ServerRequest request) {
		if (this.isClosing) {
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).bodyValue("Server is shutting down");
		}

		try {
			Map<String, List<String>> headers = request.headers().asHttpHeaders().asMultiValueMap();
			this.securityValidator.validateHeaders(headers);
		}
		catch (ServerTransportSecurityException e) {
			String errorMessage = e.getMessage();
			return ServerResponse.status(e.getStatusCode()).bodyValue(errorMessage != null ? errorMessage : "");
		}

		if (request.queryParam("sessionId").isEmpty()) {
			return ServerResponse.badRequest().bodyValue(new McpError("Session ID missing in message endpoint"));
		}

		McpServerSession session = this.sessions.get(request.queryParam("sessionId").get());

		if (session == null) {
			return ServerResponse.status(HttpStatus.NOT_FOUND)
				.bodyValue(new McpError("Session not found: " + request.queryParam("sessionId").get()));
		}

		McpTransportContext transportContext = this.contextExtractor.extract(request);

		return request.bodyToMono(String.class).flatMap(body -> {
			try {
				McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(this.jsonMapper, body);
				return session.handle(message).flatMap(response -> ServerResponse.ok().build()).onErrorResume(error -> {
					logger.error("Error processing  message: {}", error.getMessage());
					// TODO: instead of signalling the error, just respond with 200 OK
					// - the error is signalled on the SSE connection
					// return ServerResponse.ok().build();
					return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
						.bodyValue(new McpError(error.getMessage()));
				});
			}
			catch (IllegalArgumentException | IOException e) {
				logger.error("Failed to deserialize message: {}", e.getMessage());
				return ServerResponse.badRequest().bodyValue(new McpError("Invalid message format"));
			}
		}).contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext));
	}

	public static Builder builder() {
		return new Builder();
	}

	private class WebFluxMcpSessionTransport implements McpServerTransport {

		private final FluxSink<ServerSentEvent<?>> sink;

		WebFluxMcpSessionTransport(FluxSink<ServerSentEvent<?>> sink) {
			this.sink = sink;
		}

		@Override
		public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
			return Mono.fromSupplier(() -> {
				try {
					return jsonMapper.writeValueAsString(message);
				}
				catch (IOException e) {
					throw Exceptions.propagate(e);
				}
			}).doOnNext(jsonText -> {
				ServerSentEvent<Object> event = ServerSentEvent.builder()
					.event(MESSAGE_EVENT_TYPE)
					.data(jsonText)
					.build();
				this.sink.next(event);
			}).doOnError(e -> {
				// TODO log with sessionid
				Throwable exception = Exceptions.unwrap(e);
				this.sink.error(exception);
			}).then();
		}

		@Override
		public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
			return jsonMapper.convertValue(data, typeRef);
		}

		@Override
		public Mono<Void> closeGracefully() {
			return Mono.fromRunnable(this.sink::complete);
		}

		@Override
		public void close() {
			this.sink.complete();
		}

	}

	/**
	 * Builder for creating instances of {@link WebFluxSseServerTransportProvider}.
	 * <p>
	 * This builder provides a fluent API for configuring and creating instances of
	 * WebFluxSseServerTransportProvider with custom settings.
	 */
	public static class Builder {

		private @Nullable McpJsonMapper jsonMapper;

		private String baseUrl = DEFAULT_BASE_URL;

		private String messageEndpoint = DEFAULT_MESSAGE_ENDPOINT;

		private String sseEndpoint = DEFAULT_SSE_ENDPOINT;

		private @Nullable Duration keepAliveInterval;

		private McpTransportContextExtractor<ServerRequest> contextExtractor = serverRequest -> McpTransportContext.EMPTY;

		private ServerTransportSecurityValidator securityValidator = ServerTransportSecurityValidator.NOOP;

		/**
		 * Sets the McpJsonMapper to use for JSON serialization/deserialization of MCP
		 * messages.
		 * @param jsonMapper The McpJsonMapper instance. Must not be null.
		 * @return this builder instance
		 * @throws IllegalArgumentException if jsonMapper is null
		 */
		public Builder jsonMapper(McpJsonMapper jsonMapper) {
			Assert.notNull(jsonMapper, "JsonMapper must not be null");
			this.jsonMapper = jsonMapper;
			return this;
		}

		/**
		 * Sets the project basePath as endpoint prefix where clients should send their
		 * JSON-RPC messages
		 * @param baseUrl the message basePath . Must not be null.
		 * @return this builder instance
		 * @throws IllegalArgumentException if basePath is null
		 */
		public Builder basePath(String baseUrl) {
			Assert.notNull(baseUrl, "basePath must not be null");
			this.baseUrl = baseUrl;
			return this;
		}

		/**
		 * Sets the endpoint URI where clients should send their JSON-RPC messages.
		 * @param messageEndpoint The message endpoint URI. Must not be null.
		 * @return this builder instance
		 * @throws IllegalArgumentException if messageEndpoint is null
		 */
		public Builder messageEndpoint(String messageEndpoint) {
			Assert.notNull(messageEndpoint, "Message endpoint must not be null");
			this.messageEndpoint = messageEndpoint;
			return this;
		}

		/**
		 * Sets the SSE endpoint path.
		 * @param sseEndpoint The SSE endpoint path. Must not be null.
		 * @return this builder instance
		 * @throws IllegalArgumentException if sseEndpoint is null
		 */
		public Builder sseEndpoint(String sseEndpoint) {
			Assert.notNull(sseEndpoint, "SSE endpoint must not be null");
			this.sseEndpoint = sseEndpoint;
			return this;
		}

		/**
		 * Sets the interval for sending keep-alive pings to clients.
		 * @param keepAliveInterval The keep-alive interval duration. If null, keep-alive
		 * is disabled.
		 * @return this builder instance
		 */
		public Builder keepAliveInterval(@Nullable Duration keepAliveInterval) {
			this.keepAliveInterval = keepAliveInterval;
			return this;
		}

		/**
		 * Sets the context extractor that allows providing the MCP feature
		 * implementations to inspect HTTP transport level metadata that was present at
		 * HTTP request processing time. This allows to extract custom headers and other
		 * useful data for use during execution later on in the process.
		 * @param contextExtractor The contextExtractor to fill in a
		 * {@link McpTransportContext}.
		 * @return this builder instance
		 * @throws IllegalArgumentException if contextExtractor is null
		 */
		public Builder contextExtractor(McpTransportContextExtractor<ServerRequest> contextExtractor) {
			Assert.notNull(contextExtractor, "contextExtractor must not be null");
			this.contextExtractor = contextExtractor;
			return this;
		}

		/**
		 * Sets the security validator for validating HTTP requests.
		 * @param securityValidator The security validator to use. Must not be null.
		 * @return this builder instance
		 * @throws IllegalArgumentException if securityValidator is null
		 */
		public Builder securityValidator(ServerTransportSecurityValidator securityValidator) {
			Assert.notNull(securityValidator, "Security validator must not be null");
			this.securityValidator = securityValidator;
			return this;
		}

		/**
		 * Builds a new instance of {@link WebFluxSseServerTransportProvider} with the
		 * configured settings.
		 * @return A new WebFluxSseServerTransportProvider instance
		 * @throws IllegalStateException if required parameters are not set
		 */
		public WebFluxSseServerTransportProvider build() {
			return new WebFluxSseServerTransportProvider(
					this.jsonMapper == null ? McpJsonDefaults.getMapper() : this.jsonMapper, this.baseUrl,
					this.messageEndpoint, this.sseEndpoint, this.keepAliveInterval, this.contextExtractor,
					this.securityValidator);
		}

	}

}
