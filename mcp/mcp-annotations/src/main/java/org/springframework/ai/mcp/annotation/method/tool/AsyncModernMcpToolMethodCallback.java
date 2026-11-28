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

package org.springframework.ai.mcp.annotation.method.tool;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome;
import io.modelcontextprotocol.modern.McpSchema.CallToolRequest;
import io.modelcontextprotocol.modern.server.McpAsyncNotifier;
import io.modelcontextprotocol.modern.server.McpAsyncResponse;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.feature.McpAsyncTool;
import io.modelcontextprotocol.spec.McpError;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.common.ModernReactiveSupport;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpModernRequestContext;
import org.springframework.ai.mcp.modern.McpModernAsyncUtils;
import org.springframework.ai.mcp.modern.McpSchemaConverter;

/**
 * The asynchronous twin of {@link SyncModernMcpToolMethodCallback}: serves a
 * {@code tools/call} of a 2026-07-28 ("modern") MCP server by invoking a method annotated
 * with {@code @McpTool}.
 * <p>
 * A method may declare {@link McpModernRequestContext}, the SDK
 * {@link McpRequestContext}, {@link McpAsyncNotifier} or the modern
 * {@link CallToolRequest} (which carries the MRTR fields). A method that declares the
 * notifier is served as a streaming response so that progress notifications can be pushed
 * before the result; the context alone leaves the response a plain result.
 * <p>
 * The method may return a {@code Mono}, a {@code CompletionStage} or a {@code Publisher}
 * of a modern {@link CallToolOutcome} or of any legacy return type, which is converted. A
 * declared {@code Flux} is rejected at startup, and a publisher that emits more than one
 * element fails the call; see {@link ModernReactiveSupport}. A plain, blocking return
 * value is accepted too: the method then runs through
 * {@link McpModernAsyncUtils#fromBlocking}, on the calling thread when the request is
 * served blocking and on {@code boundedElastic} otherwise.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class AsyncModernMcpToolMethodCallback extends
		AbstractAsyncMcpToolMethodCallback<McpRequestContext, McpAsyncRequestContext> implements McpAsyncTool.Handler {

	private final boolean streaming;

	// Set once the runtime capability check has warned about this method.
	private final AtomicBoolean capabilityWarned = new AtomicBoolean();

	private final @Nullable McpJsonMapper jsonMapper;

	public AsyncModernMcpToolMethodCallback(ReturnMode returnMode, Method toolMethod, Object toolObject) {
		this(returnMode, toolMethod, toolObject, null);
	}

	/**
	 * @param jsonMapper the mapper that {@code McpModernRequestContext.inputResponse}
	 * converts with, normally the server's, or null for the SDK default
	 */
	public AsyncModernMcpToolMethodCallback(ReturnMode returnMode, Method toolMethod, Object toolObject,
			@Nullable McpJsonMapper jsonMapper) {
		super(returnMode, toolMethod, toolObject);
		for (Class<?> parameterType : toolMethod.getParameterTypes()) {
			ModernMethodSupport.rejectLegacyContextParameter(parameterType, toolMethod);
			ModernMethodSupport.rejectUnsupportedNotifierParameter(parameterType, toolMethod, McpAsyncNotifier.class);
		}
		ModernReactiveSupport.rejectMultiValuedReturnType(toolMethod);
		this.streaming = Arrays.stream(toolMethod.getParameterTypes())
			.anyMatch(AsyncModernMcpToolMethodCallback::isStreamingParameter);
		this.jsonMapper = jsonMapper;
	}

	/**
	 * Whether a parameter of this type makes the call a streaming response.
	 */
	public static boolean isStreamingParameter(Class<?> parameterType) {
		return ModernMethodSupport.isAsyncStreamingParameter(parameterType);
	}

	/**
	 * Whether a parameter of this type is injected by the modern callback rather than
	 * bound from the tool arguments.
	 */
	public static boolean isModernInfrastructureParameter(Class<?> parameterType) {
		return ModernMethodSupport.isInfrastructureParameter(parameterType, CallToolRequest.class);
	}

	public boolean isStreaming() {
		return this.streaming;
	}

	@Override
	protected boolean isExchangeOrContextType(Class<?> paramType) {
		return McpRequestContext.class.isAssignableFrom(paramType);
	}

	@Override
	protected McpAsyncRequestContext createRequestContext(McpRequestContext exchange,
			io.modelcontextprotocol.spec.McpSchema.CallToolRequest request) {
		throw new UnsupportedOperationException(
				"Modern tool methods do not support McpAsyncRequestContext; use McpModernRequestContext instead.");
	}

	@Override
	protected McpTransportContext resolveTransportContext(McpRequestContext context) {
		return context.transportContext();
	}

	@Override
	public Mono<McpAsyncResponse<CallToolOutcome>> call(McpRequestContext context, CallToolRequest request) {
		if (request == null) {
			return Mono.error(new IllegalArgumentException("Request must not be null"));
		}
		if (this.streaming) {
			return Mono.just(McpAsyncResponse.streaming(notifier -> invoke(context, request, notifier)));
		}
		return invoke(context, request, null).map(McpAsyncResponse::result);
	}

	private Mono<CallToolOutcome> invoke(McpRequestContext context, CallToolRequest request,
			@Nullable McpAsyncNotifier notifier) {
		return ModernReactiveSupport
			.invokeToMono(context, this.toolMethod, () -> callMethod(arguments(context, request, notifier)))
			.map(result -> toOutcome(context, result))
			.switchIfEmpty(Mono.fromSupplier(() -> toOutcome(context, null)))
			.onErrorResume(this::recover);
	}

	private Object[] arguments(McpRequestContext context, CallToolRequest request,
			@Nullable McpAsyncNotifier notifier) {
		Parameter[] parameters = this.toolMethod.getParameters();
		Object[] args = buildMethodArguments(context, toolArguments(request), McpSchemaConverter.toLegacy(request));
		ModernMethodSupport.injectContextArguments(args, parameters, context, request.inputResponses(),
				request.requestState(), null, notifier, this.jsonMapper);
		for (int i = 0; i < parameters.length; i++) {
			if (CallToolRequest.class.isAssignableFrom(parameters[i].getType())) {
				args[i] = request;
			}
		}
		return args;
	}

	private CallToolOutcome toOutcome(McpRequestContext context, @Nullable Object result) {
		if (result instanceof CallToolOutcome outcome) {
			ModernMethodSupport.checkClientCapabilities(context, outcome, this.toolMethod, this.capabilityWarned);
			return outcome;
		}
		return McpSchemaConverter.toModern(convertValueToCallToolResult(result));
	}

	private Mono<CallToolOutcome> recover(Throwable failure) {
		if (failure instanceof McpException) {
			// A modern protocol error reaches the protocol layer as is.
			return Mono.error(failure);
		}
		if (failure instanceof McpError error) {
			// A legacy protocol error carries the same semantics; translate it.
			return Mono.error(McpSchemaConverter.toModern(error));
		}
		if (failure instanceof RuntimeException e && !(failure instanceof UndeclaredThrowableException)) {
			return createAsyncErrorResult(e).map(McpSchemaConverter::toModern);
		}
		return Mono.error(failure);
	}

	/**
	 * The tool arguments without the names of injected parameters, so that an argument
	 * that happens to share a name with an infrastructure parameter is not bound to it.
	 */
	private Map<String, Object> toolArguments(CallToolRequest request) {
		Map<String, Object> arguments = request.arguments() != null ? new HashMap<>(request.arguments())
				: new HashMap<>();
		for (Parameter parameter : this.toolMethod.getParameters()) {
			if (isModernInfrastructureParameter(parameter.getType())) {
				arguments.remove(parameter.getName());
			}
		}
		return arguments;
	}

}
