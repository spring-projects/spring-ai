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

package org.springframework.ai.mcp.annotation.method.complete;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema.CompleteRequest;
import io.modelcontextprotocol.modern.McpSchema.CompleteResult;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncNotifier;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult.CompleteCompletion;
import io.modelcontextprotocol.util.DefaultMcpUriTemplateManagerFactory;

import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpProgressToken;
import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.context.McpModernSyncRequestContext;
import org.springframework.ai.mcp.modern.McpSchemaConverter;
import org.springframework.ai.mcp.modern.SyncCompletionSpecification;

/**
 * Serves a {@code completion/complete} of a 2026-07-28 ("modern") MCP server by invoking
 * a method annotated with {@code @McpComplete}.
 * <p>
 * Beyond the parameters the stateless callback supports, a method may declare
 * {@link McpModernSyncRequestContext}, the SDK {@link McpRequestContext} or the modern
 * {@link CompleteRequest}. Completions are plain results, so {@link McpSyncNotifier} is
 * not available and progress reported through the context is dropped.
 * <p>
 * The method may return a modern {@link CompleteResult} or any of the legacy return
 * types, which are converted.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public final class SyncModernMcpCompleteMethodCallback extends AbstractMcpCompleteMethodCallback
		implements SyncCompletionSpecification.Handler {

	private SyncModernMcpCompleteMethodCallback(Builder builder) {
		super(builder.method, builder.bean, builder.prompt, builder.uri, builder.uriTemplateManagerFactory);
		this.validateMethod(this.method);
	}

	/**
	 * Whether a parameter of this type is injected by the modern callback rather than
	 * bound from the completion argument.
	 */
	public static boolean isModernInfrastructureParameter(Class<?> parameterType) {
		return ModernMethodSupport.isInfrastructureParameter(parameterType, CompleteRequest.class);
	}

	@Override
	public CompleteResult complete(McpRequestContext context, CompleteRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Request must not be null");
		}
		try {
			Parameter[] parameters = this.method.getParameters();
			Object[] args = this.buildArgs(this.method, context, McpSchemaConverter.toLegacy(request));
			ModernMethodSupport.injectContextArguments(args, parameters, context, null, null, null);
			for (int i = 0; i < parameters.length; i++) {
				if (CompleteRequest.class.isAssignableFrom(parameters[i].getType())) {
					args[i] = request;
				}
			}
			this.method.setAccessible(true);
			Object result = this.method.invoke(this.bean, args);
			if (result instanceof CompleteResult modernResult) {
				return modernResult;
			}
			return McpSchemaConverter.toModern(convertToCompleteResult(result));
		}
		catch (McpException e) {
			throw e;
		}
		catch (Exception e) {
			throw ModernMethodSupport.translate(e, "complete", this.method, this.bean);
		}
	}

	private static io.modelcontextprotocol.spec.McpSchema.CompleteResult convertToCompleteResult(Object result) {
		if (result == null) {
			return new io.modelcontextprotocol.spec.McpSchema.CompleteResult(
					new CompleteCompletion(List.of(), 0, false));
		}
		if (result instanceof io.modelcontextprotocol.spec.McpSchema.CompleteResult legacyResult) {
			return legacyResult;
		}
		if (result instanceof CompleteCompletion completion) {
			return new io.modelcontextprotocol.spec.McpSchema.CompleteResult(completion);
		}
		if (result instanceof List<?> list) {
			List<String> values = new ArrayList<>();
			for (Object item : list) {
				if (!(item instanceof String value)) {
					throw new IllegalArgumentException("List items must be of type String");
				}
				values.add(value);
			}
			return new io.modelcontextprotocol.spec.McpSchema.CompleteResult(
					new CompleteCompletion(values, values.size(), false));
		}
		if (result instanceof String value) {
			return new io.modelcontextprotocol.spec.McpSchema.CompleteResult(
					new CompleteCompletion(List.of(value), 1, false));
		}
		throw new IllegalArgumentException("Unsupported return type: " + result.getClass().getName());
	}

	@Override
	protected void validateParameters(Method method) {
		for (Parameter parameter : method.getParameters()) {
			Class<?> type = parameter.getType();
			if (parameter.isAnnotationPresent(McpProgressToken.class) || McpMeta.class.isAssignableFrom(type)) {
				continue;
			}
			ModernMethodSupport.rejectLegacyContextParameter(type, method);
			if (McpSyncNotifier.class.isAssignableFrom(type)) {
				throw new IllegalArgumentException(
						"Completion methods are not streamed and cannot declare McpSyncNotifier: " + method.getName()
								+ " in " + method.getDeclaringClass().getName());
			}
			boolean valid = McpTransportContext.class.isAssignableFrom(type) || isModernInfrastructureParameter(type)
					|| io.modelcontextprotocol.spec.McpSchema.CompleteRequest.class.isAssignableFrom(type)
					|| io.modelcontextprotocol.spec.McpSchema.CompleteRequest.CompleteArgument.class
						.isAssignableFrom(type)
					|| String.class.isAssignableFrom(type);
			if (!valid) {
				throw new IllegalArgumentException(
						"Modern complete method parameters must be a request context, McpTransportContext, "
								+ "CompleteRequest, CompleteArgument, String, McpMeta or @McpProgressToken: "
								+ method.getName() + " in " + method.getDeclaringClass().getName()
								+ " has parameter of type " + type.getName());
			}
		}
	}

	@Override
	protected void validateReturnType(Method method) {
		Class<?> returnType = method.getReturnType();
		boolean valid = CompleteResult.class.isAssignableFrom(returnType)
				|| io.modelcontextprotocol.spec.McpSchema.CompleteResult.class.isAssignableFrom(returnType)
				|| CompleteCompletion.class.isAssignableFrom(returnType) || List.class.isAssignableFrom(returnType)
				|| String.class.isAssignableFrom(returnType);
		if (!valid) {
			throw new IllegalArgumentException("Method must return either a modern CompleteResult, "
					+ "CompleteResult, CompleteCompletion, List<String>, or String: " + method.getName() + " in "
					+ method.getDeclaringClass().getName() + " returns " + returnType.getName());
		}
	}

	@Override
	protected McpTransportContext resolveTransportContext(Object context) {
		return ((McpRequestContext) context).transportContext();
	}

	@Override
	protected boolean isExchangeType(Class<?> paramType) {
		return McpRequestContext.class.isAssignableFrom(paramType);
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link SyncModernMcpCompleteMethodCallback} instances.
	 */
	public static final class Builder extends AbstractBuilder<Builder, SyncModernMcpCompleteMethodCallback> {

		private Builder() {
			this.uriTemplateManagerFactory = new DefaultMcpUriTemplateManagerFactory();
		}

		@Override
		public SyncModernMcpCompleteMethodCallback build() {
			validate();
			return new SyncModernMcpCompleteMethodCallback(this);
		}

	}

}
