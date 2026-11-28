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
import java.util.List;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema.CompleteRequest;
import io.modelcontextprotocol.modern.McpSchema.CompleteResult;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.feature.McpAsyncCompletionHandler;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult.CompleteCompletion;
import io.modelcontextprotocol.util.DefaultMcpUriTemplateManagerFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpProgressToken;
import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.common.ModernReactiveSupport;
import org.springframework.ai.mcp.annotation.context.McpModernRequestContext;
import org.springframework.ai.mcp.modern.McpModernAsyncUtils;
import org.springframework.ai.mcp.modern.McpSchemaConverter;

/**
 * The asynchronous twin of {@link SyncModernMcpCompleteMethodCallback}: serves a
 * {@code completion/complete} of a 2026-07-28 ("modern") MCP server by invoking a method
 * annotated with {@code @McpComplete}.
 * <p>
 * A method may declare {@link McpModernRequestContext}, the SDK {@link McpRequestContext}
 * or the modern {@link CompleteRequest}. Completions are plain results, so neither
 * notifier is available.
 * <p>
 * The method may return a {@code Mono}, a {@code CompletionStage} or a {@code Publisher}
 * of a modern {@link CompleteResult} or of any legacy return type, which is converted. A
 * declared {@code Flux} is rejected at startup, and a publisher that emits more than one
 * element fails the call; see {@link ModernReactiveSupport}. A plain, blocking return
 * value runs through {@link McpModernAsyncUtils#fromBlocking}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class AsyncModernMcpCompleteMethodCallback extends AbstractMcpCompleteMethodCallback
		implements McpAsyncCompletionHandler {

	private AsyncModernMcpCompleteMethodCallback(Builder builder) {
		super(builder.method, builder.bean, builder.prompt, builder.uri, builder.uriTemplateManagerFactory);
		this.validateMethod(this.method);
		this.method.setAccessible(true);
	}

	/**
	 * Whether a parameter of this type is injected by the modern callback rather than
	 * bound from the completion argument.
	 */
	public static boolean isModernInfrastructureParameter(Class<?> parameterType) {
		return ModernMethodSupport.isInfrastructureParameter(parameterType, CompleteRequest.class);
	}

	@Override
	public Mono<CompleteResult> complete(McpRequestContext context, CompleteRequest request) {
		if (request == null) {
			return Mono.error(new IllegalArgumentException("Request must not be null"));
		}
		return ModernReactiveSupport
			.invokeToMono(context, this.method,
					() -> ModernReactiveSupport.invoke(this.method, this.bean, arguments(context, request)))
			.map(this::toResult)
			.switchIfEmpty(Mono.fromSupplier(() -> toResult(null)))
			.onErrorResume(failure -> {
				if (failure instanceof McpException) {
					return Mono.error(failure);
				}
				return Mono.error(ModernMethodSupport.translate(failure, "complete", this.method, this.bean));
			});
	}

	private Object[] arguments(McpRequestContext context, CompleteRequest request) {
		Parameter[] parameters = this.method.getParameters();
		Object[] args = this.buildArgs(this.method, context, McpSchemaConverter.toLegacy(request));
		ModernMethodSupport.injectContextArguments(args, parameters, context, null, null, null);
		for (int i = 0; i < parameters.length; i++) {
			if (CompleteRequest.class.isAssignableFrom(parameters[i].getType())) {
				args[i] = request;
			}
		}
		return args;
	}

	private CompleteResult toResult(@Nullable Object result) {
		if (result instanceof CompleteResult modernResult) {
			return modernResult;
		}
		return McpSchemaConverter.toModern(SyncModernMcpCompleteMethodCallback.convertToCompleteResult(result));
	}

	@Override
	protected void validateParameters(Method method) {
		for (Parameter parameter : method.getParameters()) {
			Class<?> type = parameter.getType();
			if (parameter.isAnnotationPresent(McpProgressToken.class) || McpMeta.class.isAssignableFrom(type)) {
				continue;
			}
			ModernMethodSupport.rejectLegacyContextParameter(type, method);
			if (ModernMethodSupport.isNotifierParameter(type)) {
				throw new IllegalArgumentException("Completion methods are not streamed and cannot declare a notifier: "
						+ method.getName() + " in " + method.getDeclaringClass().getName());
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
		ModernReactiveSupport.rejectMultiValuedReturnType(method);
		Class<?> outcomeType = ModernReactiveSupport.outcomeClass(method);
		// An outcome type that is not known here (Mono<?>, Object) is converted at
		// runtime.
		boolean valid = outcomeType == null || outcomeType == Object.class
				|| CompleteResult.class.isAssignableFrom(outcomeType)
				|| io.modelcontextprotocol.spec.McpSchema.CompleteResult.class.isAssignableFrom(outcomeType)
				|| CompleteCompletion.class.isAssignableFrom(outcomeType) || List.class.isAssignableFrom(outcomeType)
				|| String.class.isAssignableFrom(outcomeType);
		if (!valid) {
			throw new IllegalArgumentException("Method must return either a modern CompleteResult, "
					+ "CompleteResult, CompleteCompletion, List<String>, or String, plain or wrapped in a Mono: "
					+ method.getName() + " in " + method.getDeclaringClass().getName() + " returns "
					+ method.getGenericReturnType().getTypeName());
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
	 * Builder for {@link AsyncModernMcpCompleteMethodCallback} instances.
	 */
	public static final class Builder extends AbstractBuilder<Builder, AsyncModernMcpCompleteMethodCallback> {

		private Builder() {
			this.uriTemplateManagerFactory = new DefaultMcpUriTemplateManagerFactory();
		}

		@Override
		public AsyncModernMcpCompleteMethodCallback build() {
			validate();
			return new AsyncModernMcpCompleteMethodCallback(this);
		}

	}

}
