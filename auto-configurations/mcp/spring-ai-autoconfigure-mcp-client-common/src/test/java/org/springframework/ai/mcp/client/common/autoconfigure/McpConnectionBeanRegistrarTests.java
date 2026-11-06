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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.mcp.annotation.spring.McpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link McpConnectionBeanRegistrar}.
 *
 * @author Taewoong Kim
 */
class McpConnectionBeanRegistrarTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void registersNamedBeanForSseConnection() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.sse.connections.test_server.url=http://localhost:8080")
			.run(context -> assertSyncBeanRegistered(
					context.getBeanFactory().getBeanDefinition("mcpSyncClient_test_server"), "test_server"));
	}

	@Test
	void registersNamedBeanForStreamableHttpConnection() {
		this.contextRunner
			.withPropertyValues(
					"spring.ai.mcp.client.streamable-http.connections.http_server.url=http://localhost:9090")
			.run(context -> assertSyncBeanRegistered(
					context.getBeanFactory().getBeanDefinition("mcpSyncClient_http_server"), "http_server"));
	}

	@Test
	void registersNamedBeanForStdioConnection() {
		this.contextRunner.withPropertyValues("spring.ai.mcp.client.stdio.connections.stdio_server.command=echo")
			.run(context -> assertSyncBeanRegistered(
					context.getBeanFactory().getBeanDefinition("mcpSyncClient_stdio_server"), "stdio_server"));
	}

	@Test
	void registersNamedBeanForStdioServersConfiguration(@TempDir Path tempDirectory) throws IOException {
		Path serversConfiguration = Files.writeString(tempDirectory.resolve("mcp-servers.json"),
				"{\"mcpServers\":{\"resource.server\":{\"command\":\"echo\"}}}");

		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.stdio.servers-configuration=" + serversConfiguration.toUri())
			.run(context -> assertSyncBeanRegistered(
					context.getBeanFactory().getBeanDefinition("mcpSyncClient_resource.server"), "resource.server"));
	}

	@Test
	void registersMultipleNamedBeans() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.sse.connections.server_a.url=http://localhost:8080",
					"spring.ai.mcp.client.streamable-http.connections.server_b.url=http://localhost:8081",
					"spring.ai.mcp.client.stdio.connections.server_c.command=echo")
			.run(context -> {
				assertThat(context.getBeanFactory().containsBeanDefinition("mcpSyncClient_server_a")).isTrue();
				assertThat(context.getBeanFactory().containsBeanDefinition("mcpSyncClient_server_b")).isTrue();
				assertThat(context.getBeanFactory().containsBeanDefinition("mcpSyncClient_server_c")).isTrue();
			});
	}

	@Test
	void registersOneSelectiveBeanWhenTransportConfigurationsReuseAConnectionName() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.sse.connections.shared.url=http://localhost:8080",
					"spring.ai.mcp.client.streamable-http.connections.shared.url=http://localhost:8081")
			.run(context -> assertThat(context.getBeanFactory().containsBeanDefinition("mcpSyncClient_shared"))
				.isTrue());
	}

	@Test
	void noBeanRegisteredWithoutConnections() {
		this.contextRunner
			.run(context -> assertThat(context.getBeanNamesForType(NamedMcpSyncClientFactoryBean.class)).isEmpty());
	}

	@Test
	void backsOffWhenGeneratedBeanNameIsAlreadyDefined() {
		this.contextRunner.withBean("mcpSyncClient_existing", String.class, () -> "user-defined")
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.existing.url=http://localhost:8080")
			.run(context -> {
				assertThat(context.getBean("mcpSyncClient_existing")).isEqualTo("user-defined");
				assertThat(context.getBeanFactory().getBeanDefinition("mcpSyncClient_existing").getBeanClassName())
					.isEqualTo(String.class.getName());
			});
	}

	@Test
	void backsOffWhenGeneratedBeanNameIsAlreadyAnAlias() {
		this.contextRunner.withBean("existing", String.class, () -> "user-defined")
			.withInitializer(context -> ((BeanDefinitionRegistry) context.getBeanFactory()).registerAlias("existing",
					"mcpSyncClient_existing"))
			.withPropertyValues("spring.ai.mcp.client.streamable-http.connections.existing.url=http://localhost:8080")
			.run(context -> {
				assertThat(context.getBean("mcpSyncClient_existing")).isEqualTo("user-defined");
				assertThat(((BeanDefinitionRegistry) context.getBeanFactory()).isAlias("mcpSyncClient_existing"))
					.isTrue();
			});
	}

	@Test
	void acceptsValidConnectionNames() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.sse.connections.my-server_123.url=http://localhost:8080")
			.run(context -> assertThat(context.getBeanFactory().containsBeanDefinition("mcpSyncClient_my-server_123"))
				.isTrue());
	}

	@Test
	void registersAsyncNamedBeanWhenTypeIsAsync() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.type=ASYNC",
					"spring.ai.mcp.client.sse.connections.async_server.url=http://localhost:8080")
			.run(context -> {
				assertThat(context.getBeanFactory().containsBeanDefinition("mcpAsyncClient_async_server")).isTrue();
				assertThat(context.getBeanFactory().containsBeanDefinition("mcpSyncClient_async_server")).isFalse();
				assertAsyncBeanRegistered(context.getBeanFactory().getBeanDefinition("mcpAsyncClient_async_server"),
						"async_server");
			});
	}

	@Test
	void registersSyncBeansWhenTypeIsDefault() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.sse.connections.default_server.url=http://localhost:8080")
			.run(context -> {
				assertThat(context.getBeanFactory().containsBeanDefinition("mcpSyncClient_default_server")).isTrue();
				assertThat(context.getBeanFactory().containsBeanDefinition("mcpAsyncClient_default_server")).isFalse();
			});
	}

	private static void assertSyncBeanRegistered(BeanDefinition beanDefinition, String connectionName) {
		assertFactoryBeanRegistered(beanDefinition, NamedMcpSyncClientFactoryBean.class, connectionName);
	}

	private static void assertAsyncBeanRegistered(BeanDefinition beanDefinition, String connectionName) {
		assertFactoryBeanRegistered(beanDefinition, NamedMcpAsyncClientFactoryBean.class, connectionName);
	}

	private static void assertFactoryBeanRegistered(BeanDefinition beanDefinition, Class<?> factoryBeanClass,
			String connectionName) {
		assertThat(beanDefinition).isInstanceOf(AbstractBeanDefinition.class);
		AbstractBeanDefinition abstractBeanDefinition = (AbstractBeanDefinition) beanDefinition;
		assertThat(abstractBeanDefinition.getBeanClass()).isEqualTo(factoryBeanClass);
		assertThat(abstractBeanDefinition.getQualifiers()).hasSize(2);
		assertThat(abstractBeanDefinition.getQualifier(McpClient.class.getName()))
			.extracting(qualifier -> qualifier.getAttribute("value"))
			.isEqualTo(connectionName);
		assertThat(abstractBeanDefinition.getQualifier(Qualifier.class.getName()))
			.extracting(qualifier -> qualifier.getAttribute("value"))
			.isEqualTo(connectionName);
		assertThat(abstractBeanDefinition.isDefaultCandidate()).isFalse();
		assertThat(abstractBeanDefinition.isLazyInit()).isTrue();
		assertThat(abstractBeanDefinition.getDestroyMethodName()).isNull();
	}

	@Configuration(proxyBeanMethods = false)
	@Import(McpConnectionBeanRegistrar.class)
	static class TestConfiguration {

	}

}
