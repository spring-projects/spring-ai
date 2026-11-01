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
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerTransport;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.ProtocolVersions;
import io.modelcontextprotocol.util.Assert;
import io.modelcontextprotocol.util.KeepAliveScheduler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.function.ServerResponse.SseBuilder;

/**
 * Server-side implementation of the Model Context Protocol (MCP) streamable transport
 * layer using HTTP with Server-Sent Events (SSE) through Spring WebMVC. This
 * implementation provides a bridge between synchronous WebMVC operations and reactive
 * programming patterns to maintain compatibility with the reactive transport interface.
 *
 * <p>
 * This is the non-reactive version of
 * {@link io.modelcontextprotocol.server.transport.WebFluxStreamableServerTransportProvider}
 *
 * @author Christian Tzolov
 * @author Dariusz Jędrzejczyk
 * @author Dimitar Proynov
 * @see McpStreamableServerTransportProvider
 * @see RouterFunction
 */
public final class WebMvcStreamableServerTransportProvider implements McpStreamableServerTransportProvider {

	private static final Log logger = LogFactory.getLog(WebMvcStreamableServerTransportProvider.class);

	/**
	 * Event type for JSON-RPC messages sent through the SSE connection.
	 */
	public static final String MESSAGE_EVENT_TYPE = "message";

	/**
	 * Event type for sending the message endpoint URI to clients.
	 */
	public static final String ENDPOINT_EVENT_TYPE = "endpoint";

	/**
	 * Default base URL for the message endpoint.
	 */
	public static final String DEFAULT_BASE_URL = "";

	/**
	 * The endpoint URI where clients should send their JSON-RPC messages. Defaults to
	 * "/mcp".
	 */
	private final String mcpEndpoint;

	/**
	 * Flag indicating whether DELETE requests are disallowed on the endpoint.
	 */
	private final boolean disallowDelete;

	private final McpJsonMapper jsonMapper;

	private final RouterFunction<ServerResponse> routerFunction;

	private McpStreamableServerSession.@Nullable Factory sessionFactory;

	/**
	 * Map of active client sessions, keyed by mcp-session-id.
	 */
	private final ConcurrentHashMap<String, McpStreamableServerSession> sessions = new ConcurrentHashMap<>();

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
	 * Max number of session this provider supports before discarding new initialization
	 * requests.
	 */
	private final long maxSessions;

	/**
	 * Duration of inactivity after which an idle session is evicted. If {@code null},
	 * idle sessions are never evicted.
	 */
	private final @Nullable Duration sessionIdleTimeout;

	/**
	 * Tracks the last access time per session id, used to evict idle sessions.
	 */
	private final ConcurrentHashMap<String, Instant> sessionLastAccessTimes = new ConcurrentHashMap<>();

	private @Nullable Disposable idleSessionScheduler;

	/**
	 * Constructs a new WebMvcStreamableServerTransportProvider instance.
	 * @param jsonMapper The McpJsonMapper to use for JSON serialization/deserialization
	 * of messages.
	 * @param mcpEndpoint The endpoint URI where clients should send their JSON-RPC
	 * messages via HTTP. This endpoint will handle GET, POST, and DELETE requests.
	 * @param disallowDelete Whether to disallow DELETE requests on the endpoint.
	 * @param contextExtractor The context extractor for transport context from the
	 * request.
	 * @param keepAliveInterval The interval for keep-alive pings. If null, no keep-alive
	 * will be scheduled.
	 * @param securityValidator The security validator for validating HTTP requests.
	 * @param maxSessions The maximum number of concurrent sessions supported.
	 * @param sessionIdleTimeout The idle timeout after which sessions with no client
	 * activity are evicted. If null, idle sessions are never evicted.
	 * @throws IllegalArgumentException if any parameter is null
	 */
	private WebMvcStreamableServerTransportProvider(McpJsonMapper jsonMapper, String mcpEndpoint,
			boolean disallowDelete, McpTransportContextExtractor<ServerRequest> contextExtractor,
			@Nullable Duration keepAliveInterval, @Nullable Duration sessionIdleTimeout,
			ServerTransportSecurityValidator securityValidator, long maxSessions) {
		Assert.notNull(jsonMapper, "McpJsonMapper must not be null");
		Assert.notNull(mcpEndpoint, "MCP endpoint must not be null");
		Assert.notNull(contextExtractor, "McpTransportContextExtractor must not be null");
		Assert.notNull(securityValidator, "Security validator must not be null");

		this.jsonMapper = jsonMapper;
		this.mcpEndpoint = mcpEndpoint;
		this.disallowDelete = disallowDelete;
		this.contextExtractor = contextExtractor;
		this.securityValidator = securityValidator;
		this.maxSessions = maxSessions;
		this.sessionIdleTimeout = sessionIdleTimeout;
		this.routerFunction = RouterFunctions.route()
			.GET(this.mcpEndpoint, this::handleGet)
			.POST(this.mcpEndpoint, this::handlePost)
			.DELETE(this.mcpEndpoint, this::handleDelete)
			.build();

		if (keepAliveInterval != null) {
			this.keepAliveScheduler = KeepAliveScheduler
				.builder(() -> (this.isClosing) ? Flux.empty() : Flux.fromIterable(this.sessions.values()))
				.initialDelay(keepAliveInterval)
				.interval(keepAliveInterval)
				.build();

			this.keepAliveScheduler.start();
		}

		if (sessionIdleTimeout != null) {
			this.idleSessionScheduler = Flux.interval(sessionIdleTimeout, sessionIdleTimeout)
				.subscribe(tick -> this.evictIdleSessions());
		}
	}

