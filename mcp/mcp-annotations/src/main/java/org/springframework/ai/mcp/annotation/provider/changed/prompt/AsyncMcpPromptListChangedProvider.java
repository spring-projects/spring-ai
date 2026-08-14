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

package org.springframework.ai.mcp.annotation.provider.changed.prompt;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.util.Assert;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpPromptListChanged;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.method.changed.prompt.AsyncMcpPromptListChangedMethodCallback;
import org.springframework.ai.mcp.annotation.method.changed.prompt.AsyncPromptListChangedSpecification;

/**
 * Provider for asynchronous prompt list changed consumer callbacks.
 *
 * <p>
 * This class scans a list of objects for methods annotated with
 * {@link McpPromptListChanged} and creates {@link Function} callbacks for them. These
 * callbacks can be used to handle prompt list change notifications from MCP servers in a
 * reactive way.
 *
 * <p>
 * Example usage: <pre>{@code
 * // Create a provider with a list of objects containing @McpPromptListChanged methods
 * AsyncMcpPromptListChangedProvider provider = new AsyncMcpPromptListChangedProvider(List.of(promptListHandler));
 *
 * // Get the list of prompt list changed consumer callbacks
 * List<AsyncPromptListChangedSpecification> specifications = provider.getPromptListChangedSpecifications();
 *
 * // Add the consumers to the client features
 * McpClientFeatures.Async clientFeatures = new McpClientFeatures.Async(
 *     clientInfo, clientCapabilities, roots,
 *     toolsChangeConsumers, resourcesChangeConsumers, promptsChangeConsumers,
 *     loggingConsumers, samplingHandler);
 * }</pre>
 *
 * @author Christian Tzolov
 * @see McpPromptListChanged
 * @see AsyncMcpPromptListChangedMethodCallback
 * @see AsyncPromptListChangedSpecification
 */
public class AsyncMcpPromptListChangedProvider {

	private final List<Object> promptListChangedConsumerObjects;

	/**
	 * Create a new AsyncMcpPromptListChangedProvider.
	 * @param promptListChangedConsumerObjects the objects containing methods annotated
	 * with {@link McpPromptListChanged}
	 */
	public AsyncMcpPromptListChangedProvider(List<Object> promptListChangedConsumerObjects) {
		Assert.notNull(promptListChangedConsumerObjects, "promptListChangedConsumerObjects cannot be null");
		this.promptListChangedConsumerObjects = promptListChangedConsumerObjects;
	}

	/**
	 * Get the list of prompt list changed consumer specifications.
	 * @return the list of prompt list changed consumer specifications
	 */
	public List<AsyncPromptListChangedSpecification> getPromptListChangedSpecifications() {

		List<AsyncPromptListChangedSpecification> promptListChangedConsumers = this.promptListChangedConsumerObjects
			.stream()
			.map(consumerObject -> Stream.of(doGetClassMethods(consumerObject))
				.filter(method -> method.isAnnotationPresent(McpPromptListChanged.class))
				.filter(McpPredicates.filterNonReactiveReturnTypeMethod())
				.sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
				.map(mcpPromptListChangedConsumerMethod -> {
					var promptListChangedAnnotation = mcpPromptListChangedConsumerMethod
						.getAnnotation(McpPromptListChanged.class);

					Function<List<McpSchema.Prompt>, Mono<Void>> methodCallback = AsyncMcpPromptListChangedMethodCallback
						.builder()
						.method(mcpPromptListChangedConsumerMethod)
						.bean(consumerObject)
						.build();

					return new AsyncPromptListChangedSpecification(promptListChangedAnnotation.clients(),
							methodCallback);
				})
				.toList())
			.flatMap(List::stream)
			.toList();

		return promptListChangedConsumers;
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
