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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.util.ConcurrentReferenceHashMap;

public final class ReactiveUtils {

	private ReactiveUtils() {
	}

	private static final Map<Type, Boolean> isReactiveOfVoidCache = new ConcurrentReferenceHashMap<>(256);

	private static final Map<Type, Boolean> isReactiveOfCallToolResultCache = new ConcurrentReferenceHashMap<>(256);

	/**
	 * Check if the given type is a reactive type containing Void (e.g., Mono<Void>,
	 * Flux<Void>, Publisher<Void>)
	 */
	public static boolean isReactiveReturnTypeOfVoid(Method method) {
		Type returnType = method.getGenericReturnType();
		if (isReactiveOfVoidCache.containsKey(returnType)) {
			return isReactiveOfVoidCache.get(returnType);
		}

		if (!(returnType instanceof ParameterizedType)) {
			isReactiveOfVoidCache.putIfAbsent(returnType, false);
			return false;
		}

		boolean isReactiveOfVoid = false;

		ParameterizedType parameterizedType = (ParameterizedType) returnType;
		Type rawType = parameterizedType.getRawType();

		// Check if raw type is a reactive type (Mono, Flux, or Publisher)
		if (rawType instanceof Class) {
			Class<?> rawClass = (Class<?>) rawType;
			if (Mono.class.isAssignableFrom(rawClass) || Flux.class.isAssignableFrom(rawClass)
					|| Publisher.class.isAssignableFrom(rawClass)) {

				Type[] typeArguments = parameterizedType.getActualTypeArguments();
				if (typeArguments.length == 1) {
					Type typeArgument = typeArguments[0];
					if (typeArgument instanceof Class) {
						isReactiveOfVoid = Void.class.equals(typeArgument) || void.class.equals(typeArgument);
					}
				}
			}
		}

		isReactiveOfVoidCache.putIfAbsent(returnType, isReactiveOfVoid);

		return isReactiveOfVoid;
	}

	/**
	 * Check if the given type is a reactive type containing CallToolResult (e.g.,
	 * Mono<CallToolResult>, Flux<CallToolResult>, Publisher<CallToolResult>)
	 */
	public static boolean isReactiveReturnTypeOfCallToolResult(Method method) {

		Type returnType = method.getGenericReturnType();

		if (isReactiveOfCallToolResultCache.containsKey(returnType)) {
			return isReactiveOfCallToolResultCache.get(returnType);
		}

		if (!(returnType instanceof ParameterizedType)) {
			isReactiveOfCallToolResultCache.putIfAbsent(returnType, false);
			return false;
		}
		boolean isReactiveOfCallToolResult = false;

		ParameterizedType parameterizedType = (ParameterizedType) returnType;
		Type rawType = parameterizedType.getRawType();

		// Check if raw type is a reactive type (Mono, Flux, or Publisher)
		if (rawType instanceof Class) {
			Class<?> rawClass = (Class<?>) rawType;
			if (Mono.class.isAssignableFrom(rawClass) || Flux.class.isAssignableFrom(rawClass)
					|| Publisher.class.isAssignableFrom(rawClass)) {

				Type[] typeArguments = parameterizedType.getActualTypeArguments();
				if (typeArguments.length == 1) {
					Type typeArgument = typeArguments[0];
					if (typeArgument instanceof Class) {
						isReactiveOfCallToolResult = CallToolResult.class.isAssignableFrom((Class<?>) typeArgument);
					}
				}
			}
		}

		isReactiveOfCallToolResultCache.putIfAbsent(returnType, isReactiveOfCallToolResult);

		return isReactiveOfCallToolResult;
	}

	public static Optional<Type> getReactiveReturnTypeArgument(Method method) {

		Type returnType = method.getGenericReturnType();

		if (returnType instanceof ParameterizedType) {
			ParameterizedType parameterizedType = (ParameterizedType) returnType;
			Type rawType = parameterizedType.getRawType();

			// Check if raw type is a reactive type (Mono, Flux, or Publisher)
			if (rawType instanceof Class) {
				Class<?> rawClass = (Class<?>) rawType;
				if (Mono.class.isAssignableFrom(rawClass) || Flux.class.isAssignableFrom(rawClass)
						|| Publisher.class.isAssignableFrom(rawClass)) {

					return Optional.of(parameterizedType.getActualTypeArguments()[0]);
				}
			}
		}

		return Optional.empty();

	}

}
