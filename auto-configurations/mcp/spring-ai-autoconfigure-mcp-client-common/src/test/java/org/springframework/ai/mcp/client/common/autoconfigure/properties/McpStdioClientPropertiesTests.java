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

package org.springframework.ai.mcp.client.common.autoconfigure.properties;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.transport.ServerParameters;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link McpStdioClientProperties}.
 *
 * @author guan xu
 */
class McpStdioClientPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void configPrefixConstant() {
		assertThat(McpStdioClientProperties.CONFIG_PREFIX).isEqualTo("spring.ai.mcp.client.stdio");
	}

	@Test
	void defaultValues() {
		this.contextRunner.run(context -> {
			McpStdioClientProperties properties = context.getBean(McpStdioClientProperties.class);
			assertThat(properties.getServersConfiguration()).isNull();
			assertThat(properties.getConnections()).isNotNull();
			assertThat(properties.getConnections()).isEmpty();
		});
	}

	@Test
	void singleConnection() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.stdio.connections.server1.command=java",
					"spring.ai.mcp.client.stdio.connections.server1.args[0]=--server.port=8080",
					"spring.ai.mcp.client.stdio.connections.server1.args[1]=-jar",
					"spring.ai.mcp.client.stdio.connections.server1.args[2]=server1.jar",
					"spring.ai.mcp.client.stdio.connections.server1.env.API_KEY=sk-abc123")
			.run(context -> {
				McpStdioClientProperties properties = context.getBean(McpStdioClientProperties.class);
				assertThat(properties.getConnections()).hasSize(1);
				assertThat(properties.getConnections()).containsKey("server1");
				assertThat(properties.getConnections().get("server1"))
					.isInstanceOf(McpStdioClientProperties.Parameters.class);
				assertThat(properties.getConnections().get("server1").command()).isEqualTo("java");
				assertThat(properties.getConnections().get("server1").args()).isInstanceOf(List.class)
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.contains("--server.port=8080", "-jar", "server1.jar");
				assertThat(properties.getConnections().get("server1").env()).isInstanceOf(Map.class)
					.asInstanceOf(InstanceOfAssertFactories.MAP)
					.containsEntry("API_KEY", "sk-abc123");
			});
	}

	@Test
	void multipleConnections() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.stdio.connections.server1.command=java",
					"spring.ai.mcp.client.stdio.connections.server1.args[0]=--server.port=8080",
					"spring.ai.mcp.client.stdio.connections.server1.args[1]=-jar",
					"spring.ai.mcp.client.stdio.connections.server1.args[2]=server1.jar",
					"spring.ai.mcp.client.stdio.connections.server1.env.API_KEY=sk-abc123",
					"spring.ai.mcp.client.stdio.connections.server2.command=python",
					"spring.ai.mcp.client.stdio.connections.server2.args[0]=server2.py")
			.run(context -> {
				McpStdioClientProperties properties = context.getBean(McpStdioClientProperties.class);
				assertThat(properties.getConnections()).hasSize(2);
				assertThat(properties.getConnections()).containsKeys("server1", "server2");
				assertThat(properties.getConnections().get("server1"))
					.isInstanceOf(McpStdioClientProperties.Parameters.class);
				assertThat(properties.getConnections().get("server1").command()).isEqualTo("java");
				assertThat(properties.getConnections().get("server1").args()).isInstanceOf(List.class)
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.contains("--server.port=8080", "-jar", "server1.jar");
				assertThat(properties.getConnections().get("server1").env()).isInstanceOf(Map.class)
					.containsEntry("API_KEY", "sk-abc123");
				assertThat(properties.getConnections().get("server2"))
					.isInstanceOf(McpStdioClientProperties.Parameters.class);
				assertThat(properties.getConnections().get("server2").command()).isEqualTo("python");
				assertThat(properties.getConnections().get("server2").args()).isInstanceOf(List.class)
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.contains("server2.py");
				assertThat(properties.getConnections().get("server2").env()).isNull();
			});
	}

	@Test
	void serversConfigurationToServerParameters() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.stdio.serversConfiguration=classpath:test-mcp-servers.json")
			.run(context -> {
				McpStdioClientProperties properties = context.getBean(McpStdioClientProperties.class);
				assertThat(properties.toServerParameters()).hasSize(2);
				assertThat(properties.toServerParameters()).containsKeys("server1", "server2");
				assertThat(properties.toServerParameters().get("server1")).isInstanceOf(ServerParameters.class);
				assertThat(properties.toServerParameters().get("server1").getCommand()).isEqualTo("java");
				assertThat(properties.toServerParameters().get("server1").getArgs()).isInstanceOf(List.class)
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.contains("--server.port=8080", "-jar", "server1.jar");
				assertThat(properties.toServerParameters().get("server1").getEnv()).isInstanceOf(Map.class)
					.asInstanceOf(InstanceOfAssertFactories.MAP)
					.containsEntry("API_KEY", "sk-abc123");
				assertThat(properties.toServerParameters().get("server2")).isInstanceOf(ServerParameters.class);
				assertThat(properties.toServerParameters().get("server2").getCommand()).isEqualTo("python");
				assertThat(properties.toServerParameters().get("server2").getArgs()).isInstanceOf(List.class)
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.contains("server2.py");
				assertThat(properties.toServerParameters().get("server2").getEnv()).isInstanceOf(Map.class)
					.asInstanceOf(InstanceOfAssertFactories.MAP)
					.doesNotContainEntry("API_KEY", "sk-abc123");
			});
	}

	@Test
	void serversConfigurationAndConnectionsToServerParameters() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.stdio.serversConfiguration=classpath:test-mcp-servers.json")
			.withPropertyValues("spring.ai.mcp.client.stdio.connections.server3.command=python",
					"spring.ai.mcp.client.stdio.connections.server3.args[0]=server3.py")
			.run(context -> {
				McpStdioClientProperties properties = context.getBean(McpStdioClientProperties.class);
				assertThat(properties.toServerParameters()).hasSize(3);
				assertThat(properties.toServerParameters()).containsKeys("server1", "server2", "server3");
				assertThat(properties.toServerParameters().get("server1")).isInstanceOf(ServerParameters.class);
				assertThat(properties.toServerParameters().get("server1").getCommand()).isEqualTo("java");
				assertThat(properties.toServerParameters().get("server1").getArgs()).isInstanceOf(List.class)
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.contains("--server.port=8080", "-jar", "server1.jar");
				assertThat(properties.toServerParameters().get("server1").getEnv()).isInstanceOf(Map.class)
					.asInstanceOf(InstanceOfAssertFactories.MAP)
					.containsEntry("API_KEY", "sk-abc123");
				assertThat(properties.toServerParameters().get("server2")).isInstanceOf(ServerParameters.class);
				assertThat(properties.toServerParameters().get("server2").getCommand()).isEqualTo("python");
				assertThat(properties.toServerParameters().get("server2").getArgs()).isInstanceOf(List.class)
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.contains("server2.py");
				assertThat(properties.toServerParameters().get("server2").getEnv()).isInstanceOf(Map.class)
					.asInstanceOf(InstanceOfAssertFactories.MAP)
					.doesNotContainEntry("API_KEY", "sk-abc123");
				assertThat(properties.toServerParameters().get("server3")).isInstanceOf(ServerParameters.class);
				assertThat(properties.toServerParameters().get("server3").getCommand()).isEqualTo("python");
				assertThat(properties.toServerParameters().get("server3").getArgs()).isInstanceOf(List.class)
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.contains("server3.py");
				assertThat(properties.toServerParameters().get("server3").getEnv()).isInstanceOf(Map.class)
					.asInstanceOf(InstanceOfAssertFactories.MAP)
					.doesNotContainEntry("API_KEY", "sk-abc123");
			});
	}

	@Test
	void connectionsReplaceServersConfigurationToServerParameters() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.stdio.serversConfiguration=classpath:test-mcp-servers.json")
			.withPropertyValues("spring.ai.mcp.client.stdio.connections.server1.command=python",
					"spring.ai.mcp.client.stdio.connections.server1.args[0]=server1.py")
			.run(context -> {
				McpStdioClientProperties properties = context.getBean(McpStdioClientProperties.class);
				assertThat(properties.toServerParameters()).hasSize(2);
				assertThat(properties.toServerParameters()).containsKeys("server1", "server2");
				assertThat(properties.toServerParameters().get("server1")).isInstanceOf(ServerParameters.class);
				assertThat(properties.toServerParameters().get("server1").getCommand()).isEqualTo("python");
				assertThat(properties.toServerParameters().get("server1").getArgs()).isInstanceOf(List.class)
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.doesNotContain("--server.port=8080", "-jar", "server1.jar");
				assertThat(properties.toServerParameters().get("server1").getArgs()).isInstanceOf(List.class)
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.contains("server1.py");
			});
	}

	@Test
	void connectionsReplaceConnectionsToServerParameters() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.stdio.connections.server1.command=java",
					"spring.ai.mcp.client.stdio.connections.server1.args[0]=--server.port=8080",
					"spring.ai.mcp.client.stdio.connections.server1.args[1]=-jar",
					"spring.ai.mcp.client.stdio.connections.server1.args[2]=server1.jar",
					"spring.ai.mcp.client.stdio.connections.server1.env.API_KEY=sk-abc123",
					"spring.ai.mcp.client.stdio.connections.server1.command=python",
					"spring.ai.mcp.client.stdio.connections.server1.args[0]=server1.py")
			.run(context -> {
				McpStdioClientProperties properties = context.getBean(McpStdioClientProperties.class);
				assertThat(properties.toServerParameters()).hasSize(1);
				assertThat(properties.toServerParameters()).containsKeys("server1");
				assertThat(properties.toServerParameters().get("server1")).isInstanceOf(ServerParameters.class);
				assertThat(properties.toServerParameters().get("server1").getCommand()).isEqualTo("python");
				assertThat(properties.toServerParameters().get("server1").getArgs()).isInstanceOf(List.class)
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.doesNotContain("--server.port=8080");
				assertThat(properties.toServerParameters().get("server1").getArgs()).isInstanceOf(List.class)
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.contains("server1.py", "-jar", "server1.jar");
				assertThat(properties.toServerParameters().get("server1").getEnv()).isInstanceOf(Map.class)
					.asInstanceOf(InstanceOfAssertFactories.MAP)
					.containsEntry("API_KEY", "sk-abc123");
			});
	}

	@Configuration
	@EnableConfigurationProperties(McpStdioClientProperties.class)
	static class TestConfiguration {

	}

}
