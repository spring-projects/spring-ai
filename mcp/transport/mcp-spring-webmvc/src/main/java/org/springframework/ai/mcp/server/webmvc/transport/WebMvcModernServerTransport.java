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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.JsonRpc;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCMessage;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCNotification;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCRequest;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCResponse;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCResponse.JSONRPCError;
import io.modelcontextprotocol.modern.McpSchema.ErrorCodes;
import io.modelcontextprotocol.modern.server.McpRequestManager;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpTransportResponse;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletRequestUtils;
import io.modelcontextprotocol.server.transport.ServerHttpHeaderValidator;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.function.ServerResponse.SseBuilder;

/**
 * Spring WebMVC transport for a 2026-07-28 (modern) {@link McpRequestManager}: stateless,
 * POST only, one self-contained request or notification per call. Requests are resolved
 * on the container thread through {@link McpRequestManager#handleBlocking}, so
 * thread-locals set by servlet filters stay visible to sync handlers;
 * {@code subscriptions/listen} is served asynchronously through
 * {@link McpRequestManager#handle}.
 * <p>
 * Authentication and authorization belong in filters in front of the router function.
 * Filters may decide on the {@code Mcp-Method} and {@code Mcp-Name} headers: a request
 * whose headers disagree with its body is rejected before dispatch.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public final class WebMvcModernServerTransport {

	private static final Log logger = LogFactory.getLog(WebMvcModernServerTransport.class);

	private static final int DEFAULT_REQUEST_MAX_SIZE = 16 * 1024 * 1024;

	private static final Duration DEFAULT_KEEP_ALIVE_INTERVAL = Duration.ofSeconds(30);

	private final McpRequestManager requestManager;

	private final McpJsonMapper jsonMapper;

	private final String mcpEndpoint;

	private final McpTransportContextExtractor<ServerRequest> contextExtractor;

	private final int requestMaxSize;

	private final ServerHttpHeaderValidator httpHeaderValidator;

	private final @Nullable Duration keepAliveInterval;

	private final Map<Integer, Integer> errorStatuses;

	private final RouterFunction<ServerResponse> routerFunction;

	private volatile boolean closing = false;

	private WebMvcModernServerTransport(McpRequestManager requestManager, McpJsonMapper jsonMapper, String mcpEndpoint,
			McpTransportContextExtractor<ServerRequest> contextExtractor, int requestMaxSize,
			ServerHttpHeaderValidator httpHeaderValidator, @Nullable Duration keepAliveInterval,
			Map<Integer, Integer> errorStatuses) {
		this.requestManager = requestManager;
		this.jsonMapper = jsonMapper;
		this.mcpEndpoint = mcpEndpoint;
		this.contextExtractor = contextExtractor;
		this.requestMaxSize = requestMaxSize;
		this.httpHeaderValidator = httpHeaderValidator;
		this.keepAliveInterval = keepAliveInterval;
		this.errorStatuses = errorStatuses;
		this.routerFunction = RouterFunctions.route()
			.POST(this.mcpEndpoint, this::handlePost)
			.route(RequestPredicates.path(this.mcpEndpoint), this::rejectMethod)
			.build();
	}

	public static Builder builder(McpRequestManager requestManager) {
		return new Builder(requestManager);
	}

	/**
	 * The router function serving the MCP endpoint, to be registered with Spring WebMVC.
	 * @return the configured {@link RouterFunction}
	 */
	public RouterFunction<ServerResponse> getRouterFunction() {
		return this.routerFunction;
	}

	/**
	 * Stop accepting new requests. If the request manager is a {@link McpServer}, also
	 * asks it to end active {@code subscriptions/listen} streams gracefully.
	 * @return a Mono completing once new requests are refused
	 */
	public Mono<Void> closeGracefully() {
		return Mono.fromRunnable(() -> {
			this.closing = true;
			if (this.requestManager instanceof McpServer server) {
				server.closeGracefully();
			}
		});
	}

	private ServerResponse rejectMethod(ServerRequest request) {
		return ServerResponse.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST").build();
	}

	private ServerResponse handlePost(ServerRequest request) {
		if (this.closing) {
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("Server is shutting down");
		}
		try {
			this.httpHeaderValidator.validate(ModernTransportSupport.headerAccessor(request));
		}
		catch (ServerTransportSecurityException e) {
			String message = e.getMessage() != null ? e.getMessage() : "";
			return ServerResponse.status(e.getStatusCode()).body(message);
		}
		String contentType = request.headers().contentType().map(MediaType::toString).orElse(null);
		if (!HttpServletRequestUtils.isJsonContentType(contentType)) {
			return ServerResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
				.body("Unsupported Media Type: Content-Type must be application/json");
		}
		if (request.headers().asHttpHeaders().getContentLength() > this.requestMaxSize) {
			return ServerResponse.status(HttpStatus.CONTENT_TOO_LARGE).build();
		}

		McpTransportContext transportContext = this.contextExtractor.extract(request);

		String body;
		try {
			body = readBody(request.servletRequest(), this.requestMaxSize);
		}
		catch (BodyTooLargeException e) {
			return ServerResponse.status(HttpStatus.CONTENT_TOO_LARGE).build();
		}
		catch (IOException | RuntimeException e) {
			logger.debug("Failed to read request body: " + e.getMessage());
			return ServerResponse.badRequest().build();
		}

		JSONRPCMessage message;
		try {
			message = JsonRpc.deserializeMessage(this.jsonMapper, body);
		}
		catch (IOException e) {
			return error(JSONRPCResponse.error(null, new JSONRPCError(ErrorCodes.PARSE_ERROR, "Parse error")));
		}
		catch (JsonRpc.InvalidMessageException e) {
			return error(JSONRPCResponse.error(e.id(),
					new JSONRPCError(ErrorCodes.INVALID_REQUEST, "Invalid JSON-RPC message")));
		}

		if (message instanceof JSONRPCNotification notification) {
			try {
				this.requestManager.handleNotification(transportContext, notification)
					.contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext))
					.block();
			}
			catch (Exception e) {
				logger.error("Failed to handle notification " + notification.method(), e);
				return error(
						JSONRPCResponse.error(null, new JSONRPCError(ErrorCodes.INTERNAL_ERROR, "Internal error")));
			}
			return ServerResponse.accepted().build();
		}
		if (!(message instanceof JSONRPCRequest jsonRpcRequest)) {
			return error(JSONRPCResponse.error(null, new JSONRPCError(ErrorCodes.INVALID_REQUEST,
					"The server accepts either requests or notifications")));
		}

		String headerMismatch = ModernTransportSupport
			.validateHeaders(name -> request.headers().asHttpHeaders().getFirst(name), jsonRpcRequest);
		if (headerMismatch != null) {
			return error(JSONRPCResponse.error(jsonRpcRequest.id(),
					new JSONRPCError(ErrorCodes.HEADER_MISMATCH, headerMismatch)));
		}

		boolean listen = ModernTransportSupport.isListen(jsonRpcRequest);
		Mono<McpTransportResponse> resolved = listen ? this.requestManager.handle(transportContext, jsonRpcRequest)
				: this.requestManager.handleBlocking(transportContext, jsonRpcRequest);
		McpTransportResponse transportResponse;
		try {
			transportResponse = resolved.contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext))
				.block();
		}
		catch (Exception e) {
			logger.error("Failed to handle request " + jsonRpcRequest.method(), e);
			return error(JSONRPCResponse.error(jsonRpcRequest.id(),
					new JSONRPCError(ErrorCodes.INTERNAL_ERROR, "Internal error")));
		}

		if (transportResponse instanceof McpTransportResponse.Result result) {
			return json(HttpStatus.OK.value(), result.response());
		}
		if (transportResponse instanceof McpTransportResponse.Error error) {
			return error(error.response());
		}
		if (transportResponse instanceof McpTransportResponse.Streaming streaming) {
			return listen ? streamOverAsyncContext(transportContext, streaming)
					: streamOnContainerThread(transportContext, streaming);
		}
		return error(JSONRPCResponse.error(jsonRpcRequest.id(),
				new JSONRPCError(ErrorCodes.INTERNAL_ERROR, "Internal error")));
	}

	// Blocking on the container thread lets sync handlers run inside this subscription.
	// A client disconnect fails a write and cancels the stream, but blocking handler code
	// can't be interrupted: it runs to completion, its output discarded.
	private ServerResponse streamOnContainerThread(McpTransportContext transportContext,
			McpTransportResponse.Streaming streaming) {
		return sse(sseBuilder -> {
			try {
				commitHeaders(sseBuilder);
				ModernTransportSupport.frames(streaming.messages(), this.keepAliveInterval)
					.contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext))
					.doOnNext(frame -> writeFrame(sseBuilder, frame))
					.blockLast();
			}
			catch (RuntimeException ex) {
				logger.debug("Streaming response ended early: " + ex.getMessage());
			}
			finally {
				complete(sseBuilder);
			}
		}, null);
	}

	private ServerResponse streamOverAsyncContext(McpTransportContext transportContext,
			McpTransportResponse.Streaming streaming) {
		return sse(sseBuilder -> {
			commitHeaders(sseBuilder);
			Disposable.Swap subscription = Disposables.swap();
			sseBuilder.onTimeout(subscription::dispose);
			sseBuilder.onError(error -> subscription.dispose());
			sseBuilder.onComplete(subscription::dispose);
			subscription.update(ModernTransportSupport.frames(streaming.messages(), this.keepAliveInterval)
				.publishOn(Schedulers.boundedElastic())
				.contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext))
				.doOnNext(frame -> writeFrame(sseBuilder, frame))
				.doOnError(error -> logger.debug("Listen stream ended early: " + error.getMessage()))
				.onErrorComplete()
				.subscribe(null, null, () -> complete(sseBuilder)));
		}, Duration.ZERO);
	}

	private ServerResponse sse(Consumer<SseBuilder> consumer, @Nullable Duration timeout) {
		ServerResponse response = timeout != null ? ServerResponse.sse(consumer, timeout)
				: ServerResponse.sse(consumer);
		return new EventStreamResponse(response);
	}

	private void writeFrame(SseBuilder sseBuilder, Object frame) {
		try {
			if (frame == ModernTransportSupport.KEEP_ALIVE) {
				sseBuilder.comment("").send();
			}
			else {
				sseBuilder.event(ModernTransportSupport.SSE_EVENT_MESSAGE)
					.data(ModernTransportSupport.toJson(this.jsonMapper, frame));
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * Sends an empty comment so that the status line and headers reach the client before
	 * the handler produces its first message.
	 */
	private static void commitHeaders(SseBuilder sseBuilder) {
		try {
			sseBuilder.comment("").send();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static void complete(SseBuilder sseBuilder) {
		try {
			sseBuilder.complete();
		}
		catch (RuntimeException ex) {
			logger.debug("Failed to complete the event stream: " + ex.getMessage());
		}
	}

	// Reads the body in bounded chunks, as the SDK servlet transport does, so that a
	// chunked request without a Content-Length is capped by maxRequestSize too.
	private static String readBody(HttpServletRequest request, int maxSize) throws IOException, BodyTooLargeException {
		InputStream inputStream = request.getInputStream();
		ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int totalBytes = 0;
		int readBytes;
		while ((readBytes = inputStream.read(buf, 0, buf.length)) != -1) {
			totalBytes += readBytes;
			if (totalBytes > maxSize) {
				throw new BodyTooLargeException();
			}
			bodyBytes.write(buf, 0, readBytes);
		}
		String charset = request.getCharacterEncoding() != null ? request.getCharacterEncoding()
				: StandardCharsets.UTF_8.name();
		return bodyBytes.toString(charset);
	}

	private ServerResponse error(JSONRPCResponse jsonRpcResponse) {
		return json(ModernTransportSupport.statusFor(this.errorStatuses, jsonRpcResponse), jsonRpcResponse);
	}

	private ServerResponse json(int status, JSONRPCResponse jsonRpcResponse) {
		return ServerResponse.status(status)
			.contentType(MediaType.APPLICATION_JSON)
			.body(ModernTransportSupport.toJson(this.jsonMapper, jsonRpcResponse));
	}

	/**
	 * Builds a {@link WebMvcModernServerTransport}.
	 */
	public static final class Builder {

		private final McpRequestManager requestManager;

		private @Nullable McpJsonMapper jsonMapper;

		private String mcpEndpoint = "/mcp";

		private McpTransportContextExtractor<ServerRequest> contextExtractor = request -> McpTransportContext.EMPTY;

		private int requestMaxSize = DEFAULT_REQUEST_MAX_SIZE;

		private ServerHttpHeaderValidator httpHeaderValidator = DefaultServerTransportSecurityValidator.builder()
			.build();

		private @Nullable Duration keepAliveInterval = DEFAULT_KEEP_ALIVE_INTERVAL;

		private final Map<Integer, Integer> errorStatuses = new HashMap<>(
				ModernTransportSupport.DEFAULT_ERROR_STATUSES);

		private Builder(McpRequestManager requestManager) {
			Assert.notNull(requestManager, "requestManager must not be null");
			this.requestManager = requestManager;
		}

		public Builder jsonMapper(McpJsonMapper jsonMapper) {
			Assert.notNull(jsonMapper, "jsonMapper must not be null");
			this.jsonMapper = jsonMapper;
			return this;
		}

		public Builder messageEndpoint(String messageEndpoint) {
			Assert.hasText(messageEndpoint, "messageEndpoint must not be empty");
			this.mcpEndpoint = messageEndpoint;
			return this;
		}

		/**
		 * Extracts the {@link McpTransportContext} handlers see through
		 * {@code McpRequestContext#transportContext()}. Invoked once per POST, on the
		 * container thread, before the request is resolved.
		 * @param contextExtractor the extractor
		 * @return this builder
		 */
		public Builder contextExtractor(McpTransportContextExtractor<ServerRequest> contextExtractor) {
			Assert.notNull(contextExtractor, "contextExtractor must not be null");
			this.contextExtractor = contextExtractor;
			return this;
		}

		public Builder maxRequestSize(int requestMaxSize) {
			Assert.isTrue(requestMaxSize > 0, "requestMaxSize must be positive");
			this.requestMaxSize = requestMaxSize;
			return this;
		}

		/**
		 * Validates the headers of every POST before it is read, e.g. Host/Origin checks
		 * against DNS rebinding. A rejection is answered with the exception's status
		 * code. Defaults to the SDK's {@link DefaultServerTransportSecurityValidator},
		 * which rejects every request that carries an {@code Origin} header unless that
		 * origin is allowed; pass {@link ServerHttpHeaderValidator#NOOP} to disable the
		 * check.
		 * @param httpHeaderValidator the validator
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
		 * @param keepAliveInterval the interval, or {@code null}
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

		public WebMvcModernServerTransport build() {
			McpJsonMapper mapper = this.jsonMapper != null ? this.jsonMapper : McpJsonDefaults.getMapper();
			return new WebMvcModernServerTransport(this.requestManager, mapper, this.mcpEndpoint, this.contextExtractor,
					this.requestMaxSize, this.httpHeaderValidator, this.keepAliveInterval,
					Map.copyOf(this.errorStatuses));
		}

	}

	/**
	 * Adds the no-buffering headers an event stream needs before the SSE response starts
	 * writing; {@link ServerResponse#from} would drop the streaming body.
	 */
	private static final class EventStreamResponse implements ServerResponse {

		private final ServerResponse delegate;

		private EventStreamResponse(ServerResponse delegate) {
			this.delegate = delegate;
		}

		@Override
		public HttpStatusCode statusCode() {
			return this.delegate.statusCode();
		}

		@Override
		public HttpHeaders headers() {
			return this.delegate.headers();
		}

		@Override
		public MultiValueMap<String, Cookie> cookies() {
			return this.delegate.cookies();
		}

		@Override
		public @Nullable ModelAndView writeTo(HttpServletRequest request, HttpServletResponse response, Context context)
				throws ServletException, IOException {
			response.setHeader("Cache-Control", "no-cache, no-transform");
			response.setHeader("X-Accel-Buffering", "no");
			return this.delegate.writeTo(request, response, context);
		}

	}

	private static final class BodyTooLargeException extends Exception {

	}

}
