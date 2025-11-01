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

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link StdioTransportAutoConfiguration} with H2.
 *
 * @author guan xu
 */
@Timeout(15)
@SuppressWarnings("unchecked")
public class StdioTransportAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(McpClientAutoConfiguration.class, StdioTransportAutoConfiguration.class));

	@Test
	void connectionsStdioTest() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.stdio.connections.server1.command=npx",
					"spring.ai.mcp.client.stdio.connections.server1.args[0]=-y",
					"spring.ai.mcp.client.stdio.connections.server1.args[1]=@modelcontextprotocol/server-everything")
			.run(context -> {
				List<McpSyncClient> mcpClients = (List<McpSyncClient>) context.getBean("mcpSyncClients");
				assertThat(mcpClients).isNotNull();
				assertThat(mcpClients).hasSize(1);

				McpSyncClient mcpClient = mcpClients.get(0);
				mcpClient.ping();

				McpSchema.ListToolsResult toolsResult = mcpClient.listTools();
				assertThat(toolsResult).isNotNull();
				assertThat(toolsResult.tools()).isNotEmpty();

				McpSchema.CallToolResult result = mcpClient.callTool(McpSchema.CallToolRequest.builder()
					.name("add")
					.arguments(Map.of("operation", "add", "a", 1, "b", 2))
					.build());
				assertThat(result).isNotNull();
				assertThat(result.content()).isNotEmpty();

				mcpClient.closeGracefully();
			});
	}

}
