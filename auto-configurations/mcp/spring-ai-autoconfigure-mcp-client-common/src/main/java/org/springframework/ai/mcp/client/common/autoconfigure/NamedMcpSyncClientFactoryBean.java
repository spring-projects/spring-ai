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

package org.springframework.ai.mcp.client.common.autoconfigure;

import java.util.List;

import io.modelcontextprotocol.client.McpSyncClient;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * A {@link FactoryBean} that exposes the {@link McpSyncClient} for a named MCP
 * connection.
 *
 * <p>
 * {@link McpConnectionBeanRegistrar} registers definitions for configured connections.
 * The matching client is resolved from the existing aggregate client list, keeping
 * aggregate access and selective injection on the same client instance.
 *
 * @author Taewoong Kim
 * @see org.springframework.ai.mcp.annotation.spring.McpClient
 * @since 2.0.1
 */
public class NamedMcpSyncClientFactoryBean implements FactoryBean<McpSyncClient> {

	private final String connectionName;

	private ObjectProvider<List<NamedClientMcpTransport>> transportsProvider;

	private List<McpSyncClient> mcpSyncClients;

	/**
	 * Creates a factory bean for the specified connection.
	 * @param connectionName the configured MCP connection name
	 */
	public NamedMcpSyncClientFactoryBean(String connectionName) {
		this.connectionName = connectionName;
	}

	@Autowired
	void setTransportsProvider(ObjectProvider<List<NamedClientMcpTransport>> transportsProvider) {
		this.transportsProvider = transportsProvider;
	}

	@Autowired
	void setMcpSyncClients(@Qualifier("mcpSyncClients") List<McpSyncClient> mcpSyncClients) {
		this.mcpSyncClients = mcpSyncClients;
	}

	@Override
	public McpSyncClient getObject() {
		return findClient();
	}

	private McpSyncClient findClient() {
		List<NamedClientMcpTransport> namedTransports = this.transportsProvider.stream().flatMap(List::stream).toList();
		if (namedTransports.size() != this.mcpSyncClients.size()) {
			throw new IllegalStateException("MCP transport and sync client counts do not match");
		}

		int matchingIndex = -1;
		for (int i = 0; i < namedTransports.size(); i++) {
			if (this.connectionName.equals(namedTransports.get(i).name())) {
				if (matchingIndex >= 0) {
					throw new IllegalStateException(
							"Multiple transports found for MCP connection '" + this.connectionName + "'");
				}
				matchingIndex = i;
			}
		}

		if (matchingIndex < 0) {
			throw new IllegalStateException("No transport found for MCP connection '" + this.connectionName + "'");
		}

		return this.mcpSyncClients.get(matchingIndex);
	}

	@Override
	public Class<?> getObjectType() {
		return McpSyncClient.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

}
