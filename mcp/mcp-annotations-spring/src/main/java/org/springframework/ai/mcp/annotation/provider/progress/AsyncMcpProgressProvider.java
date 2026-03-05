/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp.annotation.provider.progress;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpProgress;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.method.progress.AsyncMcpProgressMethodCallback;
import org.springframework.ai.mcp.annotation.method.progress.AsyncProgressSpecification;

/**
 * Provider for asynchronous progress callbacks.
 *
 * <p>
 * This class scans a list of objects for methods annotated with {@link McpProgress} and
 * creates {@link Function} callbacks for them. These callbacks can be used to handle
 * progress notifications from MCP servers asynchronously.
 *
 * <p>
 * Example usage: <pre>{@code
 * // Create a provider with a list of objects containing @McpProgress methods
 * AsyncMcpProgressProvider provider = new AsyncMcpProgressProvider(List.of(progressHandler));
 *
 * // Get the list of progress callbacks
 * List<AsyncProgressSpecification> progressSpecs = provider.getProgressSpecifications();
 *
 * // Add the functions to the client features
 * McpClientFeatures.Async clientFeatures = new McpClientFeatures.Async(
 *     clientInfo, clientCapabilities, roots,
 *     toolsChangeConsumers, resourcesChangeConsumers, promptsChangeConsumers,
 *     loggingConsumers, progressHandlers, samplingHandler);
 * }</pre>
 *
 * @author Christian Tzolov
 * @see McpProgress
 * @see AsyncMcpProgressMethodCallback
 * @see ProgressNotification
 */
public class AsyncMcpProgressProvider {

	private final List<Object> progressObjects;

	/**
	 * Create a new AsyncMcpProgressProvider.
	 * @param progressObjects the objects containing methods annotated with
	 * {@link McpProgress}
	 */
	public AsyncMcpProgressProvider(List<Object> progressObjects) {
		this.progressObjects = progressObjects != null ? progressObjects : List.of();
	}

	/**
	 * Get the list of progress specifications.
	 * @return the list of progress specifications
	 */
	public List<AsyncProgressSpecification> getProgressSpecifications() {

		List<AsyncProgressSpecification> progressHandlers = this.progressObjects.stream()
			.map(progressObject -> Stream.of(doGetClassMethods(progressObject))
				.filter(method -> method.isAnnotationPresent(McpProgress.class))
				.filter(McpPredicates.filterNonReactiveReturnTypeMethod())
				.filter(method -> {
					// Check if it's specifically Mono<Void>
					Type genericReturnType = method.getGenericReturnType();
					if (genericReturnType instanceof ParameterizedType) {
						ParameterizedType paramType = (ParameterizedType) genericReturnType;
						Type[] typeArguments = paramType.getActualTypeArguments();
						if (typeArguments.length == 1) {
							return typeArguments[0] == Void.class;
						}
					}
					return false;
				})
				.sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
				.map(mcpProgressMethod -> {
					var progressAnnotation = mcpProgressMethod.getAnnotation(McpProgress.class);

					Function<ProgressNotification, Mono<Void>> methodCallback = AsyncMcpProgressMethodCallback.builder()
						.method(mcpProgressMethod)
						.bean(progressObject)
						.progress(progressAnnotation)
						.build();

					return new AsyncProgressSpecification(progressAnnotation.clients(), methodCallback);
				})
				.toList())
			.flatMap(List::stream)
			.toList();

		return progressHandlers;
	}

	/**
	 * Returns the methods of the given bean class.
	 * @param bean the bean instance
	 * @return the methods of the bean class
	 */
	protected Method[] doGetClassMethods(Object bean) {
		return bean.getClass().getDeclaredMethods();
	}

}
