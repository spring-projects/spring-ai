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

package org.springframework.ai.mcp.client.autoconfigure;

import java.util.List;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.customizer.McpAsyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.annotations.McpClientAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.client.httpclient.autoconfigure.StreamableHttpHttpClientTransportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Timeout(15)
public class StreamableHttpHttpClientTransportAutoConfigurationIT {

	private static final Logger logger = LoggerFactory
		.getLogger(StreamableHttpHttpClientTransportAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.client.initialized=false",
				"spring.ai.mcp.client.streamable-http.connections.server1.url=" + host)
		.withConfiguration(AutoConfigurations.of(McpClientAutoConfiguration.class,
				McpClientAnnotationScannerAutoConfiguration.class,
				StreamableHttpHttpClientTransportAutoConfiguration.class));

	static String host = "http://localhost:3001";

	// Uses the https://github.com/tzolov/mcp-everything-server-docker-image
	@SuppressWarnings("resource")
	static GenericContainer<?> container = new GenericContainer<>("docker.io/tzolov/mcp-everything-server:v2")
		.withCommand("node dist/index.js streamableHttp")
		.withLogConsumer(outputFrame -> System.out.println(outputFrame.getUtf8String()))
		.withExposedPorts(3001)
		.waitingFor(Wait.forHttp("/").forStatusCode(404));

	@BeforeAll
	static void setUp() {
		container.start();
		int port = container.getMappedPort(3001);
		host = "http://" + container.getHost() + ":" + port;
		logger.info("Container started at host: {}", host);
	}

	@AfterAll
	static void tearDown() {
		container.stop();
	}

	@Test
	void streamableHttpTest() {
		this.contextRunner.run(context -> {
			List<McpSyncClient> mcpClients = (List<McpSyncClient>) context.getBean("mcpSyncClients");

			assertThat(mcpClients).isNotNull();
			assertThat(mcpClients).hasSize(1);

			McpSyncClient mcpClient = mcpClients.get(0);

			mcpClient.ping();

			ListToolsResult toolsResult = mcpClient.listTools();

			assertThat(toolsResult).isNotNull();
			assertThat(toolsResult.tools()).isNotEmpty();
			assertThat(toolsResult.tools()).hasSize(8);

			logger.info("tools = {}", toolsResult);
		});
	}

	@Test
	void usesSyncRequestCustomizer() {
		this.contextRunner
			.withConfiguration(UserConfigurations.of(SyncRequestCustomizerConfiguration.class,
					AsyncRequestCustomizerConfiguration.class))
			.run(context -> {
				List<McpSyncClient> mcpClients = (List<McpSyncClient>) context.getBean("mcpSyncClients");

				assertThat(mcpClients).isNotNull();
				assertThat(mcpClients).hasSize(1);

				McpSyncClient mcpClient = mcpClients.get(0);

				mcpClient.ping();

				verify(context.getBean(McpSyncHttpClientRequestCustomizer.class), atLeastOnce()).customize(any(), any(),
						any(), any(), any());
				verifyNoInteractions(context.getBean(McpAsyncHttpClientRequestCustomizer.class));
			});
	}

	@Test
	void usesAsyncRequestCustomizer() {
		this.contextRunner.withConfiguration(UserConfigurations.of(AsyncRequestCustomizerConfiguration.class))
			.run(context -> {
				List<McpSyncClient> mcpClients = (List<McpSyncClient>) context.getBean("mcpSyncClients");

				assertThat(mcpClients).isNotNull();
				assertThat(mcpClients).hasSize(1);

				McpSyncClient mcpClient = mcpClients.get(0);

				mcpClient.ping();

				verify(context.getBean(McpAsyncHttpClientRequestCustomizer.class), atLeastOnce()).customize(any(),
						any(), any(), any(), any());
			});
	}

	@Configuration
	static class SyncRequestCustomizerConfiguration {

		@Bean
		McpSyncHttpClientRequestCustomizer syncHttpRequestCustomizer() {
			return mock(McpSyncHttpClientRequestCustomizer.class);
		}

	}

	@Configuration
	static class AsyncRequestCustomizerConfiguration {

		@Bean
		McpAsyncHttpClientRequestCustomizer asyncHttpRequestCustomizer() {
			McpAsyncHttpClientRequestCustomizer requestCustomizerMock = mock(McpAsyncHttpClientRequestCustomizer.class);
			when(requestCustomizerMock.customize(any(), any(), any(), any(), any()))
				.thenAnswer(invocation -> Mono.just(invocation.getArguments()[0]));
			return requestCustomizerMock;
		}

	}

}
