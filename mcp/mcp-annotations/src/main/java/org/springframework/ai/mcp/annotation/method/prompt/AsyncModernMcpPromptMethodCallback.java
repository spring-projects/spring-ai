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

package org.springframework.ai.mcp.annotation.method.prompt;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema.GetPromptOutcome;
import io.modelcontextprotocol.modern.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.modern.server.McpAsyncNotifier;
import io.modelcontextprotocol.modern.server.McpAsyncResponse;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.feature.McpAsyncPrompt;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.common.ModernReactiveSupport;
import org.springframework.ai.mcp.annotation.context.McpModernRequestContext;
import org.springframework.ai.mcp.modern.McpModernAsyncUtils;
import org.springframework.ai.mcp.modern.McpSchemaConverter;

/**
 * The asynchronous twin of {@link SyncModernMcpPromptMethodCallback}: serves a
 * {@code prompts/get} of a 2026-07-28 ("modern") MCP server by invoking a method
 * annotated with {@code @McpPrompt}.
 * <p>
 * A method may declare {@link McpModernRequestContext}, the SDK
 * {@link McpRequestContext}, {@link McpAsyncNotifier} or the modern
 * {@link GetPromptRequest} (which carries the MRTR fields). A method that declares the
 * notifier is served as a streaming response so that progress notifications can be pushed
 * before the result; the context alone leaves the response a plain result.
 * <p>
 * The method may return a {@code Mono}, a {@code CompletionStage} or a {@code Publisher}
 * of a modern {@link GetPromptOutcome} or of any legacy return type, which is converted.
 * A declared {@code Flux} is rejected at startup, and a publisher that emits more than
 * one element fails the call; see {@link ModernReactiveSupport}. A plain, blocking return
 * value runs through {@link McpModernAsyncUtils#fromBlocking}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class AsyncModernMcpPromptMethodCallback extends AbstractMcpPromptMethodCallback
		implements McpAsyncPrompt.Handler {

	private final boolean streaming;

	// Set once the runtime capability check has warned about this method.
	private final AtomicBoolean capabilityWarned = new AtomicBoolean();

	private final @Nullable McpJsonMapper jsonMapper;

	private AsyncModernMcpPromptMethodCallback(Builder builder) {
		super(builder.method, builder.bean, builder.prompt);
		this.method.setAccessible(true);
		this.streaming = Arrays.stream(this.method.getParameterTypes())
			.anyMatch(ModernMethodSupport::isAsyncStreamingParameter);
		this.jsonMapper = builder.jsonMapper;
	}

	/**
	 * Whether a parameter of this type is injected by the modern callback rather than
	 * bound from the prompt arguments.
	 */
	public static boolean isModernInfrastructureParameter(Class<?> parameterType) {
		return ModernMethodSupport.isInfrastructureParameter(parameterType, GetPromptRequest.class);
	}

	public boolean isStreaming() {
		return this.streaming;
	}

	@Override
	public Mono<McpAsyncResponse<GetPromptOutcome>> get(McpRequestContext context, GetPromptRequest request) {
		if (request == null) {
			return Mono.error(new IllegalArgumentException("Request must not be null"));
		}
		if (this.streaming) {
			return Mono.just(McpAsyncResponse.streaming(notifier -> invoke(context, request, notifier)));
		}
		return invoke(context, request, null).map(McpAsyncResponse::result);
	}

	private Mono<GetPromptOutcome> invoke(McpRequestContext context, GetPromptRequest request,
			@Nullable McpAsyncNotifier notifier) {
		return ModernReactiveSupport
			.invokeToMono(context, this.method,
					() -> ModernReactiveSupport.invoke(this.method, this.bean, arguments(context, request, notifier)))
			.map(result -> toOutcome(context, result))
			.switchIfEmpty(Mono.fromSupplier(() -> toOutcome(context, null)))
			.onErrorResume(this::recover);
	}

	private Object[] arguments(McpRequestContext context, GetPromptRequest request,
			@Nullable McpAsyncNotifier notifier) {
		var legacyRequest = new io.modelcontextprotocol.spec.McpSchema.GetPromptRequest(request.name(),
				promptArguments(request), request.meta());
		Parameter[] parameters = this.method.getParameters();
		Object[] args = this.buildArgs(this.method, context, legacyRequest);
		ModernMethodSupport.injectContextArguments(args, parameters, context, request.inputResponses(),
				request.requestState(), null, notifier, this.jsonMapper);
		for (int i = 0; i < parameters.length; i++) {
			if (GetPromptRequest.class.isAssignableFrom(parameters[i].getType())) {
				args[i] = request;
			}
		}
		return args;
	}

	private GetPromptOutcome toOutcome(McpRequestContext context, @Nullable Object result) {
		if (result instanceof GetPromptOutcome outcome) {
			ModernMethodSupport.checkClientCapabilities(context, outcome, this.method, this.capabilityWarned);
			return outcome;
		}
		return McpSchemaConverter.toModern(this.convertToGetPromptResult(result));
	}

	private Mono<GetPromptOutcome> recover(Throwable failure) {
		if (failure instanceof McpException) {
			return Mono.error(failure);
		}
		return Mono.error(ModernMethodSupport.translate(failure, "prompt", this.method, this.bean));
	}

	/**
	 * The prompt arguments without the names of injected parameters, so that an argument
	 * that happens to share a name with an infrastructure parameter is not bound to it.
	 */
	private Map<String, Object> promptArguments(GetPromptRequest request) {
		Map<String, Object> arguments = request.arguments() != null ? new HashMap<>(request.arguments())
				: new HashMap<>();
		for (Parameter parameter : this.method.getParameters()) {
			if (isModernInfrastructureParameter(parameter.getType())
					|| McpTransportContext.class.isAssignableFrom(parameter.getType())) {
				arguments.remove(parameter.getName());
			}
		}
		return arguments;
	}

	@Override
	protected void validateParamType(Class<?> paramType) {
		ModernMethodSupport.rejectLegacyContextParameter(paramType, this.method);
		ModernMethodSupport.rejectUnsupportedNotifierParameter(paramType, this.method, McpAsyncNotifier.class);
	}

	@Override
	protected boolean isSupportedExchangeOrContextType(Class<?> paramType) {
		return McpTransportContext.class.isAssignableFrom(paramType);
	}

	@Override
	protected Object assignExchangeType(Class<?> paramType, Object exchange) {
		return ((McpRequestContext) exchange).transportContext();
	}

	@Override
	protected void validateReturnType(Method method) {
		ModernReactiveSupport.rejectMultiValuedReturnType(method);
		Class<?> outcomeType = ModernReactiveSupport.outcomeClass(method);
		// An outcome type that is not known here (Mono<?>, Object) is converted at
		// runtime.
		boolean valid = outcomeType == null || outcomeType == Object.class
				|| GetPromptOutcome.class.isAssignableFrom(outcomeType)
				|| GetPromptResult.class.isAssignableFrom(outcomeType) || List.class.isAssignableFrom(outcomeType)
				|| PromptMessage.class.isAssignableFrom(outcomeType) || String.class.isAssignableFrom(outcomeType);
		if (!valid) {
			throw new IllegalArgumentException("Method must return either a modern GetPromptOutcome, "
					+ "GetPromptResult, List<PromptMessage>, List<String>, PromptMessage, or String, plain or "
					+ "wrapped in a Mono: " + method.getName() + " in " + method.getDeclaringClass().getName()
					+ " returns " + method.getGenericReturnType().getTypeName());
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link AsyncModernMcpPromptMethodCallback} instances.
	 */
	public static final class Builder extends AbstractBuilder<Builder, AsyncModernMcpPromptMethodCallback> {

		private @Nullable McpJsonMapper jsonMapper;

		/**
		 * Sets the mapper that {@code McpModernRequestContext.inputResponse} converts
		 * with, normally the server's. Unset, the SDK default is used.
		 */
		public Builder jsonMapper(@Nullable McpJsonMapper jsonMapper) {
			this.jsonMapper = jsonMapper;
			return this;
		}

		@Override
		public AsyncModernMcpPromptMethodCallback build() {
			validate();
			return new AsyncModernMcpPromptMethodCallback(this);
		}

	}

}
