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
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage;
import io.modelcontextprotocol.spec.ProtocolVersions;
import io.modelcontextprotocol.util.Assert;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import reactor.util.retry.Retry.RetrySignal;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Server-Sent Events (SSE) implementation of the
 * {@link io.modelcontextprotocol.spec.McpTransport} that follows the MCP HTTP with SSE
 * transport specification.
 *
 * <p>
 * This transport establishes a bidirectional communication channel where:
 * <ul>
 * <li>Inbound messages are received through an SSE connection from the server</li>
 * <li>Outbound messages are sent via HTTP POST requests to a server-provided
 * endpoint</li>
 * </ul>
 *
 * <p>
 * The message flow follows these steps:
 * <ol>
 * <li>The client establishes an SSE connection to the server's /sse endpoint</li>
 * <li>The server sends an 'endpoint' event containing the URI for sending messages</li>
 * </ol>
 *
 * This implementation uses {@link WebClient} for HTTP communications and supports JSON
 * serialization/deserialization of messages.
 *
 * @author Christian Tzolov
 * @see <a href=
 * "https://spec.modelcontextprotocol.io/specification/basic/transports/#http-with-sse">MCP
 * HTTP with SSE Transport Specification</a>
 */
public class WebFluxSseClientTransport implements McpClientTransport {

	private static final Logger logger = LoggerFactory.getLogger(WebFluxSseClientTransport.class);

	private static final String MCP_PROTOCOL_VERSION = ProtocolVersions.MCP_2024_11_05;

	/**
	 * Event type for JSON-RPC messages received through the SSE connection. The server
	 * sends messages with this event type to transmit JSON-RPC protocol data.
	 */
	private static final String MESSAGE_EVENT_TYPE = "message";

	/**
	 * Event type for receiving the message endpoint URI from the server. The server MUST
	 * send this event when a client connects, providing the URI where the client should
	 * send its messages via HTTP POST.
	 */
	private static final String ENDPOINT_EVENT_TYPE = "endpoint";

	/**
	 * Default SSE endpoint path as specified by the MCP transport specification. This
	 * endpoint is used to establish the SSE connection with the server.
	 */
	private static final String DEFAULT_SSE_ENDPOINT = "/sse";

	/**
	 * Type reference for parsing SSE events containing string data.
	 */
	private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE = new ParameterizedTypeReference<>() {
	};

	/**
	 * WebClient instance for handling both SSE connections and HTTP POST requests. Used
	 * for establishing the SSE connection and sending outbound messages.
	 */
	private final WebClient webClient;

	/**
	 * JSON mapper for serializing outbound messages and deserializing inbound messages.
	 * Handles conversion between JSON-RPC messages and their string representation.
	 */
	protected McpJsonMapper jsonMapper;

	/**
	 * Subscription for the SSE connection handling inbound messages. Used for cleanup
	 * during transport shutdown.
	 */
	private @Nullable Disposable inboundSubscription;

	/**
	 * Flag indicating if the transport is in the process of shutting down. Used to
	 * prevent new operations during shutdown and handle cleanup gracefully.
	 */
	private volatile boolean isClosing = false;

	/**
	 * Sink for managing the message endpoint URI provided by the server. Stores the most
	 * recent endpoint URI and makes it available for outbound message processing.
	 */
	protected final Sinks.One<String> messageEndpointSink = Sinks.one();

	/**
	 * The SSE endpoint URI provided by the server. Used for sending outbound messages via
	 * HTTP POST requests.
	 */
	private String sseEndpoint;

	/**
	 * Constructs a new SseClientTransport with the specified WebClient builder and
	 * ObjectMapper. Initializes both inbound and outbound message processing pipelines.
	 * @param webClientBuilder the WebClient.Builder to use for creating the WebClient
	 * instance
	 * @param jsonMapper the ObjectMapper to use for JSON processing
	 * @throws IllegalArgumentException if either parameter is null
	 */
	public WebFluxSseClientTransport(WebClient.Builder webClientBuilder, McpJsonMapper jsonMapper) {
		this(webClientBuilder, jsonMapper, DEFAULT_SSE_ENDPOINT);
	}

