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

package org.springframework.ai.mcp.annotation.spring;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.modelcontextprotocol.modern.McpSchema.ElicitRequest;
import io.modelcontextprotocol.modern.McpSchema.ElicitResult;
import io.modelcontextprotocol.modern.McpSchema.ElicitUrlRequest;
import io.modelcontextprotocol.modern.McpSchema.ProgressParams;
import io.modelcontextprotocol.modern.McpSchema.Prompt;
import io.modelcontextprotocol.modern.McpSchema.Resource;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.McpLogging;
import org.springframework.ai.mcp.annotation.McpProgress;
import org.springframework.ai.mcp.annotation.McpPromptListChanged;
import org.springframework.ai.mcp.annotation.McpResourceListChanged;
import org.springframework.ai.mcp.annotation.McpSampling;
import org.springframework.ai.mcp.annotation.McpToolListChanged;
import org.springframework.ai.mcp.annotation.common.ModernClientHandlerSupport;
import org.springframework.ai.mcp.annotation.method.elicitation.AsyncModernElicitationSpecification;
import org.springframework.ai.mcp.annotation.method.progress.AsyncModernProgressSpecification;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Registry of methods annotated with MCP Client annotations (sampling, logging, etc.).
 * All beans in the application context are scanned to find these methods automatically.
 * They are then exposed by the registry by client name.
 * <p>
 * The scanning happens in two phases:
 * <p>
 * First, once bean definitions are available, all bean types are scanned for the presence
 * of MCP annotations. In particular, this is used to prepare the result
 * {@link #getCapabilities(String)}, which is then used by MCP client auto-configurations
 * to configure the client capabilities without needing to instantiate the beans.
 * <p>
 * Second, after all singleton beans have been instantiated, all annotated beans are
 * scanned again, MCP handlers are created to match the annotations, and stored by client.
 *
 * @see McpSampling
 * @see McpElicitation
 * @see McpLogging
 * @see McpProgress
 * @see McpToolListChanged
 * @see McpPromptListChanged
 * @see McpResourceListChanged
 * @author Daniel Garnier-Moiroux
 * @since 1.1.0
 */
public class ClientMcpAsyncHandlersRegistry extends AbstractClientMcpHandlerRegistry
		implements SmartInitializingSingleton {

	private static final Log logger = LogFactory.getLog(ClientMcpAsyncHandlersRegistry.class);

	private final Map<String, Function<McpSchema.CreateMessageRequest, Mono<McpSchema.CreateMessageResult>>> samplingHandlers = new HashMap<>();

	private final Map<String, Function<McpSchema.ElicitRequest, Mono<McpSchema.ElicitResult>>> elicitationHandlers = new HashMap<>();

	private final Map<String, AsyncModernElicitationSpecification> modernFormElicitationHandlers = new HashMap<>();

	private final Map<String, AsyncModernElicitationSpecification> modernUrlElicitationHandlers = new HashMap<>();

	private final Map<String, List<AsyncModernProgressSpecification>> modernProgressHandlers = new HashMap<>();

	private final Map<String, List<Function<McpSchema.LoggingMessageNotification, Mono<Void>>>> loggingHandlers = new HashMap<>();

	private final Map<String, List<Function<McpSchema.ProgressNotification, Mono<Void>>>> progressHandlers = new HashMap<>();

	private final Map<String, List<Function<List<Tool>, Mono<Void>>>> modernToolListChangedHandlers = new HashMap<>();

	private final Map<String, List<Function<List<Prompt>, Mono<Void>>>> modernPromptListChangedHandlers = new HashMap<>();

	private final Map<String, List<Function<List<Resource>, Mono<Void>>>> modernResourceListChangedHandlers = new HashMap<>();

	private final Map<String, List<Function<List<McpSchema.Tool>, Mono<Void>>>> toolListChangedHandlers = new HashMap<>();

	private final Map<String, List<Function<List<McpSchema.Prompt>, Mono<Void>>>> promptListChangedHandlers = new HashMap<>();

	private final Map<String, List<Function<List<McpSchema.Resource>, Mono<Void>>>> resourceListChangedHandlers = new HashMap<>();

	/**
	 * Obtain the MCP capabilities declared for a given MCP client. Capabilities are
	 * registered with the {@link McpSampling} and {@link McpElicitation} annotations.
	 */
	public McpSchema.ClientCapabilities getCapabilities(String clientName) {
		return this.capabilitiesPerClient.getOrDefault(clientName, EMPTY_CAPABILITIES);
	}

	/**
	 * Invoke the sampling handler for a given MCP client.
	 *
	 * @see McpSampling
	 */
	public Mono<McpSchema.CreateMessageResult> handleSampling(String name,
			McpSchema.CreateMessageRequest samplingRequest) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling sampling request for client " + name);
		}
		var handler = this.samplingHandlers.get(name);
		if (handler != null) {
			return handler.apply(samplingRequest);
		}
		return Mono.error(new McpError(new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.METHOD_NOT_FOUND,
				"Sampling not supported", Map.of("reason", "Client does not have sampling capability"))));
	}

	/**
	 * Invoke the elicitation handler for a given MCP client.
	 *
	 * @see McpElicitation
	 */
	public Mono<McpSchema.ElicitResult> handleElicitation(String name, McpSchema.ElicitRequest elicitationRequest) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling elicitation request for client " + name);
		}
		var handler = this.elicitationHandlers.get(name);
		if (handler != null) {
			return handler.apply(elicitationRequest);
		}
		return Mono.error(new McpError(new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.METHOD_NOT_FOUND,
				"Elicitation not supported", Map.of("reason", "Client does not have elicitation capability"))));
	}

	/**
	 * Invoke the elicitation handler for a given client that speaks the modern revision.
	 * It is the same {@code @McpElicitation} method that answers a legacy client of that
	 * name.
	 * @param name the name of the client, which is the name of its connection
	 * @param elicitationRequest the modern elicitation request
	 * @return a Mono of the modern result, which fails with an
	 * {@code IllegalStateException} if no handler answers such a request for the client
	 * @since 2.1.0
	 */
	public Mono<ElicitResult> handleModernElicitation(String name, ElicitRequest elicitationRequest) {
		return this.handleModernElicitation(name, elicitationRequest, null);
	}

	/**
	 * Invoke the elicitation handler for a given client that speaks the modern revision,
	 * for a tool call with the given tool context. The handler method receives the tool
	 * context if it declares a {@code ToolContext} parameter.
	 * @param name the name of the client, which is the name of its connection
	 * @param elicitationRequest the modern elicitation request
	 * @param toolContext the tool context of the tool call that the request belongs to,
	 * or {@code null} if there is none
	 * @return a Mono of the modern result, which fails with an
	 * {@code IllegalStateException} if no handler answers such a request for the client
	 * @since 2.1.0
	 */
	public Mono<ElicitResult> handleModernElicitation(String name, ElicitRequest elicitationRequest,
			@Nullable ToolContext toolContext) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling modern elicitation request for client " + name);
		}
		boolean urlRequest = elicitationRequest instanceof ElicitUrlRequest;
		var specification = (urlRequest ? this.modernUrlElicitationHandlers : this.modernFormElicitationHandlers)
			.get(name);
		if (specification != null) {
			return specification.elicitationHandler().apply(elicitationRequest, toolContext);
		}
		return Mono.error(new IllegalStateException("No @McpElicitation handler for the "
				+ (urlRequest ? "URL" : "form") + " elicitation requests of the client '" + name + "'"));
	}

	/**
	 * Invoke all progress handlers for a given client that speaks the modern revision, in
	 * sequence.
	 * @param name the name of the client, which is the name of its connection
	 * @param progress the modern progress
	 * @return a Mono that completes when the handlers have processed the progress
	 * @since 2.1.0
	 */
	public Mono<Void> handleModernProgress(String name, ProgressParams progress) {
		return this.handleModernProgress(name, progress, null);
	}

	/**
	 * Invoke all progress handlers for a given client that speaks the modern revision, in
	 * sequence, for a tool call with the given tool context. A handler method receives
	 * the tool context if it declares a {@code ToolContext} parameter.
	 * @param name the name of the client, which is the name of its connection
	 * @param progress the modern progress
	 * @param toolContext the tool context of the tool call that reports the progress, or
	 * {@code null} if there is none
	 * @return a Mono that completes when the handlers have processed the progress
	 * @since 2.1.0
	 */
	public Mono<Void> handleModernProgress(String name, ProgressParams progress, @Nullable ToolContext toolContext) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling modern progress for client " + name);
		}
		var handlers = this.modernProgressHandlers.get(name);
		if (handlers == null) {
			return Mono.empty();
		}
		return Flux.fromIterable(handlers)
			.concatMap(handler -> handler.progressHandler().apply(progress, toolContext))
			.then();
	}

	@Override
	protected boolean isModernFormElicitationMethod(Method method) {
		return ModernClientHandlerSupport.handlesModernFormElicitationAsync(method);
	}

	@Override
	protected boolean isModernUrlElicitationMethod(Method method) {
		return ModernClientHandlerSupport.handlesModernUrlElicitationAsync(method);
	}

	/**
	 * The provider of the reactive progress handlers only takes methods that return a
	 * {@code Mono<Void>}.
	 */
	@Override
	protected boolean isModernProgressMethod(Method method) {
		return ModernClientHandlerSupport.returnsMonoOfVoid(method);
	}

	/**
	 * The providers of the reactive list-changed handlers only take methods that return a
	 * {@code Mono}.
	 */
	@Override
	protected boolean isModernListChangedMethod(Method method) {
		return Mono.class.isAssignableFrom(method.getReturnType());
	}

	/**
	 * Invoke all elicitation handlers for a given MCP client, sequentially.
	 *
	 * @see McpLogging
	 */
	public Mono<Void> handleLogging(String name, McpSchema.LoggingMessageNotification loggingMessageNotification) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling logging notification for client " + name);
		}
		var consumers = this.loggingHandlers.get(name);
		if (consumers == null) {
			return Mono.empty();
		}
		return Flux.fromIterable(consumers).flatMap(c -> c.apply(loggingMessageNotification)).then();
	}

	/**
	 * Invoke all progress handlers for a given MCP client, sequentially.
	 *
	 * @see McpProgress
	 */
	public Mono<Void> handleProgress(String name, McpSchema.ProgressNotification progressNotification) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling progress notification for client " + name);
		}
		var consumers = this.progressHandlers.get(name);
		if (consumers == null) {
			return Mono.empty();
		}
		return Flux.fromIterable(consumers).flatMap(c -> c.apply(progressNotification)).then();
	}

	/**
	 * Invoke all tool list changed handlers for a given client that speaks the 2026-07-28
	 * ("modern") MCP revision, sequentially. They are the same
	 * {@code @McpToolListChanged} methods that serve a legacy client of that name,
	 * whichever schema type their list has. A method that fails does not keep the others
	 * from hearing the change.
	 * @param name the name of the client, which is the name of its connection
	 * @param updatedTools the tools of the server, in the modern schema type
	 * @return completes when all the methods are done
	 * @since 2.1.0
	 */
	public Mono<Void> handleModernToolListChanged(String name, List<Tool> updatedTools) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling modern tool list changed notification for client " + name);
		}
		return this.callModernListChangedHandlers(name, "tool", this.modernToolListChangedHandlers.get(name),
				updatedTools);
	}

	/**
	 * Invoke all prompt list changed handlers for a given client that speaks the
	 * 2026-07-28 ("modern") MCP revision, sequentially. They are the same
	 * {@code @McpPromptListChanged} methods that serve a legacy client of that name,
	 * whichever schema type their list has. A method that fails does not keep the others
	 * from hearing the change.
	 * @param name the name of the client, which is the name of its connection
	 * @param updatedPrompts the prompts of the server, in the modern schema type
	 * @return completes when all the methods are done
	 * @since 2.1.0
	 */
	public Mono<Void> handleModernPromptListChanged(String name, List<Prompt> updatedPrompts) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling modern prompt list changed notification for client " + name);
		}
		return this.callModernListChangedHandlers(name, "prompt", this.modernPromptListChangedHandlers.get(name),
				updatedPrompts);
	}

	/**
	 * Invoke all resource list changed handlers for a given client that speaks the
	 * 2026-07-28 ("modern") MCP revision, sequentially. They are the same
	 * {@code @McpResourceListChanged} methods that serve a legacy client of that name,
	 * whichever schema type their list has. A method that fails does not keep the others
	 * from hearing the change.
	 * @param name the name of the client, which is the name of its connection
	 * @param updatedResources the resources of the server, in the modern schema type
	 * @return completes when all the methods are done
	 * @since 2.1.0
	 */
	public Mono<Void> handleModernResourceListChanged(String name, List<Resource> updatedResources) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling modern resource list changed notification for client " + name);
		}
		return this.callModernListChangedHandlers(name, "resource", this.modernResourceListChangedHandlers.get(name),
				updatedResources);
	}

	private static <T> Mono<Void> callModernListChangedHandlers(String name, String kind,
			@Nullable List<Function<List<T>, Mono<Void>>> handlers, List<T> updated) {
		if (handlers == null) {
			return Mono.empty();
		}
		return Flux.fromIterable(handlers)
			.concatMap(handler -> Mono.defer(() -> handler.apply(updated)).onErrorResume(ex -> {
				logger.warn("A " + kind + " list changed handler failed for client " + name, ex);
				return Mono.empty();
			}))
			.then();
	}

	/**
	 * Invoke all tool list changed handlers for a given MCP client, sequentially.
	 *
	 * @see McpToolListChanged
	 */
	public Mono<Void> handleToolListChanged(String name, List<McpSchema.Tool> updatedTools) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling tool list changed notification for client " + name);
		}
		var consumers = this.toolListChangedHandlers.get(name);
		if (consumers == null) {
			return Mono.empty();
		}
		return Flux.fromIterable(consumers).flatMap(c -> c.apply(updatedTools)).then();
	}

	/**
	 * Invoke all prompt list changed handlers for a given MCP client, sequentially.
	 *
	 * @see McpPromptListChanged
	 */
	public Mono<Void> handlePromptListChanged(String name, List<McpSchema.Prompt> updatedPrompts) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling prompt list changed notification for client " + name);
		}
		var consumers = this.promptListChangedHandlers.get(name);
		if (consumers == null) {
			return Mono.empty();
		}
		return Flux.fromIterable(consumers).flatMap(c -> c.apply(updatedPrompts)).then();
	}

	/**
	 * Invoke all resource list changed handlers for a given MCP client, sequentially.
	 *
	 * @see McpResourceListChanged
	 */
	public Mono<Void> handleResourceListChanged(String name, List<McpSchema.Resource> updatedResources) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling resource list changed notification for client " + name);
		}
		var consumers = this.resourceListChangedHandlers.get(name);
		if (consumers == null) {
			return Mono.empty();
		}
		return Flux.fromIterable(consumers).flatMap(c -> c.apply(updatedResources)).then();
	}

	@Override
	public void afterSingletonsInstantiated() {
		var beansByAnnotation = this.getBeansByAnnotationType();

		var samplingSpecs = AsyncMcpAnnotationProviders
			.samplingSpecifications(new ArrayList<>(beansByAnnotation.get(McpSampling.class)));
		for (var samplingSpec : samplingSpecs) {
			for (var client : samplingSpec.clients()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Registering sampling handler for " + client);
				}
				this.samplingHandlers.put(client, samplingSpec.samplingHandler());
			}
		}

		// One provider, so that the legacy and the modern handlers share their callbacks.
		var elicitationProvider = AsyncMcpAnnotationProviders
			.elicitationProvider(new ArrayList<>(beansByAnnotation.get(McpElicitation.class)));
		for (var modernElicitationSpec : elicitationProvider.getModernElicitationSpecifications()) {
			for (var client : modernElicitationSpec.clients()) {
				if (modernElicitationSpec.form()) {
					this.modernFormElicitationHandlers.put(client, modernElicitationSpec);
				}
				if (modernElicitationSpec.url()) {
					this.modernUrlElicitationHandlers.put(client, modernElicitationSpec);
				}
			}
		}
		var elicitationSpecs = elicitationProvider.getElicitationSpecifications();
		for (var elicitationSpec : elicitationSpecs) {
			for (var client : elicitationSpec.clients()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Registering elicitation handler for " + client);
				}
				this.elicitationHandlers.put(client, elicitationSpec.elicitationHandler());
			}
		}

		var loggingSpecs = AsyncMcpAnnotationProviders
			.loggingSpecifications(new ArrayList<>(beansByAnnotation.get(McpLogging.class)));
		for (var loggingSpec : loggingSpecs) {
			for (var client : loggingSpec.clients()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Registering logging handler for " + client);
				}
				this.loggingHandlers.computeIfAbsent(client, k -> new ArrayList<>()).add(loggingSpec.loggingHandler());
			}
		}

		var progressProvider = AsyncMcpAnnotationProviders
			.progressProvider(new ArrayList<>(beansByAnnotation.get(McpProgress.class)));
		for (var modernProgressSpec : progressProvider.getModernProgressSpecifications()) {
			for (var client : modernProgressSpec.clients()) {
				this.modernProgressHandlers.computeIfAbsent(client, k -> new ArrayList<>()).add(modernProgressSpec);
			}
		}
		var progressSpecs = progressProvider.getProgressSpecifications();
		for (var progressSpec : progressSpecs) {
			for (var client : progressSpec.clients()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Registering progress handler for " + client);
				}
				this.progressHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(progressSpec.progressHandler());
			}
		}

		var toolListChangedProvider = AsyncMcpAnnotationProviders
			.toolListChangedProvider(new ArrayList<>(beansByAnnotation.get(McpToolListChanged.class)));
		for (var modernSpec : toolListChangedProvider.getModernToolListChangedSpecifications()) {
			for (var client : modernSpec.clients()) {
				this.modernToolListChangedHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(modernSpec.toolListChangeHandler());
			}
		}
		var toolsListChangedSpecs = toolListChangedProvider.getToolListChangedSpecifications();
		for (var toolsListChangedSpec : toolsListChangedSpecs) {
			for (var client : toolsListChangedSpec.clients()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Registering tool list changed handler for " + client);
				}
				this.toolListChangedHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(toolsListChangedSpec.toolListChangeHandler());
			}
		}

		var promptListChangedProvider = AsyncMcpAnnotationProviders
			.promptListChangedProvider(new ArrayList<>(beansByAnnotation.get(McpPromptListChanged.class)));
		for (var modernSpec : promptListChangedProvider.getModernPromptListChangedSpecifications()) {
			for (var client : modernSpec.clients()) {
				this.modernPromptListChangedHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(modernSpec.promptListChangeHandler());
			}
		}
		var promptListChangedSpecs = promptListChangedProvider.getPromptListChangedSpecifications();
		for (var promptListChangedSpec : promptListChangedSpecs) {
			for (var client : promptListChangedSpec.clients()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Registering prompt list changed handler for " + client);
				}
				this.promptListChangedHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(promptListChangedSpec.promptListChangeHandler());
			}
		}

		var resourceListChangedProvider = AsyncMcpAnnotationProviders
			.resourceListChangedProvider(new ArrayList<>(beansByAnnotation.get(McpResourceListChanged.class)));
		for (var modernSpec : resourceListChangedProvider.getModernResourceListChangedSpecifications()) {
			for (var client : modernSpec.clients()) {
				this.modernResourceListChangedHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(modernSpec.resourceListChangeHandler());
			}
		}
		var resourceListChangedSpecs = resourceListChangedProvider.getResourceListChangedSpecifications();
		for (var resourceListChangedSpec : resourceListChangedSpecs) {
			for (var client : resourceListChangedSpec.clients()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Registering resource list changed handler for " + client);
				}
				this.resourceListChangedHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(resourceListChangedSpec.resourceListChangeHandler());
			}
		}

	}

}
