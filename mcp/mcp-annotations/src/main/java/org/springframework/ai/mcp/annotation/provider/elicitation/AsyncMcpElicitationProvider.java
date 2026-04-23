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

package org.springframework.ai.mcp.annotation.provider.elicitation;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.method.elicitation.AsyncElicitationSpecification;
import org.springframework.ai.mcp.annotation.method.elicitation.AsyncMcpElicitationMethodCallback;

/**
 * Provider for asynchronous elicitation callbacks.
 *
 * <p>
 * This class scans a list of objects for methods annotated with {@link McpElicitation}
 * and creates {@link Function} callbacks for them. These callbacks can be used to handle
 * elicitation requests from MCP servers in a reactive way.
 *
 * <p>
 * Example usage: <pre>{@code
 * // Create a provider with a list of objects containing @McpElicitation methods
 * AsyncMcpElicitationProvider provider = new AsyncMcpElicitationProvider(List.of(elicitationHandler));
 *
 * // Get the elicitation handler
 * Function<ElicitRequest, Mono<ElicitResult>> elicitationHandler = provider.getElicitationHandler();
 *
 * // Add the handler to the client features
 * McpClientFeatures.Async clientFeatures = new McpClientFeatures.Async(
 *     clientInfo, clientCapabilities, roots,
 *     toolsChangeConsumers, resourcesChangeConsumers, promptsChangeConsumers,
 *     loggingConsumers, samplingHandler, elicitationHandler);
 * }</pre>
 *
 * @author Christian Tzolov
 * @see McpElicitation
 * @see AsyncMcpElicitationMethodCallback
 * @see ElicitRequest
 * @see ElicitResult
 */
public class AsyncMcpElicitationProvider {

	private static final Logger logger = LoggerFactory.getLogger(AsyncMcpElicitationProvider.class);

	private final List<Object> elicitationObjects;

	/**
	 * Create a new AsyncMcpElicitationProvider.
	 * @param elicitationObjects the objects containing methods annotated with
	 * {@link McpElicitation}
	 */
	public AsyncMcpElicitationProvider(List<Object> elicitationObjects) {
		Assert.notNull(elicitationObjects, "elicitationObjects cannot be null");
		this.elicitationObjects = elicitationObjects;
	}

	/**
	 * Get the elicitation specifications.
	 * @return the elicitation specifications
	 * @throws IllegalStateException if no elicitation methods are found or if multiple
	 * elicitation methods are found
	 */
	public List<AsyncElicitationSpecification> getElicitationSpecifications() {
		List<AsyncElicitationSpecification> elicitationHandlers = this.elicitationObjects.stream()
			.map(elicitationObject -> Stream.of(doGetClassMethods(elicitationObject))
				.filter(method -> method.isAnnotationPresent(McpElicitation.class))
				.filter(method -> method.getParameterCount() == 1
						&& ElicitRequest.class.isAssignableFrom(method.getParameterTypes()[0]))
				.filter(McpPredicates.filterNonReactiveReturnTypeMethod())
				.sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
				.map(mcpElicitationMethod -> {
					var elicitationAnnotation = mcpElicitationMethod.getAnnotation(McpElicitation.class);

					Function<ElicitRequest, Mono<ElicitResult>> methodCallback = AsyncMcpElicitationMethodCallback
						.builder()
						.method(mcpElicitationMethod)
						.bean(elicitationObject)
						.elicitation(elicitationAnnotation)
						.build();

					return new AsyncElicitationSpecification(elicitationAnnotation.clients(), methodCallback);
				})
				.toList())
			.flatMap(List::stream)
			.toList();

		if (elicitationHandlers.isEmpty()) {
			logger.warn("No elicitation methods found");
		}
		if (elicitationHandlers.size() > 1) {
			logger.warn("Multiple elicitation methods found: {}", elicitationHandlers.size());
		}

		return elicitationHandlers;
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
