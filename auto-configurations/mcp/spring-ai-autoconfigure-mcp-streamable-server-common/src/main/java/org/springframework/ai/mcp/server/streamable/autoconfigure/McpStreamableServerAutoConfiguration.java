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

package org.springframework.ai.mcp.server.streamable.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.CollectionUtils;
import org.springframework.web.context.support.StandardServletEnvironment;

import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.AsyncSpecification;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncCompletionSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncCompletionSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import reactor.core.publisher.Mono;

/**
 * @author Christian Tzolov
 */
@AutoConfiguration(afterName = {
		"org.springframework.ai.mcp.server.streamable-http.autoconfigure.ToolCallbackConverterAutoConfiguration",
		"org.springframework.ai.mcp.server.streamable-http.webflux.autoconfigure.McpStreamableServerWebFluxAutoConfiguration",
		"org.springframework.ai.mcp.server.streamable-http.webmvc.autoconfigure.McpStreamableServerWebMvcAutoConfiguration" })
@ConditionalOnClass({ McpSchema.class, McpSyncServer.class })
@EnableConfigurationProperties(McpStreamableServerProperties.class)
@ConditionalOnProperty(prefix = McpStreamableServerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class McpStreamableServerAutoConfiguration {

	private static final LogAccessor logger = new LogAccessor(McpStreamableServerAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public McpSchema.ServerCapabilities.Builder capabilitiesBuilder() {
		return McpSchema.ServerCapabilities.builder();
	}

	@Bean
	@ConditionalOnProperty(prefix = McpStreamableServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public McpSyncServer mcpStreamableSyncServer(McpStreamableServerTransportProvider transportProvider,
			McpSchema.ServerCapabilities.Builder capabilitiesBuilder, McpStreamableServerProperties serverProperties,
			ObjectProvider<List<SyncToolSpecification>> tools,
			ObjectProvider<List<SyncResourceSpecification>> resources,
			ObjectProvider<List<SyncPromptSpecification>> prompts,
			ObjectProvider<List<SyncCompletionSpecification>> completions,
			ObjectProvider<BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>> rootsChangeConsumers,
			Environment environment) {

		McpSchema.Implementation serverInfo = new Implementation(serverProperties.getName(),
				serverProperties.getVersion());

		// Create the server with both tool and resource capabilities
		SyncSpecification serverBuilder = McpServer.sync(transportProvider).serverInfo(serverInfo);

		// Tools
		if (serverProperties.getCapabilities().isTool()) {
			logger.info("Enable tools capabilities, notification: " + serverProperties.isToolChangeNotification());
			capabilitiesBuilder.tools(serverProperties.isToolChangeNotification());

			List<SyncToolSpecification> toolSpecifications = new ArrayList<>(
					tools.stream().flatMap(List::stream).toList());

			if (!CollectionUtils.isEmpty(toolSpecifications)) {
				serverBuilder.tools(toolSpecifications);
				logger.info("Registered tools: " + toolSpecifications.size());
			}
		}

		// Resources
		if (serverProperties.getCapabilities().isResource()) {
			logger.info(
					"Enable resources capabilities, notification: " + serverProperties.isResourceChangeNotification());
			capabilitiesBuilder.resources(false, serverProperties.isResourceChangeNotification());

			List<SyncResourceSpecification> resourceSpecifications = resources.stream().flatMap(List::stream).toList();
			if (!CollectionUtils.isEmpty(resourceSpecifications)) {
				serverBuilder.resources(resourceSpecifications);
				logger.info("Registered resources: " + resourceSpecifications.size());
			}
		}

		// Prompts
		if (serverProperties.getCapabilities().isPrompt()) {
			logger.info("Enable prompts capabilities, notification: " + serverProperties.isPromptChangeNotification());
			capabilitiesBuilder.prompts(serverProperties.isPromptChangeNotification());

			List<SyncPromptSpecification> promptSpecifications = prompts.stream().flatMap(List::stream).toList();
			if (!CollectionUtils.isEmpty(promptSpecifications)) {
				serverBuilder.prompts(promptSpecifications);
				logger.info("Registered prompts: " + promptSpecifications.size());
			}
		}

		// Completions
		if (serverProperties.getCapabilities().isCompletion()) {
			logger.info("Enable completions capabilities");
			capabilitiesBuilder.completions();

			List<SyncCompletionSpecification> completionSpecifications = completions.stream()
				.flatMap(List::stream)
				.toList();
			if (!CollectionUtils.isEmpty(completionSpecifications)) {
				serverBuilder.completions(completionSpecifications);
				logger.info("Registered completions: " + completionSpecifications.size());
			}
		}

		rootsChangeConsumers.ifAvailable(consumer -> {
			BiConsumer<McpSyncServerExchange, List<McpSchema.Root>> syncConsumer = (exchange, roots) -> consumer
				.accept(exchange, roots);
			serverBuilder.rootsChangeHandler(syncConsumer);
			logger.info("Registered roots change consumer");
		});

		serverBuilder.capabilities(capabilitiesBuilder.build());

		serverBuilder.instructions(serverProperties.getInstructions());

		serverBuilder.requestTimeout(serverProperties.getRequestTimeout());
		if (environment instanceof StandardServletEnvironment) {
			serverBuilder.immediateExecution(true);
		}

		return serverBuilder.build();
	}

	@Bean
	@ConditionalOnProperty(prefix = McpStreamableServerProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public McpAsyncServer mcpStreamableAsyncServer(McpStreamableServerTransportProvider transportProvider,
			McpSchema.ServerCapabilities.Builder capabilitiesBuilder, McpStreamableServerProperties serverProperties,
			ObjectProvider<List<AsyncToolSpecification>> tools,
			ObjectProvider<List<AsyncResourceSpecification>> resources,
			ObjectProvider<List<AsyncPromptSpecification>> prompts,
			ObjectProvider<List<AsyncCompletionSpecification>> completions,
			ObjectProvider<BiConsumer<McpAsyncServerExchange, List<McpSchema.Root>>> rootsChangeConsumer) {

		McpSchema.Implementation serverInfo = new Implementation(serverProperties.getName(),
				serverProperties.getVersion());

		// Create the server with both tool and resource capabilities
		AsyncSpecification serverBuilder = McpServer.async(transportProvider).serverInfo(serverInfo);

		// Tools
		if (serverProperties.getCapabilities().isTool()) {
			List<AsyncToolSpecification> toolSpecifications = new ArrayList<>(
					tools.stream().flatMap(List::stream).toList());

			logger.info("Enable tools capabilities, notification: " + serverProperties.isToolChangeNotification());
			capabilitiesBuilder.tools(serverProperties.isToolChangeNotification());

			if (!CollectionUtils.isEmpty(toolSpecifications)) {
				serverBuilder.tools(toolSpecifications);
				logger.info("Registered tools: " + toolSpecifications.size());
			}
		}

		// Resources
		if (serverProperties.getCapabilities().isResource()) {
			logger.info(
					"Enable resources capabilities, notification: " + serverProperties.isResourceChangeNotification());
			capabilitiesBuilder.resources(false, serverProperties.isResourceChangeNotification());

			List<AsyncResourceSpecification> resourceSpecifications = resources.stream().flatMap(List::stream).toList();
			if (!CollectionUtils.isEmpty(resourceSpecifications)) {
				serverBuilder.resources(resourceSpecifications);
				logger.info("Registered resources: " + resourceSpecifications.size());
			}
		}

		// Prompts
		if (serverProperties.getCapabilities().isPrompt()) {
			logger.info("Enable prompts capabilities, notification: " + serverProperties.isPromptChangeNotification());
			capabilitiesBuilder.prompts(serverProperties.isPromptChangeNotification());
			List<AsyncPromptSpecification> promptSpecifications = prompts.stream().flatMap(List::stream).toList();

			if (!CollectionUtils.isEmpty(promptSpecifications)) {
				serverBuilder.prompts(promptSpecifications);
				logger.info("Registered prompts: " + promptSpecifications.size());
			}
		}

		// Completions
		if (serverProperties.getCapabilities().isCompletion()) {
			logger.info("Enable completions capabilities");
			capabilitiesBuilder.completions();
			List<AsyncCompletionSpecification> completionSpecifications = completions.stream()
				.flatMap(List::stream)
				.toList();

			if (!CollectionUtils.isEmpty(completionSpecifications)) {
				serverBuilder.completions(completionSpecifications);
				logger.info("Registered completions: " + completionSpecifications.size());
			}
		}

		rootsChangeConsumer.ifAvailable(consumer -> {
			BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>> asyncConsumer = (exchange, roots) -> {
				consumer.accept(exchange, roots);
				return Mono.empty();
			};
			serverBuilder.rootsChangeHandler(asyncConsumer);
			logger.info("Registered roots change consumer");
		});

		serverBuilder.capabilities(capabilitiesBuilder.build());

		serverBuilder.instructions(serverProperties.getInstructions());

		serverBuilder.requestTimeout(serverProperties.getRequestTimeout());

		return serverBuilder.build();
	}

}
