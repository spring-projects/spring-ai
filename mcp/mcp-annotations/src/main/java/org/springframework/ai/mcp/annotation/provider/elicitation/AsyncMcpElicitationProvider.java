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
import java.util.stream.Stream;

import io.modelcontextprotocol.util.Assert;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.common.ModernClientHandlerSupport;
import org.springframework.ai.mcp.annotation.method.elicitation.AsyncElicitationSpecification;
import org.springframework.ai.mcp.annotation.method.elicitation.AsyncMcpElicitationMethodCallback;
import org.springframework.ai.mcp.annotation.method.elicitation.AsyncModernElicitationSpecification;

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

	private static final Log logger = LogFactory.getLog(AsyncMcpElicitationProvider.class);

	private final List<Object> elicitationObjects;

	/** The annotated methods and their callbacks, found on first use. */
	private @Nullable List<HandlerMethod> handlerMethods;

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
	 */
	public List<AsyncElicitationSpecification> getElicitationSpecifications() {

		List<AsyncElicitationSpecification> elicitationHandlers = this.handlerMethods()
			.stream()
			.map(handlerMethod -> new AsyncElicitationSpecification(handlerMethod.clients(), handlerMethod.callback()))
			.toList();

		if (elicitationHandlers.isEmpty()) {
			logger.warn("No elicitation methods found");
		}
		if (elicitationHandlers.size() > 1) {
			if (logger.isWarnEnabled()) {
				logger.warn("Multiple elicitation methods found: " + elicitationHandlers.size());
			}
		}

		return elicitationHandlers;
	}

	/**
	 * Get the elicitation specifications for the connections that speak the 2026-07-28
	 * ("modern") MCP revision. They are created from the same {@code @McpElicitation}
	 * methods as {@link #getElicitationSpecifications()}, and with the same callbacks. A
	 * method that takes only the legacy URL request is left out, because the modern
	 * revision has no such request.
	 * @return the modern elicitation specifications
	 * @since 2.1.0
	 */
	public List<AsyncModernElicitationSpecification> getModernElicitationSpecifications() {
		return this.handlerMethods().stream().filter(handlerMethod -> {
			Method method = handlerMethod.method();
			return ModernClientHandlerSupport.handlesModernFormElicitationAsync(method)
					|| ModernClientHandlerSupport.handlesModernUrlElicitationAsync(method);
		})
			.map(handlerMethod -> new AsyncModernElicitationSpecification(handlerMethod.clients(),
					ModernClientHandlerSupport.handlesModernFormElicitationAsync(handlerMethod.method()),
					ModernClientHandlerSupport.handlesModernUrlElicitationAsync(handlerMethod.method()),
					handlerMethod.callback()::applyModern))
			.toList();
	}

	private List<HandlerMethod> handlerMethods() {
		List<HandlerMethod> handlerMethods = this.handlerMethods;
		if (handlerMethods == null) {
			handlerMethods = this.elicitationObjects.stream()
				.flatMap(elicitationObject -> Stream.of(doGetClassMethods(elicitationObject))
					.filter(method -> method.isAnnotationPresent(McpElicitation.class))
					.filter(McpPredicates.filterNonReactiveReturnTypeMethod())
					// A reactive method that does not return a Mono fails in the builder
					// of its callback, and so at startup, rather than being left out.
					.filter(ModernClientHandlerSupport::takesElicitRequest)
					.sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
					.map(mcpElicitationMethod -> {
						var elicitationAnnotation = mcpElicitationMethod.getAnnotation(McpElicitation.class);
						AsyncMcpElicitationMethodCallback methodCallback = AsyncMcpElicitationMethodCallback.builder()
							.method(mcpElicitationMethod)
							.bean(elicitationObject)
							.elicitation(elicitationAnnotation)
							.build();
						return new HandlerMethod(mcpElicitationMethod, elicitationAnnotation.clients(), methodCallback);
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
	 * An annotated method, the clients it names and its callback.
	 */
	private record HandlerMethod(Method method, String[] clients, AsyncMcpElicitationMethodCallback callback) {
	}

}
