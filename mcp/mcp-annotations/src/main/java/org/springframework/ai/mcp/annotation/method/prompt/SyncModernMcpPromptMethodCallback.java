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

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema.GetPromptOutcome;
import io.modelcontextprotocol.modern.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncNotifier;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.context.McpModernSyncRequestContext;
import org.springframework.ai.mcp.modern.McpSchemaConverter;
import org.springframework.ai.mcp.modern.SyncPromptSpecification;

/**
 * Serves a {@code prompts/get} of a 2026-07-28 ("modern") MCP server by invoking a method
 * annotated with {@code @McpPrompt}.
 * <p>
 * Beyond the parameters the stateless callback supports, a method may declare
 * {@link McpModernSyncRequestContext}, the SDK {@link McpRequestContext},
 * {@link McpSyncNotifier} or the modern {@link GetPromptRequest} (which carries the MRTR
 * fields). A method that declares the modern context or the notifier is served as a
 * streaming response so that progress notifications can be pushed before the result.
 * <p>
 * The method may return a modern {@link GetPromptOutcome} (a {@code GetPromptResult} or
 * an {@code InputRequiredResult}) or any of the legacy return types, which are converted.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public final class SyncModernMcpPromptMethodCallback extends AbstractMcpPromptMethodCallback
		implements SyncPromptSpecification.Handler {

	private final boolean streaming;

	private SyncModernMcpPromptMethodCallback(Builder builder) {
		super(builder.method, builder.bean, builder.prompt);
		this.streaming = Arrays.stream(this.method.getParameterTypes())
			.anyMatch(ModernMethodSupport::isStreamingParameter);
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
	public McpSyncResponse<GetPromptOutcome> get(McpRequestContext context, GetPromptRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Request must not be null");
		}
		if (this.streaming) {
			return McpSyncResponse.streaming(notifier -> invoke(context, request, notifier));
		}
		return McpSyncResponse.result(invoke(context, request, null));
	}

	private GetPromptOutcome invoke(McpRequestContext context, GetPromptRequest request,
			@Nullable McpSyncNotifier notifier) {
		try {
			var legacyRequest = new io.modelcontextprotocol.spec.McpSchema.GetPromptRequest(request.name(),
					promptArguments(request), request.meta());
			Parameter[] parameters = this.method.getParameters();
			Object[] args = this.buildArgs(this.method, context, legacyRequest);
			ModernMethodSupport.injectContextArguments(args, parameters, context, request.inputResponses(),
					request.requestState(), notifier);
			for (int i = 0; i < parameters.length; i++) {
				if (GetPromptRequest.class.isAssignableFrom(parameters[i].getType())) {
					args[i] = request;
				}
			}
			this.method.setAccessible(true);
			Object result = this.method.invoke(this.bean, args);
			if (result instanceof GetPromptOutcome outcome) {
				ModernMethodSupport.checkClientCapabilities(context, outcome);
				return outcome;
			}
			return McpSchemaConverter.toModern(this.convertToGetPromptResult(result));
		}
		catch (McpException e) {
			throw e;
		}
		catch (Exception e) {
			throw ModernMethodSupport.translate(e, "prompt", this.method, this.bean);
		}
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
		Class<?> returnType = method.getReturnType();
		boolean valid = GetPromptOutcome.class.isAssignableFrom(returnType)
				|| GetPromptResult.class.isAssignableFrom(returnType) || List.class.isAssignableFrom(returnType)
				|| PromptMessage.class.isAssignableFrom(returnType) || String.class.isAssignableFrom(returnType);
		if (!valid) {
			throw new IllegalArgumentException("Method must return either a modern GetPromptOutcome, "
					+ "GetPromptResult, List<PromptMessage>, List<String>, PromptMessage, or String: "
					+ method.getName() + " in " + method.getDeclaringClass().getName() + " returns "
					+ returnType.getName());
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link SyncModernMcpPromptMethodCallback} instances.
	 */
	public static final class Builder extends AbstractBuilder<Builder, SyncModernMcpPromptMethodCallback> {

		@Override
		public SyncModernMcpPromptMethodCallback build() {
			validate();
			return new SyncModernMcpPromptMethodCallback(this);
		}

	}

}
