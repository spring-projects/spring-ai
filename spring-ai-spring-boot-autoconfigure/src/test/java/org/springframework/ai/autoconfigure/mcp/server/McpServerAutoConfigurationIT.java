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

import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransport;
import io.modelcontextprotocol.spec.ServerMcpTransport;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class McpServerAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.server.enabled=true")
		.withConfiguration(AutoConfigurations.of(MpcServerAutoConfiguration.class));

	@Test
	void defaultConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(McpSyncServer.class);
			assertThat(context).hasSingleBean(ServerMcpTransport.class);
			assertThat(context.getBean(ServerMcpTransport.class)).isInstanceOf(StdioServerTransport.class);

			McpServerProperties properties = context.getBean(McpServerProperties.class);
			assertThat(properties.getName()).isEqualTo("mcp-server");
			assertThat(properties.getVersion()).isEqualTo("1.0.0");
			assertThat(properties.getTransport()).isEqualTo(McpServerProperties.Transport.STDIO);
			assertThat(properties.getType()).isEqualTo(McpServerProperties.ServerType.SYNC);
			assertThat(properties.isToolChangeNotification()).isTrue();
			assertThat(properties.isResourceChangeNotification()).isTrue();
			assertThat(properties.isPromptChangeNotification()).isTrue();
		});
	}

	@Test
	void asyncConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.type=ASYNC", "spring.ai.mcp.server.name=test-server",
					"spring.ai.mcp.server.version=2.0.0")
			.run(context -> {
				assertThat(context).hasSingleBean(McpAsyncServer.class);
				assertThat(context).doesNotHaveBean(McpSyncServer.class);

				McpServerProperties properties = context.getBean(McpServerProperties.class);
				assertThat(properties.getName()).isEqualTo("test-server");
				assertThat(properties.getVersion()).isEqualTo("2.0.0");
				assertThat(properties.getType()).isEqualTo(McpServerProperties.ServerType.ASYNC);
			});
	}

	@Test
	void disabledConfiguration() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.server.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(McpSyncServer.class);
			assertThat(context).doesNotHaveBean(McpAsyncServer.class);
			assertThat(context).doesNotHaveBean(ServerMcpTransport.class);
		});
	}

	@Test
	void notificationConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.server.tool-change-notification=false",
					"spring.ai.mcp.server.resource-change-notification=false",
					"spring.ai.mcp.server.prompt-change-notification=false")
			.run(context -> {
				McpServerProperties properties = context.getBean(McpServerProperties.class);
				assertThat(properties.isToolChangeNotification()).isFalse();
				assertThat(properties.isResourceChangeNotification()).isFalse();
				assertThat(properties.isPromptChangeNotification()).isFalse();
			});
	}

}
