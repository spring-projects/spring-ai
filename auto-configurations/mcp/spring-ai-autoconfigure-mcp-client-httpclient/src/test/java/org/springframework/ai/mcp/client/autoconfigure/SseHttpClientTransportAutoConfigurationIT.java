/*
 * Copyright 2024-2025 the original author or authors.
 */

package org.springframework.ai.mcp.client.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.httpclient.autoconfigure.SseHttpClientTransportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.AsyncHttpRequestCustomizer;
import io.modelcontextprotocol.client.transport.SyncHttpRequestCustomizer;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import reactor.core.publisher.Mono;

@Timeout(15)
public class SseHttpClientTransportAutoConfigurationIT {

	private static final Logger logger = LoggerFactory.getLogger(SseHttpClientTransportAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.client.initialized=false",
				"spring.ai.mcp.client.sse.connections.server1.url=" + host)
		.withConfiguration(
				AutoConfigurations.of(McpClientAutoConfiguration.class, SseHttpClientTransportAutoConfiguration.class));

	static String host = "http://localhost:3001";

	// Uses the https://github.com/tzolov/mcp-everything-server-docker-image
	@SuppressWarnings("resource")
	static GenericContainer<?> container = new GenericContainer<>("docker.io/tzolov/mcp-everything-server:v2")
		.withCommand("node dist/index.js sse")
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

			System.out.println("mcpClient = " + mcpClient.getServerInfo());

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

				verify(context.getBean(SyncHttpRequestCustomizer.class), atLeastOnce()).customize(any(), any(), any(),
						any());
				verifyNoInteractions(context.getBean(AsyncHttpRequestCustomizer.class));
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

				verify(context.getBean(AsyncHttpRequestCustomizer.class), atLeastOnce()).customize(any(), any(), any(),
						any());
			});
	}

	@Configuration
	static class SyncRequestCustomizerConfiguration {

		@Bean
		SyncHttpRequestCustomizer syncHttpRequestCustomizer() {
			return mock(SyncHttpRequestCustomizer.class);
		}

	}

	@Configuration
	static class AsyncRequestCustomizerConfiguration {

		@Bean
		AsyncHttpRequestCustomizer asyncHttpRequestCustomizer() {
			AsyncHttpRequestCustomizer requestCustomizerMock = mock(AsyncHttpRequestCustomizer.class);
			when(requestCustomizerMock.customize(any(), any(), any(), any()))
				.thenAnswer(invocation -> Mono.just(invocation.getArguments()[0]));
			return requestCustomizerMock;
		}

	}

}
