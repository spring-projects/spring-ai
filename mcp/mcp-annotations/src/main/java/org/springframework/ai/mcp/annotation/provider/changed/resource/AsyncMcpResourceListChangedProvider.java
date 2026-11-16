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

package org.springframework.ai.mcp.annotation.provider.changed.resource;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.util.Assert;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpResourceListChanged;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.method.changed.resource.AsyncMcpResourceListChangedMethodCallback;
import org.springframework.ai.mcp.annotation.method.changed.resource.AsyncModernResourceListChangedSpecification;
import org.springframework.ai.mcp.annotation.method.changed.resource.AsyncResourceListChangedSpecification;

/**
 * Provider for asynchronous resource list changed consumer callbacks.
 *
 * <p>
 * This class scans a list of objects for methods annotated with
 * {@link McpResourceListChanged} and creates {@link Function} callbacks for them. These
 * callbacks can be used to handle resource list change notifications from MCP servers in
 * a reactive way.
 *
 * <p>
 * Example usage: <pre>{@code
 * // Create a provider with a list of objects containing @McpResourceListChanged methods
 * AsyncMcpResourceListChangedProvider provider = new AsyncMcpResourceListChangedProvider(List.of(resourceListHandler));
 *
 * // Get the list of resource list changed consumer callbacks
 * List<AsyncResourceListChangedSpecification> specifications = provider.getResourceListChangedSpecifications();
 *
 * // Add the consumers to the client features
 * McpClientFeatures.Async clientFeatures = new McpClientFeatures.Async(
 *     clientInfo, clientCapabilities, roots,
 *     toolsChangeConsumers, resourcesChangeConsumers, promptsChangeConsumers,
 *     loggingConsumers, samplingHandler);
 * }</pre>
 *
 * @author Christian Tzolov
 * @see McpResourceListChanged
 * @see AsyncMcpResourceListChangedMethodCallback
 * @see AsyncResourceListChangedSpecification
 */
public class AsyncMcpResourceListChangedProvider {

	private final List<Object> resourceListChangedConsumerObjects;

	private @Nullable List<HandlerMethod> handlerMethods;

	/**
	 * Create a new AsyncMcpResourceListChangedProvider.
	 * @param resourceListChangedConsumerObjects the objects containing methods annotated
	 * with {@link McpResourceListChanged}
	 */
	public AsyncMcpResourceListChangedProvider(List<Object> resourceListChangedConsumerObjects) {
		Assert.notNull(resourceListChangedConsumerObjects, "resourceListChangedConsumerObjects cannot be null");
		this.resourceListChangedConsumerObjects = resourceListChangedConsumerObjects;
	}

	/**
	 * Get the list of resource list changed consumer specifications.
	 * @return the list of resource list changed consumer specifications
	 */
	public List<AsyncResourceListChangedSpecification> getResourceListChangedSpecifications() {
		return this.handlerMethods().stream().map(handlerMethod -> {
			Function<List<McpSchema.Resource>, Mono<Void>> methodCallback = handlerMethod.callback();
			return new AsyncResourceListChangedSpecification(handlerMethod.clients(), methodCallback);
		}).toList();
	}

	/**
	 * Get the resource list changed specifications for the connections that speak the
	 * 2026-07-28 ("modern") MCP revision. They are created from the same
	 * {@link McpResourceListChanged} methods as
	 * {@link #getResourceListChangedSpecifications()}, and with the same callbacks: a
	 * method serves the connections of both protocols, whichever schema type its list
	 * has.
	 * @return the modern resource list changed specifications
	 * @since 2.1.0
	 */
	public List<AsyncModernResourceListChangedSpecification> getModernResourceListChangedSpecifications() {
		return this.handlerMethods()
			.stream()
			.map(handlerMethod -> new AsyncModernResourceListChangedSpecification(handlerMethod.clients(),
					handlerMethod.callback()::applyModern))
			.toList();
	}

	/**
	 * The reactive {@link McpResourceListChanged} methods with their callbacks. The
	 * objects are scanned once, on first use, so that each method gets one callback,
	 * which the legacy and the modern specifications share.
	 */
	private List<HandlerMethod> handlerMethods() {
		List<HandlerMethod> handlerMethods = this.handlerMethods;
		if (handlerMethods == null) {
			handlerMethods = this.resourceListChangedConsumerObjects.stream()
				.flatMap(consumerObject -> Stream.of(doGetClassMethods(consumerObject))
					.filter(method -> method.isAnnotationPresent(McpResourceListChanged.class))
					.filter(McpPredicates.filterNonReactiveReturnTypeMethod())
					.sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
					.map(mcpResourceListChangedConsumerMethod -> {
						var resourceListChangedAnnotation = mcpResourceListChangedConsumerMethod
							.getAnnotation(McpResourceListChanged.class);
						AsyncMcpResourceListChangedMethodCallback methodCallback = AsyncMcpResourceListChangedMethodCallback
							.builder()
							.method(mcpResourceListChangedConsumerMethod)
							.bean(consumerObject)
							.build();
						return new HandlerMethod(resourceListChangedAnnotation.clients(), methodCallback);
					}))
				.toList();
			this.handlerMethods = handlerMethods;
		}
		return handlerMethods;
	}

	/**
	 * Returns the methods of the given bean class.
	 * @param bean the bean instance
	 * @return the methods of the bean class
	 */
	protected Method[] doGetClassMethods(Object bean) {
		return bean.getClass().getDeclaredMethods();
	}

	/**
	 * The clients an annotated method names and its callback.
	 */
	private record HandlerMethod(String[] clients, AsyncMcpResourceListChangedMethodCallback callback) {
	}

}
