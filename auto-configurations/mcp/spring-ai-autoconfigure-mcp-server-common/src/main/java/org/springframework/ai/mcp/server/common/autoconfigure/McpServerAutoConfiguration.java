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

package org.springframework.ai.mcp.server.common.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerChangeNotificationProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
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
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProviderBase;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import reactor.core.publisher.Mono;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for the Model Context Protocol (MCP)
 * Server.
 * <p>
 *
 * @author Christian Tzolov
 * @since 1.0.0
 * @see McpServerProperties
 */
@AutoConfiguration(
		afterName = { "org.springframework.ai.mcp.server.common.autoconfigure.ToolCallbackConverterAutoConfiguration",
				"org.springframework.ai.mcp.server.autoconfigure.McpServerSseWebFluxAutoConfiguration",
				"org.springframework.ai.mcp.server.autoconfigure.McpServerSseWebMvcAutoConfiguration",
				"org.springframework.ai.mcp.server.autoconfigure.McpServerStreamableHttpWebMvcAutoConfiguration",
				"org.springframework.ai.mcp.server.autoconfigure.McpServerStreamableHttpWebFluxAutoConfiguration" })
@ConditionalOnClass({ McpSchema.class })
@EnableConfigurationProperties({ McpServerProperties.class, McpServerChangeNotificationProperties.class })
@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
@Conditional(McpServerAutoConfiguration.NonStatlessServerCondition.class)
public class McpServerAutoConfiguration {

	private static final LogAccessor logger = new LogAccessor(McpServerAutoConfiguration.class);

	public static class NonStatlessServerCondition extends AnyNestedCondition {

		public NonStatlessServerCondition() {
			super(ConfigurationPhase.PARSE_CONFIGURATION);
		}

		@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "protocol", havingValue = "SSE",
				matchIfMissing = true)
		static class SseEnabledCondition {

		}

