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

package org.springframework.ai.mcp.client.common.autoconfigure;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.ai.mcp.annotation.spring.ClientMcpAsyncHandlersRegistry;
import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpAsyncClientConfigurer;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;

/**
 * Factory for creating {@link McpAsyncClient} instances with consistent configuration.
 *
 * <p>
 * This factory encapsulates the common logic for building MCP asynchronous clients,
 * ensuring that all clients (whether created for the bulk list or individual injection)
 * are configured identically with the same handlers, timeouts, and customizations.
 *
 * <p>
 * The factory is used by both {@link McpClientAutoConfiguration} (for creating the
 * {@code List<McpAsyncClient>} bean) and {@link NamedMcpAsyncClientFactoryBean} (for
 * creating individually named beans for {@code @McpClient} injection).
 *
 * @author Taewoong Kim
 * @see McpClientAutoConfiguration
 * @see NamedMcpAsyncClientFactoryBean
 * @see McpAsyncClientConfigurer
 */
public class McpAsyncClientFactory {

	private final McpClientCommonProperties commonProperties;

	private final McpAsyncClientConfigurer configurer;

	private final ClientMcpAsyncHandlersRegistry handlersRegistry;

	public McpAsyncClientFactory(McpClientCommonProperties commonProperties, McpAsyncClientConfigurer configurer,
			ClientMcpAsyncHandlersRegistry handlersRegistry) {
		this.commonProperties = commonProperties;
		this.configurer = configurer;
		this.handlersRegistry = handlersRegistry;
	}

	/**
	 * Creates an {@link McpAsyncClient} for the given transport.
	 * @param namedTransport the named transport containing connection name and transport
	 * @return a fully configured and optionally initialized MCP async client
	 */
	public McpAsyncClient createClient(NamedClientMcpTransport namedTransport) {
		String connectionName = namedTransport.name();
		String clientName = this.commonProperties.getName() + " - " + connectionName;

		McpSchema.Implementation clientInfo = new McpSchema.Implementation(clientName,
				this.commonProperties.getVersion());

		McpClient.AsyncSpec spec = McpClient.async(namedTransport.transport())
			.clientInfo(clientInfo)
			.requestTimeout(this.commonProperties.getRequestTimeout())
			.sampling(req -> this.handlersRegistry.handleSampling(connectionName, req))
			.elicitation(req -> this.handlersRegistry.handleElicitation(connectionName, req))
			.loggingConsumer(msg -> this.handlersRegistry.handleLogging(connectionName, msg))
			.progressConsumer(prog -> this.handlersRegistry.handleProgress(connectionName, prog))
			.toolsChangeConsumer(tools -> this.handlersRegistry.handleToolListChanged(connectionName, tools))
			.promptsChangeConsumer(prompts -> this.handlersRegistry.handlePromptListChanged(connectionName, prompts))
			.resourcesChangeConsumer(
					resources -> this.handlersRegistry.handleResourceListChanged(connectionName, resources))
			.capabilities(this.handlersRegistry.getCapabilities(connectionName));

		spec = this.configurer.configure(connectionName, spec);

		McpAsyncClient client = spec.build();

		if (this.commonProperties.isInitialized()) {
			client.initialize().block();
		}

		return client;
	}

}
