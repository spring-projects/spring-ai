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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.Flow;
import java.util.function.Supplier;

import io.modelcontextprotocol.modern.server.McpRequestContext;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.modern.McpModernAsyncUtils;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;

/**
 * Return-value handling shared by the asynchronous method callbacks of a 2026-07-28
 * ("modern") MCP server. An MCP request has one result, so a method answers with a single
 * value, and {@link ReactiveAdapterRegistry} tells which return types can do that:
 * <ul>
 * <li>A {@code Mono} or a {@code CompletionStage} is single-valued.</li>
 * <li>A {@code Flux}, or any other concrete multi-valued type the registry knows, is
 * rejected at startup by {@link #rejectMultiValuedReturnType(Method)}.</li>
 * <li>A method declared to return {@code Publisher}, {@code Flow.Publisher} or
 * {@code Object} is resolved from the value it returns. A publisher that emits more than
 * one element fails the call, rather than losing elements or buffering them.</li>
 * <li>Any other value is a plain result, and the method runs through
 * {@link McpModernAsyncUtils#fromBlocking}.</li>
 * </ul>
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class ModernReactiveSupport {

	private static final ReactiveAdapterRegistry adapters = ReactiveAdapterRegistry.getSharedInstance();

	private ModernReactiveSupport() {
	}

	/**
	 * Whether the declared return type is a reactive or asynchronous type: a
	 * {@code Mono}, a {@code Publisher}, a {@code CompletionStage} and the like.
	 */
	public static boolean isReactive(Method method) {
		return adapters.getAdapter(method.getReturnType()) != null;
	}

	/**
	 * Rejects a declared return type that is multi-valued by definition.
	 * {@code Publisher} and {@code Flow.Publisher} are accepted because they say nothing
	 * about the number of elements; the returned value decides.
	 * @param method the method to check
	 * @throws IllegalArgumentException if the method returns a {@code Flux} or another
	 * concrete multi-valued type
	 */
	public static void rejectMultiValuedReturnType(Method method) {
		Class<?> returnType = method.getReturnType();
		ReactiveAdapter adapter = adapters.getAdapter(returnType);
		if (adapter != null && adapter.isMultiValue() && returnType != Publisher.class
				&& returnType != Flow.Publisher.class) {
			throw new IllegalArgumentException("Method returns a " + returnType.getSimpleName()
					+ ", but an MCP request has one result; return a Mono of the result, for example a Mono<List<T>>: "
					+ method.getName() + " in " + method.getDeclaringClass().getName());
		}
	}

	/**
	 * The type the method's outcome has: the type argument of a reactive return, or the
	 * generic return type otherwise. A raw reactive return has no known outcome type.
	 */
	public static @Nullable Type outcomeType(Method method) {
		Type returnType = method.getGenericReturnType();
		if (!isReactive(method)) {
			return returnType;
		}
		return returnType instanceof ParameterizedType parameterized ? parameterized.getActualTypeArguments()[0] : null;
	}

	/**
	 * The raw class of {@link #outcomeType(Method)}: the erased return type of a
	 * non-reactive method, and for a reactive one the class of its type argument, or
	 * {@code null} when that is not known or is not a class (a wildcard or a type
	 * variable).
	 */
	public static @Nullable Class<?> outcomeClass(Method method) {
		if (!isReactive(method)) {
			return method.getReturnType();
		}
		Type type = outcomeType(method);
		if (type instanceof Class<?> clazz) {
			return clazz;
		}
		if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw) {
			return raw;
		}
		return null;
	}

	/**
	 * Invokes the method, surfacing the cause of an {@link InvocationTargetException}: a
	 * {@code RuntimeException} or {@code Error} as is, a checked exception wrapped in an
	 * {@link UndeclaredThrowableException}. The caller makes the method accessible once,
	 * when its callback is built.
	 */
	public static @Nullable Object invoke(Method method, Object bean, Object[] args) {
		try {
			return method.invoke(bean, args);
		}
		catch (IllegalAccessException ex) {
			throw new IllegalStateException("Failed to access method " + method.getName(), ex);
		}
		catch (InvocationTargetException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof RuntimeException runtime) {
				throw runtime;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new UndeclaredThrowableException(cause);
		}
	}

	/**
	 * Runs a method invocation and normalises what it returns into a {@code Mono}. A
	 * method with a reactive return type is expected not to block, so it is invoked on
	 * subscription on the calling thread; any other goes through
	 * {@link McpModernAsyncUtils#fromBlocking}.
	 * @param context the request context
	 * @param method the method being invoked
	 * @param invocation the invocation of the method
	 * @return the deferred result, empty when the method answers with nothing
	 */
	public static Mono<Object> invokeToMono(McpRequestContext context, Method method,
			Supplier<@Nullable Object> invocation) {
		if (isReactive(method)) {
			return Mono.defer(() -> toMono(invocation.get(), method));
		}
		return McpModernAsyncUtils.fromBlocking(context, invocation::get).flatMap(result -> toMono(result, method));
	}

	/**
	 * Normalises what a method returned into a {@code Mono}, by the type of the value: a
	 * single-valued type through its adapter, a multi-valued one by its only element, and
	 * anything else as a plain value. Taking two elements is enough to tell that there
	 * are too many, and cancels the publisher.
	 */
	private static Mono<Object> toMono(@Nullable Object result, Method method) {
		if (result == null) {
			return Mono.empty();
		}
		ReactiveAdapter adapter = adapters.getAdapter(result.getClass());
		if (adapter == null) {
			return Mono.just(result);
		}
		Publisher<Object> publisher = adapter.toPublisher(result);
		if (!adapter.isMultiValue()) {
			return Mono.from(publisher);
		}
		return Flux.from(publisher).take(2).collectList().flatMap(elements -> {
			if (elements.size() > 1) {
				return Mono.error(new IllegalStateException(
						"Method returned a publisher of more than one element, but an MCP request has one result; "
								+ "return a Mono of the result, for example a Mono<List<T>>: " + method.getName()
								+ " in " + method.getDeclaringClass().getName()));
			}
			return elements.isEmpty() ? Mono.empty() : Mono.just(elements.get(0));
		});
	}

}
