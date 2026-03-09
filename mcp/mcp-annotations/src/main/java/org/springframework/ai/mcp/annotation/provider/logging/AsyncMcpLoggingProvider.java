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

package org.springframework.ai.mcp.annotation.provider.logging;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import io.modelcontextprotocol.spec.McpSchema.LoggingMessageNotification;
import io.modelcontextprotocol.util.Assert;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpLogging;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.method.logging.AsyncLoggingSpecification;
import org.springframework.ai.mcp.annotation.method.logging.AsyncMcpLoggingMethodCallback;

/**
 * Provider for asynchronous logging consumer callbacks.
 *
 * <p>
 * This class scans a list of objects for methods annotated with {@link McpLogging} and
 * creates {@link Function} callbacks for them. These callbacks can be used to handle
 * logging message notifications from MCP servers in a reactive way.
 *
 * <p>
 * Example usage: <pre>{@code
 * // Create a provider with a list of objects containing @McpLoggingConsumer methods
 * AsyncMcpLoggingConsumerProvider provider = new AsyncMcpLoggingConsumerProvider(List.of(loggingHandler));
 *
 * // Get the list of logging consumer callbacks
 * List<Function<LoggingMessageNotification, Mono<Void>>> consumers = provider.getLoggingConsumers();
 *
 * // Add the consumers to the client features
 * McpClientFeatures.Async clientFeatures = new McpClientFeatures.Async(
 *     clientInfo, clientCapabilities, roots,
 *     toolsChangeConsumers, resourcesChangeConsumers, promptsChangeConsumers,
 *     consumers, samplingHandler);
 * }</pre>
 *
 * @author Christian Tzolov
 * @see McpLogging
 * @see AsyncMcpLoggingMethodCallback
 * @see LoggingMessageNotification
 */
public class AsyncMcpLoggingProvider {

	private final List<Object> loggingConsumerObjects;

	/**
	 * Create a new AsyncMcpLoggingConsumerProvider.
	 * @param loggingConsumerObjects the objects containing methods annotated with
	 * {@link McpLogging}
	 */
	public AsyncMcpLoggingProvider(List<Object> loggingConsumerObjects) {
		Assert.notNull(loggingConsumerObjects, "loggingConsumerObjects cannot be null");
		this.loggingConsumerObjects = loggingConsumerObjects;
	}

	/**
	 * Get the list of logging consumer callbacks.
	 * @return the list of logging consumer callbacks
	 */
	public List<AsyncLoggingSpecification> getLoggingSpecifications() {

		List<AsyncLoggingSpecification> loggingConsumers = this.loggingConsumerObjects.stream()
			.map(consumerObject -> Stream.of(this.doGetClassMethods(consumerObject))
				.filter(method -> method.isAnnotationPresent(McpLogging.class))
				.filter(McpPredicates.filterNonReactiveReturnTypeMethod())
				.sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
				.map(mcpLoggingConsumerMethod -> {
					var loggingConsumerAnnotation = mcpLoggingConsumerMethod.getAnnotation(McpLogging.class);

					Function<LoggingMessageNotification, Mono<Void>> methodCallback = AsyncMcpLoggingMethodCallback
						.builder()
						.method(mcpLoggingConsumerMethod)
						.bean(consumerObject)
						.loggingConsumer(loggingConsumerAnnotation)
						.build();

					return new AsyncLoggingSpecification(loggingConsumerAnnotation.clients(), methodCallback);
				})
				.toList())
			.flatMap(List::stream)
			.toList();

		return loggingConsumers;
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
