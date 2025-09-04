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

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.StatelessAsyncSpecification;
import io.modelcontextprotocol.server.McpServer.StatelessSyncSpecification;
import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncCompletionSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncPromptSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncResourceSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncCompletionSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;

import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
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

/**
 * @author Christian Tzolov
 */
@AutoConfiguration(afterName = {
		"org.springframework.ai.mcp.server.common.autoconfigure.StatelessToolCallbackConverterAutoConfiguration",
		"org.springframework.ai.mcp.server.autoconfigure.McpServerStatelessWebFluxAutoConfiguration",
		"org.springframework.ai.mcp.server.autoconfigure.McpServerStatelessWebMvcAutoConfiguration" })
@ConditionalOnClass({ McpSchema.class })
@EnableConfigurationProperties(McpServerProperties.class)
@Conditional({ McpServerStdioDisabledCondition.class,
		McpServerStatelessAutoConfiguration.EnabledStatelessServerCondition.class })
public class McpServerStatelessAutoConfiguration {

	private static final LogAccessor logger = new LogAccessor(McpServerStatelessAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public McpSchema.ServerCapabilities.Builder capabilitiesBuilder() {
		return McpSchema.ServerCapabilities.builder();
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public McpStatelessSyncServer mcpStatelessSyncServer(McpStatelessServerTransport statelessTransport,
			McpSchema.ServerCapabilities.Builder capabilitiesBuilder, McpServerProperties serverProperties,
			ObjectProvider<List<SyncToolSpecification>> tools,
			ObjectProvider<List<SyncResourceSpecification>> resources,
			ObjectProvider<List<SyncPromptSpecification>> prompts,
			ObjectProvider<List<SyncCompletionSpecification>> completions, Environment environment) {

		McpSchema.Implementation serverInfo = new Implementation(serverProperties.getName(),
				serverProperties.getVersion());

		// Create the server with both tool and resource capabilities
		StatelessSyncSpecification serverBuilder = McpServer.sync(statelessTransport).serverInfo(serverInfo);

		// Tools
		if (serverProperties.getCapabilities().isTool()) {
			capabilitiesBuilder.tools(false);

			List<SyncToolSpecification> toolSpecifications = new ArrayList<>(
					tools.stream().flatMap(List::stream).toList());

			if (!CollectionUtils.isEmpty(toolSpecifications)) {
				serverBuilder.tools(toolSpecifications);
				logger.info("Registered tools: " + toolSpecifications.size());
			}
		}

		// Resources
		if (serverProperties.getCapabilities().isResource()) {
			capabilitiesBuilder.resources(false, false);

			List<SyncResourceSpecification> resourceSpecifications = resources.stream().flatMap(List::stream).toList();
			if (!CollectionUtils.isEmpty(resourceSpecifications)) {
				serverBuilder.resources(resourceSpecifications);
				logger.info("Registered resources: " + resourceSpecifications.size());
			}
		}

		// Prompts
		if (serverProperties.getCapabilities().isPrompt()) {
			capabilitiesBuilder.prompts(false);

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
	public McpStatelessAsyncServer mcpStatelessAsyncServer(McpStatelessServerTransport statelessTransport,
			McpSchema.ServerCapabilities.Builder capabilitiesBuilder, McpServerProperties serverProperties,
			ObjectProvider<List<AsyncToolSpecification>> tools,
			ObjectProvider<List<AsyncResourceSpecification>> resources,
			ObjectProvider<List<AsyncPromptSpecification>> prompts,
			ObjectProvider<List<AsyncCompletionSpecification>> completions) {

		McpSchema.Implementation serverInfo = new Implementation(serverProperties.getName(),
				serverProperties.getVersion());

		// Create the server with both tool and resource capabilities
		StatelessAsyncSpecification serverBuilder = McpServer.async(statelessTransport).serverInfo(serverInfo);

		// Tools
		if (serverProperties.getCapabilities().isTool()) {
			List<AsyncToolSpecification> toolSpecifications = new ArrayList<>(
					tools.stream().flatMap(List::stream).toList());

			capabilitiesBuilder.tools(false);

			if (!CollectionUtils.isEmpty(toolSpecifications)) {
				serverBuilder.tools(toolSpecifications);
				logger.info("Registered tools: " + toolSpecifications.size());
			}
		}

		// Resources
		if (serverProperties.getCapabilities().isResource()) {
			capabilitiesBuilder.resources(false, false);

			List<AsyncResourceSpecification> resourceSpecifications = resources.stream().flatMap(List::stream).toList();
			if (!CollectionUtils.isEmpty(resourceSpecifications)) {
				serverBuilder.resources(resourceSpecifications);
				logger.info("Registered resources: " + resourceSpecifications.size());
			}
		}

		// Prompts
		if (serverProperties.getCapabilities().isPrompt()) {
			capabilitiesBuilder.prompts(false);
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

		serverBuilder.capabilities(capabilitiesBuilder.build());

		serverBuilder.instructions(serverProperties.getInstructions());

		serverBuilder.requestTimeout(serverProperties.getRequestTimeout());

		return serverBuilder.build();
	}

	public static class EnabledStatelessServerCondition extends AllNestedConditions {

		public EnabledStatelessServerCondition() {
			super(ConfigurationPhase.PARSE_CONFIGURATION);
		}

		@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
				matchIfMissing = true)
		static class McpServerEnabledCondition {

		}

		@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "protocol", havingValue = "STATELESS",
				matchIfMissing = false)
		static class StatelessEnabledCondition {

		}

	}

}
