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

package org.springframework.ai.mcp.server.webmvc.transport;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.function.ServerResponse.SseBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Server-side implementation of the Model Context Protocol (MCP) transport layer using
 * HTTP with Server-Sent Events (SSE) through Spring WebMVC. This implementation provides
 * a bridge between synchronous WebMVC operations and reactive programming patterns to
 * maintain compatibility with the reactive transport interface.
 *
 * <p>
 * Key features:
 * <ul>
 * <li>Implements bidirectional communication using HTTP POST for client-to-server
 * messages and SSE for server-to-client messages</li>
 * <li>Manages client sessions with unique IDs for reliable message delivery</li>
 * <li>Supports graceful shutdown with proper session cleanup</li>
 * <li>Provides JSON-RPC message handling through configured endpoints</li>
 * <li>Includes built-in error handling and logging</li>
 * </ul>
 *
 * <p>
 * The transport operates on two main endpoints:
 * <ul>
 * <li>{@code /sse} - The SSE endpoint where clients establish their event stream
 * connection</li>
 * <li>A configurable message endpoint where clients send their JSON-RPC messages via HTTP
 * POST</li>
 * </ul>
 *
 * <p>
 * This implementation uses {@link ConcurrentHashMap} to safely manage multiple client
 * sessions in a thread-safe manner. Each client session is assigned a unique ID and
 * maintains its own SSE connection.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @see McpServerTransportProvider
 * @see RouterFunction
 */
public final class WebMvcSseServerTransportProvider implements McpServerTransportProvider {

	private static final Logger logger = LoggerFactory.getLogger(WebMvcSseServerTransportProvider.class);

	/**
	 * Event type for JSON-RPC messages sent through the SSE connection.
	 */
	public static final String MESSAGE_EVENT_TYPE = "message";

	/**
	 * Event type for sending the message endpoint URI to clients.
	 */
	public static final String ENDPOINT_EVENT_TYPE = "endpoint";

	public static final String SESSION_ID = "sessionId";

	/**
	 * Default SSE endpoint path as specified by the MCP transport specification.
	 */
	public static final String DEFAULT_SSE_ENDPOINT = "/sse";

	public static final String DEFAULT_MESSAGE_ENDPOINT = "/mcp/message";

	private final McpJsonMapper jsonMapper;

	private final String messageEndpoint;

	private final String sseEndpoint;

	private final String baseUrl;

	private final RouterFunction<ServerResponse> routerFunction;

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

	private @Nullable KeepAliveScheduler keepAliveScheduler;

	/**
	 * Security validator for validating HTTP requests.
	 */
	private final ServerTransportSecurityValidator securityValidator;

