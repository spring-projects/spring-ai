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

package org.springframework.ai.mcp.annotation.provider.changed.tool;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.util.Assert;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpToolListChanged;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.method.changed.tool.AsyncMcpToolListChangedMethodCallback;
import org.springframework.ai.mcp.annotation.method.changed.tool.AsyncToolListChangedSpecification;

/**
 * Provider for asynchronous tool list changed consumer callbacks.
 *
 * <p>
 * This class scans a list of objects for methods annotated with
 * {@link McpToolListChanged} and creates {@link Function} callbacks for them. These
 * callbacks can be used to handle tool list change notifications from MCP servers in a
 * reactive way.
 *
 * <p>
 * Example usage: <pre>{@code
 * // Create a provider with a list of objects containing @McpToolListChanged methods
 * AsyncMcpToolListChangedProvider provider = new AsyncMcpToolListChangedProvider(List.of(toolListHandler));
 *
 * // Get the list of tool list changed consumer callbacks
 * List<AsyncToolListChangedSpecification> specifications = provider.getToolListChangedSpecifications();
 *
 * // Add the consumers to the client features
 * McpClientFeatures.Async clientFeatures = new McpClientFeatures.Async(
 *     clientInfo, clientCapabilities, roots,
 *     toolsChangeConsumers, resourcesChangeConsumers, promptsChangeConsumers,
 *     loggingConsumers, samplingHandler);
 * }</pre>
 *
 * @author Christian Tzolov
 * @see McpToolListChanged
 * @see AsyncMcpToolListChangedMethodCallback
 * @see AsyncToolListChangedSpecification
 */
public class AsyncMcpToolListChangedProvider {

	private final List<Object> toolListChangedConsumerObjects;

	/**
	 * Create a new AsyncMcpToolListChangedProvider.
	 * @param toolListChangedConsumerObjects the objects containing methods annotated with
	 * {@link McpToolListChanged}
	 */
	public AsyncMcpToolListChangedProvider(List<Object> toolListChangedConsumerObjects) {
		Assert.notNull(toolListChangedConsumerObjects, "toolListChangedConsumerObjects cannot be null");
		this.toolListChangedConsumerObjects = toolListChangedConsumerObjects;
	}

	/**
	 * Get the list of tool list changed consumer specifications.
	 * @return the list of tool list changed consumer specifications
	 */
	public List<AsyncToolListChangedSpecification> getToolListChangedSpecifications() {

		List<AsyncToolListChangedSpecification> toolListChangedConsumers = this.toolListChangedConsumerObjects.stream()
			.map(consumerObject -> Stream.of(doGetClassMethods(consumerObject))
				.filter(method -> method.isAnnotationPresent(McpToolListChanged.class))
				.filter(McpPredicates.filterNonReactiveReturnTypeMethod())
				.sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
				.map(mcpToolListChangedConsumerMethod -> {
					var toolListChangedAnnotation = mcpToolListChangedConsumerMethod
						.getAnnotation(McpToolListChanged.class);

					Function<List<McpSchema.Tool>, Mono<Void>> methodCallback = AsyncMcpToolListChangedMethodCallback
						.builder()
						.method(mcpToolListChangedConsumerMethod)
						.bean(consumerObject)
						.build();

					return new AsyncToolListChangedSpecification(toolListChangedAnnotation.clients(), methodCallback);
				})
				.toList())
			.flatMap(List::stream)
			.toList();

		return toolListChangedConsumers;
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
