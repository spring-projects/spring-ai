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

package org.springframework.ai.mcp.annotation.provider.complete;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.adapter.CompleteAdapter;

/**
 * Collects the {@link McpComplete} methods of the sync and async modern complete
 * providers, keyed by prompt name or URI template.
 *
 * @author Christian Tzolov
 */
final class ModernCompleteMethods {

	private ModernCompleteMethods() {
	}

	/**
	 * The handlers of the methods that complete a prompt, or a resource template when
	 * {@code prompts} is {@code false}.
	 * @param beanMethods the methods of a bean
	 * @param supported the methods the provider can serve; the others are skipped
	 * @param handler builds the handler of a bean's method
	 * @throws IllegalArgumentException if two methods complete the same prompt or
	 * template
	 */
	static <H> Map<String, H> collect(List<Object> beans, Function<Object, Method[]> beanMethods,
			Predicate<Method> supported, boolean prompts, BiFunction<Object, Method, H> handler) {
		Map<String, Method> methods = new LinkedHashMap<>();
		Map<String, H> completions = new LinkedHashMap<>();
		for (Object bean : beans) {
			// The key filter runs before the provider's filter, which warns about each
			// method it
			// skips, so that a method is reported once although collect runs for prompts
			// and for
			// templates.
			Stream.of(beanMethods.apply(bean))
				.filter(method -> method.isAnnotationPresent(McpComplete.class))
				.filter(method -> !key(method, prompts).isEmpty())
				.filter(supported)
				.sorted(Comparator.comparing(Method::getName))
				.forEach(method -> {
					String key = key(method, prompts);
					Method existing = methods.putIfAbsent(key, method);
					if (existing != null) {
						throw new IllegalArgumentException("Both " + describe(existing) + " and " + describe(method)
								+ " complete the " + (prompts ? "prompt '" : "resource template '") + key
								+ "'; one method completes every argument of a prompt or template");
					}
					completions.put(key, handler.apply(bean, method));
				});
		}
		return completions;
	}

	/**
	 * The prompt name or URI template the method completes, empty when it completes the
	 * other kind of reference.
	 * @throws IllegalArgumentException if the annotation names both or neither
	 */
	private static String key(Method method, boolean prompts) {
		McpComplete annotation = method.getAnnotation(McpComplete.class);
		CompleteAdapter.asCompleteReference(annotation, method);
		return prompts ? annotation.prompt() : annotation.uri();
	}

	private static String describe(Method method) {
		return method.getDeclaringClass().getName() + "." + method.getName();
	}

}