	/**
	 * Constructs a new WebMvcSseServerTransportProvider instance.
	 * @param jsonMapper The McpJsonMapper to use for JSON serialization/deserialization
	 * of messages.
	 * @param baseUrl The base URL for the message endpoint, used to construct the full
	 * endpoint URL for clients.
	 * @param messageEndpoint The endpoint URI where clients should send their JSON-RPC
	 * messages via HTTP POST. This endpoint will be communicated to clients through the
	 * SSE connection's initial endpoint event.
	 * @param sseEndpoint The endpoint URI where clients establish their SSE connections.
	 * @param keepAliveInterval The interval for sending keep-alive messages to clients.
	 * @param contextExtractor The contextExtractor to fill in a
	 * {@link McpTransportContext}.
	 * @param securityValidator The security validator for validating HTTP requests.
	 * @throws IllegalArgumentException if any parameter is null
	 */
	private WebMvcSseServerTransportProvider(McpJsonMapper jsonMapper, String baseUrl, String messageEndpoint,
			String sseEndpoint, @Nullable Duration keepAliveInterval,
			McpTransportContextExtractor<ServerRequest> contextExtractor,
			ServerTransportSecurityValidator securityValidator) {
		Assert.notNull(jsonMapper, "McpJsonMapper must not be null");
		Assert.notNull(baseUrl, "Message base URL must not be null");
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
	 * Broadcasts a notification to all connected clients through their SSE connections.
	 * The message is serialized to JSON and sent as an SSE event with type "message". If
	 * any errors occur during sending to a particular client, they are logged but don't
	 * prevent sending to other clients.
	 * @param method The method name for the notification
	 * @param params The parameters for the notification
	 * @return A Mono that completes when the broadcast attempt is finished
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

	/**
	 * Initiates a graceful shutdown of the transport. This method:
	 * <ul>
	 * <li>Sets the closing flag to prevent new connections</li>
	 * <li>Closes all active SSE connections</li>
	 * <li>Removes all session records</li>
	 * </ul>
	 * @return A Mono that completes when all cleanup operations are finished
	 */
	@Override
	public Mono<Void> closeGracefully() {
		return Flux.fromIterable(this.sessions.values()).doFirst(() -> {
			this.isClosing = true;
			logger.debug("Initiating graceful shutdown with {} active sessions", this.sessions.size());
		}).flatMap(McpServerSession::closeGracefully).then().doOnSuccess(v -> {
			logger.debug("Graceful shutdown completed");
			this.sessions.clear();
			if (this.keepAliveScheduler != null) {
				this.keepAliveScheduler.shutdown();
			}
		});
	}

	/**
	 * Returns the RouterFunction that defines the HTTP endpoints for this transport. The
	 * router function handles two endpoints:
	 * <ul>
	 * <li>GET /sse - For establishing SSE connections</li>
	 * <li>POST [messageEndpoint] - For receiving JSON-RPC messages from clients</li>
	 * </ul>
	 * @return The configured RouterFunction for handling HTTP requests
	 */
	public RouterFunction<ServerResponse> getRouterFunction() {
		return this.routerFunction;
	}

	/**
	 * Handles new SSE connection requests from clients by creating a new session and
	 * establishing an SSE connection. This method:
	 * <ul>
	 * <li>Generates a unique session ID</li>
	 * <li>Creates a new session with a WebMvcMcpSessionTransport</li>
	 * <li>Sends an initial endpoint event to inform the client where to send
	 * messages</li>
	 * <li>Maintains the session in the sessions map</li>
	 * </ul>
	 * @param request The incoming server request
	 * @return A ServerResponse configured for SSE communication, or an error response if
	 * the server is shutting down or the connection fails
	 */
	private ServerResponse handleSseConnection(ServerRequest request) {
		if (this.isClosing) {
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("Server is shutting down");
		}

		try {
			Map<String, List<String>> headers = request.headers().asHttpHeaders().asMultiValueMap();
			this.securityValidator.validateHeaders(headers);
		}
		catch (ServerTransportSecurityException e) {
			var message = e.getMessage() != null ? e.getMessage() : "";
			return ServerResponse.status(e.getStatusCode()).body(message);
		}

		// Send initial endpoint event
		return ServerResponse.sse(sseBuilder -> {
			WebMvcMcpSessionTransport sessionTransport = new WebMvcMcpSessionTransport(sseBuilder);
			var sf = this.sessionFactory;
			if (sf == null) {
				sseBuilder.error(new IllegalStateException("SessionFactory not configured"));
				return;
			}
			McpServerSession session = sf.create(sessionTransport);
			String sessionId = session.getId();
			logger.debug("Creating new SSE connection for session: {}", sessionId);
			sseBuilder.onComplete(() -> {
				logger.debug("SSE connection completed for session: {}", sessionId);
				this.sessions.remove(sessionId);
			});
			sseBuilder.onTimeout(() -> {
				logger.debug("SSE connection timed out for session: {}", sessionId);
				this.sessions.remove(sessionId);
			});
			this.sessions.put(sessionId, session);

			try {
				sseBuilder.event(ENDPOINT_EVENT_TYPE).data(buildEndpointUrl(sessionId));
			}
			catch (Exception e) {
				logger.error("Failed to send initial endpoint event: {}", e.getMessage());
				this.sessions.remove(sessionId);
				sseBuilder.error(e);
			}
		}, Duration.ZERO);
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
	 * Handles incoming JSON-RPC messages from clients. This method:
	 * <ul>
	 * <li>Deserializes the request body into a JSON-RPC message</li>
	 * <li>Processes the message through the session's handle method</li>
	 * <li>Returns appropriate HTTP responses based on the processing result</li>
	 * </ul>
	 * @param request The incoming server request containing the JSON-RPC message
	 * @return A ServerResponse indicating success (200 OK) or appropriate error status
	 * with error details in case of failures
	 */
	private ServerResponse handleMessage(ServerRequest request) {
		if (this.isClosing) {
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("Server is shutting down");
		}

		try {
			Map<String, List<String>> headers = request.headers().asHttpHeaders().asMultiValueMap();
			this.securityValidator.validateHeaders(headers);
		}
		catch (ServerTransportSecurityException e) {
			var message = e.getMessage() != null ? e.getMessage() : "";
			return ServerResponse.status(e.getStatusCode()).body(message);
		}

		if (request.param(SESSION_ID).isEmpty()) {
			return ServerResponse.badRequest()
				.body(new McpError(new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.INVALID_PARAMS,
						"Session ID missing in message endpoint", null)));
		}

		String sessionId = request.param(SESSION_ID).get();
		McpServerSession session = this.sessions.get(sessionId);

		if (session == null) {
			return ServerResponse.status(HttpStatus.NOT_FOUND)
				.body(new McpError(new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.INVALID_PARAMS,
						"Session not found: " + sessionId, null)));
		}

		try {
			final McpTransportContext transportContext = this.contextExtractor.extract(request);

			String body = request.body(String.class);
			McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(this.jsonMapper, body);

			// Process the message through the session's handle method
			session.handle(message).contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext)).block(); // Block
			// for
			// WebMVC
			// compatibility

