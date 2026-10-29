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

package org.springframework.ai.mcp.annotation.method.resource;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema.CacheScope;
import io.modelcontextprotocol.modern.McpSchema.ReadResourceOutcome;
import io.modelcontextprotocol.modern.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncNotifier;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.ResourceContents;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpProgressToken;
import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.context.McpModernSyncRequestContext;
import org.springframework.ai.mcp.modern.McpSchemaConverter;
import org.springframework.ai.mcp.modern.SyncResourceSpecification;

/**
 * Serves a {@code resources/read} of a 2026-07-28 ("modern") MCP server by invoking a
 * method annotated with {@code @McpResource}.
 * <p>
 * Beyond the parameters the stateless callback supports, a method may declare
 * {@link McpModernSyncRequestContext}, the SDK {@link McpRequestContext},
 * {@link McpSyncNotifier} or the modern {@link ReadResourceRequest} (which carries the
 * MRTR fields). A method that declares the modern context or the notifier is served as a
 * streaming response so that progress notifications can be pushed before the result.
 * <p>
 * The method may return a modern {@link ReadResourceOutcome} (a
 * {@code ReadResourceResult}, possibly with cache hints, or an
 * {@code InputRequiredResult}) or any of the legacy return types, which are converted. A
 * converted legacy result carries the {@link #ttlMs()} and {@link #cacheScope()} hints
 * configured on the callback (from {@code @McpResource}); a modern result is passed
 * through untouched and its own hints win.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public final class SyncModernMcpResourceMethodCallback extends AbstractMcpResourceMethodCallback
		implements SyncResourceSpecification.Handler {

	private final boolean streaming;

	private final long ttlMs;

	private final CacheScope cacheScope;

	private SyncModernMcpResourceMethodCallback(Builder builder) {
		super(builder.method, builder.bean, builder.uri, builder.name, builder.description, builder.mimeType,
				builder.resultConverter, builder.uriTemplateManagerFactory, builder.contentType, builder.meta);
		this.validateMethod(this.method);
		this.streaming = Arrays.stream(this.method.getParameterTypes())
			.anyMatch(ModernMethodSupport::isStreamingParameter);
		this.ttlMs = builder.ttlMs;
		this.cacheScope = builder.cacheScope;
	}

	/**
	 * Whether a parameter of this type is injected by the modern callback rather than
	 * bound from the request URI.
	 */
	public static boolean isModernInfrastructureParameter(Class<?> parameterType) {
		return ModernMethodSupport.isInfrastructureParameter(parameterType, ReadResourceRequest.class);
	}

	public boolean isStreaming() {
		return this.streaming;
	}

	/**
	 * The {@code ttlMs} cache hint applied to converted legacy results.
	 */
	public long ttlMs() {
		return this.ttlMs;
	}

	/**
	 * The {@code cacheScope} cache hint applied to converted legacy results.
	 */
	public CacheScope cacheScope() {
		return this.cacheScope;
	}

	@Override
	public McpSyncResponse<ReadResourceOutcome> read(McpRequestContext context, ReadResourceRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Request must not be null");
		}
		if (this.streaming) {
			return McpSyncResponse.streaming(notifier -> invoke(context, request, notifier));
		}
		return McpSyncResponse.result(invoke(context, request, null));
	}

	private ReadResourceOutcome invoke(McpRequestContext context, ReadResourceRequest request,
			@Nullable McpSyncNotifier notifier) {
		Map<String, String> uriVariableValues = this.uriTemplateManager.extractVariableValues(request.uri());
		if (!this.uriVariables.isEmpty() && uriVariableValues.size() != this.uriVariables.size()) {
			throw McpException.invalidParams("Failed to extract all URI variables from request URI: " + request.uri()
					+ ". Expected variables: " + this.uriVariables + ", but found: " + uriVariableValues.keySet());
		}
		try {
			Object[] args = buildArguments(context, request, uriVariableValues, notifier);
			this.method.setAccessible(true);
			Object result = this.method.invoke(this.bean, args);
			if (result instanceof ReadResourceOutcome outcome) {
				ModernMethodSupport.checkClientCapabilities(context, outcome);
				return outcome;
			}
			io.modelcontextprotocol.modern.McpSchema.ReadResourceResult converted = McpSchemaConverter
				.toModern(this.resultConverter.convertToReadResourceResult(result, request.uri(), this.mimeType,
						this.contentType, this.meta));
			return io.modelcontextprotocol.modern.McpSchema.ReadResourceResult.builder(converted.contents())
				.ttlMs(this.ttlMs)
				.cacheScope(this.cacheScope)
				.meta(converted.meta())
				.build();
		}
		catch (McpException e) {
			throw e;
		}
		catch (Exception e) {
			throw ModernMethodSupport.translate(e, "resource", this.method, this.bean);
		}
	}

	private Object[] buildArguments(McpRequestContext context, ReadResourceRequest request,
			Map<String, String> uriVariableValues, @Nullable McpSyncNotifier notifier) {
		Parameter[] parameters = this.method.getParameters();
		Object[] args = new Object[parameters.length];
		io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest legacyRequest = McpSchemaConverter.toLegacy(request);

		// The injected parameters are filled first so that the URI variable binding of
		// the base class only sees the remaining slots.
		ModernMethodSupport.injectContextArguments(args, parameters, context, request.inputResponses(),
				request.requestState(), notifier);
		for (int i = 0; i < parameters.length; i++) {
			Class<?> type = parameters[i].getType();
			if (parameters[i].isAnnotationPresent(McpProgressToken.class)) {
				args[i] = legacyRequest.progressToken();
			}
			else if (McpMeta.class.isAssignableFrom(type)) {
				args[i] = new McpMeta(legacyRequest.meta());
			}
			else if (ReadResourceRequest.class.isAssignableFrom(type)) {
				args[i] = request;
			}
		}
		if (!this.uriVariables.isEmpty()) {
			this.buildArgsWithUriVariables(parameters, args, context, legacyRequest, uriVariableValues);
		}
		else {
			this.buildArgsWithoutUriVariables(parameters, args, context, legacyRequest);
		}
		return args;
	}

	@Override
	protected void validateMethod(Method method) {
		if (method == null) {
			throw new IllegalArgumentException("Method must not be null");
		}
		this.validateReturnType(method);

		Set<Class<?>> injected = new HashSet<>();
		boolean hasLegacyRequest = false;
		int uriParameters = 0;
		for (Parameter parameter : method.getParameters()) {
			Class<?> type = parameter.getType();
			if (parameter.isAnnotationPresent(McpProgressToken.class) || McpMeta.class.isAssignableFrom(type)) {
				continue;
			}
			ModernMethodSupport.rejectLegacyContextParameter(type, method);
			if (isExchangeOrContextType(type)) {
				if (!injected.add(type)) {
					throw new IllegalArgumentException("Method cannot have more than one parameter of type "
							+ type.getName() + ": " + method.getName() + " in " + method.getDeclaringClass().getName());
				}
			}
			else if (io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest.class.isAssignableFrom(type)) {
				if (hasLegacyRequest) {
					throw new IllegalArgumentException(
							"Method cannot have more than one ReadResourceRequest parameter: " + method.getName()
									+ " in " + method.getDeclaringClass().getName());
				}
				hasLegacyRequest = true;
			}
			else if (String.class.isAssignableFrom(type)) {
				uriParameters++;
			}
			else {
				throw new IllegalArgumentException(
						"Modern resource method parameters must be a request context, McpTransportContext, "
								+ "ReadResourceRequest, String, McpMeta or @McpProgressToken: " + method.getName()
								+ " in " + method.getDeclaringClass().getName() + " has parameter of type "
								+ type.getName());
			}
		}
		if (this.uriVariables.isEmpty() && uriParameters > 1) {
			throw new IllegalArgumentException(
					"Method can have at most one String parameter (bound to the request URI) when no URI variables are present: "
							+ method.getName() + " in " + method.getDeclaringClass().getName());
		}
		if (!this.uriVariables.isEmpty() && uriParameters != this.uriVariables.size()) {
			throw new IllegalArgumentException("Method must have one String parameter per URI variable. Expected "
					+ this.uriVariables.size() + " but found " + uriParameters + ": " + method.getName() + " in "
					+ method.getDeclaringClass().getName() + ". URI variables: " + this.uriVariables);
		}
	}

	@Override
	protected void validateReturnType(Method method) {
		Class<?> returnType = method.getReturnType();
		boolean valid = ReadResourceOutcome.class.isAssignableFrom(returnType)
				|| ReadResourceResult.class.isAssignableFrom(returnType) || List.class.isAssignableFrom(returnType)
				|| ResourceContents.class.isAssignableFrom(returnType) || String.class.isAssignableFrom(returnType);
		if (!valid) {
			throw new IllegalArgumentException("Method must return either a modern ReadResourceOutcome, "
					+ "ReadResourceResult, List<ResourceContents>, List<String>, ResourceContents, or String: "
					+ method.getName() + " in " + method.getDeclaringClass().getName() + " returns "
					+ returnType.getName());
		}
	}

	@Override
	protected Object assignExchangeType(Class<?> paramType, Object exchange) {
		return ((McpRequestContext) exchange).transportContext();
	}

	@Override
	protected boolean isExchangeOrContextType(Class<?> paramType) {
		return McpTransportContext.class.isAssignableFrom(paramType) || isModernInfrastructureParameter(paramType);
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link SyncModernMcpResourceMethodCallback} instances.
	 */
	public static final class Builder extends AbstractBuilder<Builder, SyncModernMcpResourceMethodCallback> {

		private long ttlMs = 0L;

		private CacheScope cacheScope = CacheScope.PRIVATE;

		private Builder() {
			this.resultConverter = new DefaultMcpReadResourceResultConverter();
		}

		@Override
		protected boolean supportsCacheHints() {
			return true;
		}

		/**
		 * Sets the {@code ttlMs} cache hint applied when a legacy return value is
		 * converted. Must not be negative; {@code 0} (the default) means not cacheable.
		 */
		public Builder ttlMs(long ttlMs) {
			if (ttlMs < 0) {
				throw new IllegalArgumentException("ttlMs must not be negative");
			}
			this.ttlMs = ttlMs;
			return this;
		}

		/**
		 * Sets the {@code cacheScope} cache hint applied when a legacy return value is
		 * converted. Defaults to {@link CacheScope#PRIVATE}.
		 */
		public Builder cacheScope(CacheScope cacheScope) {
			if (cacheScope == null) {
				throw new IllegalArgumentException("cacheScope must not be null");
			}
			this.cacheScope = cacheScope;
			return this;
		}

		@Override
		public SyncModernMcpResourceMethodCallback build() {
			validate();
			return new SyncModernMcpResourceMethodCallback(this);
		}

	}

}
