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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.AsyncSpec;
import io.modelcontextprotocol.server.McpServer.SyncSpec;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncPromptRegistration;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncResourceRegistration;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolRegistration;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptRegistration;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceRegistration;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolRegistration;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.ServerMcpTransport;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;

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
 * {@link McpWebMvcServerAutoConfiguration}.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 * @see McpServerProperties
 * @see McpWebMvcServerAutoConfiguration
 * @see org.springframework.ai.mcp.ToolCallback
 */
@AutoConfiguration(after = { McpWebMvcServerAutoConfiguration.class, McpWebFluxServerAutoConfiguration.class })
@ConditionalOnClass({ McpSchema.class, McpSyncServer.class })
@EnableConfigurationProperties(McpServerProperties.class)
@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class McpServerAutoConfiguration {

	private static final LogAccessor logger = new LogAccessor(McpServerAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
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
	public List<McpServerFeatures.SyncToolRegistration> syncTools(ObjectProvider<List<ToolCallback>> toolCalls,
			McpServerProperties serverProperties) {
		List<ToolCallback> tools = toolCalls.stream().flatMap(List::stream).toList();

		return this.toSyncToolRegistration(tools, serverProperties);
	}

	private List<McpServerFeatures.SyncToolRegistration> toSyncToolRegistration(List<ToolCallback> tools,
			McpServerProperties serverProperties) {
		return tools.stream().map(tool -> {
			String toolName = tool.getToolDefinition().name();
			MimeType mimeType = (serverProperties.getToolResponseMimeType().containsKey(toolName))
					? MimeType.valueOf(serverProperties.getToolResponseMimeType().get(toolName)) : null;
			return McpToolUtils.toSyncToolRegistration(tool, mimeType);
		}).toList();
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public McpSyncServer mcpSyncServer(ServerMcpTransport transport,
			McpSchema.ServerCapabilities.Builder capabilitiesBuilder, McpServerProperties serverProperties,
			ObjectProvider<List<SyncToolRegistration>> tools, ObjectProvider<List<SyncResourceRegistration>> resources,
			ObjectProvider<List<SyncPromptRegistration>> prompts,
			ObjectProvider<Consumer<List<McpSchema.Root>>> rootsChangeConsumers,
			List<ToolCallbackProvider> toolCallbackProvider) {

		McpSchema.Implementation serverInfo = new Implementation(serverProperties.getName(),
				serverProperties.getVersion());

		// Create the server with both tool and resource capabilities
		SyncSpec serverBuilder = McpServer.sync(transport).serverInfo(serverInfo);

		List<SyncToolRegistration> toolResgistrations = new ArrayList<>(tools.stream().flatMap(List::stream).toList());

		List<ToolCallback> providerToolCallbacks = toolCallbackProvider.stream()
			.map(pr -> List.of(pr.getToolCallbacks()))
			.flatMap(List::stream)
			.filter(fc -> fc instanceof ToolCallback)
			.map(fc -> (ToolCallback) fc)
			.toList();

		toolResgistrations.addAll(this.toSyncToolRegistration(providerToolCallbacks, serverProperties));

		if (!CollectionUtils.isEmpty(toolResgistrations)) {
			serverBuilder.tools(toolResgistrations);
			capabilitiesBuilder.tools(serverProperties.isToolChangeNotification());
			logger.info("Registered tools" + toolResgistrations.size() + " notification: "
					+ serverProperties.isToolChangeNotification());
		}

		List<SyncResourceRegistration> resourceResgistrations = resources.stream().flatMap(List::stream).toList();
		if (!CollectionUtils.isEmpty(resourceResgistrations)) {
			serverBuilder.resources(resourceResgistrations);
			capabilitiesBuilder.resources(false, serverProperties.isResourceChangeNotification());
			logger.info("Registered resources" + resourceResgistrations.size() + " notification: "
					+ serverProperties.isResourceChangeNotification());
		}

		List<SyncPromptRegistration> promptResgistrations = prompts.stream().flatMap(List::stream).toList();
		if (!CollectionUtils.isEmpty(promptResgistrations)) {
			serverBuilder.prompts(promptResgistrations);
			capabilitiesBuilder.prompts(serverProperties.isPromptChangeNotification());
			logger.info("Registered prompts" + promptResgistrations.size() + " notification: "
					+ serverProperties.isPromptChangeNotification());
		}

		rootsChangeConsumers.ifAvailable(consumer -> {
			serverBuilder.rootsChangeConsumer(consumer);
			logger.info("Registered roots change consumer");
		});

		serverBuilder.capabilities(capabilitiesBuilder.build());

		return serverBuilder.build();
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public List<McpServerFeatures.AsyncToolRegistration> asyncTools(ObjectProvider<List<ToolCallback>> toolCalls,
			McpServerProperties serverProperties) {
		var tools = toolCalls.stream().flatMap(List::stream).toList();

		return this.toAsyncToolRegistration(tools, serverProperties);
	}

	private List<McpServerFeatures.AsyncToolRegistration> toAsyncToolRegistration(List<ToolCallback> tools,
			McpServerProperties serverProperties) {
		return tools.stream().map(tool -> {
			String toolName = tool.getToolDefinition().name();
			MimeType mimeType = (serverProperties.getToolResponseMimeType().containsKey(toolName))
					? MimeType.valueOf(serverProperties.getToolResponseMimeType().get(toolName)) : null;
			return McpToolUtils.toAsyncToolRegistration(tool, mimeType);
		}).toList();
	}

	@Bean
	@ConditionalOnProperty(prefix = McpServerProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	public McpAsyncServer mcpAsyncServer(ServerMcpTransport transport,
			McpSchema.ServerCapabilities.Builder capabilitiesBuilder, McpServerProperties serverProperties,
			ObjectProvider<List<AsyncToolRegistration>> tools,
			ObjectProvider<List<AsyncResourceRegistration>> resources,
			ObjectProvider<List<AsyncPromptRegistration>> prompts,
			ObjectProvider<Consumer<List<McpSchema.Root>>> rootsChangeConsumer,
			List<ToolCallbackProvider> toolCallbackProvider) {

		McpSchema.Implementation serverInfo = new Implementation(serverProperties.getName(),
				serverProperties.getVersion());

		// Create the server with both tool and resource capabilities
		AsyncSpec serverBilder = McpServer.async(transport).serverInfo(serverInfo);

		List<AsyncToolRegistration> toolResgistrations = new ArrayList<>(tools.stream().flatMap(List::stream).toList());
		List<ToolCallback> providerToolCallbacks = toolCallbackProvider.stream()
			.map(pr -> List.of(pr.getToolCallbacks()))
			.flatMap(List::stream)
			.filter(fc -> fc instanceof ToolCallback)
			.map(fc -> (ToolCallback) fc)
			.toList();

		toolResgistrations.addAll(this.toAsyncToolRegistration(providerToolCallbacks, serverProperties));

		if (!CollectionUtils.isEmpty(toolResgistrations)) {
			serverBilder.tools(toolResgistrations);
			capabilitiesBuilder.tools(serverProperties.isToolChangeNotification());
			logger.info("Registered tools" + toolResgistrations.size() + " notification: "
					+ serverProperties.isToolChangeNotification());
		}

		List<AsyncResourceRegistration> resourceResgistrations = resources.stream().flatMap(List::stream).toList();
		if (!CollectionUtils.isEmpty(resourceResgistrations)) {
			serverBilder.resources(resourceResgistrations);
			capabilitiesBuilder.resources(false, serverProperties.isResourceChangeNotification());
			logger.info("Registered resources" + resourceResgistrations.size() + " notification: "
					+ serverProperties.isResourceChangeNotification());
		}

		List<AsyncPromptRegistration> promptResgistrations = prompts.stream().flatMap(List::stream).toList();
		if (!CollectionUtils.isEmpty(promptResgistrations)) {
			serverBilder.prompts(promptResgistrations);
			capabilitiesBuilder.prompts(serverProperties.isPromptChangeNotification());
			logger.info("Registered prompts" + promptResgistrations.size() + " notification: "
					+ serverProperties.isPromptChangeNotification());
		}

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
