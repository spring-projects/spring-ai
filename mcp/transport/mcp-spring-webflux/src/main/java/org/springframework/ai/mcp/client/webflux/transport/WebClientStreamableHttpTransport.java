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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.ClosedMcpTransportSession;
import io.modelcontextprotocol.spec.DefaultMcpTransportSession;
import io.modelcontextprotocol.spec.DefaultMcpTransportStream;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpTransportException;
import io.modelcontextprotocol.spec.McpTransportSession;
import io.modelcontextprotocol.spec.McpTransportSessionNotFoundException;
import io.modelcontextprotocol.spec.McpTransportStream;
import io.modelcontextprotocol.spec.ProtocolVersions;
import io.modelcontextprotocol.util.Assert;
import io.modelcontextprotocol.util.Utils;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * An implementation of the Streamable HTTP protocol as defined by the
 * <code>2025-03-26</code> version of the MCP specification.
 *
 * <p>
 * The transport is capable of resumability and reconnects. It reacts to transport-level
 * session invalidation and will propagate {@link McpTransportSessionNotFoundException
 * appropriate exceptions} to the higher level abstraction layer when needed in order to
 * allow proper state management. The implementation handles servers that are stateful and
 * provide session meta information, but can also communicate with stateless servers that
 * do not provide a session identifier and do not support SSE streams.
 * </p>
 * <p>
 * This implementation does not handle backwards compatibility with the <a href=
 * "https://modelcontextprotocol.io/specification/2024-11-05/basic/transports#http-with-sse">"HTTP
 * with SSE" transport</a>. In order to communicate over the phased-out
 * <code>2024-11-05</code> protocol, use {@link HttpClientSseClientTransport} or
 * {@link WebFluxSseClientTransport}.
 * </p>
 *
 * @author Dariusz Jędrzejczyk
 * @see <a href=
 * "https://modelcontextprotocol.io/specification/2025-03-26/basic/transports#streamable-http">Streamable
 * HTTP transport specification</a>
 */
public final class WebClientStreamableHttpTransport implements McpClientTransport {

	private static final String MISSING_SESSION_ID = "[missing_session_id]";

	private static final Logger logger = LoggerFactory.getLogger(WebClientStreamableHttpTransport.class);

	private static final String DEFAULT_ENDPOINT = "/mcp";

	/**
	 * Event type for JSON-RPC messages received through the SSE connection. The server
	 * sends messages with this event type to transmit JSON-RPC protocol data.
	 */
	private static final String MESSAGE_EVENT_TYPE = "message";

	private static final ParameterizedTypeReference<ServerSentEvent<String>> PARAMETERIZED_TYPE_REF = new ParameterizedTypeReference<>() {
	};

	private final McpJsonMapper jsonMapper;

	private final WebClient webClient;

	private final String endpoint;

	private final boolean openConnectionOnStartup;

	private final boolean resumableStreams;

	private final AtomicReference<McpTransportSession<Disposable>> activeSession = new AtomicReference<>();

	private final AtomicReference<Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>>> handler = new AtomicReference<>();

	private final AtomicReference<@Nullable Consumer<Throwable>> exceptionHandler = new AtomicReference<>();

	private final List<String> supportedProtocolVersions;

	private final String latestSupportedProtocolVersion;

	private WebClientStreamableHttpTransport(McpJsonMapper jsonMapper, WebClient.Builder webClientBuilder,
			String endpoint, boolean resumableStreams, boolean openConnectionOnStartup,
			List<String> supportedProtocolVersions) {
		this.jsonMapper = jsonMapper;
		this.webClient = webClientBuilder.build();
		this.endpoint = endpoint;
		this.resumableStreams = resumableStreams;
		this.openConnectionOnStartup = openConnectionOnStartup;
		this.activeSession.set(createTransportSession());
		this.supportedProtocolVersions = List.copyOf(supportedProtocolVersions);
		this.latestSupportedProtocolVersion = this.supportedProtocolVersions.stream()
			.sorted(Comparator.reverseOrder())
			.findFirst()
			.get();
	}

