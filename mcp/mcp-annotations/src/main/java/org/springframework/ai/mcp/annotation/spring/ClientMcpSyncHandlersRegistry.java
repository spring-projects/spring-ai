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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import io.modelcontextprotocol.modern.McpSchema.ElicitRequest;
import io.modelcontextprotocol.modern.McpSchema.ElicitResult;
import io.modelcontextprotocol.modern.McpSchema.ElicitUrlRequest;
import io.modelcontextprotocol.modern.McpSchema.ProgressParams;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.McpLogging;
import org.springframework.ai.mcp.annotation.McpProgress;
import org.springframework.ai.mcp.annotation.McpPromptListChanged;
import org.springframework.ai.mcp.annotation.McpResourceListChanged;
import org.springframework.ai.mcp.annotation.McpSampling;
import org.springframework.ai.mcp.annotation.McpToolListChanged;
import org.springframework.ai.mcp.annotation.method.elicitation.SyncModernElicitationSpecification;
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
public class ClientMcpSyncHandlersRegistry extends AbstractClientMcpHandlerRegistry
		implements SmartInitializingSingleton {

	private static final Log logger = LogFactory.getLog(ClientMcpSyncHandlersRegistry.class);

	private final Map<String, Function<McpSchema.CreateMessageRequest, McpSchema.CreateMessageResult>> samplingHandlers = new HashMap<>();

	private final Map<String, Function<McpSchema.ElicitRequest, McpSchema.ElicitResult>> elicitationHandlers = new HashMap<>();

	private final Map<String, SyncModernElicitationSpecification> modernFormElicitationHandlers = new HashMap<>();

	private final Map<String, SyncModernElicitationSpecification> modernUrlElicitationHandlers = new HashMap<>();

	private final Map<String, List<BiConsumer<ProgressParams, @Nullable ToolContext>>> modernProgressHandlers = new HashMap<>();

	private final Map<String, List<Consumer<McpSchema.LoggingMessageNotification>>> loggingHandlers = new HashMap<>();

	private final Map<String, List<Consumer<McpSchema.ProgressNotification>>> progressHandlers = new HashMap<>();

	private final Map<String, List<Consumer<List<McpSchema.Tool>>>> toolListChangedHandlers = new HashMap<>();

	private final Map<String, List<Consumer<List<McpSchema.Prompt>>>> promptListChangedHandlers = new HashMap<>();

	private final Map<String, List<Consumer<List<McpSchema.Resource>>>> resourceListChangedHandlers = new HashMap<>();

	/**
	 * A client may have one {@code @McpElicitation} method for form requests and another
	 * one for URL requests. The URL method serves the connections that speak the
	 * 2026-07-28 ("modern") MCP revision; a legacy client is only asked form requests.
	 */
	@Override
	protected boolean allowsFormAndUrlElicitationHandlers() {
		return true;
	}

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
	public McpSchema.CreateMessageResult handleSampling(String name, McpSchema.CreateMessageRequest samplingRequest) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling sampling request for client " + name);
		}

		var handler = this.samplingHandlers.get(name);
		if (handler != null) {
			return handler.apply(samplingRequest);
		}
		throw new McpError(new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.METHOD_NOT_FOUND,
				"Sampling not supported", Map.of("reason", "Client does not have sampling capability")));
	}

	/**
	 * Invoke the elicitation handler for a given MCP client.
	 *
	 * @see McpElicitation
	 */
	public McpSchema.ElicitResult handleElicitation(String name, McpSchema.ElicitRequest elicitationRequest) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling elicitation request for client " + name);
		}

		var handler = this.elicitationHandlers.get(name);
		if (handler != null) {
			return handler.apply(elicitationRequest);
		}
		throw new McpError(new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.METHOD_NOT_FOUND,
				"Elicitation not supported", Map.of("reason", "Client does not have elicitation capability")));
	}

	/**
	 * Whether a handler answers the form elicitation requests of the given client when it
	 * speaks the 2026-07-28 ("modern") MCP revision. The answer is available before the
	 * handlers are resolved, so it can be used while the clients are created.
	 * @param name the name of the client, which is the name of its connection
	 * @return {@code true} if a modern client of that name should declare form
	 * elicitation
	 * @since 2.1.0
	 */
	public boolean handlesModernFormElicitation(String name) {
		return this.modernFormElicitationClients.contains(name);
	}

	/**
	 * Whether a handler answers the URL elicitation requests of the given client when it
	 * speaks the modern revision. Only a handler method that takes the modern
	 * {@code ElicitUrlRequest}, or the modern {@code ElicitRequest}, does.
	 * @param name the name of the client, which is the name of its connection
	 * @return {@code true} if a modern client of that name should declare URL elicitation
	 * @since 2.1.0
	 */
	public boolean handlesModernUrlElicitation(String name) {
		return this.modernUrlElicitationClients.contains(name);
	}

	/**
	 * Whether a handler receives the progress of the given client when it speaks the
	 * modern revision: a synchronous {@code @McpProgress} method names the client. The
	 * answer is available before the handlers are resolved.
	 * @param name the name of the client, which is the name of its connection
	 * @return {@code true} if a modern client of that name should ask for progress
	 * @since 2.1.0
	 */
	public boolean handlesModernProgress(String name) {
		return this.modernProgressClients.contains(name);
	}

	/**
	 * Invoke the elicitation handler for a given client that speaks the modern revision.
	 * It is the same {@code @McpElicitation} method that answers a legacy client of that
	 * name, whichever schema types the method takes.
	 * @param name the name of the client, which is the name of its connection
	 * @param elicitationRequest the modern elicitation request
	 * @return the answer of the handler, as a modern result
	 * @throws IllegalStateException if no handler answers that kind of request for the
	 * client
	 * @since 2.1.0
	 */
	public ElicitResult handleModernElicitation(String name, ElicitRequest elicitationRequest) {
		return this.handleModernElicitation(name, elicitationRequest, null);
	}

	/**
	 * Invoke the elicitation handler for a given client that speaks the modern revision,
	 * for a tool call with the given tool context. A handler method that declares a
	 * {@code ToolContext} parameter receives it, for example to find the user to ask.
	 * @param name the name of the client, which is the name of its connection
	 * @param elicitationRequest the modern elicitation request
	 * @param toolContext the tool context of the tool call that the request belongs to,
	 * or {@code null} if there is none
	 * @return the answer of the handler, as a modern result
	 * @throws IllegalStateException if no handler answers that kind of request for the
	 * client
	 * @since 2.1.0
	 */
	public ElicitResult handleModernElicitation(String name, ElicitRequest elicitationRequest,
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
		throw new IllegalStateException("No @McpElicitation handler for the " + (urlRequest ? "URL" : "form")
				+ " elicitation requests of the client '" + name + "'");
	}

	/**
	 * Invoke all progress handlers for a given client that speaks the modern revision.
	 * They are the same {@code @McpProgress} methods that serve a legacy client of that
	 * name.
	 * @param name the name of the client, which is the name of its connection
	 * @param progress the modern progress
	 * @since 2.1.0
	 */
	public void handleModernProgress(String name, ProgressParams progress) {
		this.handleModernProgress(name, progress, null);
	}

	/**
	 * Invoke all progress handlers for a given client that speaks the modern revision,
	 * for a tool call with the given tool context. A handler method that declares a
	 * {@code ToolContext} parameter receives it.
	 * @param name the name of the client, which is the name of its connection
	 * @param progress the modern progress
	 * @param toolContext the tool context of the tool call that reports the progress, or
	 * {@code null} if there is none
	 * @since 2.1.0
	 */
	public void handleModernProgress(String name, ProgressParams progress, @Nullable ToolContext toolContext) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling modern progress for client " + name);
		}

		var consumers = this.modernProgressHandlers.get(name);
		if (consumers == null) {
			return;
		}
		for (var consumer : consumers) {
			consumer.accept(progress, toolContext);
		}
	}

	/**
	 * Invoke all elicitation handlers for a given MCP client, sequentially.
	 *
	 * @see McpLogging
	 */
	public void handleLogging(String name, McpSchema.LoggingMessageNotification loggingMessageNotification) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling logging notification for client " + name);
		}

		var consumers = this.loggingHandlers.get(name);
		if (consumers == null) {
			return;
		}
		for (var consumer : consumers) {
			consumer.accept(loggingMessageNotification);
		}
	}

	/**
	 * Invoke all progress handlers for a given MCP client, sequentially.
	 *
	 * @see McpProgress
	 */
	public void handleProgress(String name, McpSchema.ProgressNotification progressNotification) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling progress notification for client " + name);
		}

		var consumers = this.progressHandlers.get(name);
		if (consumers == null) {
			return;
		}
		for (var consumer : consumers) {
			consumer.accept(progressNotification);
		}
	}

	/**
	 * Invoke all tool list changed handlers for a given MCP client, sequentially.
	 *
	 * @see McpToolListChanged
	 */
	public void handleToolListChanged(String name, List<McpSchema.Tool> updatedTools) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling tool list changed notification for client " + name);
		}

		var consumers = this.toolListChangedHandlers.get(name);
		if (consumers == null) {
			return;
		}
		for (var consumer : consumers) {
			consumer.accept(updatedTools);
		}
	}

	/**
	 * Invoke all prompt list changed handlers for a given MCP client, sequentially.
	 *
	 * @see McpPromptListChanged
	 */
	public void handlePromptListChanged(String name, List<McpSchema.Prompt> updatedPrompts) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling prompt list changed notification for client " + name);
		}

		var consumers = this.promptListChangedHandlers.get(name);
		if (consumers == null) {
			return;
		}
		for (var consumer : consumers) {
			consumer.accept(updatedPrompts);
		}
	}

	/**
	 * Invoke all resource list changed handlers for a given MCP client, sequentially.
	 *
	 * @see McpResourceListChanged
	 */
	public void handleResourceListChanged(String name, List<McpSchema.Resource> updatedResources) {
		if (logger.isDebugEnabled()) {
			logger.debug("Handling resource list changed notification for client " + name);
		}

		var consumers = this.resourceListChangedHandlers.get(name);
		if (consumers == null) {
			return;
		}
		for (var consumer : consumers) {
			consumer.accept(updatedResources);
		}
	}

	@Override
	public void afterSingletonsInstantiated() {
		var beansByAnnotation = this.getBeansByAnnotationType();

		var samplingSpecs = SyncMcpAnnotationProviders
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
		var elicitationProvider = SyncMcpAnnotationProviders
			.elicitationProvider(new ArrayList<>(beansByAnnotation.get(McpElicitation.class)));
		var elicitationSpecs = elicitationProvider.getElicitationSpecifications();
		for (var elicitationSpec : elicitationSpecs) {
			for (var client : elicitationSpec.clients()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Registering elicitation handler for " + client);
				}
				this.elicitationHandlers.put(client, elicitationSpec.elicitationHandler());
			}
		}

		var modernElicitationSpecs = elicitationProvider.getModernElicitationSpecifications();
		for (var modernElicitationSpec : modernElicitationSpecs) {
			for (var client : modernElicitationSpec.clients()) {
				if (modernElicitationSpec.form()) {
					this.modernFormElicitationHandlers.put(client, modernElicitationSpec);
				}
				if (modernElicitationSpec.url()) {
					this.modernUrlElicitationHandlers.put(client, modernElicitationSpec);
				}
			}
		}

		var loggingSpecs = SyncMcpAnnotationProviders
			.loggingSpecifications(new ArrayList<>(beansByAnnotation.get(McpLogging.class)));
		for (var loggingSpec : loggingSpecs) {
			for (var client : loggingSpec.clients()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Registering logging handler for " + client);
				}
				this.loggingHandlers.computeIfAbsent(client, k -> new ArrayList<>()).add(loggingSpec.loggingHandler());
			}
		}

		var progressProvider = SyncMcpAnnotationProviders
			.progressProvider(new ArrayList<>(beansByAnnotation.get(McpProgress.class)));
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

		var modernProgressSpecs = progressProvider.getModernProgressSpecifications();
		for (var modernProgressSpec : modernProgressSpecs) {
			for (var client : modernProgressSpec.clients()) {
				this.modernProgressHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(modernProgressSpec.progressHandler());
			}
		}

		var toolsListChangedSpecs = SyncMcpAnnotationProviders
			.toolListChangedSpecifications(new ArrayList<>(beansByAnnotation.get(McpToolListChanged.class)));
		for (var toolsListChangedSpec : toolsListChangedSpecs) {
			for (var client : toolsListChangedSpec.clients()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Registering tool list changed handler for " + client);
				}
				this.toolListChangedHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(toolsListChangedSpec.toolListChangeHandler());
			}
		}

		var promptListChangedSpecs = SyncMcpAnnotationProviders
			.promptListChangedSpecifications(new ArrayList<>(beansByAnnotation.get(McpPromptListChanged.class)));
		for (var promptListChangedSpec : promptListChangedSpecs) {
			for (var client : promptListChangedSpec.clients()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Registering prompt list changed handler for " + client);
				}
				this.promptListChangedHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(promptListChangedSpec.promptListChangeHandler());
			}
		}

		var resourceListChangedSpecs = SyncMcpAnnotationProviders
			.resourceListChangedSpecifications(new ArrayList<>(beansByAnnotation.get(McpResourceListChanged.class)));
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