	@Override
	public List<String> protocolVersions() {
		return List.of(ProtocolVersions.MCP_2024_11_05, ProtocolVersions.MCP_2025_03_26,
				ProtocolVersions.MCP_2025_06_18, ProtocolVersions.MCP_2025_11_25);
	}

	@Override
	public void setSessionFactory(McpStreamableServerSession.Factory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * Broadcasts a notification to all connected clients through their SSE connections.
	 * If any errors occur during sending to a particular client, they are logged but
	 * don't prevent sending to other clients.
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

		if (logger.isDebugEnabled()) {
			logger.debug("Attempting to broadcast message to " + this.sessions.size() + " active sessions");
		}

		return Mono.fromRunnable(() -> {
			this.sessions.values().parallelStream().forEach(session -> {
				try {
					session.sendNotification(method, params).block();
				}
				catch (Exception e) {
					if (logger.isErrorEnabled()) {
						logger.error("Failed to send message to session " + session.getId() + ": " + e.getMessage());
					}
				}
			});
		});
	}

	@Override
	public Mono<Void> notifyClient(String sessionId, String method, Object params) {
		return Mono.defer(() -> {
			McpStreamableServerSession session = this.sessions.get(sessionId);
			if (session == null) {
				if (logger.isDebugEnabled()) {
					logger.debug("Session " + sessionId + " not found");
				}
				return Mono.empty();
			}
			return session.sendNotification(method, params);
		});
	}

	/**
	 * Initiates a graceful shutdown of the transport.
	 * @return A Mono that completes when all cleanup operations are finished
	 */
	@Override
	public Mono<Void> closeGracefully() {
		return Mono.fromRunnable(() -> {
			this.isClosing = true;
			if (logger.isDebugEnabled()) {
				logger.debug("Initiating graceful shutdown with " + this.sessions.size() + " active sessions");
			}

			this.sessions.values().parallelStream().forEach(session -> {
				try {
					session.closeGracefully().block();
				}
				catch (Exception e) {
					if (logger.isErrorEnabled()) {
						logger.error("Failed to close session " + session.getId() + ": " + e.getMessage());
					}
				}
			});

			this.sessions.clear();
			this.sessionLastAccessTimes.clear();
			logger.debug("Graceful shutdown completed");
		}).then().doOnSuccess(v -> {
			if (this.keepAliveScheduler != null) {
				this.keepAliveScheduler.shutdown();
			}
			if (this.idleSessionScheduler != null) {
				this.idleSessionScheduler.dispose();
			}
		});
	}

	/**
	 * Returns the RouterFunction that defines the HTTP endpoints for this transport. The
	 * router function handles three endpoints:
	 * <ul>
	 * <li>GET [mcpEndpoint] - For establishing SSE connections and message replay</li>
	 * <li>POST [mcpEndpoint] - For receiving JSON-RPC messages from clients</li>
	 * <li>DELETE [mcpEndpoint] - For session deletion (if enabled)</li>
	 * </ul>
	 * @return The configured RouterFunction for handling HTTP requests
	 */
	public RouterFunction<ServerResponse> getRouterFunction() {
		return this.routerFunction;
	}

