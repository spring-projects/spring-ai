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

import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.McpClient;
import org.springframework.ai.mcp.annotation.spring.ClientMcpAsyncHandlersRegistry;
import org.springframework.ai.mcp.annotation.spring.ClientMcpSyncHandlersRegistry;
import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpAsyncClientConfigurer;
import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpSyncClientConfigurer;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link McpConnectionBeanRegistrar}.
 *
 * @author Taewoong Kim
 */
class McpConnectionBeanRegistrarTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfig.class)
		.withInitializer(context -> context
			.addBeanFactoryPostProcessor(new McpConnectionBeanRegistrar(context.getEnvironment())));

	@Test
	void registersNamedBeanForSseConnection() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.sse.connections.test_server.url=http://localhost:8080")
			.run(context -> {
				assertThat(context.getBeanFactory().containsBeanDefinition("mcpSyncClient_test_server")).isTrue();

				BeanDefinition def = context.getBeanFactory().getBeanDefinition("mcpSyncClient_test_server");
				assertThat(def).isInstanceOf(AbstractBeanDefinition.class);
				AbstractBeanDefinition abd = (AbstractBeanDefinition) def;

				assertThat(abd.getQualifiers()).anyMatch(q -> McpClient.class.getName().equals(q.getTypeName())
						&& "test_server".equals(q.getAttribute("value")));

				// destroyMethod must be null - Spring validates it against FactoryBean
				// class,
				// not the produced McpSyncClient. McpSyncClient's close() is
				// auto-inferred.
				assertThat(abd.getDestroyMethodName()).isNull();
			});
	}

	@Test
	void registersMultipleNamedBeans() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.sse.connections.server_a.url=http://localhost:8080",
					"spring.ai.mcp.client.sse.connections.server_b.url=http://localhost:8081")
			.run(context -> {
				assertThat(context.getBeanFactory().containsBeanDefinition("mcpSyncClient_server_a")).isTrue();
				assertThat(context.getBeanFactory().containsBeanDefinition("mcpSyncClient_server_b")).isTrue();
			});
	}

	@Test
	void registersNamedBeanForStreamableHttpConnection() {
		this.contextRunner
			.withPropertyValues(
					"spring.ai.mcp.client.streamable-http.connections.http_server.url=http://localhost:9090")
			.run(context -> assertThat(context.getBeanFactory().containsBeanDefinition("mcpSyncClient_http_server"))
				.isTrue());
	}

	@Test
	void handlesMixedTransports() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.sse.connections.sse_server.url=http://localhost:8080",
					"spring.ai.mcp.client.streamable-http.connections.http_server.url=http://localhost:9090")
			.run(context -> {
				assertThat(context.getBeanFactory().containsBeanDefinition("mcpSyncClient_sse_server")).isTrue();
				assertThat(context.getBeanFactory().containsBeanDefinition("mcpSyncClient_http_server")).isTrue();
			});
	}

	@Test
	void noBeanRegisteredWithoutConnections() {
		this.contextRunner
			.run(context -> assertThat(context.getBeanNamesForType(NamedMcpSyncClientFactoryBean.class)).isEmpty());
	}

	@Test
	void acceptsValidConnectionNames() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.sse.connections.my-server_123.url=http://localhost:8080")
			.run(context -> assertThat(context.getBeanFactory().containsBeanDefinition("mcpSyncClient_my-server_123"))
				.isTrue());
	}

	// Async mode tests

	@Test
	void registersAsyncNamedBeanWhenTypeIsAsync() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.type=ASYNC",
					"spring.ai.mcp.client.sse.connections.async_server.url=http://localhost:8080")
			.run(context -> {
				assertThat(context.getBeanFactory().containsBeanDefinition("mcpAsyncClient_async_server")).isTrue();
				assertThat(context.getBeanFactory().containsBeanDefinition("mcpSyncClient_async_server")).isFalse();

				BeanDefinition def = context.getBeanFactory().getBeanDefinition("mcpAsyncClient_async_server");
				assertThat(def).isInstanceOf(AbstractBeanDefinition.class);
				AbstractBeanDefinition abd = (AbstractBeanDefinition) def;

				assertThat(abd.getQualifiers()).anyMatch(q -> McpClient.class.getName().equals(q.getTypeName())
						&& "async_server".equals(q.getAttribute("value")));
			});
	}

	@Test
	void registersMultipleAsyncNamedBeans() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.type=ASYNC",
					"spring.ai.mcp.client.sse.connections.server_a.url=http://localhost:8080",
					"spring.ai.mcp.client.sse.connections.server_b.url=http://localhost:8081")
			.run(context -> {
				assertThat(context.getBeanFactory().containsBeanDefinition("mcpAsyncClient_server_a")).isTrue();
				assertThat(context.getBeanFactory().containsBeanDefinition("mcpAsyncClient_server_b")).isTrue();
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

	@Configuration
	static class TestConfig {

		@Bean
		public McpSyncClientConfigurer mcpSyncClientConfigurer() {
			return new McpSyncClientConfigurer(Collections.emptyList());
		}

		@Bean
		public McpClientCommonProperties mcpClientCommonProperties() {
			return new McpClientCommonProperties();
		}

		@Bean
		public ObjectProvider<?> transportsProvider() {
			return mock(ObjectProvider.class);
		}

		@Bean
		public ClientMcpSyncHandlersRegistry clientMcpSyncHandlersRegistry() {
			return mock(ClientMcpSyncHandlersRegistry.class);
		}

		@Bean
		public McpSyncClientFactory mcpSyncClientFactory(McpClientCommonProperties commonProperties,
				McpSyncClientConfigurer configurer, ClientMcpSyncHandlersRegistry handlersRegistry) {
			return new McpSyncClientFactory(commonProperties, configurer, handlersRegistry);
		}

		// Async beans for async mode tests

		@Bean
		public McpAsyncClientConfigurer mcpAsyncClientConfigurer() {
			return new McpAsyncClientConfigurer(Collections.emptyList());
		}

		@Bean
		public ClientMcpAsyncHandlersRegistry clientMcpAsyncHandlersRegistry() {
			return mock(ClientMcpAsyncHandlersRegistry.class);
		}

		@Bean
		public McpAsyncClientFactory mcpAsyncClientFactory(McpClientCommonProperties commonProperties,
				McpAsyncClientConfigurer configurer, ClientMcpAsyncHandlersRegistry handlersRegistry) {
			return new McpAsyncClientFactory(commonProperties, configurer, handlersRegistry);
		}

	}

}