	@Override
	public List<String> protocolVersions() {
		return this.supportedProtocolVersions;
	}

	/**
	 * Create a stateful builder for creating {@link WebClientStreamableHttpTransport}
	 * instances.
	 * @param webClientBuilder the {@link WebClient.Builder} to use
	 * @return a builder which will create an instance of
	 * {@link WebClientStreamableHttpTransport} once {@link Builder#build()} is called
	 */
	public static Builder builder(WebClient.Builder webClientBuilder) {
		return new Builder(webClientBuilder);
	}

	@Override
	public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
		return Mono.deferContextual(ctx -> {
			this.handler.set(handler);
			if (this.openConnectionOnStartup) {
				logger.debug("Eagerly opening connection on startup");
				return this.reconnect(null).then();
			}
			return Mono.empty();
		});
	}

	private McpTransportSession<Disposable> createTransportSession() {
		Function<String, Publisher<Void>> onClose = sessionId -> sessionId == null ? Mono.empty()
				: this.webClient.delete()
					.uri(this.endpoint)
					.header(HttpHeaders.MCP_SESSION_ID, sessionId)
					.header(HttpHeaders.PROTOCOL_VERSION, this.latestSupportedProtocolVersion)
					.retrieve()
					.toBodilessEntity()
					.onErrorComplete(e -> {
						logger.warn("Got error when closing transport", e);
						return true;
					})
					.then();
		return new DefaultMcpTransportSession(onClose);
	}

	private McpTransportSession<Disposable> createClosedSession(McpTransportSession<Disposable> existingSession) {
		var existingSessionId = Optional.ofNullable(existingSession)
			.filter(session -> !(session instanceof ClosedMcpTransportSession<Disposable>))
			.flatMap(McpTransportSession::sessionId)
			.orElse(null);
		return new ClosedMcpTransportSession<>(existingSessionId);
	}

	@Override
	public void setExceptionHandler(Consumer<Throwable> handler) {
		logger.debug("Exception handler registered");
		this.exceptionHandler.set(handler);
	}

	private void handleException(Throwable t) {
		logger.debug("Handling exception for session {}", sessionIdOrPlaceholder(this.activeSession.get()), t);
		if (t instanceof McpTransportSessionNotFoundException) {
			McpTransportSession<?> invalidSession = this.activeSession.getAndSet(createTransportSession());
			logger.warn("Server does not recognize session {}. Invalidating.", invalidSession.sessionId());
			invalidSession.close();
		}
		Consumer<Throwable> handler = this.exceptionHandler.get();
		if (handler != null) {
			handler.accept(t);
		}
	}

	@Override
	public Mono<Void> closeGracefully() {
		return Mono.defer(() -> {
			logger.debug("Graceful close triggered");
			McpTransportSession<Disposable> currentSession = this.activeSession.getAndUpdate(this::createClosedSession);
			if (currentSession != null) {
				return Mono.from(currentSession.closeGracefully());
			}
			return Mono.empty();
		});
	}

	private Mono<Disposable> reconnect(@Nullable McpTransportStream<Disposable> stream) {
		return Mono.deferContextual(ctx -> {
			if (stream != null) {
				logger.debug("Reconnecting stream {} with lastId {}", stream.streamId(), stream.lastId());
			}
			else {
				logger.debug("Reconnecting with no prior stream");
			}
			// Here we attempt to initialize the client. In case the server supports SSE,
			// we will establish a long-running
			// session here and listen for messages. If it doesn't, that's ok, the server
			// is a simple, stateless one.
			final AtomicReference<@Nullable Disposable> disposableRef = new AtomicReference<>();
			final McpTransportSession<Disposable> transportSession = this.activeSession.get();

			Disposable connection = this.webClient.get()
				.uri(this.endpoint)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.header(HttpHeaders.PROTOCOL_VERSION,
						Objects.requireNonNullElse(ctx.getOrDefault(McpAsyncClient.NEGOTIATED_PROTOCOL_VERSION,
								this.latestSupportedProtocolVersion), this.latestSupportedProtocolVersion))
				.headers(httpHeaders -> {
					transportSession.sessionId().ifPresent(id -> httpHeaders.add(HttpHeaders.MCP_SESSION_ID, id));
					if (stream != null) {
						stream.lastId().ifPresent(id -> httpHeaders.add(HttpHeaders.LAST_EVENT_ID, id));
					}
				})
				.exchangeToFlux(response -> {
					if (isEventStream(response)) {
						logger.debug("Established SSE stream via GET");
						return eventStream(stream, response);
					}
					else if (isNotAllowed(response)) {
						logger.debug("The server does not support SSE streams, using request-response mode.");
						return Flux.empty();
					}
					else if (isNotFound(response)) {
						if (transportSession.sessionId().isPresent()) {
							String sessionIdRepresentation = sessionIdOrPlaceholder(transportSession);
							return mcpSessionNotFoundError(sessionIdRepresentation);
						}
						else {
							return this.extractError(response, MISSING_SESSION_ID);
						}
					}
					else {
						return response.<McpSchema.JSONRPCMessage>createError()
							.doOnError(e -> logger.info("Opening an SSE stream failed. This can be safely ignored.", e))
							.flux();
					}
				})
				.flatMap(jsonrpcMessage -> this.handler.get().apply(Mono.just(jsonrpcMessage)))
				.onErrorComplete(t -> {
					this.handleException(t);
					return true;
				})
				.doFinally(s -> {
					Disposable ref = disposableRef.getAndSet(null);
					if (ref != null) {
						transportSession.removeConnection(ref);
					}
				})
				.contextWrite(ctx)
				.subscribe();

			disposableRef.set(connection);
			transportSession.addConnection(connection);
			return Mono.just(connection);
		});
	}

	@Override
	public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
		String jsonText;
		try {
			jsonText = this.jsonMapper.writeValueAsString(message);
		}
		catch (IOException e) {
			return Mono.error(new RuntimeException("Failed to serialize message", e));
		}
		return Mono.create(sink -> {
			logger.debug("Sending message {}", message);
			// Here we attempt to initialize the client.
			// In case the server supports SSE, we will establish a long-running session
			// here and
			// listen for messages.
			// If it doesn't, nothing actually happens here, that's just the way it is...
			final AtomicReference<@Nullable Disposable> disposableRef = new AtomicReference<>();
			final McpTransportSession<Disposable> transportSession = this.activeSession.get();

			Disposable connection = Flux.deferContextual(ctx -> this.webClient.post()
				.uri(this.endpoint)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
				.header(HttpHeaders.PROTOCOL_VERSION,
						Objects.requireNonNullElse(ctx.getOrDefault(McpAsyncClient.NEGOTIATED_PROTOCOL_VERSION,
								this.latestSupportedProtocolVersion), this.latestSupportedProtocolVersion))
				.headers(httpHeaders -> transportSession.sessionId()
					.ifPresent(id -> httpHeaders.add(HttpHeaders.MCP_SESSION_ID, id)))
				.bodyValue(jsonText)
				.exchangeToFlux(response -> {
					if (transportSession
						.markInitialized(response.headers().asHttpHeaders().getFirst(HttpHeaders.MCP_SESSION_ID))) {
						// Once we have a session, we try to open an async stream for
						// the server to send notifications and requests out-of-band.
						reconnect(null).contextWrite(sink.contextView()).subscribe();
					}

					String sessionRepresentation = sessionIdOrPlaceholder(transportSession);

					// The spec mentions only ACCEPTED, but the existing SDKs can return
					// 200 OK for notifications
					if (response.statusCode().is2xxSuccessful()) {
						Optional<MediaType> contentType = response.headers().contentType();
						long contentLength = response.headers().contentLength().orElse(-1);
						// Existing SDKs consume notifications with no response body nor
						// content type
						if (contentType.isEmpty() || contentLength == 0
								|| response.statusCode().equals(HttpStatus.ACCEPTED)) {
							logger.trace("Message was successfully sent via POST for session {}",
									sessionRepresentation);
							// signal the caller that the message was successfully
							// delivered
							sink.success();
							// communicate to downstream there is no streamed data coming
							return Flux.empty();
						}
						else {
							MediaType mediaType = contentType.get();
							if (mediaType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM)) {
								logger.debug("Established SSE stream via POST");
								// communicate to caller that the message was delivered
								sink.success();
								// starting a stream
								return newEventStream(response, sessionRepresentation);
							}
							else if (mediaType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
								logger.trace("Received response to POST for session {}", sessionRepresentation);
								// communicate to caller the message was delivered
								sink.success();
								return directResponseFlux(message, response);
							}
							else {
								logger.warn("Unknown media type {} returned for POST in session {}", contentType,
										sessionRepresentation);
								return Flux.error(new RuntimeException("Unknown media type returned: " + contentType));
							}
						}
					}
					else {
						if (isNotFound(response) && !sessionRepresentation.equals(MISSING_SESSION_ID)) {
							return mcpSessionNotFoundError(sessionRepresentation);
						}
						return this.extractError(response, sessionRepresentation);
					}
				}))
				.flatMap(jsonRpcMessage -> this.handler.get().apply(Mono.just(jsonRpcMessage)))
				.onErrorComplete(t -> {
					// handle the error first
					this.handleException(t);
					// inform the caller of sendMessage
					sink.error(t);
					return true;
				})
				.doFinally(s -> {
					Disposable ref = disposableRef.getAndSet(null);
					if (ref != null) {
						transportSession.removeConnection(ref);
					}
				})
				.contextWrite(sink.contextView())
				.subscribe();
			disposableRef.set(connection);
			transportSession.addConnection(connection);
		});
	}

	private static Flux<McpSchema.JSONRPCMessage> mcpSessionNotFoundError(String sessionRepresentation) {
		logger.warn("Session {} was not found on the MCP server", sessionRepresentation);
		// inform the stream/connection subscriber
		return Flux.error(new McpTransportSessionNotFoundException(sessionRepresentation));
	}

	private Flux<McpSchema.JSONRPCMessage> extractError(ClientResponse response, String sessionRepresentation) {
		return response.<McpSchema.JSONRPCMessage>createError().onErrorResume(e -> {
			WebClientResponseException responseException = (WebClientResponseException) e;
			byte[] body = responseException.getResponseBodyAsByteArray();
			McpSchema.JSONRPCResponse.JSONRPCError jsonRpcError = null;
			Exception toPropagate;
			try {
				McpSchema.JSONRPCResponse jsonRpcResponse = this.jsonMapper.readValue(body,
						McpSchema.JSONRPCResponse.class);
				jsonRpcError = jsonRpcResponse.error();
				toPropagate = jsonRpcError != null ? new McpError(jsonRpcError)
						: new McpTransportException("Can't parse the jsonResponse " + jsonRpcResponse);
			}
			catch (IOException ex) {
				toPropagate = new McpTransportException("Sending request failed, " + e.getMessage(), e);
				logger.debug("Received content together with {} HTTP code response: {}", response.statusCode(), body);
			}

			// Some implementations can return 400 when presented with a
			// session id that it doesn't know about, so we will
			// invalidate the session
			// https://github.com/modelcontextprotocol/typescript-sdk/issues/389
			if (responseException.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
				if (!sessionRepresentation.equals(MISSING_SESSION_ID)) {
					return Mono.error(new McpTransportSessionNotFoundException(sessionRepresentation, toPropagate));
				}
				return Mono.error(new McpTransportException("Received 400 BAD REQUEST for session "
						+ sessionRepresentation + ". " + toPropagate.getMessage(), toPropagate));
			}
			return Mono.error(toPropagate);
		}).flux();
	}

	private Flux<McpSchema.JSONRPCMessage> eventStream(@Nullable McpTransportStream<Disposable> stream,
			ClientResponse response) {
		McpTransportStream<Disposable> sessionStream = stream != null ? stream
				: new DefaultMcpTransportStream<>(this.resumableStreams, this::reconnect);
		logger.debug("Connected stream {}", sessionStream.streamId());

		var idWithMessages = response.bodyToFlux(PARAMETERIZED_TYPE_REF).map(this::parse);
		return Flux.from(sessionStream.consumeSseStream(idWithMessages));
	}

	private static boolean isNotFound(ClientResponse response) {
		return response.statusCode().isSameCodeAs(HttpStatus.NOT_FOUND);
	}

	private static boolean isNotAllowed(ClientResponse response) {
		return response.statusCode().isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED);
	}

	private static boolean isEventStream(ClientResponse response) {
		return response.statusCode().is2xxSuccessful() && response.headers().contentType().isPresent()
				&& response.headers().contentType().get().isCompatibleWith(MediaType.TEXT_EVENT_STREAM);
	}

	private static String sessionIdOrPlaceholder(McpTransportSession<?> transportSession) {
		return transportSession.sessionId().orElse(MISSING_SESSION_ID);
	}

	private Flux<McpSchema.JSONRPCMessage> directResponseFlux(McpSchema.JSONRPCMessage sentMessage,
			ClientResponse response) {
		return response.bodyToMono(String.class).<Iterable<McpSchema.JSONRPCMessage>>handle((responseMessage, s) -> {
			try {
				if (sentMessage instanceof McpSchema.JSONRPCNotification) {
					logger.warn("Notification: {} received non-compliant response: {}", sentMessage,
							Utils.hasText(responseMessage) ? responseMessage : "[empty]");
					s.complete();
				}
				else {
					McpSchema.JSONRPCMessage jsonRpcResponse = McpSchema.deserializeJsonRpcMessage(this.jsonMapper,
							responseMessage);
					s.next(List.of(jsonRpcResponse));
				}
			}
			catch (IOException e) {
				s.error(new McpTransportException(e));
			}
		}).flatMapIterable(Function.identity());
	}

	private Flux<McpSchema.JSONRPCMessage> newEventStream(ClientResponse response, String sessionRepresentation) {
		McpTransportStream<Disposable> sessionStream = new DefaultMcpTransportStream<>(this.resumableStreams,
				this::reconnect);
		logger.trace("Sent POST and opened a stream ({}) for session {}", sessionStream.streamId(),
				sessionRepresentation);
		return eventStream(sessionStream, response);
	}

	@Override
	public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
		return this.jsonMapper.convertValue(data, typeRef);
	}

	private Tuple2<Optional<String>, Iterable<McpSchema.JSONRPCMessage>> parse(ServerSentEvent<String> event) {
		if (MESSAGE_EVENT_TYPE.equals(event.event())) {
			try {
				// We don't support batching ATM and probably won't since the next version
				// considers removing it.
				McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(this.jsonMapper, event.data());
				String eventId = event.id();
				Optional<String> id = eventId != null ? Optional.of(eventId) : Optional.empty();
				return Tuples.of(id, List.of(message));
			}
			catch (IOException ioException) {
				throw new McpTransportException("Error parsing JSON-RPC message: " + event.data(), ioException);
			}
		}
		else {
			logger.debug("Received SSE event with type: {}", event);
			return Tuples.of(Optional.empty(), List.of());
		}
	}

	/**
	 * Builder for {@link WebClientStreamableHttpTransport}.
	 */
	public static final class Builder {

		@Nullable private McpJsonMapper jsonMapper;

		private WebClient.Builder webClientBuilder;

		private String endpoint = DEFAULT_ENDPOINT;

		private boolean resumableStreams = true;

		private boolean openConnectionOnStartup = false;

		private List<String> supportedProtocolVersions = List.of(ProtocolVersions.MCP_2024_11_05,
				ProtocolVersions.MCP_2025_03_26, ProtocolVersions.MCP_2025_06_18, ProtocolVersions.MCP_2025_11_25);

		private Builder(WebClient.Builder webClientBuilder) {
			Assert.notNull(webClientBuilder, "WebClient.Builder must not be null");
			this.webClientBuilder = webClientBuilder;
		}

		/**
		 * Configure the {@link McpJsonMapper} to use.
		 * @param jsonMapper instance to use
		 * @return the builder instance
		 */
		public Builder jsonMapper(McpJsonMapper jsonMapper) {
			Assert.notNull(jsonMapper, "JsonMapper must not be null");
			this.jsonMapper = jsonMapper;
			return this;
		}

		/**
		 * Configure the {@link WebClient.Builder} to construct the {@link WebClient}.
		 * @param webClientBuilder instance to use
		 * @return the builder instance
		 */
		public Builder webClientBuilder(WebClient.Builder webClientBuilder) {
			Assert.notNull(webClientBuilder, "WebClient.Builder must not be null");
			this.webClientBuilder = webClientBuilder;
			return this;
		}

		/**
		 * Configure the endpoint to make HTTP requests against.
		 * @param endpoint endpoint to use
		 * @return the builder instance
		 */
		public Builder endpoint(String endpoint) {
			Assert.hasText(endpoint, "endpoint must be a non-empty String");
			this.endpoint = endpoint;
			return this;
		}

		/**
		 * Configure whether to use the stream resumability feature by keeping track of
		 * SSE event ids.
		 * @param resumableStreams if {@code true} event ids will be tracked and upon
		 * disconnection, the last seen id will be used upon reconnection as a header to
		 * resume consuming messages.
		 * @return the builder instance
		 */
		public Builder resumableStreams(boolean resumableStreams) {
			this.resumableStreams = resumableStreams;
			return this;
		}

		/**
		 * Configure whether the client should open an SSE connection upon startup. Not
		 * all servers support this (although it is in theory possible with the current
		 * specification), so use with caution. By default, this value is {@code false}.
		 * @param openConnectionOnStartup if {@code true} the {@link #connect(Function)}
		 * method call will try to open an SSE connection before sending any JSON-RPC
		 * request
		 * @return the builder instance
		 */
		public Builder openConnectionOnStartup(boolean openConnectionOnStartup) {
			this.openConnectionOnStartup = openConnectionOnStartup;
			return this;
		}

		/**
		 * Sets the list of supported protocol versions used in version negotiation. By
		 * default, the client will send the latest of those versions in the
		 * {@code MCP-Protocol-Version} header.
		 * <p>
		 * Setting this value only updates the values used in version negotiation, and
		 * does NOT impact the actual capabilities of the transport. It should only be
		 * used for compatibility with servers having strict requirements around the
		 * {@code MCP-Protocol-Version} header.
		 * @param supportedProtocolVersions protocol versions supported by this transport
		 * @return this builder
		 * @see <a href=
		 * "https://modelcontextprotocol.io/specification/2024-11-05/basic/lifecycle#version-negotiation">version
		 * negotiation specification</a>
		 * @see <a href=
		 * "https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#protocol-version-header">Protocol
		 * Version Header</a>
		 */
		public Builder supportedProtocolVersions(List<String> supportedProtocolVersions) {
			Assert.notEmpty(supportedProtocolVersions, "supportedProtocolVersions must not be empty");
			this.supportedProtocolVersions = Collections.unmodifiableList(supportedProtocolVersions);
			return this;
		}

		/**
		 * Construct a fresh instance of {@link WebClientStreamableHttpTransport} using
		 * the current builder configuration.
		 * @return a new instance of {@link WebClientStreamableHttpTransport}
		 */
		public WebClientStreamableHttpTransport build() {
			return new WebClientStreamableHttpTransport(
					this.jsonMapper == null ? McpJsonMapper.getDefault() : this.jsonMapper, this.webClientBuilder,
					this.endpoint, this.resumableStreams, this.openConnectionOnStartup, this.supportedProtocolVersions);
		}

	}

}
