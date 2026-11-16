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

package org.springframework.ai.mcp.annotation.method.elicitation;

import java.lang.reflect.Method;
import java.util.function.Function;

import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.common.ModernClientHandlerSupport;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;
import org.springframework.ai.util.JsonHelper;

/**
 * Class for creating Function callbacks around elicitation methods that return Mono.
 *
 * This class provides a way to convert methods annotated with {@link McpElicitation} into
 * callback functions that can be used to handle elicitation requests in a reactive way.
 * It supports methods with a single ElicitRequest parameter.
 *
 * @author Christian Tzolov
 */
public final class AsyncMcpElicitationMethodCallback extends AbstractMcpElicitationMethodCallback
		implements Function<ElicitRequest, Mono<ElicitResult>> {

	private static final JsonHelper jsonHelper = new JsonHelper();

	/** The position of the elicitation request among the parameters of the method. */
	private final int requestParameterIndex;

	/** The position of the {@link ToolContext} parameter, or -1 if there is none. */
	private final int toolContextParameterIndex;

	private final Class<?> requestParameterType;

	/** Whether the method takes the request types of the modern schema. */
	private final boolean modernRequest;

	private final int parameterCount;

	private AsyncMcpElicitationMethodCallback(Builder builder) {
		super(builder.method, builder.bean);
		// The shape of the method, which the validation above has accepted: one request
		// parameter and, optionally, a ToolContext.
		Class<?>[] parameterTypes = this.method.getParameterTypes();
		int requestIndex = 0;
		int toolContextIndex = -1;
		for (int i = 0; i < parameterTypes.length; i++) {
			if (ModernClientHandlerSupport.isToolContextType(parameterTypes[i])) {
				toolContextIndex = i;
			}
			else {
				requestIndex = i;
			}
		}
		this.parameterCount = parameterTypes.length;
		this.requestParameterIndex = requestIndex;
		this.toolContextParameterIndex = toolContextIndex;
		this.requestParameterType = parameterTypes[requestIndex];
		this.modernRequest = ModernClientHandlerSupport.isModernElicitRequestType(this.requestParameterType);
	}

	/**
	 * Apply the callback to the given request.
	 * <p>
	 * This method builds the arguments for the method call, invokes the method, and
	 * returns a Mono that completes with the result.
	 * @param request The elicitation request, must not be null
	 * @return A Mono that completes with the result of the method invocation
	 * @throws McpElicitationMethodException if there is an error invoking the elicitation
	 * method
	 * @throws IllegalArgumentException if the request is null
	 */
	@Override
	public Mono<ElicitResult> apply(ElicitRequest request) {
		if (request == null) {
			return Mono.error(new IllegalArgumentException("Request must not be null"));
		}
		// The request in the type the method takes
		Object argument;
		try {
			argument = this.modernRequest ? ModernClientHandlerSupport.toModernElicitRequest(request) : request;
		}
		catch (Exception e) {
			return Mono.error(new McpElicitationMethodException(
					"Error invoking elicitation method: " + this.method.getName(), e));
		}
		return this.invoke(argument, null).flatMap(this::toLegacyResult);
	}

	/**
	 * Apply the callback to the elicitation request of a modern client.
	 * <p>
	 * A form request is passed to a method of either schema. A URL request can only be
	 * passed to a method that takes the modern types, because the legacy URL request
	 * requires an {@code elicitationId} that the modern one does not have.
	 * @param request The modern elicitation request, must not be null
	 * @return A Mono of the answer of the method, as a modern result. It fails with a
	 * {@link McpElicitationMethodException} if the method cannot be invoked, cannot take
	 * the request, or returns no result
	 * @since 2.1.0
	 */
	public Mono<McpSchema.ElicitResult> applyModern(McpSchema.ElicitRequest request) {
		return this.applyModern(request, null);
	}

	/**
	 * Apply the callback to the elicitation request of a modern client, for a tool call
	 * with the given tool context. The method receives the tool context if it declares a
	 * {@link ToolContext} parameter.
	 * @param request The modern elicitation request, must not be null
	 * @param toolContext The tool context of the tool call that the request belongs to,
	 * or {@code null} if there is none
	 * @return A Mono of the answer of the method, as a modern result. It fails with a
	 * {@link McpElicitationMethodException} if the method cannot be invoked, cannot take
	 * the request, or returns no result
	 * @since 2.1.0
	 */
	public Mono<McpSchema.ElicitResult> applyModern(McpSchema.ElicitRequest request,
			@Nullable ToolContext toolContext) {
		if (request == null) {
			return Mono.error(new IllegalArgumentException("Request must not be null"));
		}
		// The request in the type the method takes
		Object argument;
		try {
			argument = this.modernRequest ? request : ModernClientHandlerSupport.toLegacyElicitRequest(request);
		}
		catch (Exception e) {
			return Mono.error(new McpElicitationMethodException(
					"Error invoking elicitation method: " + this.method.getName(), e));
		}
		// A modern client needs an answer, so no result is a failure.
		return this.invoke(argument, toolContext)
			.flatMap(this::toModernResult)
			.switchIfEmpty(Mono.error(() -> new McpElicitationMethodException(
					"Error invoking elicitation method: " + this.method.getName(),
					new IllegalStateException("The method returned no result"))));
	}

	/**
	 * Invokes the method with the request, which is already of the schema the method
	 * takes, and with the tool context where the method declares one.
	 */
	private Mono<Object> invoke(Object request, @Nullable ToolContext toolContext) {
		try {
			if (!this.requestParameterType.isInstance(request)) {
				// For example a URL request for a method that takes form requests only.
				throw new IllegalArgumentException("The method takes " + this.requestParameterType.getSimpleName()
						+ " and cannot answer a request of type " + request.getClass().getSimpleName());
			}
			Object[] args = new Object[this.parameterCount];
			args[this.requestParameterIndex] = request;
			if (this.toolContextParameterIndex >= 0) {
				args[this.toolContextParameterIndex] = toolContext;
			}
			this.method.setAccessible(true);
			Object result = this.method.invoke(this.bean, args);
			if (result instanceof Mono<?> monoResult) {
				return monoResult.cast(Object.class);
			}
			return Mono.error(new McpElicitationMethodException(
					"Method must return Mono<ElicitResult> or Mono<StructuredElicitResult>: " + this.method.getName()));
		}
		catch (Exception e) {
			return Mono.error(new McpElicitationMethodException(
					"Error invoking elicitation method: " + this.method.getName(), e));
		}
	}

	/**
	 * The answer of the method as a legacy result, whichever result type the method
	 * returns.
	 */
	private Mono<ElicitResult> toLegacyResult(Object value) {
		if (value instanceof StructuredElicitResult<?> structuredElicitResult) {
			var content = structuredElicitResult.structuredContent() != null
					? jsonHelper.convertToMap(structuredElicitResult.structuredContent()) : null;
			return Mono.just(ElicitResult.builder(structuredElicitResult.action())
				.content(content)
				.meta(structuredElicitResult.meta())
				.build());
		}
		if (value instanceof ElicitResult legacyResult) {
			return Mono.just(legacyResult);
		}
		if (value instanceof McpSchema.ElicitResult modernResult) {
			return Mono.just(ModernClientHandlerSupport.toLegacyElicitResult(modernResult));
		}
		return Mono.error(new McpElicitationMethodException(
				"Method must return Mono<ElicitResult> or Mono<StructuredElicitResult>: " + this.method.getName()));
	}

	/**
	 * The answer of the method as a modern result, whichever result type the method
	 * returns.
	 */
	private Mono<McpSchema.ElicitResult> toModernResult(Object value) {
		if (value instanceof McpSchema.ElicitResult modernResult) {
			return Mono.just(modernResult);
		}
		if (value instanceof StructuredElicitResult<?> structuredElicitResult) {
			var content = structuredElicitResult.structuredContent() != null
					? jsonHelper.convertToMap(structuredElicitResult.structuredContent()) : null;
			return Mono.just(new McpSchema.ElicitResult(
					McpSchema.ElicitResult.Action.valueOf(structuredElicitResult.action().name()), content));
		}
		if (value instanceof ElicitResult legacyResult) {
			return Mono.just(ModernClientHandlerSupport.toModernElicitResult(legacyResult));
		}
		return Mono.error(new McpElicitationMethodException(
				"Method must return Mono<ElicitResult> or Mono<StructuredElicitResult>: " + this.method.getName()));
	}

	/**
	 * Accepts a {@link ToolContext} parameter next to the elicitation request.
	 */
	@Override
	protected void validateParameters(Method method) {
		if (!ModernClientHandlerSupport.takesToolContext(method)) {
			super.validateParameters(method);
			return;
		}
		Class<?>[] parameterTypes = method.getParameterTypes();
		boolean requestAndToolContext = parameterTypes.length == 2
				&& (this.isRequestType(parameterTypes[0]) || this.isRequestType(parameterTypes[1]));
		if (!requestAndToolContext) {
			throw new IllegalArgumentException(
					"Method must have an ElicitRequest parameter and, optionally, a ToolContext parameter: "
							+ method.getName() + " in " + method.getDeclaringClass().getName());
		}
	}

	/**
	 * Accepts the elicitation request types of the modern schema in addition to those of
	 * the legacy protocol.
	 */
	@Override
	protected boolean isRequestType(Class<?> paramType) {
		return super.isRequestType(paramType) || ModernClientHandlerSupport.isModernElicitRequestType(paramType);
	}

	/**
	 * Validates that the method return type is compatible with the elicitation callback.
	 * @param method The method to validate
	 * @throws IllegalArgumentException if the return type is not compatible
	 */
	@Override
	protected void validateReturnType(Method method) {
		Class<?> returnType = method.getReturnType();

		if (!Mono.class.isAssignableFrom(returnType)) {
			throw new IllegalArgumentException(
					"Method must return Mono<ElicitResult> or Mono<StructuredElicitResult>: " + method.getName()
							+ " in " + method.getDeclaringClass().getName() + " returns " + returnType.getName());
		}
	}

	/**
	 * Checks if a parameter type is compatible with the exchange type.
	 * @param paramType The parameter type to check
	 * @return true if the parameter type is compatible with the exchange type, false
	 * otherwise
	 */
	@Override
	protected boolean isExchangeType(Class<?> paramType) {
		// No exchange type for elicitation methods
		return false;
	}

	/**
	 * Create a new builder.
	 * @return A new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for creating AsyncMcpElicitationMethodCallback instances.
	 * <p>
	 * This builder provides a fluent API for constructing
	 * AsyncMcpElicitationMethodCallback instances with the required parameters.
	 */
	public final static class Builder extends AbstractBuilder<Builder, AsyncMcpElicitationMethodCallback> {

		/**
		 * Build the callback.
		 * @return A new AsyncMcpElicitationMethodCallback instance
		 */
		@Override
		public AsyncMcpElicitationMethodCallback build() {
			validate();
			return new AsyncMcpElicitationMethodCallback(this);
		}

	}

}
