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
import java.util.function.Consumer;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpElicitation;
import org.springaicommunity.mcp.annotation.McpLogging;
import org.springaicommunity.mcp.annotation.McpProgress;
import org.springaicommunity.mcp.annotation.McpPromptListChanged;
import org.springaicommunity.mcp.annotation.McpResourceListChanged;
import org.springaicommunity.mcp.annotation.McpSampling;
import org.springaicommunity.mcp.annotation.McpToolListChanged;

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

	private final Map<String, Function<McpSchema.CreateMessageRequest, McpSchema.CreateMessageResult>> samplingHandlers = new HashMap<>();

	private final Map<String, Function<McpSchema.ElicitRequest, McpSchema.ElicitResult>> elicitationHandlers = new HashMap<>();

	private final Map<String, List<Consumer<McpSchema.LoggingMessageNotification>>> loggingHandlers = new HashMap<>();

	private final Map<String, List<Consumer<McpSchema.ProgressNotification>>> progressHandlers = new HashMap<>();

	private final Map<String, List<Consumer<List<McpSchema.Tool>>>> toolListChangedHandlers = new HashMap<>();

	private final Map<String, List<Consumer<List<McpSchema.Prompt>>>> promptListChangedHandlers = new HashMap<>();

	private final Map<String, List<Consumer<List<McpSchema.Resource>>>> resourceListChangedHandlers = new HashMap<>();

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
		var handler = this.samplingHandlers.get(name);
		if (handler != null) {
			return handler.apply(samplingRequest);
		}
		// TODO: handle null
		return null;
	}

	/**
	 * Invoke the elicitation handler for a given MCP client.
	 *
	 * @see McpElicitation
	 */
	public McpSchema.ElicitResult handleElicitation(String name, McpSchema.ElicitRequest elicitationRequest) {
		var handler = this.elicitationHandlers.get(name);
		if (handler != null) {
			return handler.apply(elicitationRequest);
		}
		// TODO: handle null
		return null;
	}

	/**
	 * Invoke all elicitation handlers for a given MCP client, sequentially.
	 *
	 * @see McpLogging
	 */
	public void handleLogging(String name, McpSchema.LoggingMessageNotification loggingMessageNotification) {
		var consumers = this.loggingHandlers.get(name);
		if (consumers == null) {
			// TODO handle
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
		var consumers = this.progressHandlers.get(name);
		if (consumers == null) {
			// TODO handle
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
		var consumers = this.toolListChangedHandlers.get(name);
		if (consumers == null) {
			// TODO handle
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
		var consumers = this.promptListChangedHandlers.get(name);
		if (consumers == null) {
			// TODO handle
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
		var consumers = this.resourceListChangedHandlers.get(name);
		if (consumers == null) {
			// TODO handle
			return;
		}
		for (var consumer : consumers) {
			consumer.accept(updatedResources);
		}
	}

	@Override
	public void afterSingletonsInstantiated() {
		var beansByAnnotation = getBeansByAnnotationType();

		var samplingSpecs = SyncMcpAnnotationProviders
			.samplingSpecifications(new ArrayList<>(beansByAnnotation.get(McpSampling.class)));
		for (var samplingSpec : samplingSpecs) {
			for (var client : samplingSpec.clients()) {
				this.samplingHandlers.put(client, samplingSpec.samplingHandler());
			}
		}

		var elicitationSpecs = SyncMcpAnnotationProviders
			.elicitationSpecifications(new ArrayList<>(beansByAnnotation.get(McpElicitation.class)));
		for (var elicitationSpec : elicitationSpecs) {
			for (var client : elicitationSpec.clients()) {
				this.elicitationHandlers.put(client, elicitationSpec.elicitationHandler());
			}
		}

		var loggingSpecs = SyncMcpAnnotationProviders
			.loggingSpecifications(new ArrayList<>(beansByAnnotation.get(McpLogging.class)));
		for (var loggingSpec : loggingSpecs) {
			for (var client : loggingSpec.clients()) {
				this.loggingHandlers.computeIfAbsent(client, k -> new ArrayList<>()).add(loggingSpec.loggingHandler());
			}
		}

		var progressSpecs = SyncMcpAnnotationProviders
			.progressSpecifications(new ArrayList<>(beansByAnnotation.get(McpProgress.class)));
		for (var progressSpec : progressSpecs) {
			for (var client : progressSpec.clients()) {
				this.progressHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(progressSpec.progressHandler());
			}
		}

		var toolsListChangedSpecs = SyncMcpAnnotationProviders
			.toolListChangedSpecifications(new ArrayList<>(beansByAnnotation.get(McpToolListChanged.class)));
		for (var toolsListChangedSpec : toolsListChangedSpecs) {
			for (var client : toolsListChangedSpec.clients()) {
				this.toolListChangedHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(toolsListChangedSpec.toolListChangeHandler());
			}
		}

		var promptListChangedSpecs = SyncMcpAnnotationProviders
			.promptListChangedSpecifications(new ArrayList<>(beansByAnnotation.get(McpPromptListChanged.class)));
		for (var promptListChangedSpec : promptListChangedSpecs) {
			for (var client : promptListChangedSpec.clients()) {
				this.promptListChangedHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(promptListChangedSpec.promptListChangeHandler());
			}
		}

		var resourceListChangedSpecs = SyncMcpAnnotationProviders
			.resourceListChangedSpecifications(new ArrayList<>(beansByAnnotation.get(McpResourceListChanged.class)));
		for (var resourceListChangedSpec : resourceListChangedSpecs) {
			for (var client : resourceListChangedSpec.clients()) {
				this.resourceListChangedHandlers.computeIfAbsent(client, k -> new ArrayList<>())
					.add(resourceListChangedSpec.resourceListChangeHandler());
			}
		}

	}

}