			return ServerResponse.ok().build();
		}
		catch (IllegalArgumentException | IOException e) {
			logger.error("Failed to deserialize message: {}", e.getMessage());
			return ServerResponse.badRequest()
				.body(new McpError(new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.PARSE_ERROR,
						"Invalid message format", null)));
		}
		catch (Exception e) {
			logger.error("Error handling message: {}", e.getMessage());
			return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new McpError(new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.INTERNAL_ERROR,
						e.getMessage(), null)));
		}
	}

	/**
	 * Creates a new Builder instance for configuring and creating instances of
	 * WebMvcSseServerTransportProvider.
	 * @return A new Builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Implementation of McpServerTransport for WebMVC SSE sessions. This class handles
	 * the transport-level communication for a specific client session.
	 */
	private class WebMvcMcpSessionTransport implements McpServerTransport {

		private final SseBuilder sseBuilder;

		/**
		 * Lock to ensure thread-safe access to the SSE builder when sending messages.
		 * This prevents concurrent modifications that could lead to corrupted SSE events.
		 */
		private final ReentrantLock sseBuilderLock = new ReentrantLock();

		/**
		 * Creates a new session transport with the specified SSE builder.
		 * @param sseBuilder The SSE builder for sending server events to the client
		 */
		WebMvcMcpSessionTransport(SseBuilder sseBuilder) {
			this.sseBuilder = sseBuilder;
		}

		/**
		 * Sends a JSON-RPC message to the client through the SSE connection.
		 * @param message The JSON-RPC message to send
		 * @return A Mono that completes when the message has been sent
		 */
		@Override
		public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
			return Mono.fromRunnable(() -> {
				this.sseBuilderLock.lock();
				try {
					String jsonText = jsonMapper.writeValueAsString(message);
					this.sseBuilder.event(MESSAGE_EVENT_TYPE).data(jsonText);
				}
				catch (Exception e) {
					logger.error("Failed to send message: {}", e.getMessage());
					this.sseBuilder.error(e);
				}
				finally {
					this.sseBuilderLock.unlock();
				}
			});
		}

		/**
		 * Converts data from one type to another using the configured McpJsonMapper.
		 * @param data The source data object to convert
		 * @param typeRef The target type reference
		 * @param <T> The target type
		 * @return The converted object of type T
		 */
		@Override
		public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
			return jsonMapper.convertValue(data, typeRef);
		}

		/**
		 * Initiates a graceful shutdown of the transport.
		 * @return A Mono that completes when the shutdown is complete
		 */
		@Override
		public Mono<Void> closeGracefully() {
			return Mono.fromRunnable(() -> {
				this.sseBuilderLock.lock();
				try {
					this.sseBuilder.complete();
				}
				catch (Exception e) {
					logger.warn("Failed to complete SSE builder: {}", e.getMessage());
				}
				finally {
					this.sseBuilderLock.unlock();
				}
			});
		}

		/**
		 * Closes the transport immediately.
		 */
		@Override
		public void close() {
			this.sseBuilderLock.lock();
			try {
				this.sseBuilder.complete();
			}
			catch (Exception e) {
				logger.warn("Failed to complete SSE builder: {}", e.getMessage());
			}
			finally {
				this.sseBuilderLock.unlock();
			}
		}

	}

	/**
	 * Builder for creating instances of WebMvcSseServerTransportProvider.
	 * <p>
	 * This builder provides a fluent API for configuring and creating instances of
	 * WebMvcSseServerTransportProvider with custom settings.
	 */
	public static class Builder {

		private @Nullable McpJsonMapper jsonMapper;

		private String baseUrl = "";

		private String messageEndpoint = DEFAULT_MESSAGE_ENDPOINT;

		private String sseEndpoint = DEFAULT_SSE_ENDPOINT;

		private @Nullable Duration keepAliveInterval;

		private McpTransportContextExtractor<ServerRequest> contextExtractor = serverRequest -> McpTransportContext.EMPTY;

		private ServerTransportSecurityValidator securityValidator = ServerTransportSecurityValidator.NOOP;

		/**
		 * Sets the JSON object mapper to use for message serialization/deserialization.
		 * @param jsonMapper The object mapper to use
		 * @return This builder instance for method chaining
		 */
		public Builder jsonMapper(McpJsonMapper jsonMapper) {
			Assert.notNull(jsonMapper, "McpJsonMapper must not be null");
			this.jsonMapper = jsonMapper;
			return this;
		}

		/**
		 * Sets the base URL for the server transport.
		 * @param baseUrl The base URL to use
		 * @return This builder instance for method chaining
		 */
		public Builder baseUrl(String baseUrl) {
			Assert.notNull(baseUrl, "Base URL must not be null");
			this.baseUrl = baseUrl;
			return this;
		}

		/**
		 * Sets the endpoint path where clients will send their messages.
		 * @param messageEndpoint The message endpoint path
		 * @return This builder instance for method chaining
		 */
		public Builder messageEndpoint(String messageEndpoint) {
			Assert.hasText(messageEndpoint, "Message endpoint must not be empty");
			this.messageEndpoint = messageEndpoint;
			return this;
		}

		/**
		 * Sets the endpoint path where clients will establish SSE connections.
		 * <p>
		 * If not specified, the default value of {@link #DEFAULT_SSE_ENDPOINT} will be
		 * used.
		 * @param sseEndpoint The SSE endpoint path
		 * @return This builder instance for method chaining
		 */
		public Builder sseEndpoint(String sseEndpoint) {
			Assert.hasText(sseEndpoint, "SSE endpoint must not be empty");
			this.sseEndpoint = sseEndpoint;
			return this;
		}

		/**
		 * Sets the interval for keep-alive pings.
		 * <p>
		 * If not specified, keep-alive pings will be disabled.
		 * @param keepAliveInterval The interval duration for keep-alive pings
		 * @return This builder instance for method chaining
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
		 * Builds a new instance of WebMvcSseServerTransportProvider with the configured
		 * settings.
		 * @return A new WebMvcSseServerTransportProvider instance
		 * @throws IllegalStateException if jsonMapper or messageEndpoint is not set
		 */
		public WebMvcSseServerTransportProvider build() {
			if (this.messageEndpoint == null) {
				throw new IllegalStateException("MessageEndpoint must be set");
			}
			return new WebMvcSseServerTransportProvider(
					this.jsonMapper == null ? McpJsonDefaults.getMapper() : this.jsonMapper, this.baseUrl,
					this.messageEndpoint, this.sseEndpoint, this.keepAliveInterval, this.contextExtractor,
					this.securityValidator);
		}

	}

}
