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

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome;
import io.modelcontextprotocol.modern.McpSchema.CallToolRequest;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncNotifier;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.spec.McpError;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.context.McpModernSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.modern.McpSchemaConverter;
import org.springframework.ai.mcp.modern.SyncToolSpecification;

/**
 * Serves a {@code tools/call} of a 2026-07-28 ("modern") MCP server by invoking a method
 * annotated with {@code @McpTool}.
 * <p>
 * Beyond the parameters the legacy callbacks support, a method may declare
 * {@link McpModernSyncRequestContext}, the SDK {@link McpRequestContext},
 * {@link McpSyncNotifier} or the modern {@link CallToolRequest} (which carries the MRTR
 * fields). A method that declares the modern context or the notifier is served as a
 * streaming response so that progress notifications can be pushed before the result.
 * <p>
 * The method may return a modern {@link CallToolOutcome} (a {@code CallToolResult} or an
 * {@code InputRequiredResult}) or any of the legacy return types, which are converted.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public final class SyncModernMcpToolMethodCallback
		extends AbstractSyncMcpToolMethodCallback<McpRequestContext, McpSyncRequestContext>
		implements SyncToolSpecification.Handler {

	private final boolean streaming;

	public SyncModernMcpToolMethodCallback(ReturnMode returnMode, Method toolMethod, Object toolObject) {
		super(returnMode, toolMethod, toolObject);
		for (Class<?> parameterType : toolMethod.getParameterTypes()) {
			ModernMethodSupport.rejectLegacyContextParameter(parameterType, toolMethod);
		}
		this.streaming = Arrays.stream(toolMethod.getParameterTypes())
			.anyMatch(SyncModernMcpToolMethodCallback::isStreamingParameter);
	}

	/**
	 * Whether a parameter of this type makes the call a streaming response.
	 */
	public static boolean isStreamingParameter(Class<?> parameterType) {
		return ModernMethodSupport.isStreamingParameter(parameterType);
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
	protected McpSyncRequestContext createRequestContext(McpRequestContext exchange,
			io.modelcontextprotocol.spec.McpSchema.CallToolRequest request) {
		throw new UnsupportedOperationException(
				"Modern tool methods do not support McpSyncRequestContext; use McpModernSyncRequestContext instead.");
	}

	@Override
	protected McpTransportContext resolveTransportContext(McpRequestContext context) {
		return context.transportContext();
	}

	@Override
	public McpSyncResponse<CallToolOutcome> call(McpRequestContext context, CallToolRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Request must not be null");
		}
		if (this.streaming) {
			return McpSyncResponse.streaming(notifier -> invoke(context, request, notifier));
		}
		return McpSyncResponse.result(invoke(context, request, null));
	}

	private CallToolOutcome invoke(McpRequestContext context, CallToolRequest request,
			@Nullable McpSyncNotifier notifier) {
		try {
			Parameter[] parameters = this.toolMethod.getParameters();
			Object[] args = buildMethodArguments(context, toolArguments(request), McpSchemaConverter.toLegacy(request));
			ModernMethodSupport.injectContextArguments(args, parameters, context, request.inputResponses(),
					request.requestState(), notifier);
			for (int i = 0; i < parameters.length; i++) {
				if (CallToolRequest.class.isAssignableFrom(parameters[i].getType())) {
					args[i] = request;
				}
			}
			Object result = callMethod(args);
			if (result instanceof CallToolOutcome outcome) {
				ModernMethodSupport.checkClientCapabilities(context, outcome);
				return outcome;
			}
			return McpSchemaConverter.toModern(processResult(result));
		}
		catch (UndeclaredThrowableException e) {
			throw e;
		}
		catch (McpException e) {
			// A modern protocol error reaches the protocol layer as is.
			throw e;
		}
		catch (McpError e) {
			// A legacy protocol error carries the same semantics; translate it.
			throw McpSchemaConverter.toModern(e);
		}
		catch (RuntimeException e) {
			return McpSchemaConverter.toModern(createSyncErrorResult(e));
		}
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
