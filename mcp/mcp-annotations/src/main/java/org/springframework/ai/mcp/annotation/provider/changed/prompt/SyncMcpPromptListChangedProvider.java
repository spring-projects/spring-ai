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
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.util.Assert;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.mcp.annotation.McpPromptListChanged;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.method.changed.prompt.SyncMcpPromptListChangedMethodCallback;
import org.springframework.ai.mcp.annotation.method.changed.prompt.SyncModernPromptListChangedSpecification;
import org.springframework.ai.mcp.annotation.method.changed.prompt.SyncPromptListChangedSpecification;

/**
 * Provider for synchronous prompt list changed consumer callbacks.
 *
 * <p>
 * This class scans a list of objects for methods annotated with
 * {@link McpPromptListChanged} and creates {@link Consumer} callbacks for them. These
 * callbacks can be used to handle prompt list change notifications from MCP servers.
 *
 * <p>
 * Example usage: <pre>{@code
 * // Create a provider with a list of objects containing @McpPromptListChanged methods
 * SyncMcpPromptListChangedProvider provider = new SyncMcpPromptListChangedProvider(List.of(promptListHandler));
 *
 * // Get the list of prompt list changed consumer callbacks
 * List<SyncPromptListChangedSpecification> specifications = provider.getPromptListChangedSpecifications();
 *
 * // Add the consumers to the client features
 * McpClientFeatures.Sync clientFeatures = new McpClientFeatures.Sync(
 *     clientInfo, clientCapabilities, roots,
 *     toolsChangeConsumers, resourcesChangeConsumers, promptsChangeConsumers,
 *     loggingConsumers, samplingHandler);
 * }</pre>
 *
 * @author Christian Tzolov
 * @see McpPromptListChanged
 * @see SyncMcpPromptListChangedMethodCallback
 * @see SyncPromptListChangedSpecification
 */
public class SyncMcpPromptListChangedProvider {

	private final List<Object> promptListChangedConsumerObjects;

	private @Nullable List<HandlerMethod> handlerMethods;

	/**
	 * Create a new SyncMcpPromptListChangedProvider.
	 * @param promptListChangedConsumerObjects the objects containing methods annotated
	 * with {@link McpPromptListChanged}
	 */
	public SyncMcpPromptListChangedProvider(List<Object> promptListChangedConsumerObjects) {
		Assert.notNull(promptListChangedConsumerObjects, "promptListChangedConsumerObjects cannot be null");
		this.promptListChangedConsumerObjects = promptListChangedConsumerObjects;
	}

	/**
	 * Get the list of prompt list changed consumer specifications.
	 * @return the list of prompt list changed consumer specifications
	 */
	public List<SyncPromptListChangedSpecification> getPromptListChangedSpecifications() {
		return this.handlerMethods().stream().map(handlerMethod -> {
			Consumer<List<McpSchema.Prompt>> methodCallback = handlerMethod.callback();
			return new SyncPromptListChangedSpecification(handlerMethod.clients(), methodCallback);
		}).toList();
	}

	/**
	 * Get the prompt list changed specifications for the connections that speak the
	 * 2026-07-28 ("modern") MCP revision. They are created from the same
	 * {@link McpPromptListChanged} methods as
	 * {@link #getPromptListChangedSpecifications()}, and with the same callbacks: a
	 * method serves the connections of both protocols, whichever schema type its list
	 * has.
	 * @return the modern prompt list changed specifications
	 * @since 2.1.0
	 */
	public List<SyncModernPromptListChangedSpecification> getModernPromptListChangedSpecifications() {
		return this.handlerMethods()
			.stream()
			.map(handlerMethod -> new SyncModernPromptListChangedSpecification(handlerMethod.clients(),
					handlerMethod.callback()::acceptModern))
			.toList();
	}

	/**
	 * The synchronous {@link McpPromptListChanged} methods with their callbacks. The
	 * objects are scanned once, on first use, so that each method gets one callback,
	 * which the legacy and the modern specifications share.
	 */
	private List<HandlerMethod> handlerMethods() {
		List<HandlerMethod> handlerMethods = this.handlerMethods;
		if (handlerMethods == null) {
			handlerMethods = this.promptListChangedConsumerObjects.stream()
				.flatMap(consumerObject -> Stream.of(doGetClassMethods(consumerObject))
					.filter(method -> method.isAnnotationPresent(McpPromptListChanged.class))
					.filter(McpPredicates.filterReactiveReturnTypeMethod())
					.sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
					.map(mcpPromptListChangedConsumerMethod -> {
						var promptListChangedAnnotation = mcpPromptListChangedConsumerMethod
							.getAnnotation(McpPromptListChanged.class);

						SyncMcpPromptListChangedMethodCallback methodCallback = SyncMcpPromptListChangedMethodCallback
							.builder()
							.method(mcpPromptListChangedConsumerMethod)
							.bean(consumerObject)
							.promptListChanged(promptListChangedAnnotation)
							.build();

						return new HandlerMethod(promptListChangedAnnotation.clients(), methodCallback);
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
	private record HandlerMethod(String[] clients, SyncMcpPromptListChangedMethodCallback callback) {
	}

}
