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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import io.modelcontextprotocol.util.Assert;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Implementation of a WebFlux based {@link McpStatelessServerTransport}.
 *
 * @author Dariusz Jędrzejczyk
 */
public final class WebFluxStatelessServerTransport implements McpStatelessServerTransport {

	private static final Logger logger = LoggerFactory.getLogger(WebFluxStatelessServerTransport.class);

	private final McpJsonMapper jsonMapper;

	private final String mcpEndpoint;

	private final RouterFunction<?> routerFunction;

	private @Nullable McpStatelessServerHandler mcpHandler;

	private McpTransportContextExtractor<ServerRequest> contextExtractor;

	private volatile boolean isClosing = false;

	/**
	 * Security validator for validating HTTP requests.
	 */
	private final ServerTransportSecurityValidator securityValidator;

	private WebFluxStatelessServerTransport(McpJsonMapper jsonMapper, String mcpEndpoint,
			McpTransportContextExtractor<ServerRequest> contextExtractor,
			ServerTransportSecurityValidator securityValidator) {
		Assert.notNull(jsonMapper, "jsonMapper must not be null");
		Assert.notNull(mcpEndpoint, "mcpEndpoint must not be null");
		Assert.notNull(contextExtractor, "contextExtractor must not be null");
		Assert.notNull(securityValidator, "Security validator must not be null");

		this.jsonMapper = jsonMapper;
		this.mcpEndpoint = mcpEndpoint;
		this.contextExtractor = contextExtractor;
		this.securityValidator = securityValidator;
		this.routerFunction = RouterFunctions.route()
			.GET(this.mcpEndpoint, this::handleGet)
			.POST(this.mcpEndpoint, this::handlePost)
			.build();
	}

	@Override
	public void setMcpHandler(McpStatelessServerHandler mcpHandler) {
		this.mcpHandler = mcpHandler;
	}

	@Override
	public Mono<Void> closeGracefully() {
		return Mono.fromRunnable(() -> this.isClosing = true);
	}

	/**
	 * Returns the WebFlux router function that defines the transport's HTTP endpoints.
	 * This router function should be integrated into the application's web configuration.
	 *
	 * <p>
	 * The router function defines one endpoint handling two HTTP methods:
	 * <ul>
	 * <li>GET {messageEndpoint} - Unsupported, returns 405 METHOD NOT ALLOWED</li>
	 * <li>POST {messageEndpoint} - For handling client requests and notifications</li>
	 * </ul>
	 * @return The configured {@link RouterFunction} for handling HTTP requests
	 */
	public RouterFunction<?> getRouterFunction() {
		return this.routerFunction;
	}

	private Mono<ServerResponse> handleGet(ServerRequest request) {
		return ServerResponse.status(HttpStatus.METHOD_NOT_ALLOWED).build();
	}

	private Mono<ServerResponse> handlePost(ServerRequest request) {
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

		List<MediaType> acceptHeaders = request.headers().asHttpHeaders().getAccept();
		if (!(acceptHeaders.contains(MediaType.APPLICATION_JSON)
				&& acceptHeaders.contains(MediaType.TEXT_EVENT_STREAM))) {
			return ServerResponse.badRequest().build();
		}

		return request.bodyToMono(String.class).<ServerResponse>flatMap(body -> {
			try {
				McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(this.jsonMapper, body);

				if (message instanceof McpSchema.JSONRPCRequest jsonrpcRequest) {
					return Objects.requireNonNull(this.mcpHandler, "mcpHandler must be set before use")
						.handleRequest(transportContext, jsonrpcRequest)
						.flatMap(jsonrpcResponse -> {
							try {
								String json = this.jsonMapper.writeValueAsString(jsonrpcResponse);
								return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(json);
							}
							catch (IOException e) {
								logger.error("Failed to serialize response: {}", e.getMessage());
								return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
									.bodyValue(new McpError("Failed to serialize response"));
							}
						});
				}
				else if (message instanceof McpSchema.JSONRPCNotification jsonrpcNotification) {
					return Objects.requireNonNull(this.mcpHandler, "mcpHandler must be set before use")
						.handleNotification(transportContext, jsonrpcNotification)
						.then(ServerResponse.accepted().build());
				}
				else {
					return ServerResponse.badRequest()
						.bodyValue(new McpError("The server accepts either requests or notifications"));
				}
			}
			catch (IllegalArgumentException | IOException e) {
				logger.error("Failed to deserialize message: {}", e.getMessage());
				return ServerResponse.badRequest().bodyValue(new McpError("Invalid message format"));
			}
		}).contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext));
	}

	/**
	 * Create a builder for the server.
	 * @return a fresh {@link Builder} instance.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for creating instances of {@link WebFluxStatelessServerTransport}.
	 * <p>
	 * This builder provides a fluent API for configuring and creating instances of
	 * WebFluxSseServerTransportProvider with custom settings.
	 */
	public static final class Builder {

		private @Nullable McpJsonMapper jsonMapper;

		private String mcpEndpoint = "/mcp";

		private McpTransportContextExtractor<ServerRequest> contextExtractor = serverRequest -> McpTransportContext.EMPTY;

		private ServerTransportSecurityValidator securityValidator = ServerTransportSecurityValidator.NOOP;

		private Builder() {
			// used by a static method
		}

		/**
		 * Sets the JsonMapper to use for JSON serialization/deserialization of MCP
		 * messages.
		 * @param jsonMapper The JsonMapper instance. Must not be null.
		 * @return this builder instance
		 * @throws IllegalArgumentException if jsonMapper is null
		 */
		public Builder jsonMapper(McpJsonMapper jsonMapper) {
			Assert.notNull(jsonMapper, "JsonMapper must not be null");
			this.jsonMapper = jsonMapper;
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
			this.mcpEndpoint = messageEndpoint;
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
			Assert.notNull(contextExtractor, "Context extractor must not be null");
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
		 * Builds a new instance of {@link WebFluxStatelessServerTransport} with the
		 * configured settings.
		 * @return A new WebFluxSseServerTransportProvider instance
		 * @throws IllegalStateException if required parameters are not set
		 */
		public WebFluxStatelessServerTransport build() {
			Assert.notNull(this.mcpEndpoint, "Message endpoint must be set");
			return new WebFluxStatelessServerTransport(
					this.jsonMapper == null ? McpJsonDefaults.getMapper() : this.jsonMapper, this.mcpEndpoint,
					this.contextExtractor, this.securityValidator);
		}

	}

}
