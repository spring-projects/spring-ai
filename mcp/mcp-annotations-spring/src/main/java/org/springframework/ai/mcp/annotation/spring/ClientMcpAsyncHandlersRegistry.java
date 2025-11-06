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

package org.springframework.ai.mcp.annotation.spring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpElicitation;
import org.springaicommunity.mcp.annotation.McpLogging;
import org.springaicommunity.mcp.annotation.McpProgress;
import org.springaicommunity.mcp.annotation.McpPromptListChanged;
import org.springaicommunity.mcp.annotation.McpResourceListChanged;
import org.springaicommunity.mcp.annotation.McpSampling;
import org.springaicommunity.mcp.annotation.McpToolListChanged;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

	private static final Logger logger = LoggerFactory.getLogger(ClientMcpAsyncHandlersRegistry.class);

	private final Map<String, Function<McpSchema.CreateMessageRequest, Mono<McpSchema.CreateMessageResult>>> samplingHandlers = new HashMap<>();

	private final Map<String, Function<McpSchema.ElicitRequest, Mono<McpSchema.ElicitResult>>> elicitationHandlers = new HashMap<>();

	private final Map<String, List<Function<McpSchema.LoggingMessageNotification, Mono<Void>>>> loggingHandlers = new HashMap<>();

	private final Map<String, List<Function<McpSchema.ProgressNotification, Mono<Void>>>> progressHandlers = new HashMap<>();

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
		logger.debug("Handling sampling request for client {}", name);
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
		logger.debug("Handling elicitation request for client {}", name);
		var handler = this.elicitationHandlers.get(name);
		if (handler != null) {
			return handler.apply(elicitationRequest);
		}
		return Mono.error(new McpError(new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.METHOD_NOT_FOUND,
				"Elicitation not supported", Map.of("reason", "Client does not have elicitation capability"))));
	}

	/**
	 * Invoke all elicitation handlers for a given MCP client, sequentially.
	 *
	 * @see McpLogging
	 */
	public Mono<Void> handleLogging(String name, McpSchema.LoggingMessageNotification loggingMessageNotification) {
		logger.debug("Handling logging notification for client {}", name);
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
		logger.debug("Handling progress notification for client {}", name);
		var consumers = this.progressHandlers.get(name);
		if (consumers == null) {
			return Mono.empty();
		}
		return Flux.fromIterable(consumers).flatMap(c -> c.apply(progressNotification)).then();
	}

	/**
	 * Invoke all tool list changed handlers for a given MCP client, sequentially.
	 *
	 * @see McpToolListChanged
	 */
	public Mono<Void> handleToolListChanged(String name, List<McpSchema.Tool> updatedTools) {
		logger.debug("Handling tool list changed notification for client {}", name);
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
		logger.debug("Handling prompt list changed notification for client {}", name);
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
		logger.debug("Handling resource list changed notification for client {}", name);
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
				logger.debug("Registering sampling handler for {}", client);
				this.samplingHandlers.put(client, samplingSpec.samplingHandler());
			}
		}

		var elicitationSpecs = AsyncMcpAnnotationProviders
			.elicitationSpecifications(new ArrayList<>(beansByAnnotation.get(McpElicitation.class)));
		for (var elicitationSpec : elicitationSpecs) {
			for (var client : elicitationSpec.clients()) {
				logger.debug("Registering elicitation handler for {}", client);
				this.elicitationHandlers.put(client, elicitationSpec.elicitationHandler());
			}
		}

		var loggingSpecs = AsyncMcpAnnotationProviders
			.loggingSpecifications(new ArrayList<>(beansByAnnotation.get(McpLogging.class)));
		for (var loggingSpec : loggingSpecs) {
			for (var client : loggingSpec.clients()) {
				logger.debug("Registering logging handler for {}", client);
				this.loggingHandlers.computeIfAbsent(client, k -> new ArrayList<>()).add(loggingSpec.loggingHandler());
			}
		}

		var progressSpecs = AsyncMcpAnnotationProviders
			.progressSpecifications(new ArrayList<>(beansByAnnotation.get(McpProgress.class)));
		for (var progressSpec : progressSpecs) {
			for (var client : progressSpec.clients()) {
				logger.debug("Registering progress handler for {}", client);
				this.progressHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(progressSpec.progressHandler());
			}
		}

		var toolsListChangedSpecs = AsyncMcpAnnotationProviders
			.toolListChangedSpecifications(new ArrayList<>(beansByAnnotation.get(McpToolListChanged.class)));
		for (var toolsListChangedSpec : toolsListChangedSpecs) {
			for (var client : toolsListChangedSpec.clients()) {
				logger.debug("Registering tool list changed handler for {}", client);
				this.toolListChangedHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(toolsListChangedSpec.toolListChangeHandler());
			}
		}

		var promptListChangedSpecs = AsyncMcpAnnotationProviders
			.promptListChangedSpecifications(new ArrayList<>(beansByAnnotation.get(McpPromptListChanged.class)));
		for (var promptListChangedSpec : promptListChangedSpecs) {
			for (var client : promptListChangedSpec.clients()) {
				logger.debug("Registering prompt list changed handler for {}", client);
				this.promptListChangedHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(promptListChangedSpec.promptListChangeHandler());
			}
		}

		var resourceListChangedSpecs = AsyncMcpAnnotationProviders
			.resourceListChangedSpecifications(new ArrayList<>(beansByAnnotation.get(McpResourceListChanged.class)));
		for (var resourceListChangedSpec : resourceListChangedSpecs) {
			for (var client : resourceListChangedSpec.clients()) {
				logger.debug("Registering resource list changed handler for {}", client);
				this.resourceListChangedHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(resourceListChangedSpec.resourceListChangeHandler());
			}
		}

	}

}
