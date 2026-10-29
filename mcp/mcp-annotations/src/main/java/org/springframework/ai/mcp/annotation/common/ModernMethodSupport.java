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

package org.springframework.ai.mcp.annotation.common;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.function.Predicate;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.modern.McpSchema.ElicitUrlRequest;
import io.modelcontextprotocol.modern.McpSchema.ErrorCodes;
import io.modelcontextprotocol.modern.McpSchema.InputRequest;
import io.modelcontextprotocol.modern.McpSchema.InputRequiredResult;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncNotifier;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpError;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.mcp.annotation.context.DefaultMcpModernSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpModernSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.modern.McpSchemaConverter;

/**
 * Parameter handling shared by the method callbacks of a 2026-07-28 ("modern") MCP
 * server.
 * <p>
 * A modern method may declare {@link McpModernSyncRequestContext}, the SDK
 * {@link McpRequestContext}, {@link McpSyncNotifier} and {@link McpTransportContext} next
 * to the primitive's own parameters. The legacy exchange and request context types are
 * rejected because there is no session to back them.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public final class ModernMethodSupport {

	private static final Log logger = LogFactory.getLog(ModernMethodSupport.class);

	private ModernMethodSupport() {
	}

	/**
	 * Whether a parameter of this type makes the invocation a streaming response, so that
	 * notifications can be pushed before the result.
	 */
	public static boolean isStreamingParameter(Class<?> parameterType) {
		return McpModernSyncRequestContext.class.isAssignableFrom(parameterType)
				|| McpSyncNotifier.class.isAssignableFrom(parameterType);
	}

	/**
	 * Whether a parameter of this type is a modern context parameter, injected by the
	 * callback rather than bound from the request.
	 */
	public static boolean isContextParameter(Class<?> parameterType) {
		return isStreamingParameter(parameterType) || McpRequestContext.class.isAssignableFrom(parameterType);
	}

	/**
	 * Whether a parameter of this type is injected by a modern callback rather than bound
	 * from the request: a modern context parameter or the primitive's modern request
	 * type.
	 * @param modernRequestType the modern request record of the primitive, for example
	 * {@code io.modelcontextprotocol.modern.McpSchema.CallToolRequest}
	 */
	public static boolean isInfrastructureParameter(Class<?> parameterType, Class<?> modernRequestType) {
		return isContextParameter(parameterType) || modernRequestType.isAssignableFrom(parameterType);
	}

	/**
	 * Rejects the session-bound legacy parameter types with an explanation of what to use
	 * instead.
	 * @throws IllegalArgumentException if the type is a legacy exchange or request
	 * context
	 */
	public static void rejectLegacyContextParameter(Class<?> parameterType, Method method) {
		if (McpSyncServerExchange.class.isAssignableFrom(parameterType)
				|| McpAsyncServerExchange.class.isAssignableFrom(parameterType)) {
			throw new IllegalArgumentException("Modern (2026-07-28) methods must not declare parameter of type "
					+ parameterType.getName() + "; use McpRequestContext or McpTransportContext instead. Method: "
					+ method.getName() + " in " + method.getDeclaringClass().getName());
		}
		if (McpSyncRequestContext.class.isAssignableFrom(parameterType)
				|| McpAsyncRequestContext.class.isAssignableFrom(parameterType)) {
			throw new IllegalArgumentException("Modern (2026-07-28) methods must not declare parameter of type "
					+ parameterType.getName() + "; use McpModernSyncRequestContext instead. Method: " + method.getName()
					+ " in " + method.getDeclaringClass().getName());
		}
	}

	/**
	 * Enforces the 2026-07-28 rule that a server must not request input the client did
	 * not declare a capability for. When {@code outcome} is an
	 * {@code InputRequiredResult}, every input request is checked against
	 * {@code ctx.clientCapabilities()}; the first missing capability is reported as
	 * JSON-RPC error {@code -32021} whose data names the capabilities the request needs.
	 * Any other outcome passes through untouched.
	 * @param ctx the request context carrying the client's declared capabilities
	 * @param outcome the handler's result
	 * @throws McpException {@code -32021 Missing required client capability}
	 * @since 2.2.0
	 */
	public static void checkClientCapabilities(McpRequestContext ctx, @Nullable Object outcome) {
		if (!(outcome instanceof InputRequiredResult inputRequired) || inputRequired.inputRequests() == null) {
			return;
		}
		ClientCapabilities declared = ctx.clientCapabilities();
		boolean needsForm = false;
		boolean needsUrl = false;
		boolean needsSampling = false;
		boolean needsRoots = false;
		for (InputRequest request : inputRequired.inputRequests().values()) {
			switch (request.method()) {
				case McpSchema.METHOD_ELICITATION_CREATE -> {
					if (isUrlElicitation(request.params())) {
						if (!declared.supportsElicitationUrl()) {
							needsUrl = true;
						}
					}
					else if (!declared.supportsElicitationForm()) {
						needsForm = true;
					}
				}
				case McpSchema.METHOD_SAMPLING_CREATE_MESSAGE -> {
					if (!declared.supportsSampling()) {
						needsSampling = true;
					}
				}
				case McpSchema.METHOD_ROOTS_LIST -> {
					if (!declared.supportsRoots()) {
						needsRoots = true;
					}
				}
				default -> {
					// unknown input request kinds are the SDK's concern
				}
			}
		}
		if (!(needsForm || needsUrl || needsSampling || needsRoots)) {
			return;
		}
		ClientCapabilities.Builder required = ClientCapabilities.builder();
		if (needsForm || needsUrl) {
			required.elicitation(
					new ClientCapabilities.Elicitation(needsForm ? new ClientCapabilities.Elicitation.Form() : null,
							needsUrl ? new ClientCapabilities.Elicitation.Url() : null));
		}
		if (needsSampling) {
			required.sampling();
		}
		if (needsRoots) {
			required.roots();
		}
		throw McpException.missingClientCapability(required.build());
	}

	/**
	 * An elicitation is URL mode when its params say so: the SDK record, or any other
	 * representation (a map built by hand, or one round-tripped through JSON) whose
	 * {@code mode} field is {@code "url"}. Everything else is form mode, the default.
	 */
	private static boolean isUrlElicitation(@Nullable Object params) {
		if (params instanceof ElicitUrlRequest) {
			return true;
		}
		if (params instanceof Map<?, ?> map) {
			return ElicitUrlRequest.MODE.equals(map.get("mode"));
		}
		return false;
	}

	/**
	 * A provider filter that skips, with a warning naming the modern server and the
	 * replacement type, any method declaring a legacy exchange or request context
	 * parameter. The callbacks reject such methods outright; the providers skip them so
	 * that one unconverted method does not prevent the application from starting.
	 */
	public static Predicate<Method> filterLegacyContextMethods() {
		return method -> {
			for (Class<?> type : method.getParameterTypes()) {
				try {
					rejectLegacyContextParameter(type, method);
				}
				catch (IllegalArgumentException e) {
					if (logger.isWarnEnabled()) {
						logger.warn("Skipping method on the 2026-07-28 (modern) server: " + e.getMessage());
					}
					return false;
				}
			}
			return true;
		};
	}

	/**
	 * Fills the modern context parameters and {@link McpTransportContext} into the
	 * argument array, leaving every other slot untouched.
	 * @param inputResponses the request's MRTR input responses, if any
	 * @param requestState the request's unsealed MRTR state, if any
	 * @param notifier the notifier of a streaming response, or null for a plain result
	 */
	public static void injectContextArguments(Object[] args, Parameter[] parameters, McpRequestContext context,
			@Nullable Map<String, Object> inputResponses, @Nullable String requestState,
			@Nullable McpSyncNotifier notifier) {
		McpModernSyncRequestContext modernContext = null;
		for (int i = 0; i < parameters.length; i++) {
			Class<?> type = parameters[i].getType();
			if (McpModernSyncRequestContext.class.isAssignableFrom(type)) {
				if (modernContext == null) {
					modernContext = DefaultMcpModernSyncRequestContext.builder()
						.context(context)
						.inputResponses(inputResponses)
						.requestState(requestState)
						.notifier(notifier)
						.build();
				}
				args[i] = modernContext;
			}
			else if (McpSyncNotifier.class.isAssignableFrom(type)) {
				args[i] = notifier;
			}
			else if (McpRequestContext.class.isAssignableFrom(type)) {
				args[i] = context;
			}
			else if (McpTransportContext.class.isAssignableFrom(type)) {
				args[i] = context.transportContext();
			}
		}
	}

	/**
	 * Translates a failure of the method invocation into the protocol error the modern
	 * server reports: modern exceptions pass through, legacy protocol errors are
	 * converted and anything else is logged and reported as an internal error, as the SDK
	 * does, so that its message stays server-side.
	 */
	public static McpException translate(Throwable failure, String primitive, Method method, Object bean) {
		Throwable cause = failure instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause()
				: failure;
		if (cause instanceof McpException e) {
			return e;
		}
		if (cause instanceof McpError e) {
			return McpSchemaConverter.toModern(e);
		}
		logger.warn("Unhandled exception invoking " + primitive + " method " + method.getName() + " in "
				+ bean.getClass().getName(), cause);
		return new McpException(ErrorCodes.INTERNAL_ERROR, "Internal error");
	}

}