	/**
	 * Setup the listening SSE connections and message replay.
	 * @param request The incoming server request
	 * @return A ServerResponse configured for SSE communication, or an error response
	 */
	private ServerResponse handleGet(ServerRequest request) {
		if (this.isClosing) {
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("Server is shutting down");
		}

		try {
			var headers = HeaderUtils.collectHeaders(request);
			this.securityValidator.validateHeaders(headers);
		}
		catch (ServerTransportSecurityException e) {
			var message = e.getMessage() != null ? e.getMessage() : "";
			return ServerResponse.status(e.getStatusCode()).body(message);
		}

		List<MediaType> acceptHeaders = request.headers().asHttpHeaders().getAccept();
		if (!acceptHeaders.contains(MediaType.TEXT_EVENT_STREAM)) {
			return ServerResponse.badRequest().body("Invalid Accept header. Expected TEXT_EVENT_STREAM");
		}

		McpTransportContext transportContext = this.contextExtractor.extract(request);

		if (!hasMcpSessionIdHeader(request)) {
			return ServerResponse.badRequest().body("Session ID required in mcp-session-id header");
		}

		String sessionId = request.headers().header(HttpHeaders.MCP_SESSION_ID).get(0);
		McpStreamableServerSession session = this.sessions.get(sessionId);

		if (session == null || sessionId == null) {
			return ServerResponse.notFound().build();
		}

		touchSession(sessionId);

		if (logger.isDebugEnabled()) {
			logger.debug("Handling GET request for session: " + sessionId);
		}

		try {
			return ServerResponse.sse(sseBuilder -> {
				sseBuilder.onTimeout(() -> {
					if (logger.isDebugEnabled()) {
						logger.debug("SSE connection timed out for session: " + sessionId);
					}
				});

				WebMvcStreamableMcpSessionTransport sessionTransport = new WebMvcStreamableMcpSessionTransport(
						sessionId, sseBuilder);

				// Check if this is a replay request
				if (!request.headers().header(HttpHeaders.LAST_EVENT_ID).isEmpty()) {
					String lastId = request.headers().asHttpHeaders().getFirst(HttpHeaders.LAST_EVENT_ID);

					try {
						session.replay(lastId)
							.contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext))
							.toIterable()
							.forEach(message -> {
								try {
									sessionTransport.sendMessage(message)
										.contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext))
										.block();
								}
								catch (Exception e) {
									if (logger.isErrorEnabled()) {
										logger.error("Failed to replay message: " + e.getMessage());
									}
									sseBuilder.error(e);
								}
							});
					}
					catch (Exception e) {
						if (logger.isErrorEnabled()) {
							logger.error("Failed to replay messages: " + e.getMessage());
						}
						sseBuilder.error(e);
					}
				}
				else {
					// Establish new listening stream
					McpStreamableServerSession.McpStreamableServerSessionStream listeningStream = session
						.listeningStream(sessionTransport);

					sseBuilder.onComplete(() -> {
						if (logger.isDebugEnabled()) {
							logger.debug("SSE connection completed for session: " + sessionId);
						}
						listeningStream.close();
					});
				}
			}, Duration.ZERO);
		}
		catch (Exception e) {
			if (logger.isErrorEnabled()) {
				logger.error("Failed to handle GET request for session " + sessionId + ": " + e.getMessage());
			}
			return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Handles POST requests for incoming JSON-RPC messages from clients.
	 * @param request The incoming server request containing the JSON-RPC message
	 * @return A ServerResponse indicating success or appropriate error status
	 */
	private ServerResponse handlePost(ServerRequest request) {
		if (this.isClosing) {
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("Server is shutting down");
		}

		try {
			var headers = HeaderUtils.collectHeaders(request);
			this.securityValidator.validateHeaders(headers);
		}
		catch (ServerTransportSecurityException e) {
			var message = e.getMessage() != null ? e.getMessage() : "";
			return ServerResponse.status(e.getStatusCode()).body(message);
		}

		List<MediaType> acceptHeaders = request.headers().asHttpHeaders().getAccept();
		if (!acceptHeaders.contains(MediaType.TEXT_EVENT_STREAM)
				|| !acceptHeaders.contains(MediaType.APPLICATION_JSON)) {
			return ServerResponse.badRequest()
				.body(McpError.builder(McpSchema.ErrorCodes.METHOD_NOT_FOUND)
					.message("Invalid Accept headers. Expected TEXT_EVENT_STREAM and APPLICATION_JSON")
					.build());
		}

		McpTransportContext transportContext = this.contextExtractor.extract(request);

		try {
			String body = request.body(String.class);
			McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(this.jsonMapper, body);

			// Handle initialization request
			if (message instanceof McpSchema.JSONRPCRequest jsonrpcRequest
					&& jsonrpcRequest.method().equals(McpSchema.METHOD_INITIALIZE)) {
				return handleInitRequest(jsonrpcRequest, hasMcpSessionIdHeader(request));
			}

			// Handle other messages that require a session
			if (!hasMcpSessionIdHeader(request)) {
				return ServerResponse.badRequest()
					.body(McpError.builder(McpSchema.ErrorCodes.METHOD_NOT_FOUND)
						.message("Session ID missing")
						.build());
			}

			String sessionId = request.headers().header(HttpHeaders.MCP_SESSION_ID).get(0);
			McpStreamableServerSession session = this.sessions.get(sessionId);

			if (session == null || sessionId == null) {
				return ServerResponse.status(HttpStatus.NOT_FOUND)
					.body(McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
						.message("Session not found: " + sessionId)
						.build());
			}

			touchSession(sessionId);

			if (message instanceof McpSchema.JSONRPCResponse jsonrpcResponse) {
				session.accept(jsonrpcResponse)
					.contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext))
					.block();
				return ServerResponse.accepted().build();
			}
			else if (message instanceof McpSchema.JSONRPCNotification jsonrpcNotification) {
				session.accept(jsonrpcNotification)
					.contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext))
					.block();
				return ServerResponse.accepted().build();
			}
			else if (message instanceof McpSchema.JSONRPCRequest jsonrpcRequest) {
				// For streaming responses, we need to return SSE
				return ServerResponse.sse(sseBuilder -> {
					sseBuilder.onComplete(() -> {
						if (logger.isDebugEnabled()) {
							logger.debug("Request response stream completed for session: " + sessionId);
						}
					});
					sseBuilder.onTimeout(() -> {
						if (logger.isDebugEnabled()) {
							logger.debug("Request response stream timed out for session: " + sessionId);
						}
					});

					WebMvcStreamableMcpSessionTransport sessionTransport = new WebMvcStreamableMcpSessionTransport(
							sessionId, sseBuilder);

					try {
						session.responseStream(jsonrpcRequest, sessionTransport)
							.contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext))
							.block();
					}
					catch (Exception e) {
						if (logger.isErrorEnabled()) {
							logger.error("Failed to handle request stream: " + e.getMessage());
						}
						sseBuilder.error(e);
					}
				}, Duration.ZERO);
			}
			else {
				return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
						.message("Unknown message type")
						.build());
			}
		}
		catch (IllegalArgumentException | IOException e) {
			if (logger.isErrorEnabled()) {
				logger.error("Failed to deserialize message: " + e.getMessage());
			}
			return ServerResponse.badRequest()
				.body(McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST).message("Invalid message format").build());
		}
		catch (Exception e) {
			if (logger.isErrorEnabled()) {
				logger.error("Error handling message: " + e.getMessage());
			}
			return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
					.message("Internal server error. Check server logs for details.")
					.build());
		}
	}

	/**
	 * Handles DELETE requests for session deletion.
	 * @param request The incoming server request
	 * @return A ServerResponse indicating success or appropriate error status
	 */
	private ServerResponse handleDelete(ServerRequest request) {
		if (this.isClosing) {
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("Server is shutting down");
		}

		try {
			var headers = HeaderUtils.collectHeaders(request);
			this.securityValidator.validateHeaders(headers);
		}
		catch (ServerTransportSecurityException e) {
			var message = e.getMessage() != null ? e.getMessage() : "";
			return ServerResponse.status(e.getStatusCode()).body(message);
		}

		if (this.disallowDelete) {
			return ServerResponse.status(HttpStatus.METHOD_NOT_ALLOWED).build();
		}

		McpTransportContext transportContext = this.contextExtractor.extract(request);

		if (!hasMcpSessionIdHeader(request)) {
			return ServerResponse.badRequest().body("Session ID required in mcp-session-id header");
		}

		String sessionId = request.headers().asHttpHeaders().getFirst(HttpHeaders.MCP_SESSION_ID);
		McpStreamableServerSession session = this.sessions.get(sessionId);

		if (session == null) {
			return ServerResponse.notFound().build();
		}

		try {
			session.delete().contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext)).block();
			if (sessionId != null) {
				removeSession(sessionId);
			}
			return ServerResponse.ok().build();
		}
		catch (Exception e) {
			if (logger.isErrorEnabled()) {
				logger.error("Failed to delete session " + sessionId + ": " + e.getMessage());
			}
			return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
					.message("Internal server error. Check server logs for details.")
					.build());
		}
	}

	private ServerResponse handleInitRequest(McpSchema.JSONRPCRequest jsonrpcRequest, boolean hasMcpSessionIdHeader) {
		McpSchema.InitializeRequest initializeRequest = this.jsonMapper.convertValue(jsonrpcRequest.params(),
				new TypeRef<McpSchema.InitializeRequest>() {
				});
		var sf = this.sessionFactory;
		if (sf == null) {
			return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
					.message("SessionFactory not configured")
					.build());
		}
		if (hasMcpSessionIdHeader) {
			return ServerResponse.status(HttpStatus.BAD_REQUEST)
				.body(McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
					.message("Session already initialized")
					.build());
		}
		if (this.sessions.size() >= this.maxSessions) {
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
					.message("Max number of sessions reached")
					.build());
		}
		McpStreamableServerSession.McpStreamableServerSessionInit init = sf.startSession(initializeRequest);
		this.sessions.put(init.session().getId(), init.session());
		if (this.sessionIdleTimeout != null) {
			this.sessionLastAccessTimes.put(init.session().getId(), Instant.now());
		}

		try {
			McpSchema.InitializeResult initResult = init.initResult().block();

			return ServerResponse.ok()
				.contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.MCP_SESSION_ID, init.session().getId())
				.body(new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, jsonrpcRequest.id(), initResult, null));
		}
		catch (Exception e) {
			if (logger.isErrorEnabled()) {
				logger.error("Failed to initialize session: " + e.getMessage());
			}
			return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
					.message("Internal server error. Check server logs for details.")
					.build());
		}
	}

	/**
	 * Records the current time as the last access time for the given session, so that an
	 * active session is not evicted as idle. No-op when idle eviction is disabled or the
	 * session is no longer tracked.
	 * @param sessionId the id of the session that was just accessed
	 */
	private void touchSession(String sessionId) {
		if (this.sessionIdleTimeout != null) {
			this.sessionLastAccessTimes.computeIfPresent(sessionId, (id, lastAccess) -> Instant.now());
		}
	}

	/**
	 * Evicts and closes sessions that have been idle for longer than the configured
	 * {@code sessionIdleTimeout}. This reclaims session slots held by clients that
	 * initialized a session but never interacted with it again, mitigating resource
	 * exhaustion from abandoned sessions.
	 */
	private void evictIdleSessions() {
		if (this.sessionIdleTimeout == null || this.isClosing) {
			return;
		}
		Instant now = Instant.now();
		for (var entry : this.sessionLastAccessTimes.entrySet()) {
			if (Duration.between(entry.getValue(), now).compareTo(this.sessionIdleTimeout) > 0) {
				String sessionId = entry.getKey();
				McpStreamableServerSession session = removeSession(sessionId);
				if (logger.isDebugEnabled()) {
					logger.debug("Evicting idle session: " + sessionId);
				}
				session.closeGracefully().onErrorComplete().subscribe();
			}
		}
	}

	/**
	 * Checks whether the given request carries an {@code Mcp-Session-Id} header.
	 * @param request the incoming server request
	 * @return {@code true} if the header is present, regardless of its value
	 */
	private static boolean hasMcpSessionIdHeader(ServerRequest request) {
		return !request.headers().header(HttpHeaders.MCP_SESSION_ID).isEmpty();
	}

	private McpStreamableServerSession removeSession(String sessionId) {
		this.sessionLastAccessTimes.remove(sessionId);
		return this.sessions.remove(sessionId);
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Implementation of McpStreamableServerTransport for WebMVC SSE sessions. This class
	 * handles the transport-level communication for a specific client session.
	 *
	 * <p>
	 * This class is thread-safe and uses a ReentrantLock to synchronize access to the
	 * underlying SSE builder to prevent race conditions when multiple threads attempt to
	 * send messages concurrently.
	 */
	private class WebMvcStreamableMcpSessionTransport implements McpStreamableServerTransport {

		private final String sessionId;

		private final SseBuilder sseBuilder;

		private final ReentrantLock lock = new ReentrantLock();

		private volatile boolean closed = false;

		/**
		 * Creates a new session transport with the specified ID and SSE builder.
		 * @param sessionId The unique identifier for this session
		 * @param sseBuilder The SSE builder for sending server events to the client
		 */
		WebMvcStreamableMcpSessionTransport(String sessionId, SseBuilder sseBuilder) {
			this.sessionId = sessionId;
			this.sseBuilder = sseBuilder;
			if (logger.isDebugEnabled()) {
				logger.debug("Streamable session transport " + sessionId + " initialized with SSE builder");
			}
		}

		/**
		 * Sends a JSON-RPC message to the client through the SSE connection.
		 * @param message The JSON-RPC message to send
		 * @return A Mono that completes when the message has been sent
		 */
		@Override
		public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
			return sendMessage(message, null);
		}

		/**
		 * Sends a JSON-RPC message to the client through the SSE connection with a
		 * specific message ID.
		 * @param message The JSON-RPC message to send
		 * @param messageId The message ID for SSE event identification
		 * @return A Mono that completes when the message has been sent
		 */
		@Override
		public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message, @Nullable String messageId) {
			return Mono.fromRunnable(() -> {
				if (this.closed) {
					if (logger.isDebugEnabled()) {
						logger.debug("Attempted to send message to closed session: " + this.sessionId);
					}
					return;
				}

				this.lock.lock();
				try {
					if (this.closed) {
						if (logger.isDebugEnabled()) {
							logger.debug("Session " + this.sessionId + " was closed during message send attempt");
						}
						return;
					}

					String jsonText = jsonMapper.writeValueAsString(message);
					this.sseBuilder.id(messageId != null ? messageId : this.sessionId)
						.event(MESSAGE_EVENT_TYPE)
						.data(jsonText);
					if (logger.isDebugEnabled()) {
						logger.debug("Message sent to session " + this.sessionId + " with ID " + messageId);
					}
				}
				catch (Exception e) {
					if (logger.isErrorEnabled()) {
						logger.error("Failed to send message to session " + this.sessionId + ": " + e.getMessage());
					}
					try {
						this.sseBuilder.error(e);
					}
					catch (Exception errorException) {
						if (logger.isErrorEnabled()) {
							logger.error("Failed to send error to SSE builder for session " + this.sessionId + ": "
									+ errorException.getMessage());
						}
					}
				}
				finally {
					this.lock.unlock();
				}
			});
		}

		/**
		 * Converts data from one type to another using the configured McpJsonMapper.
		 * @param data The source data object to convert
		 * @param typeRef The target type reference
		 * @return The converted object of type T
		 * @param <T> The target type
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
			return Mono.fromRunnable(() -> WebMvcStreamableMcpSessionTransport.this.close());
		}

		/**
		 * Closes the transport immediately.
		 */
		@Override
		public void close() {
			this.lock.lock();
			try {
				if (this.closed) {
					if (logger.isDebugEnabled()) {
						logger.debug("Session transport " + this.sessionId + " already closed");
					}
					return;
				}

				this.closed = true;

				this.sseBuilder.complete();
				if (logger.isDebugEnabled()) {
					logger.debug("Successfully completed SSE builder for session " + this.sessionId);
				}
			}
			catch (Exception e) {
				if (logger.isWarnEnabled()) {
					logger.warn("Failed to complete SSE builder for session " + this.sessionId + ": " + e.getMessage());
				}
			}
			finally {
				this.lock.unlock();
			}
		}

	}

	/**
	 * Builder for creating instances of {@link WebMvcStreamableServerTransportProvider}.
	 */
	public static class Builder {

		private @Nullable McpJsonMapper jsonMapper;

		private String mcpEndpoint = "/mcp";

		private boolean disallowDelete = false;

		private McpTransportContextExtractor<ServerRequest> contextExtractor = serverRequest -> McpTransportContext.EMPTY;

		private @Nullable Duration keepAliveInterval;

		private ServerTransportSecurityValidator securityValidator = ServerTransportSecurityValidator.NOOP;

		private long maxSessions = 100_000L;

		private @Nullable Duration sessionIdleTimeout;

		/**
		 * Sets the McpJsonMapper to use for JSON serialization/deserialization of MCP
		 * messages.
		 * @param jsonMapper The McpJsonMapper instance. Must not be null.
		 * @return this builder instance
		 * @throws IllegalArgumentException if jsonMapper is null
		 */
		public Builder jsonMapper(McpJsonMapper jsonMapper) {
			Assert.notNull(jsonMapper, "McpJsonMapper must not be null");
			this.jsonMapper = jsonMapper;
			return this;
		}

		/**
		 * Sets the endpoint URI where clients should send their JSON-RPC messages.
		 * @param mcpEndpoint The MCP endpoint URI. Must not be null.
		 * @return this builder instance
		 * @throws IllegalArgumentException if mcpEndpoint is null
		 */
		public Builder mcpEndpoint(String mcpEndpoint) {
			Assert.notNull(mcpEndpoint, "MCP endpoint must not be null");
			this.mcpEndpoint = mcpEndpoint;
			return this;
		}

		/**
		 * Sets whether to disallow DELETE requests on the endpoint.
		 * @param disallowDelete true to disallow DELETE requests, false otherwise
		 * @return this builder instance
		 */
		public Builder disallowDelete(boolean disallowDelete) {
			this.disallowDelete = disallowDelete;
			return this;
		}

		/**
		 * Sets the context extractor that allows providing the MCP feature
		 * implementations to inspect HTTP transport level metadata that was present at
		 * HTTP request processing time. This allows extracting custom headers and other
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
		 * Sets the keep-alive interval for the transport. If set, a keep-alive scheduler
		 * will be created to periodically check and send keep-alive messages to clients.
		 * @param keepAliveInterval The interval duration for keep-alive messages, or null
		 * to disable keep-alive
		 * @return this builder instance
		 */
		public Builder keepAliveInterval(@Nullable Duration keepAliveInterval) {
			this.keepAliveInterval = keepAliveInterval;
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
		 * Sets the maximum supported sessions for this provider. If not set defaults to
		 * 100_000.
		 * @param maxSessions - maximum allowed sessions for this provider
		 * @return this builder instance
		 * @throws IllegalArgumentException if maximum sessions is not positive number
		 */
		public Builder maxSessions(long maxSessions) {
			Assert.isTrue(maxSessions >= 0, "Max sessions must be greater than 0");
			this.maxSessions = maxSessions;
			return this;
		}

		/**
		 * Sets the idle timeout after which sessions with no client activity are evicted
		 * and closed. Activity is any inbound request (GET, POST or DELETE) resolving to
		 * the session. This reclaims session slots held by clients that initialized a
		 * session but never interacted with it again, mitigating resource exhaustion from
		 * abandoned sessions.
		 * <p>
		 * Keep-alive and idle eviction are complementary and may be enabled together:
		 * keep-alive pings preserve connected clients (a ping response counts as
		 * activity), while idle eviction reclaims abandoned sessions. When both are set,
		 * {@code sessionIdleTimeout} must be greater than the keep-alive interval so that
		 * a connected client answering pings is not evicted before it can respond;
		 * otherwise {@link #build()} throws.
		 * @param sessionIdleTimeout the maximum idle duration before eviction, or null to
		 * disable idle eviction (the default)
		 * @return this builder instance
		 */
		public Builder sessionIdleTimeout(@Nullable Duration sessionIdleTimeout) {
			this.sessionIdleTimeout = sessionIdleTimeout;
			return this;
		}

		/**
		 * Builds a new instance of {@link WebMvcStreamableServerTransportProvider} with
		 * the configured settings.
		 * @return A new WebMvcStreamableServerTransportProvider instance
		 * @throws IllegalStateException if required parameters are not set
		 */
		public WebMvcStreamableServerTransportProvider build() {
			Assert.notNull(this.mcpEndpoint, "MCP endpoint must be set");
			if (this.sessionIdleTimeout != null && this.keepAliveInterval != null) {
				Assert.isTrue(this.sessionIdleTimeout.compareTo(this.keepAliveInterval) > 0,
						"sessionIdleTimeout must be greater than keepAliveInterval, otherwise a connected client "
								+ "may be evicted before it can answer a keep-alive ping");
			}
			return new WebMvcStreamableServerTransportProvider(
					this.jsonMapper == null ? McpJsonDefaults.getMapper() : this.jsonMapper, this.mcpEndpoint,
					this.disallowDelete, this.contextExtractor, this.keepAliveInterval, this.sessionIdleTimeout,
					this.securityValidator, this.maxSessions);
		}

	}

}
