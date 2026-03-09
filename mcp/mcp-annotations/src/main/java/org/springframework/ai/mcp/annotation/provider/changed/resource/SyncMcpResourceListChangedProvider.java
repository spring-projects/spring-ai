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

package org.springframework.ai.mcp.annotation.provider.changed.resource;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.util.Assert;

import org.springframework.ai.mcp.annotation.McpResourceListChanged;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.method.changed.resource.SyncMcpResourceListChangedMethodCallback;
import org.springframework.ai.mcp.annotation.method.changed.resource.SyncResourceListChangedSpecification;

/**
 * Provider for synchronous resource list changed consumer callbacks.
 *
 * <p>
 * This class scans a list of objects for methods annotated with
 * {@link McpResourceListChanged} and creates {@link Consumer} callbacks for them. These
 * callbacks can be used to handle resource list change notifications from MCP servers.
 *
 * <p>
 * Example usage: <pre>{@code
 * // Create a provider with a list of objects containing @McpResourceListChanged methods
 * SyncMcpResourceListChangedProvider provider = new SyncMcpResourceListChangedProvider(List.of(resourceListHandler));
 *
 * // Get the list of resource list changed consumer callbacks
 * List<SyncResourceListChangedSpecification> specifications = provider.getResourceListChangedSpecifications();
 *
 * // Add the consumers to the client features
 * McpClientFeatures.Sync clientFeatures = new McpClientFeatures.Sync(
 *     clientInfo, clientCapabilities, roots,
 *     toolsChangeConsumers, resourcesChangeConsumers, promptsChangeConsumers,
 *     loggingConsumers, samplingHandler);
 * }</pre>
 *
 * @author Christian Tzolov
 * @see McpResourceListChanged
 * @see SyncMcpResourceListChangedMethodCallback
 * @see SyncResourceListChangedSpecification
 */
public class SyncMcpResourceListChangedProvider {

	private final List<Object> resourceListChangedConsumerObjects;

	/**
	 * Create a new SyncMcpResourceListChangedProvider.
	 * @param resourceListChangedConsumerObjects the objects containing methods annotated
	 * with {@link McpResourceListChanged}
	 */
	public SyncMcpResourceListChangedProvider(List<Object> resourceListChangedConsumerObjects) {
		Assert.notNull(resourceListChangedConsumerObjects, "resourceListChangedConsumerObjects cannot be null");
		this.resourceListChangedConsumerObjects = resourceListChangedConsumerObjects;
	}

	/**
	 * Get the list of resource list changed consumer specifications.
	 * @return the list of resource list changed consumer specifications
	 */
	public List<SyncResourceListChangedSpecification> getResourceListChangedSpecifications() {

		List<SyncResourceListChangedSpecification> resourceListChangedConsumers = this.resourceListChangedConsumerObjects
			.stream()
			.map(consumerObject -> Stream.of(doGetClassMethods(consumerObject))
				.filter(method -> method.isAnnotationPresent(McpResourceListChanged.class))
				.filter(McpPredicates.filterReactiveReturnTypeMethod())
				.sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
				.map(mcpResourceListChangedConsumerMethod -> {
					var resourceListChangedAnnotation = mcpResourceListChangedConsumerMethod
						.getAnnotation(McpResourceListChanged.class);

					Consumer<List<McpSchema.Resource>> methodCallback = SyncMcpResourceListChangedMethodCallback
						.builder()
						.method(mcpResourceListChangedConsumerMethod)
						.bean(consumerObject)
						.resourceListChanged(resourceListChangedAnnotation)
						.build();

					return new SyncResourceListChangedSpecification(resourceListChangedAnnotation.clients(),
							methodCallback);
				})
				.toList())
			.flatMap(List::stream)
			.toList();

		return resourceListChangedConsumers;
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