		@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "protocol",
				havingValue = "STREAMABLE", matchIfMissing = false)
		static class StreamableEnabledCondition {

		}

	}

	public static class EnabledSseServerCondition extends AllNestedConditions {

		public EnabledSseServerCondition() {
			super(ConfigurationPhase.PARSE_CONFIGURATION);
		}

		@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
				matchIfMissing = true)
		static class McpServerEnabledCondition {

		}

		@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "protocol", havingValue = "SSE",
				matchIfMissing = true)
		static class SseEnabledCondition {

		}

	}

	public static class EnabledStreamableServerCondition extends AllNestedConditions {

		public EnabledStreamableServerCondition() {
			super(ConfigurationPhase.PARSE_CONFIGURATION);
		}

		@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
				matchIfMissing = true)
		static class McpServerEnabledCondition {

		}

		@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "protocol",
				havingValue = "STREAMABLE", matchIfMissing = false)
		static class StreamableEnabledCondition {

		}

	}

	@Bean
	@ConditionalOnMissingBean
	public McpServerTransportProviderBase stdioServerTransport() {
		return new StdioServerTransportProvider();
	}

	@Bean
	@ConditionalOnMissingBean
	public McpSchema.ServerCapabilities.Builder capabilitiesBuilder() {
		return McpSchema.ServerCapabilities.builder();
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public McpSyncServer mcpSyncServer(McpServerTransportProviderBase transportProvider,
			McpSchema.ServerCapabilities.Builder capabilitiesBuilder, McpServerProperties serverProperties,
			McpServerChangeNotificationProperties changeNotificationProperties,
			ObjectProvider<List<SyncToolSpecification>> tools,
			ObjectProvider<List<SyncResourceSpecification>> resources,
			ObjectProvider<List<SyncPromptSpecification>> prompts,
			ObjectProvider<List<SyncCompletionSpecification>> completions,
			ObjectProvider<BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>> rootsChangeConsumers,
			Environment environment) {

		McpSchema.Implementation serverInfo = new Implementation(serverProperties.getName(),
				serverProperties.getVersion());

		// Create the server with both tool and resource capabilities
		SyncSpecification<?> serverBuilder;
		if (transportProvider instanceof McpStreamableServerTransportProvider) {
			serverBuilder = McpServer.sync((McpStreamableServerTransportProvider) transportProvider);
		}
		else {
			serverBuilder = McpServer.sync((McpServerTransportProvider) transportProvider);
		}
		serverBuilder.serverInfo(serverInfo);

		// Tools
		if (serverProperties.getCapabilities().isTool()) {
			logger.info("Enable tools capabilities, notification: "
					+ changeNotificationProperties.isToolChangeNotification());
			capabilitiesBuilder.tools(changeNotificationProperties.isToolChangeNotification());

			List<SyncToolSpecification> toolSpecifications = new ArrayList<>(
					tools.stream().flatMap(List::stream).toList());

			if (!CollectionUtils.isEmpty(toolSpecifications)) {
				serverBuilder.tools(toolSpecifications);
				logger.info("Registered tools: " + toolSpecifications.size());
			}
		}

		// Resources
		if (serverProperties.getCapabilities().isResource()) {
			logger.info("Enable resources capabilities, notification: "
					+ changeNotificationProperties.isResourceChangeNotification());
			capabilitiesBuilder.resources(false, changeNotificationProperties.isResourceChangeNotification());

			List<SyncResourceSpecification> resourceSpecifications = resources.stream().flatMap(List::stream).toList();
			if (!CollectionUtils.isEmpty(resourceSpecifications)) {
				serverBuilder.resources(resourceSpecifications);
				logger.info("Registered resources: " + resourceSpecifications.size());
			}
		}

		// Prompts
		if (serverProperties.getCapabilities().isPrompt()) {
			logger.info("Enable prompts capabilities, notification: "
					+ changeNotificationProperties.isPromptChangeNotification());
			capabilitiesBuilder.prompts(changeNotificationProperties.isPromptChangeNotification());

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
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public McpAsyncServer mcpAsyncServer(McpServerTransportProviderBase transportProvider,
			McpSchema.ServerCapabilities.Builder capabilitiesBuilder, McpServerProperties serverProperties,
			McpServerChangeNotificationProperties changeNotificationProperties,
			ObjectProvider<List<AsyncToolSpecification>> tools,
			ObjectProvider<List<AsyncResourceSpecification>> resources,
			ObjectProvider<List<AsyncPromptSpecification>> prompts,
			ObjectProvider<List<AsyncCompletionSpecification>> completions,
			ObjectProvider<BiConsumer<McpAsyncServerExchange, List<McpSchema.Root>>> rootsChangeConsumer) {

		McpSchema.Implementation serverInfo = new Implementation(serverProperties.getName(),
				serverProperties.getVersion());

		// Create the server with both tool and resource capabilities
		AsyncSpecification<?> serverBuilder;
		if (transportProvider instanceof McpStreamableServerTransportProvider) {
			serverBuilder = McpServer.async((McpStreamableServerTransportProvider) transportProvider);
		}
		else {
			serverBuilder = McpServer.async((McpServerTransportProvider) transportProvider);
		}
		serverBuilder.serverInfo(serverInfo);

		// Tools
		if (serverProperties.getCapabilities().isTool()) {
			List<AsyncToolSpecification> toolSpecifications = new ArrayList<>(
					tools.stream().flatMap(List::stream).toList());

			logger.info("Enable tools capabilities, notification: "
					+ changeNotificationProperties.isToolChangeNotification());
			capabilitiesBuilder.tools(changeNotificationProperties.isToolChangeNotification());

			if (!CollectionUtils.isEmpty(toolSpecifications)) {
				serverBuilder.tools(toolSpecifications);
				logger.info("Registered tools: " + toolSpecifications.size());
			}
		}

		// Resources
		if (serverProperties.getCapabilities().isResource()) {
			logger.info("Enable resources capabilities, notification: "
					+ changeNotificationProperties.isResourceChangeNotification());
			capabilitiesBuilder.resources(false, changeNotificationProperties.isResourceChangeNotification());

			List<AsyncResourceSpecification> resourceSpecifications = resources.stream().flatMap(List::stream).toList();
			if (!CollectionUtils.isEmpty(resourceSpecifications)) {
				serverBuilder.resources(resourceSpecifications);
				logger.info("Registered resources: " + resourceSpecifications.size());
			}
		}

		// Prompts
		if (serverProperties.getCapabilities().isPrompt()) {
			logger.info("Enable prompts capabilities, notification: "
					+ changeNotificationProperties.isPromptChangeNotification());
			capabilitiesBuilder.prompts(changeNotificationProperties.isPromptChangeNotification());
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
