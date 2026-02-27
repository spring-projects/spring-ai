/*
 * Copyright 2023-2024 the original author or authors.
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

import java.util.List;

import io.modelcontextprotocol.client.McpAsyncClient;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A {@link FactoryBean} that creates {@link McpAsyncClient} instances for named MCP
 * connections.
 *
 * <p>
 * This factory bean bridges the gap between dynamic bean registration (done at
 * configuration time) and actual client creation (which requires dependencies that are
 * only available at runtime). The {@link McpConnectionBeanRegistrar} registers
 * definitions for this factory bean, and Spring instantiates them later when all
 * dependencies are ready.
 *
 * <p>
 * Client creation is delegated to {@link McpAsyncClientFactory} to ensure consistent
 * configuration with clients created by {@link McpClientAutoConfiguration}.
 *
 * @author Taewoong Kim
 * @see McpConnectionBeanRegistrar
 * @see McpAsyncClientFactory
 * @see org.springframework.ai.mcp.McpClient
 */
public class NamedMcpAsyncClientFactoryBean implements FactoryBean<McpAsyncClient> {

	private final String connectionName;

	private McpAsyncClientFactory mcpAsyncClientFactory;

	private ObjectProvider<List<NamedClientMcpTransport>> transportsProvider;

	private McpAsyncClient cachedClient;

	/**
	 * Creates a new factory bean for the specified connection.
	 * @param connectionName the name of the MCP connection from properties
	 */
	public NamedMcpAsyncClientFactoryBean(String connectionName) {
		this.connectionName = connectionName;
	}

	@Autowired
	public void setMcpAsyncClientFactory(McpAsyncClientFactory mcpAsyncClientFactory) {
		this.mcpAsyncClientFactory = mcpAsyncClientFactory;
	}

	@Autowired
	public void setTransportsProvider(ObjectProvider<List<NamedClientMcpTransport>> transportsProvider) {
		this.transportsProvider = transportsProvider;
	}

	@Override
	public McpAsyncClient getObject() throws Exception {
		if (this.cachedClient != null) {
			return this.cachedClient;
		}

		// Find the transport for this connection
		NamedClientMcpTransport namedTransport = findTransport();
		if (namedTransport == null) {
			throw new IllegalStateException("No transport found for MCP connection '" + this.connectionName + "'");
		}

		// Delegate to the shared factory for consistent client creation
		this.cachedClient = this.mcpAsyncClientFactory.createClient(namedTransport);

		return this.cachedClient;
	}

	private NamedClientMcpTransport findTransport() {
		return this.transportsProvider.orderedStream()
			.flatMap(List::stream)
			.filter(t -> this.connectionName.equals(t.name()))
			.findFirst()
			.orElse(null);
	}

	@Override
	public Class<?> getObjectType() {
		return McpAsyncClient.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

}