	/**
	 * Constructs a new SseClientTransport with the specified WebClient builder and
	 * ObjectMapper. Initializes both inbound and outbound message processing pipelines.
	 * @param webClientBuilder the WebClient.Builder to use for creating the WebClient
	 * instance
	 * @param jsonMapper the ObjectMapper to use for JSON processing
	 * @param sseEndpoint the SSE endpoint URI to use for establishing the connection
	 * @throws IllegalArgumentException if either parameter is null
	 */
	public WebFluxSseClientTransport(WebClient.Builder webClientBuilder, McpJsonMapper jsonMapper, String sseEndpoint) {
		Assert.notNull(jsonMapper, "jsonMapper must not be null");
		Assert.notNull(webClientBuilder, "WebClient.Builder must not be null");
		Assert.hasText(sseEndpoint, "SSE endpoint must not be null or empty");

		this.jsonMapper = jsonMapper;
		this.webClient = webClientBuilder.build();
		this.sseEndpoint = sseEndpoint;
	}

	@Override
	public List<String> protocolVersions() {
		return List.of(MCP_PROTOCOL_VERSION);
	}

	/**
	 * Establishes a connection to the MCP server using Server-Sent Events (SSE). This
	 * method initiates the SSE connection and sets up the message processing pipeline.
	 *
	 * <p>
	 * The connection process follows these steps:
	 * <ol>
	 * <li>Establishes an SSE connection to the server's /sse endpoint</li>
	 * <li>Waits for the server to send an 'endpoint' event with the message posting
	 * URI</li>
	 * <li>Sets up message handling for incoming JSON-RPC messages</li>
	 * </ol>
	 *
	 * <p>
	 * The connection is considered established only after receiving the endpoint event
	 * from the server.
	 * @param handler a function that processes incoming JSON-RPC messages and returns
	 * responses
	 * @return a Mono that completes when the connection is fully established
	 */
	@Override
	public Mono<Void> connect(Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>> handler) {
		// TODO: Avoid eager connection opening and enable resilience
		// -> upon disconnects, re-establish connection
		// -> allow optimizing for eager connection start using a constructor flag
		Flux<ServerSentEvent<String>> events = eventStream();
		this.inboundSubscription = events.concatMap(event -> Mono.just(event).<JSONRPCMessage>handle((e, s) -> {
			if (ENDPOINT_EVENT_TYPE.equals(event.event())) {
				String messageEndpointUri = event.data();
				if (this.messageEndpointSink.tryEmitValue(messageEndpointUri).isSuccess()) {
					s.complete();
				}
				else {
					// TODO: clarify with the spec if multiple events can be
					// received
					s.error(new RuntimeException("Failed to handle SSE endpoint event"));
				}
			}
			else if (MESSAGE_EVENT_TYPE.equals(event.event())) {
				try {
					JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(this.jsonMapper, event.data());
					s.next(message);
				}
				catch (IOException ioException) {
					s.error(ioException);
				}
			}
			else {
				logger.debug("Received unrecognized SSE event type: {}", event);
				s.complete();
			}
		}).transform(handler)).subscribe();

		// The connection is established once the server sends the endpoint event
		return this.messageEndpointSink.asMono().then();
	}

	/**
	 * Sends a JSON-RPC message to the server using the endpoint provided during
	 * connection.
	 *
	 * <p>
	 * Messages are sent via HTTP POST requests to the server-provided endpoint URI. The
	 * message is serialized to JSON before transmission. If the transport is in the
	 * process of closing, the message send operation is skipped gracefully.
	 * @param message the JSON-RPC message to send
	 * @return a Mono that completes when the message has been sent successfully
	 * @throws RuntimeException if message serialization fails
	 */
	@Override
	public Mono<Void> sendMessage(JSONRPCMessage message) {
		// The messageEndpoint is the endpoint URI to send the messages
		// It is provided by the server as part of the endpoint event
		return this.messageEndpointSink.asMono().flatMap(messageEndpointUri -> {
			if (this.isClosing) {
				return Mono.empty();
			}
			try {
				String jsonText = this.jsonMapper.writeValueAsString(message);
				return this.webClient.post()
					.uri(messageEndpointUri)
					.contentType(MediaType.APPLICATION_JSON)
					.header(HttpHeaders.PROTOCOL_VERSION, MCP_PROTOCOL_VERSION)
					.bodyValue(jsonText)
					.retrieve()
					.toBodilessEntity()
					.doOnSuccess(response -> logger.debug("Message sent successfully"))
					.doOnError(error -> {
						if (!this.isClosing) {
							logger.error("Error sending message: {}", error.getMessage());
						}
					});
			}
			catch (IOException e) {
				if (!this.isClosing) {
					return Mono.error(new RuntimeException("Failed to serialize message", e));
				}
				return Mono.empty();
			}
		}).then(); // TODO: Consider non-200-ok response
	}

