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
import java.lang.reflect.UndeclaredThrowableException;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.context.McpRequestContextTypes;
import org.springframework.ai.util.JsonHelper;

/**
 * Abstract base class for creating Function callbacks around async tool methods.
 *
 * This class provides common functionality for converting methods annotated with
 * {@link McpTool} into callback functions that can be used to handle tool requests
 * asynchronously.
 *
 * @param <T> The type of the context parameter (e.g., McpAsyncServerExchange or
 * McpTransportContext)
 * @author Christian Tzolov
 * @author Sebastien Deleuze
 */
public abstract class AbstractAsyncMcpToolMethodCallback<T, RC extends McpRequestContextTypes<?>>
		extends AbstractMcpToolMethodCallback<T, RC> {

	private static final JsonHelper jsonHelper = new JsonHelper();

	/**
	 * @deprecated no longer consulted. Exception handling now follows the {@code @Tool}
	 * contract based on the exception type. Retained for backward compatibility. Will be
	 * removed in 2.1.0.
	 */
	@Deprecated
	protected Class<? extends Throwable> toolCallExceptionClass = Exception.class;

	protected AbstractAsyncMcpToolMethodCallback(ReturnMode returnMode, Method toolMethod, Object toolObject) {
		super(returnMode, toolMethod, toolObject);
	}

	/**
	 * @deprecated use
	 * {@link #AbstractAsyncMcpToolMethodCallback(ReturnMode, Method, Object)}. The
	 * {@code toolCallExceptionClass} argument is ignored: exception handling now follows
	 * the {@code @Tool} contract based on the exception type. Will be removed in 2.1.0.
	 */
	@Deprecated
	protected AbstractAsyncMcpToolMethodCallback(ReturnMode returnMode, Method toolMethod, Object toolObject,
			Class<? extends Throwable> toolCallExceptionClass) {
		super(returnMode, toolMethod, toolObject);
		this.toolCallExceptionClass = toolCallExceptionClass;
	}

	/**
	 * Convert reactive types to Mono<CallToolResult>
	 * @param result The result from the method invocation
	 * @return A Mono<CallToolResult> representing the processed result
	 */
	protected Mono<CallToolResult> convertToCallToolResult(Object result) {
		// Handle Mono types
		if (result instanceof Mono) {

			Mono<?> monoResult = (Mono<?>) result;

			// Check if the Mono contains CallToolResult
			if (ReactiveUtils.isReactiveReturnTypeOfCallToolResult(this.toolMethod)) {
				return (Mono<CallToolResult>) monoResult;
			}

			// Handle Mono<Void> for VOID return type
			if (ReactiveUtils.isReactiveReturnTypeOfVoid(this.toolMethod)) {
				return monoResult
					.then(Mono.just(CallToolResult.builder().addTextContent(jsonHelper.toJson("Done")).build()));
			}

			// Handle other Mono types - map the emitted value to CallToolResult
			return monoResult.map(this::mapValueToCallToolResult).onErrorResume(this::toErrorResultOrPropagate);
		}

		// Handle Flux by taking the first element
		if (result instanceof Flux) {
			Flux<?> fluxResult = (Flux<?>) result;

			// Check if the Flux contains CallToolResult
			if (ReactiveUtils.isReactiveReturnTypeOfCallToolResult(this.toolMethod)) {
				return ((Flux<CallToolResult>) fluxResult).next();
			}

			// Handle Mono<Void> for VOID return type
			if (ReactiveUtils.isReactiveReturnTypeOfVoid(this.toolMethod)) {
				return fluxResult
					.then(Mono.just(CallToolResult.builder().addTextContent(jsonHelper.toJson("Done")).build()));
			}

			// Handle other Flux types by taking the first element and mapping
			return fluxResult.next().map(this::mapValueToCallToolResult).onErrorResume(this::toErrorResultOrPropagate);
		}

		// Handle other Publisher types
		if (result instanceof Publisher) {
			Publisher<?> publisherResult = (Publisher<?>) result;
			Mono<?> monoFromPublisher = Mono.from(publisherResult);

			// Check if the Publisher contains CallToolResult
			if (ReactiveUtils.isReactiveReturnTypeOfCallToolResult(this.toolMethod)) {
				return (Mono<CallToolResult>) monoFromPublisher;
			}

			// Handle Mono<Void> for VOID return type
			if (ReactiveUtils.isReactiveReturnTypeOfVoid(this.toolMethod)) {
				return monoFromPublisher
					.then(Mono.just(CallToolResult.builder().addTextContent(jsonHelper.toJson("Done")).build()));
			}

			// Handle other Publisher types by mapping the emitted value
			return monoFromPublisher.map(this::mapValueToCallToolResult).onErrorResume(this::toErrorResultOrPropagate);
		}

		// This should not happen in async context, but handle as fallback
		throw new IllegalStateException(
				"Expected reactive return type but got: " + (result != null ? result.getClass().getName() : "null"));
	}

	/**
	 * Map individual values to CallToolResult This method delegates to the parent class's
	 * convertValueToCallToolResult method to avoid code duplication.
	 * @param value The value to map
	 * @return A CallToolResult representing the mapped value
	 */
	protected CallToolResult mapValueToCallToolResult(Object value) {
		return convertValueToCallToolResult(value);
	}

	/**
	 * Resolves a reactive error either into an error {@link CallToolResult} conveyed to
	 * the model, or into a propagated error that fails the model interaction. Mirrors the
	 * {@code @Tool} contract: ordinary {@link RuntimeException}s are conveyed to the
	 * model, while declared checked exceptions (carried as
	 * {@link UndeclaredThrowableException}), {@link Error}s and protocol-level
	 * {@link McpError}s bubble up as hard failures.
	 * @param e the reactive error
	 * @return an error result or a propagated error
	 */
	private Mono<CallToolResult> toErrorResultOrPropagate(Throwable e) {
		if (e instanceof RuntimeException && !(e instanceof UndeclaredThrowableException) && !(e instanceof McpError)) {
			return Mono.just(CallToolResult.builder()
				.isError(true)
				.addTextContent("Error invoking method: %s".formatted(e.getMessage()))
				.build());
		}
		return Mono.error(e);
	}

	/**
	 * Creates an error result for exceptions that occur during method invocation.
	 * @param e The exception that occurred
	 * @return A Mono<CallToolResult> representing the error
	 */
	protected Mono<CallToolResult> createAsyncErrorResult(Exception e) {
		Throwable rootCause = findCauseUsingPlainJava(e);
		return Mono.just(CallToolResult.builder()
			.isError(true)
			.addTextContent(e.getMessage() + System.lineSeparator() + rootCause.getMessage())
			.build());
	}

	/**
	 * Validates that the request is not null.
	 * @param request The request to validate
	 * @return A Mono error if the request is null, otherwise Mono.empty()
	 */
	protected Mono<Void> validateRequest(CallToolRequest request) {
		if (request == null) {
			return Mono.error(new IllegalArgumentException("Request must not be null"));
		}
		return Mono.empty();
	}

	/**
	 * Determines if the given parameter type is an exchange or context type that should
	 * be injected. Subclasses must implement this method to specify which types are
	 * considered exchange or context types.
	 * @param paramType The parameter type to check
	 * @return true if the parameter type is an exchange or context type, false otherwise
	 */
	protected abstract boolean isExchangeOrContextType(Class<?> paramType);

}
