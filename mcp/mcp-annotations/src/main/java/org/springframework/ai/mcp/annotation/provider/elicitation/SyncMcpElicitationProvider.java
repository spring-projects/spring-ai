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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.common.ModernClientHandlerSupport;
import org.springframework.ai.mcp.annotation.method.elicitation.SyncElicitationSpecification;
import org.springframework.ai.mcp.annotation.method.elicitation.SyncMcpElicitationMethodCallback;
import org.springframework.ai.mcp.annotation.method.elicitation.SyncModernElicitationSpecification;

/**
 * Provider for synchronous elicitation callbacks.
 *
 * <p>
 * This class scans a list of objects for methods annotated with {@link McpElicitation}
 * and creates {@link Function} callbacks for them. These callbacks can be used to handle
 * elicitation requests from MCP servers.
 *
 * <p>
 * Example usage: <pre>{@code
 * // Create a provider with a list of objects containing @McpElicitation methods
 * SyncMcpElicitationProvider provider = new SyncMcpElicitationProvider(List.of(elicitationHandler));
 *
 * // Get the elicitation handler
 * Function<ElicitRequest, ElicitResult> elicitationHandler = provider.getElicitationHandler();
 *
 * // Add the handler to the client features
 * McpClientFeatures.Sync clientFeatures = new McpClientFeatures.Sync(
 *     clientInfo, clientCapabilities, roots,
 *     toolsChangeConsumers, resourcesChangeConsumers, promptsChangeConsumers,
 *     loggingConsumers, samplingHandler, elicitationHandler);
 * }</pre>
 *
 * @author Christian Tzolov
 * @see McpElicitation
 * @see SyncMcpElicitationMethodCallback
 * @see ElicitRequest
 * @see ElicitResult
 */
public class SyncMcpElicitationProvider {

	private static final Log logger = LogFactory.getLog(SyncMcpElicitationProvider.class);

	private final List<Object> elicitationObjects;

	private @Nullable List<HandlerMethod> handlerMethods;

	/**
	 * Create a new SyncMcpElicitationProvider.
	 * @param elicitationObjects the objects containing methods annotated with
	 * {@link McpElicitation}
	 */
	public SyncMcpElicitationProvider(List<Object> elicitationObjects) {
		Assert.notNull(elicitationObjects, "elicitationObjects cannot be null");
		this.elicitationObjects = elicitationObjects;
	}

	/**
	 * Get the elicitation specifications.
	 * @return the elicitation specifications
	 * @throws IllegalStateException if no elicitation methods are found or if multiple
	 * elicitation methods are found
	 */
	public List<SyncElicitationSpecification> getElicitationSpecifications() {
		List<SyncElicitationSpecification> elicitationHandlers = this.handlerMethods()
			.stream()
			.filter(handlerMethod -> ModernClientHandlerSupport.handlesLegacyElicitation(handlerMethod.method()))
			.map(handlerMethod -> {
				Function<ElicitRequest, ElicitResult> methodCallback = handlerMethod.callback();
				return new SyncElicitationSpecification(handlerMethod.clients(), methodCallback);
			})
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
	 * ("modern") MCP revision. They are created from the same {@link McpElicitation}
	 * methods as {@link #getElicitationSpecifications()}, and with the same callbacks: a
	 * method serves the connections of both protocols, whichever schema types it takes. A
	 * method that cannot answer a modern request, because it takes the legacy URL
	 * request, is left out.
	 * @return the modern elicitation specifications
	 * @since 2.1.0
	 */
	public List<SyncModernElicitationSpecification> getModernElicitationSpecifications() {
		return this.handlerMethods().stream().filter(handlerMethod -> {
			Method method = handlerMethod.method();
			return ModernClientHandlerSupport.handlesModernFormElicitation(method)
					|| ModernClientHandlerSupport.handlesModernUrlElicitation(method);
		})
			.map(handlerMethod -> new SyncModernElicitationSpecification(handlerMethod.clients(),
					ModernClientHandlerSupport.handlesModernFormElicitation(handlerMethod.method()),
					ModernClientHandlerSupport.handlesModernUrlElicitation(handlerMethod.method()),
					handlerMethod.callback()::applyModern))
			.toList();
	}

	/**
	 * The synchronous {@link McpElicitation} methods with their callbacks. The objects
	 * are scanned once, on first use, so that each method gets one callback, which the
	 * legacy and the modern specifications share.
	 */
	private List<HandlerMethod> handlerMethods() {
		List<HandlerMethod> handlerMethods = this.handlerMethods;
		if (handlerMethods == null) {
			handlerMethods = this.elicitationObjects.stream()
				.flatMap(elicitationObject -> Stream.of(doGetClassMethods(elicitationObject))
					.filter(method -> method.isAnnotationPresent(McpElicitation.class))
					.filter(McpPredicates.filterReactiveReturnTypeMethod())
					.filter(method -> ModernClientHandlerSupport.elicitRequestParameterType(method) != null)
					.sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
					.map(mcpElicitationMethod -> {
						var elicitationAnnotation = mcpElicitationMethod.getAnnotation(McpElicitation.class);

						SyncMcpElicitationMethodCallback methodCallback = SyncMcpElicitationMethodCallback.builder()
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
	private record HandlerMethod(Method method, String[] clients, SyncMcpElicitationMethodCallback callback) {
	}

}