	/**
	 * Initializes and starts the inbound SSE event processing. Establishes the SSE
	 * connection and sets up event handling for both message and endpoint events.
	 * Includes automatic retry logic for handling transient connection failures.
	 */
	// visible for tests
	protected Flux<ServerSentEvent<String>> eventStream() { // @formatter:off
		return this.webClient
			.get()
			.uri(this.sseEndpoint)
			.accept(MediaType.TEXT_EVENT_STREAM)
			.header(HttpHeaders.PROTOCOL_VERSION, MCP_PROTOCOL_VERSION)
			.retrieve()
			.bodyToFlux(SSE_TYPE)
			.retryWhen(Retry.from(retrySignal -> retrySignal.handle(this.inboundRetryHandler)));
	} // @formatter:on

	/**
	 * Retry handler for the inbound SSE stream. Implements the retry logic for handling
	 * connection failures and other errors.
	 */
	private BiConsumer<RetrySignal, SynchronousSink<Object>> inboundRetryHandler = (retrySpec, sink) -> {
		if (this.isClosing) {
			logger.debug("SSE connection closed during shutdown");
			sink.error(retrySpec.failure());
			return;
		}
		if (retrySpec.failure() instanceof IOException) {
			logger.debug("Retrying SSE connection after IO error");
			sink.next(retrySpec);
			return;
		}
		logger.error("Fatal SSE error, not retrying: {}", retrySpec.failure().getMessage());
		sink.error(retrySpec.failure());
	};

	/**
	 * Implements graceful shutdown of the transport. Cleans up all resources including
	 * subscriptions and schedulers. Ensures orderly shutdown of both inbound and outbound
	 * message processing.
	 * @return a Mono that completes when shutdown is finished
	 */
	@Override
	public Mono<Void> closeGracefully() { // @formatter:off
		return Mono.fromRunnable(() -> {
			this.isClosing = true;

			// Dispose of subscriptions

			if (this.inboundSubscription != null) {
				this.inboundSubscription.dispose();
			}

		})
		.then()
		.subscribeOn(Schedulers.boundedElastic());
	} // @formatter:on

	/**
	 * Unmarshalls data from a generic Object into the specified type using the configured
	 * ObjectMapper.
	 *
	 * <p>
	 * This method is particularly useful when working with JSON-RPC parameters or result
	 * objects that need to be converted to specific Java types. It leverages Jackson's
	 * type conversion capabilities to handle complex object structures.
	 * @param <T> the target type to convert the data into
	 * @param data the source object to convert
	 * @param typeRef the TypeRef describing the target type
	 * @return the unmarshalled object of type T
	 * @throws IllegalArgumentException if the conversion cannot be performed
	 */
	@Override
	public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
		return this.jsonMapper.convertValue(data, typeRef);
	}

	/**
	 * Creates a new builder for {@link WebFluxSseClientTransport}.
	 * @param webClientBuilder the WebClient.Builder to use for creating the WebClient
	 * instance
	 * @return a new builder instance
	 */
	public static Builder builder(WebClient.Builder webClientBuilder) {
		return new Builder(webClientBuilder);
	}

	/**
	 * Builder for {@link WebFluxSseClientTransport}.
	 */
	public static class Builder {

		private final WebClient.Builder webClientBuilder;

		private String sseEndpoint = DEFAULT_SSE_ENDPOINT;

		private @Nullable McpJsonMapper jsonMapper;

		/**
		 * Creates a new builder with the specified WebClient.Builder.
		 * @param webClientBuilder the WebClient.Builder to use
		 */
		public Builder(WebClient.Builder webClientBuilder) {
			Assert.notNull(webClientBuilder, "WebClient.Builder must not be null");
			this.webClientBuilder = webClientBuilder;
		}

		/**
		 * Sets the SSE endpoint path.
		 * @param sseEndpoint the SSE endpoint path
		 * @return this builder
		 */
		public Builder sseEndpoint(String sseEndpoint) {
			Assert.hasText(sseEndpoint, "sseEndpoint must not be empty");
			this.sseEndpoint = sseEndpoint;
			return this;
		}

		/**
		 * Sets the JSON mapper for serialization/deserialization.
		 * @param jsonMapper the JsonMapper to use
		 * @return this builder
		 */
		public Builder jsonMapper(McpJsonMapper jsonMapper) {
			Assert.notNull(jsonMapper, "jsonMapper must not be null");
			this.jsonMapper = jsonMapper;
			return this;
		}

		/**
		 * Builds a new {@link WebFluxSseClientTransport} instance.
		 * @return a new transport instance
		 */
		public WebFluxSseClientTransport build() {
			return new WebFluxSseClientTransport(this.webClientBuilder,
					this.jsonMapper == null ? McpJsonDefaults.getMapper() : this.jsonMapper, this.sseEndpoint);
		}

	}

}
