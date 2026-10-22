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

package org.springframework.ai.mcp.server.webflux.transport.modern;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.JsonRpc;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCMessage;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCNotification;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCRequest;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCResponse;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCResponse.JSONRPCError;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.ErrorCodes;
import io.modelcontextprotocol.modern.McpSchema.MetaKeys;
import io.modelcontextprotocol.modern.server.McpRequestManager;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpTransportResponse;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HeaderAccessor;
import io.modelcontextprotocol.server.transport.ServerHttpHeaderValidator;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import io.modelcontextprotocol.util.Assert;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * A WebFlux transport for a modern {@link McpRequestManager}: stateless, POST only, one
 * self-contained request or notification per call. It never blocks: sync handlers run on
 * {@code Schedulers.boundedElastic()}, async handlers on the thread serving the request.
 * <p>
 * Authentication and authorization, including {@code 401}/{@code 403} challenges, belong
 * in filters in front of the {@link #getRouterFunction() router function}. Filters may
 * decide on the {@code Mcp-Method} and {@code Mcp-Name} headers: a request whose headers
 * disagree with its body is rejected before dispatch.
 *
 * @author Dariusz Jędrzejczyk
 * @since 2.1.0
 */
public final class WebFluxMcpTransport {

	private static final Log logger = LogFactory.getLog(WebFluxMcpTransport.class);

	private static final int DEFAULT_REQUEST_MAX_SIZE = 16 * 1024 * 1024;

	private static final Duration DEFAULT_KEEP_ALIVE_INTERVAL = Duration.ofSeconds(30);

	private static final String MESSAGE_EVENT_TYPE = "message";

	private static final ServerSentEvent<String> KEEP_ALIVE = ServerSentEvent.<String>builder().comment("").build();

	// The statuses the MCP specification gives its error codes; any other code is
	// answered with 200.
	private static final Map<Integer, Integer> DEFAULT_ERROR_STATUSES = Map.of(ErrorCodes.PARSE_ERROR,
			HttpStatus.BAD_REQUEST.value(), ErrorCodes.INVALID_REQUEST, HttpStatus.BAD_REQUEST.value(),
			ErrorCodes.INVALID_PARAMS, HttpStatus.BAD_REQUEST.value(), ErrorCodes.HEADER_MISMATCH,
			HttpStatus.BAD_REQUEST.value(), ErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY,
			HttpStatus.BAD_REQUEST.value(), ErrorCodes.UNSUPPORTED_PROTOCOL_VERSION, HttpStatus.BAD_REQUEST.value(),
			ErrorCodes.METHOD_NOT_FOUND, HttpStatus.NOT_FOUND.value());

	private final McpRequestManager requestManager;

	private final McpJsonMapper jsonMapper;

	private final McpTransportContextExtractor<ServerRequest> contextExtractor;

	private final int maxRequestSize;

	private final ServerHttpHeaderValidator httpHeaderValidator;

	private final @Nullable Duration keepAliveInterval;

	private final Map<Integer, Integer> errorStatuses;

	private final RouterFunction<ServerResponse> routerFunction;

	private volatile boolean closing = false;

	private WebFluxMcpTransport(McpRequestManager requestManager, McpJsonMapper jsonMapper, String mcpEndpoint,
			McpTransportContextExtractor<ServerRequest> contextExtractor, int maxRequestSize,
			ServerHttpHeaderValidator httpHeaderValidator, @Nullable Duration keepAliveInterval,
			Map<Integer, Integer> errorStatuses) {
		this.requestManager = requestManager;
		this.jsonMapper = jsonMapper;
		this.contextExtractor = contextExtractor;
		this.maxRequestSize = maxRequestSize;
		this.httpHeaderValidator = httpHeaderValidator;
		this.keepAliveInterval = keepAliveInterval;
		this.errorStatuses = errorStatuses;
		this.routerFunction = RouterFunctions.route()
			.POST(mcpEndpoint, this::handlePost)
			.route(RequestPredicates.path(mcpEndpoint), this::rejectMethod)
			.build();
	}

	/**
	 * Create a builder for a transport serving {@code requestManager}.
	 * @param requestManager the request manager answering requests, usually a
	 * {@link McpServer}
	 * @return a new builder
	 */
	public static Builder builder(McpRequestManager requestManager) {
		return new Builder(requestManager);
	}

	/**
	 * The router function serving the MCP endpoint, to be registered with the
	 * application's WebFlux configuration.
	 * @return the router function
	 */
	public RouterFunction<ServerResponse> getRouterFunction() {
		return this.routerFunction;
	}

	/**
	 * Stop accepting new requests. If the request manager is a {@link McpServer}, also
	 * asks it to end active {@code subscriptions/listen} streams gracefully.
	 */
	public void closeGracefully() {
		this.closing = true;
		if (this.requestManager instanceof McpServer server) {
			server.closeGracefully();
		}
	}

	private Mono<ServerResponse> rejectMethod(ServerRequest request) {
		// Modern servers never mint sessions or resumable streams; a legacy GET/DELETE
		// gets a plain 405, without a session or stream.
		return ServerResponse.status(HttpStatus.METHOD_NOT_ALLOWED).allow(HttpMethod.POST).build();
	}

	private Mono<ServerResponse> handlePost(ServerRequest request) {
		if (this.closing) {
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).bodyValue("Server is shutting down");
		}
		try {
			this.httpHeaderValidator.validate(headerAccessor(request));
		}
		catch (ServerTransportSecurityException ex) {
			String message = ex.getMessage();
			return ServerResponse.status(ex.getStatusCode()).bodyValue(message != null ? message : "");
		}
		if (request.headers().contentLength().orElse(-1) > this.maxRequestSize) {
			return ServerResponse.status(HttpStatus.CONTENT_TOO_LARGE).build();
		}

		McpTransportContext transportContext = this.contextExtractor.extract(request);
		Charset charset = request.headers().contentType().map(MediaType::getCharset).orElse(StandardCharsets.UTF_8);

		// Bodies without a Content-Length are only capped while they are read.
		return DataBufferUtils.join(request.body(BodyExtractors.toDataBuffers()), this.maxRequestSize)
			.map(buffer -> decode(buffer, charset))
			.defaultIfEmpty("")
			.flatMap(body -> handleBody(request, transportContext, body))
			.onErrorResume(DataBufferLimitException.class,
					ex -> ServerResponse.status(HttpStatus.CONTENT_TOO_LARGE).build());
	}

	private Mono<ServerResponse> handleBody(ServerRequest request, McpTransportContext transportContext, String body) {
		JSONRPCMessage message;
		try {
			message = JsonRpc.deserializeMessage(this.jsonMapper, body);
		}
		catch (IOException ex) {
			return errorResponse(JSONRPCResponse.error(null, new JSONRPCError(ErrorCodes.PARSE_ERROR, "Parse error")));
		}
		catch (JsonRpc.InvalidMessageException ex) {
			return errorResponse(JSONRPCResponse.error(ex.id(),
					new JSONRPCError(ErrorCodes.INVALID_REQUEST, "Invalid JSON-RPC message")));
		}

		if (message instanceof JSONRPCNotification notification) {
			return this.requestManager.handleNotification(transportContext, notification)
				.then(ServerResponse.accepted().build());
		}

		if (!(message instanceof JSONRPCRequest jsonRpcRequest)) {
			return errorResponse(JSONRPCResponse.error(null, new JSONRPCError(ErrorCodes.INVALID_REQUEST,
					"The server accepts either requests or notifications")));
		}

		String headerMismatch = validateHeaders(request, jsonRpcRequest);
		if (headerMismatch != null) {
			return errorResponse(JSONRPCResponse.error(jsonRpcRequest.id(),
					new JSONRPCError(ErrorCodes.HEADER_MISMATCH, headerMismatch)));
		}

		// Never handleBlocking: this thread may be an event loop, so sync user code has
		// to run elsewhere.
		return this.requestManager.handle(transportContext, jsonRpcRequest).flatMap(this::toServerResponse);
	}

	private Mono<ServerResponse> toServerResponse(McpTransportResponse response) {
		if (response instanceof McpTransportResponse.Result result) {
			return jsonResponse(HttpStatus.OK.value(), result.response());
		}
		if (response instanceof McpTransportResponse.Error error) {
			return errorResponse(error.response());
		}
		McpTransportResponse.Streaming streaming = (McpTransportResponse.Streaming) response;
		return ServerResponse.ok()
			.contentType(MediaType.TEXT_EVENT_STREAM)
			.header(HttpHeaders.CACHE_CONTROL, "no-cache")
			.header("X-Accel-Buffering", "no")
			.body(BodyInserters.fromServerSentEvents(events(streaming.messages())));
	}

	private Flux<ServerSentEvent<String>> events(Flux<JSONRPCMessage> messages) {
		Flux<ServerSentEvent<String>> events = messages.map(this::messageEvent);
		Duration interval = this.keepAliveInterval;
		if (interval != null) {
			// Keep-alives stop with the stream. A keep-alive that fails to write is how a
			// quiet stream notices the client is gone. A tick nobody has requested is
			// dropped: Flux.interval would otherwise fail the stream while a slow client
			// holds up the connection.
			events = events.publish(shared -> shared.mergeWith(Flux.interval(interval)
				.onBackpressureDrop()
				.map(tick -> KEEP_ALIVE)
				.takeUntilOther(shared.then())));
		}
		// The response is committed with its first event, which a handler may take a
		// while to produce. A leading comment sends the status and headers right away,
		// so a client waiting for them does not time out on a slow request.
		return events.startWith(KEEP_ALIVE)
			// A disconnect cancels the stream while an error may be on its way. A
			// cancelled subscriber drops errors before any error consumer runs;
			// onErrorComplete absorbs them even after cancellation.
			.doOnError(ex -> {
				if (logger.isDebugEnabled()) {
					logger.debug("Streaming response ended early: " + ex.getMessage());
				}
			})
			.onErrorComplete();
	}

	private ServerSentEvent<String> messageEvent(JSONRPCMessage message) {
		return ServerSentEvent.builder(toJson(message)).event(MESSAGE_EVENT_TYPE).build();
	}

	private Mono<ServerResponse> errorResponse(JSONRPCResponse response) {
		JSONRPCError error = response.error();
		int status = error != null ? this.errorStatuses.getOrDefault(error.code(), HttpStatus.OK.value())
				: HttpStatus.OK.value();
		return jsonResponse(status, response);
	}

	private Mono<ServerResponse> jsonResponse(int status, JSONRPCResponse response) {
		return Mono.fromCallable(() -> toJson(response))
			.flatMap(json -> ServerResponse.status(status).contentType(MediaType.APPLICATION_JSON).bodyValue(json));
	}

	private String toJson(JSONRPCMessage message) {
		try {
			return this.jsonMapper.writeValueAsString(message);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	// Checks Mcp-Method, MCP-Protocol-Version and, for the three named methods, Mcp-Name
	// against the body. Returns a description of the mismatch, or null if there is none.
	private static @Nullable String validateHeaders(ServerRequest request, JSONRPCRequest jsonRpcRequest) {
		ServerRequest.Headers headers = request.headers();
		String methodHeader = headers.firstHeader("Mcp-Method");
		if (methodHeader == null) {
			return "Missing required header: Mcp-Method";
		}
		if (!methodHeader.equals(jsonRpcRequest.method())) {
			return "Mcp-Method header does not match request method";
		}

		// A body without a readable version is left to McpServer's _meta validation,
		// which answers it with -32602.
		String bodyVersion = protocolVersionFromMeta(jsonRpcRequest.params());
		if (bodyVersion != null) {
			String versionHeader = headers.firstHeader("MCP-Protocol-Version");
			if (versionHeader == null) {
				return "Missing required header: MCP-Protocol-Version";
			}
			if (!versionHeader.equals(bodyVersion)) {
				return "MCP-Protocol-Version header does not match _meta protocol version";
			}
		}

		String nameKey = switch (jsonRpcRequest.method()) {
			case McpSchema.METHOD_TOOLS_CALL, McpSchema.METHOD_PROMPTS_GET -> "name";
			case McpSchema.METHOD_RESOURCES_READ -> "uri";
			default -> null;
		};
		if (nameKey == null || !(jsonRpcRequest.params() instanceof Map<?, ?> paramsMap)) {
			return null;
		}
		if (!(paramsMap.get(nameKey) instanceof String expectedName)) {
			return null;
		}
		String nameHeader = headers.firstHeader("Mcp-Name");
		if (nameHeader == null) {
			return "Missing required header: Mcp-Name";
		}
		String decoded = decodeMcpNameHeader(nameHeader);
		if (decoded == null) {
			return "Mcp-Name header has a malformed Base64 value";
		}
		if (!expectedName.equals(decoded)) {
			return "Mcp-Name header does not match request name/uri";
		}
		return null;
	}

	private static @Nullable String protocolVersionFromMeta(@Nullable Object params) {
		if (params instanceof Map<?, ?> paramsMap && paramsMap.get("_meta") instanceof Map<?, ?> meta
				&& meta.get(MetaKeys.PROTOCOL_VERSION) instanceof String version) {
			return version;
		}
		return null;
	}

	private static @Nullable String decodeMcpNameHeader(String value) {
		if (value.startsWith("=?base64?") && value.endsWith("?=")) {
			String base64 = value.substring("=?base64?".length(), value.length() - "?=".length());
			try {
				return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
			}
			catch (IllegalArgumentException ex) {
				return null;
			}
		}
		return value;
	}

	private static HeaderAccessor headerAccessor(ServerRequest request) {
		HttpHeaders headers = request.headers().asHttpHeaders();
		return new HeaderAccessor() {
			@Override
			public List<String> getHeader(String name) {
				return headers.getOrEmpty(name);
			}

			@Override
			public List<String> getHeaderNames() {
				return List.copyOf(headers.headerNames());
			}
		};
	}

	private static String decode(DataBuffer buffer, Charset charset) {
		try {
			return buffer.toString(charset);
		}
		finally {
			DataBufferUtils.release(buffer);
		}
	}

	/**
	 * Builder for {@link WebFluxMcpTransport}.
	 *
	 * @since 2.1.0
	 */
	public static final class Builder {

		private final McpRequestManager requestManager;

		private @Nullable McpJsonMapper jsonMapper;

		private String mcpEndpoint = "/mcp";

		private McpTransportContextExtractor<ServerRequest> contextExtractor = request -> McpTransportContext.EMPTY;

		private int maxRequestSize = DEFAULT_REQUEST_MAX_SIZE;

		// No allowed origins: requests without an Origin (non-browser clients) pass, any
		// cross-origin browser request is rejected until explicitly allowed.
		private ServerHttpHeaderValidator httpHeaderValidator = DefaultServerTransportSecurityValidator.builder()
			.build();

		private @Nullable Duration keepAliveInterval = DEFAULT_KEEP_ALIVE_INTERVAL;

		private final Map<Integer, Integer> errorStatuses = new HashMap<>(DEFAULT_ERROR_STATUSES);

		private Builder(McpRequestManager requestManager) {
			Assert.notNull(requestManager, "requestManager must not be null");
			this.requestManager = requestManager;
		}

		/**
		 * The mapper for JSON-RPC messages. Defaults to
		 * {@link McpJsonDefaults#getMapper()}.
		 * @param jsonMapper the JSON mapper
		 * @return this builder
		 */
		public Builder jsonMapper(McpJsonMapper jsonMapper) {
			Assert.notNull(jsonMapper, "jsonMapper must not be null");
			this.jsonMapper = jsonMapper;
			return this;
		}

		/**
		 * The path of the MCP endpoint. Defaults to {@code /mcp}.
		 * @param mcpEndpoint the endpoint path
		 * @return this builder
		 */
		public Builder endpoint(String mcpEndpoint) {
			Assert.hasText(mcpEndpoint, "mcpEndpoint must not be empty");
			this.mcpEndpoint = mcpEndpoint;
			return this;
		}

		/**
		 * Extracts the {@link McpTransportContext} handlers see through
		 * {@code McpRequestContext#transportContext()}. Invoked once per POST, before the
		 * body is read.
		 * @param contextExtractor the context extractor
		 * @return this builder
		 */
		public Builder contextExtractor(McpTransportContextExtractor<ServerRequest> contextExtractor) {
			Assert.notNull(contextExtractor, "contextExtractor must not be null");
			this.contextExtractor = contextExtractor;
			return this;
		}

		/**
		 * The largest request body accepted, in bytes; larger ones are answered with
		 * {@code 413}. Defaults to 16 MiB.
		 * @param maxRequestSize the maximum body size
		 * @return this builder
		 */
		public Builder maxRequestSize(int maxRequestSize) {
			Assert.isTrue(maxRequestSize > 0, "maxRequestSize must be positive");
			this.maxRequestSize = maxRequestSize;
			return this;
		}

		/**
		 * Validates the headers of every POST before it is read, e.g. Host/Origin checks
		 * against DNS rebinding. A rejection is answered with the exception's status
		 * code. Defaults to rejecting every request that carries an {@code Origin}
		 * header.
		 * @param httpHeaderValidator the header validator
		 * @return this builder
		 */
		public Builder httpHeaderValidator(ServerHttpHeaderValidator httpHeaderValidator) {
			Assert.notNull(httpHeaderValidator, "httpHeaderValidator must not be null");
			this.httpHeaderValidator = httpHeaderValidator;
			return this;
		}

		/**
		 * How often an open event stream gets an SSE comment, keeping intermediaries from
		 * closing it and detecting clients that went away. {@code null} disables
		 * keep-alives. Defaults to 30 seconds.
		 * @param keepAliveInterval the keep-alive interval, or {@code null}
		 * @return this builder
		 */
		public Builder keepAliveInterval(@Nullable Duration keepAliveInterval) {
			Assert.isTrue(keepAliveInterval == null || !(keepAliveInterval.isNegative() || keepAliveInterval.isZero()),
					"keepAliveInterval must be positive");
			this.keepAliveInterval = keepAliveInterval;
			return this;
		}

		/**
		 * The HTTP status answering a JSON-RPC error with {@code code}, unless a stream
		 * has already started. Defaults cover the codes the MCP specification defines;
		 * other codes are answered with 200.
		 * @param code the JSON-RPC error code
		 * @param status the HTTP status
		 * @return this builder
		 */
		public Builder errorStatus(int code, int status) {
			Assert.isTrue(status >= 200 && status <= 599, "status must be a valid HTTP status");
			this.errorStatuses.put(code, status);
			return this;
		}

		/**
		 * Build the transport.
		 * @return a new transport
		 */
		public WebFluxMcpTransport build() {
			McpJsonMapper mapper = this.jsonMapper != null ? this.jsonMapper : McpJsonDefaults.getMapper();
			return new WebFluxMcpTransport(this.requestManager, mapper, this.mcpEndpoint, this.contextExtractor,
					this.maxRequestSize, this.httpHeaderValidator, this.keepAliveInterval,
					Map.copyOf(this.errorStatuses));
		}

	}

}
