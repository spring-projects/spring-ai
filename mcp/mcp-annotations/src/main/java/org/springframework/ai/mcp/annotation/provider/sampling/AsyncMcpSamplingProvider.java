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

package org.springframework.ai.mcp.annotation.provider.sampling;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult;
import io.modelcontextprotocol.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpSampling;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.method.sampling.AsyncMcpSamplingMethodCallback;
import org.springframework.ai.mcp.annotation.method.sampling.AsyncSamplingSpecification;

/**
 * Provider for asynchronous sampling callbacks.
 *
 * <p>
 * This class scans a list of objects for methods annotated with {@link McpSampling} and
 * creates {@link Function} callbacks for them. These callbacks can be used to handle
 * sampling requests from MCP servers in a reactive way.
 *
 * <p>
 * Example usage: <pre>{@code
 * // Create a provider with a list of objects containing @McpSampling methods
 * AsyncMcpSamplingProvider provider = new AsyncMcpSamplingProvider(List.of(samplingHandler));
 *
 * // Get the sampling handler
 * Function<CreateMessageRequest, Mono<CreateMessageResult>> samplingHandler = provider.getSamplingHandler();
 *
 * // Add the handler to the client features
 * McpClientFeatures.Async clientFeatures = new McpClientFeatures.Async(
 *     clientInfo, clientCapabilities, roots,
 *     toolsChangeConsumers, resourcesChangeConsumers, promptsChangeConsumers,
 *     loggingConsumers, samplingHandler);
 * }</pre>
 *
 * @author Christian Tzolov
 * @see McpSampling
 * @see AsyncMcpSamplingMethodCallback
 * @see CreateMessageRequest
 * @see CreateMessageResult
 */
public class AsyncMcpSamplingProvider {

	private static final Logger logger = LoggerFactory.getLogger(AsyncMcpSamplingProvider.class);

	private final List<Object> samplingObjects;

	/**
	 * Create a new AsyncMcpSamplingProvider.
	 * @param samplingObjects the objects containing methods annotated with
	 * {@link McpSampling}
	 */
	public AsyncMcpSamplingProvider(List<Object> samplingObjects) {
		Assert.notNull(samplingObjects, "samplingObjects cannot be null");
		this.samplingObjects = samplingObjects;
	}

	/**
	 * Get the sampling handler.
	 * @return the sampling handler
	 * @throws IllegalStateException if no sampling methods are found or if multiple
	 * sampling methods are found
	 */
	public List<AsyncSamplingSpecification> getSamplingSpecifictions() {
		List<AsyncSamplingSpecification> samplingHandlers = this.samplingObjects.stream()
			.map(samplingObject -> Stream.of(doGetClassMethods(samplingObject))
				.filter(method -> method.isAnnotationPresent(McpSampling.class))
				.filter(method -> method.getParameterCount() == 1
						&& CreateMessageRequest.class.isAssignableFrom(method.getParameterTypes()[0]))
				.filter(McpPredicates.filterNonReactiveReturnTypeMethod())
				.sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
				.map(mcpSamplingMethod -> {
					var samplingAnnotation = mcpSamplingMethod.getAnnotation(McpSampling.class);

					Function<CreateMessageRequest, Mono<CreateMessageResult>> methodCallback = AsyncMcpSamplingMethodCallback
						.builder()
						.method(mcpSamplingMethod)
						.bean(samplingObject)
						.sampling(samplingAnnotation)
						.build();

					return new AsyncSamplingSpecification(samplingAnnotation.clients(), methodCallback);
				})
				.toList())
			.flatMap(List::stream)
			.toList();

		if (samplingHandlers.isEmpty()) {
			logger.warn("No sampling methods found");
		}
		if (samplingHandlers.size() > 1) {
			logger.warn("Multiple sampling methods found: {}", samplingHandlers.size());
		}

		return samplingHandlers;
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
