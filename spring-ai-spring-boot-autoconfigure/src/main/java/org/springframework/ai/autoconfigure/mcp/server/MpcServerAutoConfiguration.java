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

package org.springframework.ai.autoconfigure.mcp.server;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import io.modelcontextprotocol.server.McpServer;
import reactor.core.publisher.Mono;
import io.modelcontextprotocol.server.McpServer.SyncSpec;
import io.modelcontextprotocol.server.McpServer.AsyncSpec;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolRegistration;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolRegistration;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.ServerMcpTransport;

import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.log.LogAccessor;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for the Model Context Protocol (MCP)
 * Server.
 * <p>
 * This configuration class sets up the core MCP server components with support for both
 * synchronous and asynchronous operation modes. The server type is controlled through the
 * {@code spring.ai.mcp.server.type} property, defaulting to SYNC mode.
 * <p>
 * Core features and capabilities include:
 * <ul>
 * <li>Tools: Extensible tool registration system supporting both sync and async
 * execution</li>
 * <li>Resources: Static and dynamic resource management with optional change
 * notifications</li>
 * <li>Prompts: Configurable prompt templates with change notification support</li>
 * <li>Transport: Flexible transport layer with built-in support for:
 * <ul>
 * <li>STDIO (default): Standard input/output based communication</li>
 * <li>WebMvc: HTTP-based transport when Spring MVC is available</li>
 * <li>WebFlux: Reactive transport when Spring WebFlux is available</li>
 * </ul>
 * </li>
 * </ul>
 * <p>
 * The configuration is activated when:
 * <ul>
 * <li>The required MCP classes ({@link McpSchema} and {@link McpSyncServer}) are on the
 * classpath</li>
 * <li>The {@code spring.ai.mcp.server.enabled} property is true (default)</li>
 * </ul>
 * <p>
 * Server configuration is managed through {@link McpServerProperties} with support for:
 * <ul>
 * <li>Server identification (name, version)</li>
 * <li>Transport selection</li>
 * <li>Change notification settings for tools, resources, and prompts</li>
 * <li>Sync/Async operation mode selection</li>
 * </ul>
 * <p>
 * WebMvc transport support is provided separately by
 * {@link MpcWebMvcServerAutoConfiguration}.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 * @see McpServerProperties
 * @see MpcWebMvcServerAutoConfiguration
 * @see org.springframework.ai.mcp.ToolCallback
 */
@AutoConfiguration
@ConditionalOnClass({ McpSchema.class, McpSyncServer.class })
@EnableConfigurationProperties(McpServerProperties.class)
@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true")
public class MpcServerAutoConfiguration {

	private static final LogAccessor logger = new LogAccessor(MpcServerAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "transport", havingValue = "STDIO",
			matchIfMissing = true)
	public ServerMcpTransport stdioServerTransport() {
		return new StdioServerTransport();
	}

	@Bean
	@ConditionalOnMissingBean
	public McpSchema.ServerCapabilities.Builder capabilitiesBuilder() {
		return McpSchema.ServerCapabilities.builder();
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<McpServerFeatures.SyncToolRegistration> syncTools(List<ToolCallback> toolCalls) {
		return McpToolUtils.toSyncToolRegistration(toolCalls);
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public McpSyncServer mcpSyncServer(ServerMcpTransport transport,
			McpSchema.ServerCapabilities.Builder capabilitiesBuilder, McpServerProperties serverProperties,
			ObjectProvider<List<SyncToolRegistration>> tools,
			ObjectProvider<List<McpServerFeatures.SyncResourceRegistration>> resources,
			ObjectProvider<List<McpServerFeatures.SyncPromptRegistration>> prompts,
			ObjectProvider<Consumer<List<McpSchema.Root>>> rootsChangeConsumers) {

		McpSchema.Implementation serverInfo = new Implementation(serverProperties.getName(),
				serverProperties.getVersion());

		// Create the server with both tool and resource capabilities
		SyncSpec serverBuilder = McpServer.sync(transport).serverInfo(serverInfo);

		tools.ifAvailable(toolsList -> {
			serverBuilder.tools(toolsList);
			capabilitiesBuilder.tools(serverProperties.isToolChangeNotification());
			logger.info("Registered tools" + toolsList.size() + " notification: "
					+ serverProperties.isToolChangeNotification());
		});

		resources.ifAvailable(resourceList -> {
			serverBuilder.resources(resourceList);
			capabilitiesBuilder.resources(false, serverProperties.isResourceChangeNotification());
			logger.info("Registered resources" + resourceList.size() + " notification: "
					+ serverProperties.isResourceChangeNotification());
		});

		prompts.ifAvailable(promptList -> {
			serverBuilder.prompts(promptList);
			capabilitiesBuilder.prompts(serverProperties.isPromptChangeNotification());
			logger.info("Registered prompts" + promptList.size() + " notification: "
					+ serverProperties.isPromptChangeNotification());
		});

		rootsChangeConsumers.ifAvailable(consumer -> {
			serverBuilder.rootsChangeConsumer(consumer);
			logger.info("Registered roots change consumer");
		});

		serverBuilder.capabilities(capabilitiesBuilder.build());

		return serverBuilder.build();
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public List<McpServerFeatures.AsyncToolRegistration> asyncTools(List<ToolCallback> toolCalls) {
		return McpToolUtils.toAsyncToolRegistration(toolCalls);
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public McpAsyncServer mcpAsyncServer(ServerMcpTransport transport,
			McpSchema.ServerCapabilities.Builder capabilitiesBuilder, McpServerProperties serverProperties,
			ObjectProvider<List<AsyncToolRegistration>> tools,
			ObjectProvider<List<McpServerFeatures.AsyncResourceRegistration>> resources,
			ObjectProvider<List<McpServerFeatures.AsyncPromptRegistration>> prompts,
			ObjectProvider<Consumer<List<McpSchema.Root>>> rootsChangeConsumer) {

		McpSchema.Implementation serverInfo = new Implementation(serverProperties.getName(),
				serverProperties.getVersion());

		// Create the server with both tool and resource capabilities
		AsyncSpec serverBilder = McpServer.async(transport).serverInfo(serverInfo);

		tools.ifAvailable(toolsList -> {
			serverBilder.tools(toolsList);
			capabilitiesBuilder.tools(serverProperties.isToolChangeNotification());
			logger.info("Registered tools" + toolsList.size() + " notification: "
					+ serverProperties.isToolChangeNotification());
		});

		resources.ifAvailable(resourceList -> {
			serverBilder.resources(resourceList);
			capabilitiesBuilder.resources(false, serverProperties.isResourceChangeNotification());
			logger.info("Registered resources" + resourceList.size() + " notification: "
					+ serverProperties.isResourceChangeNotification());
		});

		prompts.ifAvailable(promptList -> {
			serverBilder.prompts(promptList);
			capabilitiesBuilder.prompts(serverProperties.isPromptChangeNotification());
			logger.info("Registered prompts" + promptList.size() + " notification: "
					+ serverProperties.isPromptChangeNotification());
		});

		rootsChangeConsumer.ifAvailable(consumer -> {
			Function<List<McpSchema.Root>, Mono<Void>> asyncConsumer = roots -> {
				consumer.accept(roots);
				return Mono.empty();
			};
			serverBilder.rootsChangeConsumer(asyncConsumer);
			logger.info("Registered roots change consumer");
		});

		serverBilder.capabilities(capabilitiesBuilder.build());

		return serverBilder.build();
	}

}
