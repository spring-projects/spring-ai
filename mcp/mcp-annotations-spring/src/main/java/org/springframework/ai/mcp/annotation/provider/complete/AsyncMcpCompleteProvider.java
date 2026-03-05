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

package org.springframework.ai.mcp.annotation.provider.complete;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncCompletionSpecification;
import io.modelcontextprotocol.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.adapter.CompleteAdapter;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.method.complete.AsyncMcpCompleteMethodCallback;

/**
 * Provider for asynchronous MCP complete methods.
 *
 * This provider creates completion specifications for methods annotated with
 * {@link McpComplete} that return reactive types and work with
 * {@link McpAsyncServerExchange}.
 *
 * @author Christian Tzolov
 */
public class AsyncMcpCompleteProvider {

	private static final Logger logger = LoggerFactory.getLogger(AsyncMcpCompleteProvider.class);

	private final List<Object> completeObjects;

	/**
	 * Create a new AsyncMcpCompletionProvider.
	 * @param completeObjects the objects containing methods annotated with
	 * {@link McpComplete}
	 */
	public AsyncMcpCompleteProvider(List<Object> completeObjects) {
		Assert.notNull(completeObjects, "completeObjects cannot be null");
		this.completeObjects = completeObjects;
	}

	/**
	 * Get the async completion specifications.
	 * @return the list of async completion specifications
	 */
	public List<AsyncCompletionSpecification> getCompleteSpecifications() {

		List<AsyncCompletionSpecification> asyncCompleteSpecification = this.completeObjects.stream()
			.map(completeObject -> Stream.of(doGetClassMethods(completeObject))
				.filter(method -> method.isAnnotationPresent(McpComplete.class))
				.filter(McpPredicates.filterNonReactiveReturnTypeMethod())
				.sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
				.map(mcpCompleteMethod -> {
					var completeAnnotation = mcpCompleteMethod.getAnnotation(McpComplete.class);
					var completeRef = CompleteAdapter.asCompleteReference(completeAnnotation, mcpCompleteMethod);

					var methodCallback = AsyncMcpCompleteMethodCallback.builder()
						.method(mcpCompleteMethod)
						.bean(completeObject)
						.prompt(completeAnnotation.prompt().isEmpty() ? null : completeAnnotation.prompt())
						.uri(completeAnnotation.uri().isEmpty() ? null : completeAnnotation.uri())
						.build();

					return new AsyncCompletionSpecification(completeRef, methodCallback);
				})
				.toList())
			.flatMap(List::stream)
			.toList();

		if (asyncCompleteSpecification.isEmpty()) {
			logger.warn("No async complete methods found in the provided complete objects: {}", this.completeObjects);
		}

		return asyncCompleteSpecification;
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
